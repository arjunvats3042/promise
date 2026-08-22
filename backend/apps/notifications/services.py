import datetime
from typing import Optional, Union
import uuid

from django.db import transaction
from django.utils import timezone as django_timezone

from apps.notifications.models import Reminder, UserNotificationPreferences
from apps.users.models import User


def get_or_create_preferences(user: User) -> UserNotificationPreferences:
    """Retrieves or creates durable notification preferences for a user."""
    prefs, _ = UserNotificationPreferences.objects.get_or_create(user=user)
    return prefs


def schedule_reminder(
    user: User,
    entity_type: str,
    entity_id: Union[str, uuid.UUID],
    event_type: str,
    scheduled_for: datetime.datetime,
    target_timestamp: Optional[datetime.datetime] = None,
    target_period: str = "",
    identity_key: Optional[str] = None,
) -> tuple[Reminder, bool]:
    """Schedules a durable reminder instance idempotently.

    Returns (reminder, created).
    """
    if identity_key is None:
        target = target_timestamp or target_period
        identity_key = Reminder.compute_identity_key(
            user_id=user.id,
            entity_type=entity_type,
            entity_id=entity_id,
            event_type=event_type,
            target=target,
        )

    with transaction.atomic():
        reminder, created = Reminder.objects.select_for_update().get_or_create(
            identity_key=identity_key,
            defaults={
                "user": user,
                "entity_type": entity_type.upper(),
                "entity_id": entity_id,
                "event_type": event_type,
                "scheduled_for": scheduled_for,
                "target_timestamp": target_timestamp,
                "target_period": target_period,
                "status": Reminder.ReminderStatus.SCHEDULED,
            },
        )
    return reminder, created


def cancel_reminders_for_entity(
    entity_type: str,
    entity_id: Union[str, uuid.UUID],
    reason: str = "RESOLVED",
) -> int:
    """Cancels all active/scheduled reminders for a specific entity.

    Used when a commitment or goal is completed, cancelled, snoozed, or checked in.
    Returns the number of cancelled reminders.
    """
    now = django_timezone.now()
    with transaction.atomic():
        updated_count = Reminder.objects.filter(
            entity_type=entity_type.upper(),
            entity_id=entity_id,
            status__in=[Reminder.ReminderStatus.SCHEDULED, Reminder.ReminderStatus.CLAIMED],
        ).update(
            status=Reminder.ReminderStatus.CANCELLED,
            cancelled_at=now,
            cancellation_reason=reason,
        )
    return updated_count


def mark_reminder_dispatched(reminder_id: Union[str, uuid.UUID]) -> Optional[Reminder]:
    """Marks a reminder as dispatched."""
    now = django_timezone.now()
    with transaction.atomic():
        try:
            reminder = Reminder.objects.select_for_update().get(id=reminder_id)
        except Reminder.DoesNotExist:
            return None
        reminder.status = Reminder.ReminderStatus.DISPATCHED
        reminder.dispatched_at = now
        reminder.attempts += 1
        reminder.save(update_fields=["status", "dispatched_at", "attempts", "updated_at"])
        return reminder


def mark_reminder_suppressed(
    reminder_id: Union[str, uuid.UUID],
    reason: str = "SUPPRESSED",
) -> Optional[Reminder]:
    """Marks a reminder as suppressed (e.g. quiet hours, user disabled)."""
    now = django_timezone.now()
    with transaction.atomic():
        try:
            reminder = Reminder.objects.select_for_update().get(id=reminder_id)
        except Reminder.DoesNotExist:
            return None
        reminder.status = Reminder.ReminderStatus.SUPPRESSED
        reminder.cancelled_at = now
        reminder.cancellation_reason = reason
        reminder.save(update_fields=["status", "cancelled_at", "cancellation_reason", "updated_at"])
        return reminder


def mark_reminder_failed(
    reminder_id: Union[str, uuid.UUID],
    error: str,
) -> Optional[Reminder]:
    """Marks a reminder attempt as failed."""
    with transaction.atomic():
        try:
            reminder = Reminder.objects.select_for_update().get(id=reminder_id)
        except Reminder.DoesNotExist:
            return None
        reminder.status = Reminder.ReminderStatus.FAILED
        reminder.attempts += 1
        reminder.last_error = error
        reminder.save(update_fields=["status", "attempts", "last_error", "updated_at"])
        return reminder


def handle_goal_event(envelope: dict) -> None:
    """Processes goal integration events for notifications."""
    event_type = envelope.get("event_type")
    if event_type == "goal.chat.message_created":
        payload = envelope.get("payload", {})
        goal_id = payload.get("goal_id")
        message_id = payload.get("message_id")
        sender_id = payload.get("sender_id")

        if not goal_id or not message_id or not sender_id:
            return

        from apps.goals.models import ChatMessage, Goal, GoalParticipant

        goal = Goal.objects.filter(id=goal_id, is_shared=True, status=Goal.Status.ACTIVE).first()
        if not goal:
            return

        msg = ChatMessage.objects.filter(id=message_id, goal=goal).first()
        if not msg:
            return

        active_participants = (
            GoalParticipant.objects.filter(
                goal=goal,
                status=GoalParticipant.Status.ACTIVE,
            )
            .exclude(user_id=sender_id)
            .select_related("user")
        )

        now = django_timezone.now()
        target_hex = uuid.UUID(str(message_id)).hex
        for participant in active_participants:
            identity_key = f"{participant.user_id}:GOAL:{goal.id}:goal.chat.message_created:{message_id}"
            schedule_reminder(
                user=participant.user,
                entity_type=Reminder.EntityType.GOAL,
                entity_id=goal.id,
                event_type="goal.chat.message_created",
                scheduled_for=now,
                target_timestamp=msg.created_at,
                target_period=target_hex,
                identity_key=identity_key,
            )
