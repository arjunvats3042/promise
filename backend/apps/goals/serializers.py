from zoneinfo import ZoneInfo

from django.utils import timezone
from rest_framework import serializers

from apps.goals.models import Goal, GoalCheckIn
from apps.goals.services import goal_progress, goal_streak


class GoalSerializer(serializers.ModelSerializer):
    is_ended = serializers.SerializerMethodField()
    progress = serializers.SerializerMethodField()
    current_streak = serializers.SerializerMethodField()

    class Meta:
        model = Goal
        fields = (
            "id",
            "title",
            "description",
            "status",
            "timezone",
            "start_date",
            "end_date",
            "recurrence_kind",
            "weekdays",
            "period_unit",
            "times_per_period",
            "tracking_kind",
            "target_value",
            "target_unit",
            "source",
            "paused_at",
            "completed_at",
            "cancelled_at",
            "created_at",
            "updated_at",
            "is_ended",
            "progress",
            "current_streak",
        )
        read_only_fields = fields

    def get_is_ended(self, obj):
        if obj.end_date is None:
            return False
        today = timezone.now().astimezone(ZoneInfo(obj.timezone)).date()
        return today > obj.end_date

    def get_progress(self, obj):
        return goal_progress(obj)

    def get_current_streak(self, obj):
        return goal_streak(obj)


class GoalCreateSerializer(serializers.Serializer):
    title = serializers.CharField(max_length=255, allow_blank=False)
    description = serializers.CharField(required=False, allow_blank=True, default="")
    timezone = serializers.CharField(
        max_length=63, required=False, allow_blank=True, default=None, allow_null=True
    )
    start_date = serializers.DateField(required=False, allow_null=True, default=None)
    end_date = serializers.DateField(required=False, allow_null=True, default=None)
    recurrence_kind = serializers.ChoiceField(choices=Goal.RecurrenceKind.choices)
    weekdays = serializers.ListField(
        child=serializers.IntegerField(min_value=1, max_value=7),
        required=False,
        allow_null=True,
        default=None,
    )
    period_unit = serializers.ChoiceField(
        choices=Goal.PeriodUnit.choices,
        required=False,
        allow_null=True,
        default=None,
    )
    times_per_period = serializers.IntegerField(
        required=False, allow_null=True, default=None, min_value=1, max_value=7
    )
    tracking_kind = serializers.ChoiceField(
        choices=Goal.TrackingKind.choices,
        required=False,
        default=Goal.TrackingKind.BINARY,
    )
    target_value = serializers.IntegerField(
        required=False, allow_null=True, default=None, min_value=1
    )
    target_unit = serializers.CharField(
        max_length=32, required=False, allow_blank=True, default=""
    )


class GoalUpdateSerializer(serializers.Serializer):
    title = serializers.CharField(max_length=255, required=False, allow_blank=False)
    description = serializers.CharField(required=False, allow_blank=True)
    timezone = serializers.CharField(max_length=63, required=False, allow_blank=False)
    start_date = serializers.DateField(required=False)
    end_date = serializers.DateField(required=False, allow_null=True)
    recurrence_kind = serializers.ChoiceField(
        choices=Goal.RecurrenceKind.choices, required=False
    )
    weekdays = serializers.ListField(
        child=serializers.IntegerField(min_value=1, max_value=7),
        required=False,
    )
    period_unit = serializers.ChoiceField(
        choices=Goal.PeriodUnit.choices,
        required=False,
        allow_null=True,
    )
    times_per_period = serializers.IntegerField(
        required=False, allow_null=True, min_value=1, max_value=7
    )
    tracking_kind = serializers.ChoiceField(
        choices=Goal.TrackingKind.choices, required=False
    )
    target_value = serializers.IntegerField(
        required=False, allow_null=True, min_value=1
    )
    target_unit = serializers.CharField(
        max_length=32, required=False, allow_blank=True
    )


class GoalListQuerySerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=Goal.Status.choices, required=False)
    recurrence_kind = serializers.ChoiceField(
        choices=Goal.RecurrenceKind.choices, required=False
    )
    tracking_kind = serializers.ChoiceField(
        choices=Goal.TrackingKind.choices, required=False
    )


class GoalCheckInSerializer(serializers.ModelSerializer):
    class Meta:
        model = GoalCheckIn
        fields = (
            "id",
            "period_date",
            "status",
            "value",
            "note",
            "checked_at",
            "created_at",
            "updated_at",
        )
        read_only_fields = fields


class GoalCheckInWriteSerializer(serializers.Serializer):
    period_date = serializers.DateField(required=False, allow_null=True, default=None)
    status = serializers.ChoiceField(choices=GoalCheckIn.Status.choices)
    value = serializers.IntegerField(
        required=False, allow_null=True, default=None, min_value=0
    )
    note = serializers.CharField(required=False, allow_blank=True, default="")


class GoalCheckInListQuerySerializer(serializers.Serializer):
    start_date = serializers.DateField(required=False)
    end_date = serializers.DateField(required=False)
    status = serializers.ChoiceField(choices=GoalCheckIn.Status.choices, required=False)
