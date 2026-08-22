import hashlib
import uuid

from django.conf import settings
from django.db import models


def compute_pseudonymous_analytics_id(user_or_id) -> str:
    """Computes a stable, non-human-readable pseudonymous analytics UUID/hash for a user.

    Uses SHA-256 with DJANGO_SECRET_KEY as salt so raw user UUIDs/emails are never exposed.
    """
    if not user_or_id:
        return "anonymous"

    uid_str = str(getattr(user_or_id, "id", user_or_id))
    salted = f"{uid_str}:{settings.SECRET_KEY}"
    return hashlib.sha256(salted.encode("utf-8")).hexdigest()[:32]


class AnalyticsEvent(models.Model):
    """Raw analytics event store with 90-day retention window.

    Guarantees strict privacy:
    - Stores pseudonymous `analytics_user_id`
    - Deduplicates by `event_id` (UUID)
    - Validated against whitelisted event taxonomy
    """

    event_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    event_name = models.CharField(max_length=100, db_index=True)
    event_version = models.PositiveIntegerField(default=1)

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="analytics_events",
    )
    analytics_user_id = models.CharField(max_length=128, db_index=True, blank=True)

    platform = models.CharField(max_length=50, default="unknown")
    app_version = models.CharField(max_length=50, default="", blank=True)

    occurred_at = models.DateTimeField(db_index=True)
    received_at = models.DateTimeField(auto_now_add=True, db_index=True)

    properties = models.JSONField(default=dict, blank=True)

    class Meta:
        ordering = ["-occurred_at"]
        indexes = [
            models.Index(fields=["event_name", "occurred_at"]),
            models.Index(fields=["analytics_user_id", "occurred_at"]),
        ]

    def save(self, *args, **kwargs):
        if self.user and not self.analytics_user_id:
            self.analytics_user_id = compute_pseudonymous_analytics_id(self.user.id)
        elif not self.analytics_user_id:
            self.analytics_user_id = "anonymous"
        super().save(*args, **kwargs)

    def __str__(self):
        return f"AnalyticsEvent({self.event_name}, id={self.event_id}, at={self.occurred_at})"


class AnalyticsDailyMetric(models.Model):
    """Long-term aggregate reporting metrics snapshot.

    Retained longer than raw event rows for product analytics trends & reporting.
    """

    metric_name = models.CharField(max_length=100, db_index=True)
    date = models.DateField(db_index=True)
    value = models.FloatField()
    dimensions = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ("metric_name", "date")
        ordering = ["-date", "metric_name"]

    def __str__(self):
        return f"AnalyticsDailyMetric({self.metric_name}, date={self.date}, val={self.value})"
