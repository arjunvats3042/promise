from zoneinfo import ZoneInfo

from django.utils import timezone
from rest_framework import serializers

from apps.goals.models import Goal, GoalCheckIn, GoalParticipant
from apps.goals.services import (
    collective_progress,
    get_participant,
    goal_progress,
    goal_streak,
    invite_expires_at,
    is_shared_goal,
)


class GoalParticipantSerializer(serializers.ModelSerializer):
    user_name = serializers.CharField(source="user.name", read_only=True)

    class Meta:
        model = GoalParticipant
        fields = (
            "id",
            "user_id",
            "user_name",
            "role",
            "status",
            "invited_at",
            "joined_at",
            "left_at",
        )
        read_only_fields = fields


class GoalInvitePreviewSerializer(serializers.ModelSerializer):
    inviter_user_id = serializers.UUIDField(source="created_by_id", read_only=True)
    inviter_name = serializers.CharField(source="created_by.name", read_only=True)
    invitation_status = serializers.SerializerMethodField()
    invitation_expires_at = serializers.SerializerMethodField()

    class Meta:
        model = Goal
        fields = (
            "id",
            "title",
            "description",
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
            "inviter_user_id",
            "inviter_name",
            "invitation_status",
            "invitation_expires_at",
        )
        read_only_fields = fields

    def get_invitation_status(self, obj):
        viewer = self.context["request"].user
        participant = get_participant(obj, viewer)
        return participant.status if participant is not None else None

    def get_invitation_expires_at(self, obj):
        viewer = self.context["request"].user
        participant = get_participant(obj, viewer)
        expires = invite_expires_at(participant)
        return expires.isoformat() if expires is not None else None


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

    def _viewer_participant(self, obj):
        request = self.context.get("request")
        if request is None or not getattr(request, "user", None):
            return None
        return get_participant(obj, request.user)

    def get_is_ended(self, obj):
        if obj.end_date is None:
            return False
        today = timezone.now().astimezone(ZoneInfo(obj.timezone)).date()
        return today > obj.end_date

    def get_progress(self, obj):
        return goal_progress(obj, participant=self._viewer_participant(obj))

    def get_current_streak(self, obj):
        return goal_streak(obj, participant=self._viewer_participant(obj))


class SharedGoalSerializer(GoalSerializer):
    collective_progress = serializers.SerializerMethodField()
    participants = serializers.SerializerMethodField()
    membership_role = serializers.SerializerMethodField()
    membership_status = serializers.SerializerMethodField()

    class Meta(GoalSerializer.Meta):
        fields = GoalSerializer.Meta.fields + (
            "collective_progress",
            "participants",
            "membership_role",
            "membership_status",
        )
        read_only_fields = fields

    def get_collective_progress(self, obj):
        return collective_progress(obj)

    def get_participants(self, obj):
        rows = obj.participants.filter(
            status=GoalParticipant.Status.ACTIVE
        ).order_by("joined_at", "created_at")
        return GoalParticipantSerializer(rows, many=True).data

    def get_membership_role(self, obj):
        participant = self._viewer_participant(obj)
        return participant.role if participant is not None else None

    def get_membership_status(self, obj):
        participant = self._viewer_participant(obj)
        return participant.status if participant is not None else None


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


class GoalInviteCreateSerializer(serializers.Serializer):
    user_id = serializers.UUIDField()
