from zoneinfo import ZoneInfo

from django.utils import timezone
from rest_framework import serializers

from apps.goals.models import ChatMessage, Goal, GoalCheckIn, GoalParticipant
from apps.goals.services import (
    MAX_SHARED_GOAL_PARTICIPANTS,
    _invite_is_expired,
    collective_progress,
    get_participant,
    goal_progress,
    goal_streak,
    invite_expires_at,
    is_shared_goal,
    shared_goal_group_summary,
    shared_goal_milestones,
    shared_goal_weekly_reflection,
)


class GoalParticipantSerializer(serializers.ModelSerializer):
    user_name = serializers.CharField(source="user.name", read_only=True)
    avatar_url = serializers.CharField(source="user.avatar_url", read_only=True, default="")
    is_expired = serializers.SerializerMethodField()
    invitation_expires_at = serializers.SerializerMethodField()

    class Meta:
        model = GoalParticipant
        fields = (
            "id",
            "user_id",
            "user_name",
            "avatar_url",
            "role",
            "status",
            "invited_at",
            "invitation_expires_at",
            "is_expired",
            "joined_at",
            "left_at",
        )
        read_only_fields = fields

    def get_is_expired(self, obj) -> bool:
        return _invite_is_expired(obj)

    def get_invitation_expires_at(self, obj):
        expires = invite_expires_at(obj)
        return expires.isoformat() if expires is not None else None


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
    unread_chat_count = serializers.SerializerMethodField()
    latest_chat_message = serializers.SerializerMethodField()

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
            "is_shared",
            "paused_at",
            "completed_at",
            "cancelled_at",
            "created_at",
            "updated_at",
            "is_ended",
            "progress",
            "current_streak",
            "unread_chat_count",
            "latest_chat_message",
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

    def get_unread_chat_count(self, obj) -> int:
        if not obj.is_shared:
            return 0
        request = self.context.get("request")
        if request is None or not getattr(request, "user", None) or not request.user.is_authenticated:
            return 0
        try:
            from apps.goals.services import get_chat_summary
            summary = get_chat_summary(viewer=request.user, goal_id=obj.id)
            return summary.get("unread_count", 0)
        except Exception:
            return 0

    def get_latest_chat_message(self, obj):
        if not obj.is_shared:
            return None
        latest = obj.chat_messages.select_related("sender").order_by("-created_at", "-id").first()
        if not latest:
            return None
        return {
            "id": str(latest.id),
            "text": latest.body,
            "sender_name": latest.sender.name or "Partner",
            "sender_id": str(latest.sender_id),
            "created_at": latest.created_at.isoformat(),
        }


class SharedGoalSerializer(GoalSerializer):
    collective_progress = serializers.SerializerMethodField()
    participants = serializers.SerializerMethodField()
    membership_role = serializers.SerializerMethodField()
    membership_status = serializers.SerializerMethodField()
    group_summary = serializers.SerializerMethodField()
    milestones = serializers.SerializerMethodField()
    weekly_reflection = serializers.SerializerMethodField()
    participant_limit = serializers.SerializerMethodField()

    class Meta(GoalSerializer.Meta):
        fields = GoalSerializer.Meta.fields + (
            "collective_progress",
            "participants",
            "membership_role",
            "membership_status",
            "group_summary",
            "milestones",
            "weekly_reflection",
            "participant_limit",
        )
        read_only_fields = fields

    def get_collective_progress(self, obj):
        return collective_progress(obj)

    def get_participants(self, obj):
        if hasattr(obj, "_prefetched_objects_cache") and "participants" in obj._prefetched_objects_cache:
            rows = [p for p in obj.participants.all() if p.status in (GoalParticipant.Status.ACTIVE, GoalParticipant.Status.INVITED)]
            rows.sort(key=lambda p: (p.joined_at or p.created_at, p.created_at))
        else:
            rows = (
                obj.participants.filter(status__in=[GoalParticipant.Status.ACTIVE, GoalParticipant.Status.INVITED])
                .select_related("user")
                .order_by("joined_at", "created_at")
            )
        return GoalParticipantSerializer(rows, many=True).data

    def get_membership_role(self, obj):
        participant = self._viewer_participant(obj)
        return participant.role if participant is not None else None

    def get_membership_status(self, obj):
        participant = self._viewer_participant(obj)
        return participant.status if participant is not None else None

    def get_group_summary(self, obj):
        return shared_goal_group_summary(obj)

    def get_milestones(self, obj):
        return shared_goal_milestones(obj)

    def get_weekly_reflection(self, obj):
        return shared_goal_weekly_reflection(obj)

    def get_participant_limit(self, obj) -> int:
        return MAX_SHARED_GOAL_PARTICIPANTS


