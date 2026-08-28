from rest_framework import serializers


class SyncActionItemSerializer(serializers.Serializer):
    action_id = serializers.UUIDField(required=True)
    action_type = serializers.ChoiceField(
        choices=[
            "CREATE_COMMITMENT",
            "COMPLETE_COMMITMENT",
            "CHECK_IN_GOAL",
            "CREATE_GOAL",
            "DELETE_COMMITMENT",
        ],
        required=True,
    )
    entity_id = serializers.CharField(required=True)
    payload = serializers.DictField(required=False, default=dict)
    client_timestamp = serializers.DateTimeField(required=False, allow_null=True)


class SyncBatchRequestSerializer(serializers.Serializer):
    actions = SyncActionItemSerializer(many=True, required=True)


class SyncActionResultSerializer(serializers.Serializer):
    action_id = serializers.UUIDField()
    status = serializers.ChoiceField(choices=["APPLIED", "ALREADY_PROCESSED", "FAILED"])
    error_message = serializers.CharField(required=False, allow_null=True)


class SyncBatchResponseSerializer(serializers.Serializer):
    results = SyncActionResultSerializer(many=True)
    server_timestamp = serializers.DateTimeField()
