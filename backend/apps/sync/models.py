import uuid
from django.conf import settings
from django.db import models


class SyncActionAudit(models.Model):
    class ActionStatus(models.TextChoices):
        APPLIED = "APPLIED", "Applied"
        ALREADY_PROCESSED = "ALREADY_PROCESSED", "Already Processed"
        FAILED = "FAILED", "Failed"

    action_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="sync_actions",
    )
    action_type = models.CharField(max_length=64)
    entity_id = models.CharField(max_length=64, blank=True, default="")
    status = models.CharField(
        max_length=32,
        choices=ActionStatus.choices,
        default=ActionStatus.APPLIED,
    )
    processed_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "sync_action_audits"
        indexes = [
            models.Index(fields=["user", "processed_at"]),
        ]
