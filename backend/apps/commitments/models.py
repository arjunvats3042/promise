from django.conf import settings
from django.db import models
from django.utils import timezone

from apps.core.models import BaseModel


class Commitment(BaseModel):
    """A finite, accountable promise.

    OVERDUE is not a stored status. It is derived from PENDING/WAITING,
    a past due_at, and is_overdue(). Status transitions belong in the
    service layer, not save().
    """

    class Status(models.TextChoices):
        PENDING = "PENDING", "Pending"
        WAITING = "WAITING", "Waiting"
        SNOOZED = "SNOOZED", "Snoozed"
        COMPLETED = "COMPLETED", "Completed"
        CANCELLED = "CANCELLED", "Cancelled"

    class DuePrecision(models.TextChoices):
        NONE = "NONE", "None"
        DATE = "DATE", "Date"
        DATETIME = "DATETIME", "Datetime"

    class Source(models.TextChoices):
        MANUAL = "MANUAL", "Manual"
        AI_TEXT = "AI_TEXT", "AI text"
        AI_SCREENSHOT = "AI_SCREENSHOT", "AI screenshot"
        SHARED = "SHARED", "Shared"
        IMPORT = "IMPORT", "Import"
        SYSTEM = "SYSTEM", "System"

    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="created_commitments",
    )
    title = models.CharField(max_length=255)
    description = models.TextField(blank=True, default="")
    status = models.CharField(
        max_length=16,
        choices=Status.choices,
        default=Status.PENDING,
        help_text="Stored lifecycle. COMPLETED and CANCELLED are terminal. OVERDUE is derived.",
    )
    due_at = models.DateTimeField(null=True, blank=True)
    due_precision = models.CharField(
        max_length=16,
        choices=DuePrecision.choices,
        default=DuePrecision.NONE,
        help_text="NONE has no deadline. DATE is a calendar date. DATETIME is an exact time.",
    )
    source = models.CharField(
        max_length=16,
        choices=Source.choices,
        default=Source.MANUAL,
    )
    snoozed_until = models.DateTimeField(null=True, blank=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    cancelled_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "commitments"
        indexes = [
            models.Index(
                fields=["created_by", "status", "due_at"],
                name="commitments_creator_status_due",
            ),
            models.Index(
                fields=["status", "due_at"],
                name="commitments_status_due_at",
            ),
        ]

    def __str__(self):
        return f"Commitment {self.id}"

    def is_overdue(self, at=None):
        if self.status not in (self.Status.PENDING, self.Status.WAITING):
            return False
        if self.due_at is None:
            return False
        now = at if at is not None else timezone.now()
        return now >= self.due_at


class CommitmentParticipant(BaseModel):
    """Membership of a user on a commitment. Personal and shared use this table."""

    class Role(models.TextChoices):
        RESPONSIBLE = "RESPONSIBLE", "Responsible"
        RECIPIENT = "RECIPIENT", "Recipient"
        OBSERVER = "OBSERVER", "Observer"

    class Status(models.TextChoices):
        ACTIVE = "ACTIVE", "Active"

    commitment = models.ForeignKey(
        Commitment,
        on_delete=models.CASCADE,
        related_name="participants",
    )
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="commitment_participations",
    )
    role = models.CharField(max_length=16, choices=Role.choices)
    status = models.CharField(
        max_length=16,
        choices=Status.choices,
        default=Status.ACTIVE,
    )
    joined_at = models.DateTimeField()

    class Meta:
        db_table = "commitment_participants"
        constraints = [
            models.UniqueConstraint(
                fields=["commitment", "user"],
                name="uniq_commitment_participant_user",
            ),
        ]

    def __str__(self):
        return f"CommitmentParticipant {self.id}"


class CommitmentEvent(BaseModel):
    """Append-only history row. Do not treat metadata as canonical state."""

    class EventType(models.TextChoices):
        CREATED = "CREATED", "Created"
        UPDATED = "UPDATED", "Updated"
        COMPLETED = "COMPLETED", "Completed"
        SNOOZED = "SNOOZED", "Snoozed"
        UNSNOOZED = "UNSNOOZED", "Unsnoozed"
        WAITING = "WAITING", "Waiting"
        CANCELLED = "CANCELLED", "Cancelled"

    commitment = models.ForeignKey(
        Commitment,
        on_delete=models.CASCADE,
        related_name="events",
    )
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="commitment_events",
    )
    event_type = models.CharField(max_length=16, choices=EventType.choices)
    metadata = models.JSONField(default=dict)

    class Meta:
        db_table = "commitment_events"
        indexes = [
            models.Index(
                fields=["commitment", "created_at"],
                name="commitments_event_created",
            ),
        ]

    def __str__(self):
        return f"CommitmentEvent {self.id}"
