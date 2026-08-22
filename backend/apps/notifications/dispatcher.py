from collections import namedtuple
import datetime
from datetime import timedelta
import logging
from typing import Any, Dict, List, Optional
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from django.db import transaction
from django.utils import timezone as django_timezone

from apps.commitments.models import Commitment
from apps.goals.models import ChatMessage, Goal, GoalCheckIn, GoalParticipant
from apps.notifications.fcm import FcmClientProtocol, get_fcm_client
from apps.notifications.models import (
    NotificationDelivery,
    Reminder,
    UserDevice,
    UserNotificationPreferences,
)
from apps.notifications.services import (
    cancel_reminders_for_entity,
    get_or_create_preferences,
    mark_reminder_dispatched,
    mark_reminder_failed,
    mark_reminder_suppressed,
)

logger = logging.getLogger("promise")

BATCH_SIZE = 50
LEASE_DURATION = timedelta(minutes=5)
MAX_DELIVERY_ATTEMPTS = 5

_BACKOFF = (
    timedelta(seconds=15),
    timedelta(minutes=1),
    timedelta(minutes=5),
    timedelta(minutes=15),
)

DispatchResult = namedtuple("DispatchResult", ["dispatched", "suppressed", "cancelled", "retried", "failed"])


def backoff_for_attempt(attempts: int) -> timedelta:
    if attempts < 1:
        attempts = 1
    idx = min(attempts, len(_BACKOFF)) - 1
    return _BACKOFF[idx]


def is_in_quiet_hours(user_tz: str, quiet_start: datetime.time, quiet_end: datetime.time, now_utc: datetime.datetime) -> bool:
    """Checks whether the current time falls within user's quiet hours."""
    try:
        tz = ZoneInfo(user_tz)
    except (ZoneInfoNotFoundError, ValueError):
        tz = ZoneInfo("UTC")

    local_dt = now_utc.astimezone(tz)
    current_time = local_dt.time()

    if quiet_start <= quiet_end:
        # e.g., 01:00 to 06:00
        return quiet_start <= current_time < quiet_end
    else:
        # e.g., 22:00 to 08:00 (spans midnight)
        return current_time >= quiet_start or current_time < quiet_end


def next_quiet_hours_exit(user_tz: str, quiet_end: datetime.time, now_utc: datetime.datetime) -> datetime.datetime:
    """Calculates the next upcoming quiet hours exit instant in UTC."""
    try:
        tz = ZoneInfo(user_tz)
    except (ZoneInfoNotFoundError, ValueError):
        tz = ZoneInfo("UTC")

    local_dt = now_utc.astimezone(tz)
    exit_today = local_dt.replace(
        hour=quiet_end.hour,
        minute=quiet_end.minute,
        second=quiet_end.second,
        microsecond=0,
    )
    if exit_today <= local_dt:
        exit_dt = exit_today + timedelta(days=1)
    else:
        exit_dt = exit_today
    return exit_dt.astimezone(datetime.timezone.utc)


def dispatch_due_reminders(*, fcm_client: Optional[FcmClientProtocol] = None) -> DispatchResult:
    """Claims due reminders and executes per-device push notification delivery."""
    if fcm_client is None:
        fcm_client = get_fcm_client()

    dispatched = 0
    suppressed = 0
    cancelled = 0
    retried = 0
    failed = 0

    while True:
        batch_res = _dispatch_batch(fcm_client)
        dispatched += batch_res.dispatched
        suppressed += batch_res.suppressed
        cancelled += batch_res.cancelled
        retried += batch_res.retried
        failed += batch_res.failed
        if batch_res.claimed == 0:
            break

    return DispatchResult(
        dispatched=dispatched,
        suppressed=suppressed,
        cancelled=cancelled,
        retried=retried,
        failed=failed,
    )


_BatchResult = namedtuple("_BatchResult", ["dispatched", "suppressed", "cancelled", "retried", "failed", "claimed"])


