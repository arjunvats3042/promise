import datetime
from typing import Optional, Union

from django.conf import settings
from django.db import models
from django.utils import timezone as django_timezone

from apps.core.models import BaseModel, UUIDBaseModel


class UserNotificationPreferences(BaseModel):
    """Durable user notification preferences across all client devices.

    Stored per user in PostgreSQL. Local daylight saving time and timezone
    calculations use User.timezone dynamically.
    """

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="notification_preferences",
    )
    enabled = models.BooleanField(default=True)

    # Commitment categories
    commitments_due_soon = models.BooleanField(default=True)
    commitments_due_now = models.BooleanField(default=True)
    commitments_overdue = models.BooleanField(default=True)

    # Goal / Practice categories & anchor times
    goals_daily_reminder = models.BooleanField(default=True)
    goals_daily_reminder_time = models.TimeField(default=datetime.time(8, 30))
    goals_evening_reminder = models.BooleanField(default=True)
    goals_evening_reminder_time = models.TimeField(default=datetime.time(20, 30))

    # Shared goal categories
    shared_goals_activity = models.BooleanField(default=True)
    shared_goals_chat = models.BooleanField(default=True)

    # Weekly digest
    weekly_digest_enabled = models.BooleanField(default=False)

    # Quiet hours
    quiet_hours_enabled = models.BooleanField(default=True)
    quiet_hours_start = models.TimeField(default=datetime.time(22, 0))
    quiet_hours_end = models.TimeField(default=datetime.time(8, 0))

    class Meta:
        db_table = "user_notification_preferences"
        verbose_name = "user notification preference"
        verbose_name_plural = "user notification preferences"

    def __str__(self):
        return f"NotificationPreferences(user_id={self.user_id}, enabled={self.enabled})"


class UserDevice(BaseModel):
    """Registered client device for push notification dispatch."""

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="devices",
    )
    fcm_token = models.CharField(max_length=512, unique=True)
    device_id = models.CharField(max_length=128, db_index=True)
    device_name = models.CharField(max_length=128, blank=True, default="")
    platform = models.CharField(max_length=32, default="ANDROID")
    app_version = models.CharField(max_length=32, blank=True, default="")
    is_active = models.BooleanField(default=True)
    last_seen_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "user_devices"
        verbose_name = "user device"
        verbose_name_plural = "user devices"
        constraints = [
            models.UniqueConstraint(fields=["user", "device_id"], name="unique_user_device_id"),
        ]
        indexes = [
            models.Index(fields=["user", "is_active"], name="user_devices_active_idx"),
            models.Index(fields=["device_id", "user"], name="user_devices_id_idx"),
        ]

    def __str__(self):
        return f"UserDevice({self.device_id}, user={self.user_id}, active={self.is_active})"


class Reminder(UUIDBaseModel):
    """Durable scheduled reminder instance.

    Enforces semantic identity and idempotency via identity_key:
    {user_id}:{entity_type}:{entity_id}:{event_type}:{target_timestamp_or_period}
    """

    class EntityType(models.TextChoices):
        COMMITMENT = "COMMITMENT", "Commitment"
        GOAL = "GOAL", "Goal"
        SYSTEM = "SYSTEM", "System"
        SECURITY = "SECURITY", "Security"
        DIGEST = "DIGEST", "Digest"

    class ReminderStatus(models.TextChoices):
        SCHEDULED = "SCHEDULED", "Scheduled"
        CLAIMED = "CLAIMED", "Claimed"
        DISPATCHED = "DISPATCHED", "Dispatched"
        CANCELLED = "CANCELLED", "Cancelled"
        SUPPRESSED = "SUPPRESSED", "Suppressed"
        FAILED = "FAILED", "Failed"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="reminders",
    )
    entity_type = models.CharField(max_length=32, choices=EntityType.choices)
    entity_id = models.UUIDField(db_index=True)
    event_type = models.CharField(max_length=64)

    target_timestamp = models.DateTimeField(null=True, blank=True)
    target_period = models.CharField(max_length=128, blank=True, default="")

    identity_key = models.CharField(max_length=255, unique=True)
    status = models.CharField(
        max_length=32,
        choices=ReminderStatus.choices,
        default=ReminderStatus.SCHEDULED,
    )

    scheduled_for = models.DateTimeField()
    claimed_at = models.DateTimeField(null=True, blank=True)
    lease_expires_at = models.DateTimeField(null=True, blank=True)
    dispatched_at = models.DateTimeField(null=True, blank=True)
    cancelled_at = models.DateTimeField(null=True, blank=True)
    cancellation_reason = models.CharField(max_length=64, blank=True, default="")

    attempts = models.PositiveIntegerField(default=0)
    last_error = models.TextField(blank=True, default="")

    class Meta:
        db_table = "reminders"
        verbose_name = "reminder"
        verbose_name_plural = "reminders"
        indexes = [
            models.Index(fields=["status", "scheduled_for"], name="reminders_due_idx"),
            models.Index(fields=["user", "status"], name="reminders_user_status_idx"),
            models.Index(fields=["entity_type", "entity_id", "status"], name="reminders_entity_status_idx"),
        ]

    def __str__(self):
        return f"Reminder({self.identity_key}, status={self.status})"

    @staticmethod
    def compute_identity_key(
        user_id: Union[str, models.UUIDField],
        entity_type: str,
        entity_id: Union[str, models.UUIDField],
        event_type: str,
        target: Optional[Union[str, datetime.datetime, datetime.date]] = None,
    ) -> str:
        """Computes canonical semantic identity key for a reminder."""
        if isinstance(target, datetime.datetime):
            target_str = target.isoformat()
        elif isinstance(target, datetime.date):
            target_str = target.isoformat()
        elif target:
            target_str = str(target)
        else:
            target_str = "none"
        return f"{user_id}:{entity_type.upper()}:{entity_id}:{event_type}:{target_str}"

    def save(self, *args, **kwargs):
        if not self.identity_key:
            target = self.target_timestamp or self.target_period
            self.identity_key = self.compute_identity_key(
                user_id=self.user_id,
                entity_type=self.entity_type,
                entity_id=self.entity_id,
                event_type=self.event_type,
                target=target,
            )
        super().save(*args, **kwargs)


class NotificationDelivery(BaseModel):
    """Per-device durable delivery attempt for a scheduled reminder."""

    class DeliveryStatus(models.TextChoices):
        PENDING = "PENDING", "Pending"
        SENT = "SENT", "Sent"
        FAILED = "FAILED", "Failed"
        CANCELLED = "CANCELLED", "Cancelled"

    reminder = models.ForeignKey(
        Reminder,
        on_delete=models.CASCADE,
        related_name="deliveries",
    )
    user_device = models.ForeignKey(
        UserDevice,
        on_delete=models.CASCADE,
        related_name="deliveries",
    )
    status = models.CharField(
        max_length=32,
        choices=DeliveryStatus.choices,
        default=DeliveryStatus.PENDING,
    )
    attempts = models.PositiveIntegerField(default=0)
    last_error = models.TextField(blank=True, default="")
    sent_at = models.DateTimeField(null=True, blank=True)
    next_attempt_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "notification_deliveries"
        verbose_name = "notification delivery"
        verbose_name_plural = "notification deliveries"
        constraints = [
            models.UniqueConstraint(fields=["reminder", "user_device"], name="unique_reminder_device_delivery"),
        ]
        indexes = [
            models.Index(fields=["status", "next_attempt_at"], name="delivery_pending_idx"),
        ]

    def __str__(self):
        return f"NotificationDelivery(reminder={self.reminder_id}, device={self.user_device_id}, status={self.status})"
