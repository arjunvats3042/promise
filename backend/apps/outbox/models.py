from django.db import models
from django.db.models import Q

from apps.core.models import BaseModel


class OutboxEvent(BaseModel):
    """Transport row for later Kafka publication. Not audit history."""

    aggregate_type = models.CharField(max_length=32)
    aggregate_id = models.UUIDField()
    event_type = models.CharField(max_length=64)
    event_version = models.PositiveSmallIntegerField(default=1)
    payload = models.JSONField(default=dict)
    occurred_at = models.DateTimeField()
    published_at = models.DateTimeField(null=True, blank=True)
    attempts = models.PositiveIntegerField(default=0)
    next_attempt_at = models.DateTimeField(null=True, blank=True)
    last_error = models.CharField(max_length=2048, blank=True, default="")

    class Meta:
        db_table = "outbox_events"
        indexes = [
            models.Index(
                fields=["next_attempt_at", "created_at"],
                name="outbox_unpublished_due",
                condition=Q(published_at__isnull=True)
                & Q(next_attempt_at__isnull=False),
            ),
        ]

    def __str__(self):
        return f"OutboxEvent {self.id}"


class ProcessedEvent(BaseModel):
    """Idempotency record for a consumer group and Kafka event_id."""

    consumer_group = models.CharField(max_length=128)
    event_id = models.UUIDField()
    processed_at = models.DateTimeField()

    class Meta:
        db_table = "processed_events"
        constraints = [
            models.UniqueConstraint(
                fields=["consumer_group", "event_id"],
                name="uniq_processed_event_group_event",
            ),
        ]

    def __str__(self):
        return f"ProcessedEvent {self.consumer_group} {self.event_id}"