def _dispatch_batch(fcm_client: FcmClientProtocol) -> _BatchResult:
    now = django_timezone.now()
    dispatched = 0
    suppressed = 0
    cancelled = 0
    retried = 0
    failed = 0

    # Step 1: Claim due reminders with SKIP LOCKED and lease
    with transaction.atomic():
        due_query = (
            Reminder.objects.select_for_update(skip_locked=True)
            .filter(
                status=Reminder.ReminderStatus.SCHEDULED,
                scheduled_for__lte=now,
            )
            .order_by("scheduled_for", "created_at")[:BATCH_SIZE]
        )
        expired_lease_query = (
            Reminder.objects.select_for_update(skip_locked=True)
            .filter(
                status=Reminder.ReminderStatus.CLAIMED,
                lease_expires_at__lte=now,
            )
            .order_by("lease_expires_at", "created_at")[:BATCH_SIZE]
        )

        reminders = list(due_query) or list(expired_lease_query)
        for r in reminders:
            r.status = Reminder.ReminderStatus.CLAIMED
            r.claimed_at = now
            r.lease_expires_at = now + LEASE_DURATION
            r.save(update_fields=["status", "claimed_at", "lease_expires_at", "updated_at"])

    claimed_count = len(reminders)
    if claimed_count == 0:
        return _BatchResult(0, 0, 0, 0, 0, 0)

    # Step 2: Process each claimed reminder
    for reminder in reminders:
        # Re-fetch with preferences & user
        user = reminder.user
        prefs = get_or_create_preferences(user)

        # Check 1: User master & category preferences
        if not prefs.enabled:
            mark_reminder_suppressed(reminder.id, "USER_DISABLED")
            suppressed += 1
            continue

        if reminder.event_type == "commitment.due_soon" and not prefs.commitments_due_soon:
            mark_reminder_suppressed(reminder.id, "CATEGORY_DISABLED")
            suppressed += 1
            continue
        if reminder.event_type == "commitment.due_now" and not prefs.commitments_due_now:
            mark_reminder_suppressed(reminder.id, "CATEGORY_DISABLED")
            suppressed += 1
            continue
        if reminder.event_type == "commitment.overdue" and not prefs.commitments_overdue:
            mark_reminder_suppressed(reminder.id, "CATEGORY_DISABLED")
            suppressed += 1
            continue
        if reminder.event_type == "goal.today_practice" and not prefs.goals_daily_reminder:
            mark_reminder_suppressed(reminder.id, "CATEGORY_DISABLED")
            suppressed += 1
            continue
        if reminder.event_type in ("goal.streak_protection", "goal.checkin_reminder") and not prefs.goals_evening_reminder:
            mark_reminder_suppressed(reminder.id, "CATEGORY_DISABLED")
            suppressed += 1
            continue

        # Check 2: Quiet hours
        if prefs.quiet_hours_enabled and is_in_quiet_hours(user.timezone, prefs.quiet_hours_start, prefs.quiet_hours_end, now):
            # If it's a non-critical reminder, defer to quiet hours exit
            if reminder.event_type != "commitment.due_now":
                next_exit = next_quiet_hours_exit(user.timezone, prefs.quiet_hours_end, now)
                reminder.status = Reminder.ReminderStatus.SCHEDULED
                reminder.scheduled_for = next_exit
                reminder.lease_expires_at = None
                reminder.save(update_fields=["status", "scheduled_for", "lease_expires_at", "updated_at"])
                retried += 1
                continue

        # Check 3: Resolved state
        if reminder.entity_type == Reminder.EntityType.COMMITMENT:
            commitment = Commitment.objects.filter(id=reminder.entity_id).first()
            if commitment is None or commitment.status in (Commitment.Status.COMPLETED, Commitment.Status.CANCELLED):
                cancel_reminders_for_entity(Reminder.EntityType.COMMITMENT, reminder.entity_id, "RESOLVED")
                cancelled += 1
                continue
            if commitment.status == Commitment.Status.SNOOZED and reminder.event_type != "commitment.snooze_expired":
                if commitment.snoozed_until and commitment.snoozed_until > now:
                    cancel_reminders_for_entity(Reminder.EntityType.COMMITMENT, reminder.entity_id, "SNOOZED")
                    cancelled += 1
                    continue
            title = "Due now" if reminder.event_type == "commitment.due_now" else "Due soon"
            body = commitment.title
            deep_link = f"promise://commitment/{commitment.id}"

        elif reminder.entity_type == Reminder.EntityType.GOAL:
            goal = Goal.objects.filter(id=reminder.entity_id).first()
            if goal is None or goal.status != Goal.Status.ACTIVE:
                cancel_reminders_for_entity(Reminder.EntityType.GOAL, reminder.entity_id, "PAUSED")
                cancelled += 1
                continue

            if reminder.event_type == "goal.chat.message_created":
                is_active_member = GoalParticipant.objects.filter(
                    goal=goal,
                    user=reminder.user,
                    status=GoalParticipant.Status.ACTIVE,
                ).exists()
                if not is_active_member:
                    mark_reminder_suppressed(reminder.id, "NO_LONGER_ACTIVE_PARTICIPANT")
                    suppressed += 1
                    continue

                msg = (
                    ChatMessage.objects.filter(id=reminder.target_period, goal=goal)
                    .select_related("sender")
                    .first()
                )
                if msg is None:
                    mark_reminder_suppressed(reminder.id, "MESSAGE_NOT_FOUND")
                    suppressed += 1
                    continue

                sender_name = msg.sender.name or msg.sender.email.split("@")[0]
                from apps.goals.consumers import is_user_present_in_chat

                if is_user_present_in_chat(str(goal.id), str(reminder.user.id)):
                    mark_reminder_suppressed(reminder.id, "PRESENCE_ACTIVE_IN_CHAT")
                    suppressed += 1
                    continue

                title = goal.title
                body = f"{sender_name}: {msg.body}"
                deep_link = f"promise://goal/{goal.id}/chat"
            else:
                if reminder.target_period:
                    if GoalCheckIn.objects.filter(goal_id=reminder.entity_id, period_date=reminder.target_period).exists():
                        cancel_reminders_for_entity(Reminder.EntityType.GOAL, reminder.entity_id, "ALREADY_CHECKED_IN")
                        cancelled += 1
                        continue
                title = "Practice check-in" if "streak" in reminder.event_type else "Today’s practice"
                body = goal.title
                deep_link = f"promise://goal/{goal.id}"
        else:
            title = "Promise Reminder"
            body = "You have an active promise item."
            deep_link = "promise://home"

        # Check 4: Active Devices
        active_devices = list(UserDevice.objects.filter(user=user, is_active=True))
        if not active_devices:
            mark_reminder_suppressed(reminder.id, "NO_ACTIVE_DEVICES")
            suppressed += 1
            continue

        priority = "high" if reminder.event_type == "commitment.due_now" else "normal"
        payload_data = {
            "reminder_id": str(reminder.id),
            "identity_key": reminder.identity_key,
            "entity_type": reminder.entity_type,
            "entity_id": str(reminder.entity_id),
            "event_type": reminder.event_type,
            "title": title,
            "body": body,
            "deep_link": deep_link,
        }

        # Step 3: Dispatch per-device
        for device in active_devices:
            delivery, _ = NotificationDelivery.objects.get_or_create(
                reminder=reminder,
                user_device=device,
            )
            if delivery.status in (
                NotificationDelivery.DeliveryStatus.SENT,
                NotificationDelivery.DeliveryStatus.CANCELLED,
            ):
                continue
            if delivery.next_attempt_at and delivery.next_attempt_at > now:
                continue

            # Send via FCM
            send_res = fcm_client.send(
                token=device.fcm_token,
                title=title,
                body=body,
                data=payload_data,
                priority=priority,
            )

            if send_res.success:
                delivery.status = NotificationDelivery.DeliveryStatus.SENT
                delivery.sent_at = django_timezone.now()
                delivery.last_error = ""
                delivery.save(update_fields=["status", "sent_at", "last_error", "updated_at"])
            elif send_res.is_unregistered:
                device.is_active = False
                device.save(update_fields=["is_active", "updated_at"])
                delivery.status = NotificationDelivery.DeliveryStatus.CANCELLED
                delivery.last_error = "registration-token-not-registered"
                delivery.save(update_fields=["status", "last_error", "updated_at"])
            else:
                # Transient failure
                delivery.attempts += 1
                delivery.last_error = send_res.error
                if delivery.attempts >= MAX_DELIVERY_ATTEMPTS:
                    delivery.status = NotificationDelivery.DeliveryStatus.FAILED
                    delivery.next_attempt_at = None
                else:
                    delivery.status = NotificationDelivery.DeliveryStatus.PENDING
                    delivery.next_attempt_at = django_timezone.now() + backoff_for_attempt(delivery.attempts)
                delivery.save(update_fields=["attempts", "last_error", "status", "next_attempt_at", "updated_at"])

        # Step 4: Resolve logical reminder status
        all_deliveries = list(NotificationDelivery.objects.filter(reminder=reminder))
        pending = [d for d in all_deliveries if d.status == NotificationDelivery.DeliveryStatus.PENDING]

        if pending:
            # Retries remain
            reminder.status = Reminder.ReminderStatus.SCHEDULED
            valid_next = [d.next_attempt_at for d in pending if d.next_attempt_at]
            reminder.scheduled_for = min(valid_next) if valid_next else (django_timezone.now() + timedelta(minutes=1))
            reminder.lease_expires_at = None
            reminder.save(update_fields=["status", "scheduled_for", "lease_expires_at", "updated_at"])
            retried += 1
        else:
            # No pending deliveries
            sent_count = sum(1 for d in all_deliveries if d.status == NotificationDelivery.DeliveryStatus.SENT)
            failed_count = sum(1 for d in all_deliveries if d.status == NotificationDelivery.DeliveryStatus.FAILED)

            if sent_count > 0:
                reminder.status = Reminder.ReminderStatus.DISPATCHED
                reminder.dispatched_at = django_timezone.now()
                reminder.lease_expires_at = None
                reminder.save(update_fields=["status", "dispatched_at", "lease_expires_at", "updated_at"])
                dispatched += 1
            elif failed_count == len(all_deliveries):
                reminder.status = Reminder.ReminderStatus.FAILED
                reminder.lease_expires_at = None
                reminder.save(update_fields=["status", "lease_expires_at", "updated_at"])
                failed += 1
            else:
                reminder.status = Reminder.ReminderStatus.SUPPRESSED
                reminder.cancellation_reason = "ALL_DEVICES_UNREGISTERED"
                reminder.lease_expires_at = None
                reminder.save(update_fields=["status", "cancellation_reason", "lease_expires_at", "updated_at"])
                suppressed += 1

    return _BatchResult(dispatched, suppressed, cancelled, retried, failed, claimed_count)