class GoalOwnershipTransferSerializer(serializers.Serializer):
    participant_id = serializers.CharField(required=False, allow_blank=True, default=None)
    user_id = serializers.CharField(required=False, allow_blank=True, default=None)

    def validate(self, attrs):
        if not attrs.get("participant_id") and not attrs.get("user_id"):
            raise serializers.ValidationError("Either participant_id or user_id must be provided.")
        return attrs


class GoalReinviteSerializer(serializers.Serializer):
    participant_id = serializers.CharField(required=False, allow_blank=True, default=None)
    user_id = serializers.CharField(required=False, allow_blank=True, default=None)

    def validate(self, attrs):
        if not attrs.get("participant_id") and not attrs.get("user_id"):
            raise serializers.ValidationError("Either participant_id or user_id must be provided.")
        return attrs


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
    is_shared = serializers.BooleanField(required=False, default=False)


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

    def to_representation(self, instance):
        data = super().to_representation(instance)
        request = self.context.get("request")
        if request and getattr(request, "user", None):
            # Redact note if the viewer is not the author of this check-in and not the owner
            if instance.created_by_id != request.user.id and instance.goal.created_by_id != request.user.id:
                data["note"] = ""

        user = None
        if instance.participant and getattr(instance.participant, "user", None):
            user = instance.participant.user
        elif instance.created_by:
            user = instance.created_by

        if user:
            data["user_id"] = str(user.id)
            data["user_name"] = user.name
            data["user_avatar_url"] = user.avatar_url
        else:
            data["user_id"] = str(instance.created_by_id) if instance.created_by_id else ""
            data["user_name"] = ""
            data["user_avatar_url"] = None

        return data


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


class ChatMessageSenderSerializer(serializers.Serializer):
    id = serializers.UUIDField(source="sender_id", read_only=True)
    name = serializers.CharField(source="sender.name", read_only=True)
    avatar_url = serializers.CharField(source="sender.avatar_url", read_only=True, default="")


class ChatMessageSerializer(serializers.ModelSerializer):
    sender = ChatMessageSenderSerializer(source="*", read_only=True)

    class Meta:
        model = ChatMessage
        fields = (
            "id",
            "sender",
            "body",
            "created_at",
        )
        read_only_fields = fields


class ChatMessageWriteSerializer(serializers.Serializer):
    body = serializers.CharField(max_length=2000, allow_blank=False, trim_whitespace=True)


class ChatReadWriteSerializer(serializers.Serializer):
    last_read_message_id = serializers.UUIDField(required=True)


class ChatSummarySerializer(serializers.Serializer):
    unread_count = serializers.IntegerField()
    latest_message = ChatMessageSerializer(allow_null=True)


class GoalActivityActorSerializer(serializers.Serializer):
    id = serializers.UUIDField(read_only=True)
    name = serializers.CharField(read_only=True)
    avatar_url = serializers.CharField(default="", read_only=True)


class GoalActivityItemSerializer(serializers.Serializer):
    id = serializers.UUIDField(read_only=True)
    event_type = serializers.CharField(read_only=True)
    actor = GoalActivityActorSerializer(read_only=True, allow_null=True)
    target_user = GoalActivityActorSerializer(read_only=True, allow_null=True)
    summary = serializers.CharField(read_only=True)
    period_date = serializers.DateField(read_only=True, allow_null=True)
    created_at = serializers.DateTimeField(read_only=True)


class ChatSearchQuerySerializer(serializers.Serializer):
    q = serializers.CharField(min_length=1, max_length=100, trim_whitespace=True)
    limit = serializers.IntegerField(min_value=1, max_value=50, default=20, required=False)

    def validate_q(self, value):
        collapsed = " ".join(value.strip().split())
        if not collapsed:
            raise serializers.ValidationError("Search query cannot be empty.")
        return collapsed


class ChatSearchResultSerializer(serializers.Serializer):
    id = serializers.UUIDField(read_only=True)
    message_id = serializers.UUIDField(source="id", read_only=True)
    sender = ChatMessageSenderSerializer(source="*", read_only=True)
    body = serializers.CharField(read_only=True)
    snippet = serializers.SerializerMethodField()
    created_at = serializers.DateTimeField(read_only=True)

    def get_snippet(self, obj):
        body = obj.body or ""
        if len(body) <= 120:
            return body
        return body[:117] + "..."
