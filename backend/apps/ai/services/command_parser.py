from datetime import datetime, timedelta, timezone as tz
import time
from typing import Any, Dict, List, Optional

from django.db.models import Q

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import COMMAND_PARSER_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature
from apps.commitments.models import Commitment
from apps.commitments.serializers import CommitmentSerializer
from apps.goals.models import Goal
from apps.goals.serializers import GoalSerializer

COMMAND_PARSER_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "entity": {"type": "STRING", "enum": ["commitments", "goals"]},
        "status": {"type": "STRING", "enum": ["all", "open", "completed", "overdue"]},
        "period": {"type": "STRING", "enum": ["today", "this_week", "this_month", "all"]},
        "due_before": {"type": "STRING"},
        "due_after": {"type": "STRING"},
        "query_text": {"type": "STRING"},
        "interpreted_query_preview": {"type": "STRING"},
    },
    "required": ["entity", "status", "period", "interpreted_query_preview"],
}


def parse_and_execute_command(
    user,
    natural_query: str,
    timezone: str = "Asia/Kolkata",
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Translates natural language to query intent, then safely executes backend filter."""
    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("command_parser")
    sanitized_prompt = f"User timezone: {timezone}\nUser question: {natural_query.strip()}"
    start_time = time.time()
    success = False
    failure_category = None

    try:
        parsed_filter = provider.generate_structured(
            prompt=sanitized_prompt,
            schema=COMMAND_PARSER_SCHEMA,
            system_prompt=COMMAND_PARSER_PROMPT_V1,
            model=model,
        )
        success = True
    except Exception as e:
        failure_category = e.__class__.__name__
        raise
    finally:
        latency_ms = int((time.time() - start_time) * 1000)
        record_ai_metric(
            feature="command_parser",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )

    entity = parsed_filter.get("entity", "commitments")
    status = parsed_filter.get("status", "open")
    period = parsed_filter.get("period", "all")
    query_text = parsed_filter.get("query_text", "")

    now = datetime.now(tz=tz.utc)

    # Compute period start
    period_start = None
    if period == "today":
        period_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    elif period == "this_week":
        period_start = now - timedelta(days=7)
    elif period == "this_month":
        period_start = now - timedelta(days=30)

    results = []

    if entity == "commitments":
        qs = Commitment.objects.filter(created_by=user)
        if status == "open":
            qs = qs.filter(status=Commitment.Status.PENDING)
        elif status == "completed":
            qs = qs.filter(status=Commitment.Status.COMPLETED)
        elif status == "overdue":
            qs = qs.filter(status=Commitment.Status.PENDING, due_at__lt=now)

        if period_start is not None:
            qs = qs.filter(created_at__gte=period_start)

        if query_text:
            qs = qs.filter(Q(title__icontains=query_text) | Q(description__icontains=query_text))

        items = list(qs.order_by("due_at", "-created_at")[:20])
        results = CommitmentSerializer(items, many=True).data

    else:
        qs = Goal.objects.filter(created_by=user)
        if status == "open":
            qs = qs.filter(status=Goal.Status.ACTIVE)
        elif status == "completed":
            qs = qs.filter(status=Goal.Status.COMPLETED)

        if query_text:
            qs = qs.filter(Q(title__icontains=query_text) | Q(description__icontains=query_text))

        items = list(qs.order_by("-updated_at")[:20])
        results = GoalSerializer(items, many=True).data

    return {
        "filter": parsed_filter,
        "entity": entity,
        "interpreted_query_preview": parsed_filter.get("interpreted_query_preview", natural_query),
        "results_count": len(results),
        "results": results,
    }
