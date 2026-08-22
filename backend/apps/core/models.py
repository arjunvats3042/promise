import uuid

from django.db import models


class UUIDBaseModel(models.Model):
    """Abstract base for models retaining 128-bit UUID primary keys.

    Used for security tokens (AuthSession), distributed transport events
    (OutboxEvent, ProcessedEvent), and semantic scheduler state (Reminder).
    """

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True


class BaseModel(models.Model):
    """Shared abstract base for domain models with dual UUID and sequential integer IDs.

    id (UUID) serves as the primary key preserving referential integrity and data.
    numeric_id (BIGINT) provides a human-readable sequential identifier for clean
    URLs, deep links, and WebSocket routing during staged migration.
    """

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    numeric_id = models.BigIntegerField(null=True, blank=True, db_index=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True

    def save(self, *args, **kwargs):
        if not self.numeric_id:
            try:
                max_id = self.__class__.objects.aggregate(models.Max("numeric_id"))["numeric_id__max"]
                self.numeric_id = (max_id or 0) + 1
            except Exception:
                pass
        super().save(*args, **kwargs)
