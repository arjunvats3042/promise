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
    payload = envelope.get("payload", {})
    goal_id = payload.get("goal_id")

    if not goal_id:
        return

    from apps.goals.models import ChatMessage, Goal, GoalParticipant

    goal = Goal.objects.filter(id=goal_id, is_shared=True, status=Goal.Status.ACTIVE).first()
    if not goal:
        return

    now = django_timezone.now()

    if event_type == "goal.chat.message_created":
        message_id = payload.get("message_id")
        sender_id = payload.get("sender_id")
        if not message_id or not sender_id:
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

    elif event_type == "goal.participant.joined":
        actor_id = payload.get("user_id") or payload.get("actor_id")
        owner = goal.created_by
        if owner and str(owner.id) != str(actor_id):
            actor_name = payload.get("user_name", "A participant")
            identity_key = f"{owner.id}:GOAL:{goal.id}:goal.participant.joined:{actor_id}"
            schedule_reminder(
                user=owner,
                entity_type=Reminder.EntityType.GOAL,
                entity_id=goal.id,
                event_type="goal.participant.joined",
                scheduled_for=now,
                target_period=str(actor_id or ""),
                identity_key=identity_key,
            )

    elif event_type == "goal.participant.left":
        actor_id = payload.get("user_id") or payload.get("actor_id")
        owner = goal.created_by
        if owner and str(owner.id) != str(actor_id):
            identity_key = f"{owner.id}:GOAL:{goal.id}:goal.participant.left:{actor_id}:{now.date().isoformat()}"
            schedule_reminder(
                user=owner,
                entity_type=Reminder.EntityType.GOAL,
                entity_id=goal.id,
                event_type="goal.participant.left",
                scheduled_for=now,
                target_period=str(actor_id or ""),
                identity_key=identity_key,
            )

    elif event_type == "goal.participant.removed":
        target_user_id = payload.get("user_id")
        if target_user_id:
            target_user = User.objects.filter(id=target_user_id).first()
            if target_user:
                identity_key = f"{target_user.id}:GOAL:{goal.id}:goal.participant.removed:{now.date().isoformat()}"
                schedule_reminder(
                    user=target_user,
                    entity_type=Reminder.EntityType.GOAL,
                    entity_id=goal.id,
                    event_type="goal.participant.removed",
                    scheduled_for=now,
                    target_period=str(goal.id),
                    identity_key=identity_key,
                )

    elif event_type == "goal.ownership_transferred":
        new_owner_id = payload.get("new_owner_id")
        if new_owner_id:
            new_owner = User.objects.filter(id=new_owner_id).first()
            if new_owner:
                identity_key = f"{new_owner.id}:GOAL:{goal.id}:goal.ownership_transferred:{now.date().isoformat()}"
                schedule_reminder(
                    user=new_owner,
                    entity_type=Reminder.EntityType.GOAL,
                    entity_id=goal.id,
                    event_type="goal.ownership_transferred",
                    scheduled_for=now,
                    target_period=str(goal.id),
                    identity_key=identity_key,
                )


SYSTEM_UUID = uuid.UUID("00000000-0000-0000-0000-000000000000")
WEEKLY_DIGEST_ANCHOR_DAY = 6  # Sunday
WEEKLY_DIGEST_ANCHOR_TIME = datetime.time(19, 0)


