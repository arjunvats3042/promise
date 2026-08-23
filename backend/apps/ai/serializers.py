from rest_framework import serializers


class GoalSuggestionRequestSerializer(serializers.Serializer):
    prompt = serializers.CharField(max_length=1000, required=True, allow_blank=False)
    timezone = serializers.CharField(max_length=50, default="Asia/Kolkata")


class GoalSuggestionResponseSerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=["READY", "NEEDS_CLARIFICATION"], default="READY")
    clarification_question = serializers.CharField(required=False, allow_null=True)
    title = serializers.CharField(required=False, allow_blank=True, default="")
    description = serializers.CharField(required=False, allow_blank=True, default="")
    recurrence_kind = serializers.CharField(required=False, allow_blank=True, default="DAILY")
    weekdays = serializers.ListField(child=serializers.IntegerField(), required=False, default=list)
    period_unit = serializers.CharField(required=False, allow_null=True)
    times_per_period = serializers.IntegerField(required=False, allow_null=True)
    tracking_kind = serializers.CharField(required=False, allow_blank=True, default="BINARY")
    target_value = serializers.DecimalField(max_digits=10, decimal_places=2, required=False, allow_null=True)
    target_unit = serializers.CharField(required=False, allow_blank=True, default="")
    reasoning = serializers.CharField(required=False, allow_blank=True, default="")


class CommitmentRefinementRequestSerializer(serializers.Serializer):
    prompt = serializers.CharField(max_length=1000, required=True, allow_blank=False)
    timezone = serializers.CharField(max_length=50, default="Asia/Kolkata")


class CommitmentRefinementResponseSerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=["READY", "NEEDS_CLARIFICATION"], default="READY")
    current_interpretation = serializers.CharField(required=False, allow_blank=True, default="")
    missing_information = serializers.CharField(required=False, allow_null=True)
    clarifying_question = serializers.CharField(required=False, allow_null=True)
    refined_title = serializers.CharField(required=False, allow_blank=True, default="")
    refined_description = serializers.CharField(required=False, allow_blank=True, default="")
    suggested_due_at = serializers.CharField(required=False, allow_null=True)
    suggested_due_precision = serializers.CharField(required=False, allow_null=True)
    reasoning = serializers.CharField(required=False, allow_blank=True, default="")


class ThoughtParserRequestSerializer(serializers.Serializer):
    thought = serializers.CharField(max_length=500, required=True, allow_blank=False)
    timezone = serializers.CharField(max_length=50, default="Asia/Kolkata")


class ParsedThoughtItemSerializer(serializers.Serializer):
    type = serializers.ChoiceField(choices=["commitment", "goal"])
    title = serializers.CharField()
    description = serializers.CharField(required=False, allow_blank=True, default="")
    confidence = serializers.ChoiceField(choices=["HIGH", "MEDIUM", "LOW"], default="HIGH")
    due_at = serializers.CharField(required=False, allow_null=True)
    due_precision = serializers.CharField(required=False, allow_null=True)
    recurrence_kind = serializers.CharField(required=False, allow_null=True)
    weekdays = serializers.ListField(child=serializers.IntegerField(), required=False, default=list)
    tracking_kind = serializers.CharField(required=False, allow_null=True)
    target_value = serializers.DecimalField(max_digits=10, decimal_places=2, required=False, allow_null=True)
    target_unit = serializers.CharField(required=False, allow_blank=True, default="")


class ThoughtParserResponseSerializer(serializers.Serializer):
    items = ParsedThoughtItemSerializer(many=True)


class WeeklyInsightsResponseSerializer(serializers.Serializer):
    facts = serializers.DictField()
    insights = serializers.DictField()
    is_fallback = serializers.BooleanField(default=False)
    generated_at = serializers.CharField(required=False, allow_blank=True)
    period_start = serializers.CharField(required=False, allow_blank=True)
    period_end = serializers.CharField(required=False, allow_blank=True)


class CommandParserRequestSerializer(serializers.Serializer):
    query = serializers.CharField(max_length=500, required=True, allow_blank=False)
    timezone = serializers.CharField(max_length=50, default="Asia/Kolkata")


class CommandParserResponseSerializer(serializers.Serializer):
    filter = serializers.DictField()
    entity = serializers.CharField()
    interpreted_query_preview = serializers.CharField(required=False, allow_blank=True, default="")
    results_count = serializers.IntegerField()
    results = serializers.ListField()


class PlannerRequestSerializer(serializers.Serializer):
    prompt = serializers.CharField(max_length=1000, required=False, allow_blank=True)
    commitment_ids = serializers.ListField(child=serializers.UUIDField(), required=False, default=list)


class PlannerItemSerializer(serializers.Serializer):
    commitment_id = serializers.CharField()
    suggested_time_slot = serializers.CharField()
    priority_rank = serializers.IntegerField()
    is_fixed_deadline = serializers.BooleanField(default=False)
    note = serializers.CharField(required=False, allow_blank=True, default="")


class PlannerResponseSerializer(serializers.Serializer):
    planned_order = PlannerItemSerializer(many=True)
    conflict_notes = serializers.CharField(required=False, allow_null=True)
    summary_advice = serializers.CharField()


class ReflectionRequestSerializer(serializers.Serializer):
    item_type = serializers.ChoiceField(choices=["goal", "commitment", "general"])
    item_id = serializers.CharField(required=False, allow_blank=True, default="")
    notes = serializers.CharField(max_length=1000, required=False, allow_blank=True, default="")


class ReflectionResponseSerializer(serializers.Serializer):
    reflection_summary = serializers.CharField()
    suggested_adjustments = serializers.ListField(child=serializers.CharField())
    smaller_next_action = serializers.CharField()


class SharedGoalWeeklySummaryResponseSerializer(serializers.Serializer):
    facts = serializers.DictField()
    summary = serializers.DictField()


class ChatSummaryRequestSerializer(serializers.Serializer):
    limit = serializers.IntegerField(default=50, min_value=1, max_value=200)


class ChatSummaryResponseSerializer(serializers.Serializer):
    summary = serializers.CharField()
    key_decisions = serializers.ListField(child=serializers.CharField(), default=list)
    agreed_actions = serializers.ListField(child=serializers.CharField(), default=list)
    important_dates = serializers.ListField(child=serializers.CharField(), default=list)
    open_questions = serializers.ListField(child=serializers.CharField(), default=list)


class DailyMotivationQuoteSerializer(serializers.Serializer):
    id = serializers.UUIDField()
    date = serializers.DateField()
    quote = serializers.CharField()
    provider = serializers.CharField()
    model = serializers.CharField()
    created_at = serializers.DateTimeField()
    updated_at = serializers.DateTimeField()
