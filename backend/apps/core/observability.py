"""Lightweight observability and diagnostic helpers for async queues and workers."""

from django.utils import timezone

from apps.notifications.models import Reminder
from apps.outbox.models import OutboxEvent


def get_outbox_metrics():
    """Returns real-time snapshot of the transactional outbox queue."""
    now = timezone.now()
    pending = OutboxEvent.objects.filter(published_at__isnull=True)
    pending_count = pending.count()
    failed_count = pending.filter(attempts__gt=0).count()

    oldest_event = pending.order_by("created_at").first()
    oldest_age_seconds = None
    if oldest_event is not None:
        oldest_age_seconds = max(0, int((now - oldest_event.created_at).total_seconds()))

    return {
        "pending_count": pending_count,
        "failed_count": failed_count,
        "oldest_pending_age_seconds": oldest_age_seconds,
    }


def get_notification_metrics():
    """Returns real-time snapshot of reminder scheduling and delivery state."""
    scheduled = Reminder.objects.filter(status=Reminder.ReminderStatus.SCHEDULED).count()
    claimed = Reminder.objects.filter(status=Reminder.ReminderStatus.CLAIMED).count()
    failed = Reminder.objects.filter(status=Reminder.ReminderStatus.FAILED).count()
    dispatched = Reminder.objects.filter(status=Reminder.ReminderStatus.DISPATCHED).count()
    suppressed = Reminder.objects.filter(status=Reminder.ReminderStatus.SUPPRESSED).count()
    cancelled = Reminder.objects.filter(status=Reminder.ReminderStatus.CANCELLED).count()

    return {
        "scheduled_count": scheduled,
        "claimed_count": claimed,
        "failed_count": failed,
        "dispatched_count": dispatched,
        "suppressed_count": suppressed,
        "cancelled_count": cancelled,
    }