def calculate_weekly_digest_summary(user: User, week_start: datetime.date, week_end: datetime.date) -> Optional[dict]:
    """Calculates deterministic weekly summary metrics for a user's activity in a given week.

    Returns None if there was zero meaningful activity during the week.
    """
    from apps.commitments.models import CommitmentEvent
    from apps.goals.models import Goal, GoalCheckIn, GoalParticipant

    # 1. Commitments completed by user in the week
    completed_commitments = CommitmentEvent.objects.filter(
        commitment__created_by=user,
        event_type="COMPLETED",
        created_at__date__gte=week_start,
        created_at__date__lte=week_end,
    ).count()

    # 2. Personal goal practices completed
    user_completed_practices = GoalCheckIn.objects.filter(
        goal__created_by=user,
        created_by=user,
        status=GoalCheckIn.Status.COMPLETED,
        period_date__gte=week_start,
        period_date__lte=week_end,
    ).count()

    # 3. Shared goal practices completed across all groups user is in
    user_active_shared_goals = Goal.objects.filter(
        is_shared=True,
        participants__user=user,
        participants__status=GoalParticipant.Status.ACTIVE,
    ).distinct()

    shared_practices = 0
    if user_active_shared_goals.exists():
        shared_practices = GoalCheckIn.objects.filter(
            goal__in=user_active_shared_goals,
            status=GoalCheckIn.Status.COMPLETED,
            period_date__gte=week_start,
            period_date__lte=week_end,
        ).count()

    total_activity = completed_commitments + user_completed_practices + shared_practices
    if total_activity == 0:
        return None

    # Construct neutral, non-shaming copy
    parts = []
    if completed_commitments > 0:
        parts.append(f"{completed_commitments} commitment{'s' if completed_commitments != 1 else ''} completed")
    if user_completed_practices > 0:
        parts.append(f"{user_completed_practices} practice{'s' if user_completed_practices != 1 else ''} completed")
    if shared_practices > 0:
        parts.append(f"{shared_practices} shared practice{'s' if shared_practices != 1 else ''} completed")

    headline = " • ".join(parts) if parts else "Weekly activity summary"
    body = f"This week: {headline}."

    return {
        "completed_commitments": completed_commitments,
        "user_completed_practices": user_completed_practices,
        "shared_practices": shared_practices,
        "total_activity": total_activity,
        "body": body,
    }


def schedule_weekly_digest(user: User, reference_date: Optional[datetime.date] = None) -> Optional[Reminder]:
    """Generates and schedules an idempotent weekly digest reminder for the user.

    Returns the Reminder or None if no activity or already scheduled.
    """
    from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

    try:
        tz = ZoneInfo(user.timezone or "UTC")
    except (ZoneInfoNotFoundError, ValueError):
        tz = ZoneInfo("UTC")

    now = django_timezone.now().astimezone(tz)
    target_date = reference_date or now.date()

    # Current week bounds (Monday to Sunday)
    week_start = target_date - datetime.timedelta(days=target_date.weekday())
    week_end = week_start + datetime.timedelta(days=6)
    iso_year, iso_week, _ = week_start.isocalendar()
    iso_week_str = f"{iso_year}-W{iso_week:02d}"

    summary = calculate_weekly_digest_summary(user, week_start, week_end)
    if summary is None:
        return None

    # Schedule for Sunday at anchor time in user's timezone
    scheduled_dt_local = datetime.datetime.combine(week_end, WEEKLY_DIGEST_ANCHOR_TIME, tzinfo=tz)
    scheduled_dt_utc = scheduled_dt_local.astimezone(datetime.timezone.utc)

    identity_key = f"{user.id}:DIGEST:{SYSTEM_UUID}:digest.weekly:{iso_week_str}"

    reminder, _ = schedule_reminder(
        user=user,
        entity_type=Reminder.EntityType.DIGEST,
        entity_id=SYSTEM_UUID,
        event_type="digest.weekly",
        scheduled_for=scheduled_dt_utc,
        target_period=iso_week_str,
        identity_key=identity_key,
    )
    return reminder


def schedule_security_alert(user: User, event_type: str, metadata: Optional[dict] = None) -> Reminder:
    """Schedules an immediate high-priority security reminder for a user."""
    now = django_timezone.now()
    metadata_id = metadata.get("event_id") if metadata else None
    unique_marker = metadata_id or str(uuid.uuid4())
    identity_key = f"{user.id}:SECURITY:{SYSTEM_UUID}:{event_type}:{unique_marker}"

    reminder, _ = schedule_reminder(
        user=user,
        entity_type=Reminder.EntityType.SECURITY,
        entity_id=SYSTEM_UUID,
        event_type=event_type,
        scheduled_for=now,
        target_period=unique_marker,
        identity_key=identity_key,
    )
    return reminder
