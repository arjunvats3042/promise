from rest_framework import serializers

from apps.notifications.models import Reminder, UserDevice, UserNotificationPreferences


class UserDeviceSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserDevice
        fields = [
            "id",
            "device_id",
            "device_name",
            "platform",
            "app_version",
            "is_active",
            "last_seen_at",
            "created_at",
        ]
        read_only_fields = ["id", "is_active", "last_seen_at", "created_at"]


class RegisterDeviceSerializer(serializers.Serializer):
    fcm_token = serializers.CharField(max_length=512)
    device_id = serializers.CharField(max_length=128)
    device_name = serializers.CharField(max_length=128, required=False, allow_blank=True, default="")
    platform = serializers.CharField(max_length=32, required=False, default="ANDROID")
    app_version = serializers.CharField(max_length=32, required=False, allow_blank=True, default="")


class UserNotificationPreferencesSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserNotificationPreferences
        fields = [
            "enabled",
            "commitments_due_soon",
            "commitments_due_now",
            "commitments_overdue",
            "goals_daily_reminder",
            "goals_daily_reminder_time",
            "goals_evening_reminder",
            "goals_evening_reminder_time",
            "shared_goals_activity",
            "shared_goals_chat",
            "weekly_digest_enabled",
            "quiet_hours_enabled",
            "quiet_hours_start",
            "quiet_hours_end",
            "updated_at",
        ]
        read_only_fields = ["updated_at"]


class NotificationHistoryItemSerializer(serializers.ModelSerializer):
    title = serializers.SerializerMethodField()
    body = serializers.SerializerMethodField()
    deep_link = serializers.SerializerMethodField()
    category = serializers.SerializerMethodField()

    class Meta:
        model = Reminder
        fields = [
            "id",
            "entity_type",
            "entity_id",
            "event_type",
            "category",
            "title",
            "body",
            "deep_link",
            "status",
            "scheduled_for",
            "dispatched_at",
            "created_at",
        ]
        read_only_fields = fields

    def get_category(self, obj) -> str:
        if obj.entity_type == "COMMITMENT":
            return "COMMITMENT"
        if obj.entity_type == "GOAL":
            return "PRACTICE" if not obj.event_type.startswith("goal.chat") else "CHAT"
        if obj.entity_type == "DIGEST" or obj.event_type == "digest.weekly":
            return "DIGEST"
        if obj.entity_type == "SECURITY" or obj.event_type.startswith("security."):
            return "SECURITY"
        return "SYSTEM"

    def get_title(self, obj) -> str:
        if obj.entity_type == "COMMITMENT":
            if obj.event_type == "commitment.due_now":
                return "Due now"
            elif obj.event_type == "commitment.overdue":
                return "Unfinished commitment"
            return "Due soon"
        elif obj.entity_type == "GOAL":
            from apps.goals.models import Goal
            goal = Goal.objects.filter(id=obj.entity_id).first()
            return goal.title if goal else "Practice check-in"
        elif obj.entity_type == "DIGEST" or obj.event_type == "digest.weekly":
            return "Your Promise week"
        elif obj.entity_type == "SECURITY":
            return "Security alert" if obj.event_type != "security.new_device_login" else "New device login"
        return "Promise Notification"

    def get_body(self, obj) -> str:
        if obj.entity_type == "COMMITMENT":
            from apps.commitments.models import Commitment
            c = Commitment.objects.filter(id=obj.entity_id).first()
            return c.title if c else "Commitment reminder"
        elif obj.entity_type == "GOAL":
            if obj.event_type == "goal.chat.message_created":
                return "New message in group chat"
            elif obj.event_type == "goal.participant.joined":
                return "A participant joined your shared goal."
            elif obj.event_type == "goal.participant.left":
                return "A participant left your shared goal."
            elif obj.event_type == "goal.participant.removed":
                return "You were removed from this goal."
            elif obj.event_type == "goal.ownership_transferred":
                return "You are now the owner of this goal."
            from apps.goals.models import Goal
            goal = Goal.objects.filter(id=obj.entity_id).first()
            return goal.title if goal else "Practice check-in"
        elif obj.entity_type == "DIGEST" or obj.event_type == "digest.weekly":
            return "Your weekly practice and commitment summary is ready."
        elif obj.entity_type == "SECURITY":
            return "A new device logged into your Promise account." if obj.event_type == "security.new_device_login" else "Important account security event."
        return "You have an active reminder from Promise."

    def get_deep_link(self, obj) -> str:
        if obj.entity_type == "COMMITMENT":
            return f"promise://commitment/{obj.entity_id}"
        elif obj.entity_type == "GOAL":
            if obj.event_type == "goal.chat.message_created":
                return f"promise://goal/{obj.entity_id}/chat"
            return f"promise://goal/{obj.entity_id}"
        elif obj.entity_type == "SECURITY":
            return "promise://profile"
        return "promise://home"
