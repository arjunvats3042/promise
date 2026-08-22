from rest_framework import serializers

from apps.notifications.models import UserDevice, UserNotificationPreferences


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
            "quiet_hours_enabled",
            "quiet_hours_start",
            "quiet_hours_end",
            "updated_at",
        ]
        read_only_fields = ["updated_at"]
