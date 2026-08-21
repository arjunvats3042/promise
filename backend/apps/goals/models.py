from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from django.conf import settings
from django.contrib.postgres.fields import ArrayField
from django.core.exceptions import ValidationError
from django.db import models

from apps.core.models import BaseModel


def validate_iana_timezone(value):
    if not value:
        raise ValidationError("Timezone is required.")
    try:
        ZoneInfo(value)
    except (ZoneInfoNotFoundError, KeyError):
        raise ValidationError("Invalid timezone.")


class Goal(BaseModel):
    """A recurring personal practice.

    Progress and streaks are derived from check-ins. Recurrence lives on
    this row, not on per-day clones. Status transitions belong in the
    service layer, not save(). Sharing is membership via GoalParticipant.
    """

    class Status(models.TextChoices):
        ACTIVE = "ACTIVE", "Active"
        PAUSED = "PAUSED", "Paused"
        COMPLETED = "COMPLETED", "Completed"
        CANCELLED = "CANCELLED", "Cancelled"

    class RecurrenceKind(models.TextChoices):
        DAILY = "DAILY", "Daily"
        WEEKLY_DAYS = "WEEKLY_DAYS", "Weekly days"
        N_PER_PERIOD = "N_PER_PERIOD", "N per period"

    class PeriodUnit(models.TextChoices):
        WEEK = "WEEK", "Week"

    class TrackingKind(models.TextChoices):
        BINARY = "BINARY", "Binary"
        COUNT = "COUNT", "Count"

    class Source(models.TextChoices):
        MANUAL = "MANUAL", "Manual"
        AI_TEXT = "AI_TEXT", "AI text"
        AI_SCREENSHOT = "AI_SCREENSHOT", "AI screenshot"
        IMPORT = "IMPORT", "Import"
        SYSTEM = "SYSTEM", "System"

    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="created_goals",
    )
    title = models.CharField(max_length=255)
    description = models.TextField(blank=True, default="")
    status = models.CharField(
        max_length=16,
        choices=Status.choices,
        default=Status.ACTIVE,
        help_text="Stored lifecycle. COMPLETED and CANCELLED are terminal.",
    )
    timezone = models.CharField(max_length=63, blank=True, default="")
    start_date = models.DateField()
    end_date = models.DateField(null=True, blank=True)
    recurrence_kind = models.CharField(
        max_length=16,
        choices=RecurrenceKind.choices,
    )
    weekdays = ArrayField(
        models.PositiveSmallIntegerField(),
        default=list,
        blank=True,
        help_text="ISO weekdays 1–7 (Mon–Sun). Empty unless WEEKLY_DAYS.",
    )
    period_unit = models.CharField(
        max_length=8,
        choices=PeriodUnit.choices,
        null=True,
        blank=True,
    )
    times_per_period = models.PositiveSmallIntegerField(null=True, blank=True)
    tracking_kind = models.CharField(
        max_length=8,
        choices=TrackingKind.choices,
        default=TrackingKind.BINARY,
    )
    target_value = models.PositiveIntegerField(null=True, blank=True)
    target_unit = models.CharField(max_length=32, blank=True, default="")
    source = models.CharField(
        max_length=16,
        choices=Source.choices,
        default=Source.MANUAL,
    )
    paused_at = models.DateTimeField(null=True, blank=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    cancelled_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "goals"
        indexes = [
            models.Index(
                fields=["created_by", "status"],
                name="goals_creator_status",
            ),
        ]
        constraints = [
            models.CheckConstraint(
                condition=models.Q(end_date__isnull=True)
                | models.Q(end_date__gte=models.F("start_date")),
                name="goal_end_date_gte_start_date",
            ),
            models.CheckConstraint(
                condition=~models.Q(recurrence_kind="DAILY")
                | (
                    models.Q(period_unit__isnull=True)
                    & models.Q(times_per_period__isnull=True)
                    & models.Q(weekdays__len=0)
                ),
                name="goal_recurrence_daily",
            ),
            models.CheckConstraint(
                condition=~models.Q(recurrence_kind="WEEKLY_DAYS")
                | (
                    models.Q(period_unit__isnull=True)
                    & models.Q(times_per_period__isnull=True)
                    & models.Q(weekdays__len__gte=1)
                ),
                name="goal_recurrence_weekly_days",
            ),
            models.CheckConstraint(
                condition=~models.Q(recurrence_kind="N_PER_PERIOD")
                | (
                    models.Q(period_unit="WEEK")
                    & models.Q(period_unit__isnull=False)
                    & models.Q(times_per_period__isnull=False)
                    & models.Q(times_per_period__gte=1)
                    & models.Q(times_per_period__lte=7)
                    & models.Q(weekdays__len=0)
                ),
                name="goal_recurrence_n_per_period",
            ),
            models.CheckConstraint(
                condition=models.Q(weekdays__contained_by=[1, 2, 3, 4, 5, 6, 7]),
                name="goal_weekdays_iso",
            ),
            models.CheckConstraint(
                condition=~models.Q(tracking_kind="BINARY")
                | models.Q(target_value__isnull=True),
                name="goal_tracking_binary",
            ),
            models.CheckConstraint(
                condition=~models.Q(tracking_kind="COUNT")
                | models.Q(target_value__gte=1),
                name="goal_tracking_count",
            ),
        ]

    def __str__(self):
        return f"Goal {self.id}"

    def save(self, *args, **kwargs):
        if not self.timezone and self.created_by_id:
            self.timezone = self.created_by.timezone
        validate_iana_timezone(self.timezone)
        super().save(*args, **kwargs)


class GoalParticipant(BaseModel):
    """Membership of a user on a goal. Personal and shared use this table."""

    class Role(models.TextChoices):
        OWNER = "OWNER", "Owner"
        PARTICIPANT = "PARTICIPANT", "Participant"

    class Status(models.TextChoices):
        INVITED = "INVITED", "Invited"
        ACTIVE = "ACTIVE", "Active"
        DECLINED = "DECLINED", "Declined"
        LEFT = "LEFT", "Left"
        REMOVED = "REMOVED", "Removed"

    goal = models.ForeignKey(
        Goal,
        on_delete=models.CASCADE,
        related_name="participants",
    )
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="goal_participations",
    )
    role = models.CharField(max_length=16, choices=Role.choices)
    status = models.CharField(
        max_length=16,
        choices=Status.choices,
        default=Status.ACTIVE,
    )
    invited_at = models.DateTimeField(null=True, blank=True)
    joined_at = models.DateTimeField(null=True, blank=True)
    left_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "goal_participants"
        indexes = [
            models.Index(
                fields=["user", "status"],
                name="goals_participant_user_status",
            ),
        ]
        constraints = [
            models.UniqueConstraint(
                fields=["goal", "user"],
                name="uniq_goal_participant_user",
            ),
        ]

    def __str__(self):
        return f"GoalParticipant {self.id}"


