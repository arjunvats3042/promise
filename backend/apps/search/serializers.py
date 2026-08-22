from rest_framework import serializers


class SearchQuerySerializer(serializers.Serializer):
    q = serializers.CharField(min_length=1, max_length=100, trim_whitespace=True)
    type = serializers.ChoiceField(
        choices=["all", "commitments", "goals"],
        default="all",
        required=False,
    )

    def validate_q(self, value):
        collapsed = " ".join(value.strip().split())
        if not collapsed:
            raise serializers.ValidationError("Search query cannot be empty.")
        return collapsed


class CommitmentSearchResultSerializer(serializers.Serializer):
    id = serializers.UUIDField()
    numeric_id = serializers.IntegerField(required=False, allow_null=True)
    title = serializers.CharField()
    description = serializers.CharField(allow_blank=True, default="")
    status = serializers.CharField()
    due_at = serializers.DateTimeField(allow_null=True)
    due_precision = serializers.CharField(default="NONE")
    created_at = serializers.DateTimeField()
    updated_at = serializers.DateTimeField()


class GoalSearchResultSerializer(serializers.Serializer):
    id = serializers.UUIDField()
    numeric_id = serializers.IntegerField(required=False, allow_null=True)
    title = serializers.CharField()
    description = serializers.CharField(allow_blank=True, default="")
    status = serializers.CharField()
    recurrence_kind = serializers.CharField(default="DAILY")
    cadence = serializers.CharField(source="recurrence_kind", read_only=True)
    start_date = serializers.DateField()
    target_value = serializers.IntegerField(allow_null=True, required=False)
    is_shared = serializers.BooleanField()
    participant_count = serializers.IntegerField(required=False, default=1)
    created_at = serializers.DateTimeField()
    updated_at = serializers.DateTimeField()


class GlobalSearchResponseSerializer(serializers.Serializer):
    commitments = CommitmentSearchResultSerializer(many=True)
    goals = GoalSearchResultSerializer(many=True)
    shared_goals = GoalSearchResultSerializer(many=True)
