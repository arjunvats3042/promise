import json
import uuid

from django.utils import timezone
from rest_framework import serializers

from apps.analytics.events import is_event_whitelisted, sanitize_analytics_properties


class AnalyticsEventSerializer(serializers.Serializer):
    event_id = serializers.UUIDField(required=False, default=uuid.uuid4)
    event_name = serializers.CharField(max_length=100)
    event_version = serializers.IntegerField(default=1, min_value=1)
    occurred_at = serializers.DateTimeField(required=False)
    platform = serializers.CharField(max_length=50, default="android")
    app_version = serializers.CharField(max_length=50, default="1.0.0", allow_blank=True)
    properties = serializers.DictField(required=False, default=dict)

    def validate_event_name(self, value):
        if not is_event_whitelisted(value):
            raise serializers.ValidationError(
                f"Event '{value}' is not in the approved analytics taxonomy."
            )
        return value

    def validate_properties(self, value):
        if not isinstance(value, dict):
            return {}
        # Sanitize properties to remove forbidden PII/content keys
        sanitized = sanitize_analytics_properties(value)
        # Check size constraint (< 2048 bytes)
        encoded = json.dumps(sanitized).encode("utf-8")
        if len(encoded) > 2048:
            raise serializers.ValidationError("Analytics properties payload exceeds 2 KiB limit.")
        return sanitized

    def validate(self, data):
        if "occurred_at" not in data or data["occurred_at"] is None:
            data["occurred_at"] = timezone.now()

        # Reject events with timestamp too far in future (> 1 hour) or past (> 30 days)
        now = timezone.now()
        occurred = data["occurred_at"]
        if occurred > now + timezone.timedelta(hours=1):
            raise serializers.ValidationError({"occurred_at": "Timestamp cannot be in the future."})
        if occurred < now - timezone.timedelta(days=30):
            data["occurred_at"] = now - timezone.timedelta(days=30)

        return data


class AnalyticsBatchSerializer(serializers.Serializer):
    events = serializers.ListField(
        child=AnalyticsEventSerializer(),
        min_length=1,
        max_length=50,
        allow_empty=False,
    )
