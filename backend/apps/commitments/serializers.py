from django.utils import timezone
from rest_framework import serializers

from apps.commitments.models import Commitment


class CommitmentSerializer(serializers.ModelSerializer):
    is_overdue = serializers.SerializerMethodField()

    class Meta:
        model = Commitment
        fields = (
            "id",
            "title",
            "description",
            "status",
            "due_at",
            "due_precision",
            "source",
            "snoozed_until",
            "completed_at",
            "cancelled_at",
            "created_at",
            "updated_at",
            "is_overdue",
        )
        read_only_fields = fields

    def get_is_overdue(self, obj):
        return obj.is_overdue()


class CommitmentCreateSerializer(serializers.Serializer):
    title = serializers.CharField(max_length=255, allow_blank=False)
    description = serializers.CharField(required=False, allow_blank=True, default="")
    due_at = serializers.DateTimeField(required=False, allow_null=True, default=None)
    due_precision = serializers.ChoiceField(
        choices=Commitment.DuePrecision.choices,
        required=False,
        default=Commitment.DuePrecision.NONE,
    )
    source = serializers.ChoiceField(
        choices=Commitment.Source.choices,
        required=False,
        default=Commitment.Source.MANUAL,
    )

    def validate(self, attrs):
        _validate_due_fields(attrs.get("due_at"), attrs.get("due_precision"))
        return attrs


class CommitmentUpdateSerializer(serializers.Serializer):
    title = serializers.CharField(max_length=255, required=False, allow_blank=False)
    description = serializers.CharField(required=False, allow_blank=True)
    due_at = serializers.DateTimeField(required=False, allow_null=True)
    due_precision = serializers.ChoiceField(
        choices=Commitment.DuePrecision.choices,
        required=False,
    )

    def validate(self, attrs):
        if "due_at" in attrs and "due_precision" in attrs:
            _validate_due_fields(attrs["due_at"], attrs["due_precision"])
        return attrs


class CommitmentListQuerySerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=Commitment.Status.choices, required=False)
    source = serializers.ChoiceField(choices=Commitment.Source.choices, required=False)
    is_overdue = serializers.BooleanField(required=False)


class SnoozeSerializer(serializers.Serializer):
    snoozed_until = serializers.DateTimeField()

    def validate_snoozed_until(self, value):
        if timezone.is_naive(value):
            raise serializers.ValidationError("Must be timezone-aware.")
        if value <= timezone.now():
            raise serializers.ValidationError("Must be in the future.")
        return value


def _validate_due_fields(due_at, due_precision):
    if due_precision == Commitment.DuePrecision.NONE:
        if due_at is not None:
            raise serializers.ValidationError(
                {"due_at": "Must be empty when due_precision is NONE."}
            )
        return
    if due_at is None:
        raise serializers.ValidationError(
            {"due_at": "Required when due_precision is DATE or DATETIME."}
        )