class GoalCheckIn(BaseModel):
    """What a participant recorded for one local period_date of a goal.

    Identity is (goal, participant, period_date). Absence means missed; do
    not store MISSED rows. value is null for binary/SKIPPED and an integer
    for count.
    """

    class Status(models.TextChoices):
        COMPLETED = "COMPLETED", "Completed"
        SKIPPED = "SKIPPED", "Skipped"

    goal = models.ForeignKey(
        Goal,
        on_delete=models.CASCADE,
        related_name="check_ins",
    )
    participant = models.ForeignKey(
        GoalParticipant,
        on_delete=models.CASCADE,
        related_name="check_ins",
    )
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="goal_check_ins",
    )
    period_date = models.DateField()
    status = models.CharField(max_length=16, choices=Status.choices)
    value = models.PositiveIntegerField(null=True, blank=True)
    note = models.TextField(blank=True, default="")
    checked_at = models.DateTimeField()

    class Meta:
        db_table = "goal_check_ins"
        constraints = [
            models.UniqueConstraint(
                fields=["goal", "participant", "period_date"],
                name="uniq_goal_check_in_participant_period",
            ),
        ]

    def __str__(self):
        return f"GoalCheckIn {self.id}"


class GoalEvent(BaseModel):
    """Append-only history row. Do not treat metadata as canonical state."""

    class EventType(models.TextChoices):
        CREATED = "CREATED", "Created"
        UPDATED = "UPDATED", "Updated"
        PAUSED = "PAUSED", "Paused"
        RESUMED = "RESUMED", "Resumed"
        COMPLETED = "COMPLETED", "Completed"
        CANCELLED = "CANCELLED", "Cancelled"
        CHECKIN_RECORDED = "CHECKIN_RECORDED", "Check-in recorded"
        CHECKIN_UPDATED = "CHECKIN_UPDATED", "Check-in updated"
        PARTICIPANT_INVITED = "PARTICIPANT_INVITED", "Participant invited"
        PARTICIPANT_JOINED = "PARTICIPANT_JOINED", "Participant joined"
        PARTICIPANT_DECLINED = "PARTICIPANT_DECLINED", "Participant declined"
        PARTICIPANT_LEFT = "PARTICIPANT_LEFT", "Participant left"
        PARTICIPANT_REMOVED = "PARTICIPANT_REMOVED", "Participant removed"

    goal = models.ForeignKey(
        Goal,
        on_delete=models.CASCADE,
        related_name="events",
    )
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="goal_events",
    )
    event_type = models.CharField(max_length=32, choices=EventType.choices)
    metadata = models.JSONField(default=dict)

    class Meta:
        db_table = "goal_events"
        indexes = [
            models.Index(
                fields=["goal", "created_at"],
                name="goals_event_created",
            ),
        ]

    def __str__(self):
        return f"GoalEvent {self.id}"
