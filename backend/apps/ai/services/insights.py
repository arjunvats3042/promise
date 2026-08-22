from datetime import datetime, timedelta, timezone as tz
import time
from typing import Any, Dict, Optional

from django.db.models import Q

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import INSIGHTS_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature
from apps.commitments.models import Commitment
from apps.goals.models import Goal, GoalCheckIn

INSIGHTS_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "summary": {"type": "STRING"},
        "observed_patterns": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
        "constructive_suggestion": {"type": "STRING"},
    },
    "required": ["summary", "observed_patterns", "constructive_suggestion"],
}


def generate_weekly_insights(
    user,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Extracts factual weekly metrics for the user and generates a strictly grounded AI summary."""
    now = datetime.now(tz=tz.utc)
    seven_days_ago = now - timedelta(days=7)

    # 1. Deterministic Commitments metrics
    commitments_qs = Commitment.objects.filter(
        created_by=user,
        created_at__gte=seven_days_ago,
    )
    total_commitments = commitments_qs.count()
    completed_qs = commitments_qs.filter(status=Commitment.Status.COMPLETED)
    completed_commitments = completed_qs.count()
    overdue_commitments = commitments_qs.filter(
        Q(status=Commitment.Status.PENDING) & Q(due_at__lt=now)
    ).count()

    # Time-of-day completion breakdown
    morning_completions = 0
    afternoon_completions = 0
    evening_completions = 0

    for c in completed_qs:
        if c.completed_at:
            hour = c.completed_at.hour
            if hour < 12:
                morning_completions += 1
            elif hour < 18:
                afternoon_completions += 1
            else:
                evening_completions += 1

    completion_rate = round(completed_commitments / total_commitments, 2) if total_commitments > 0 else 0.0

    # 2. Goals metrics
    active_goals = Goal.objects.filter(
        created_by=user,
        status=Goal.Status.ACTIVE,
    ).count()

    check_ins_count = GoalCheckIn.objects.filter(
        created_by=user,
        created_at__gte=seven_days_ago,
    ).count()

    # Build strictly factual summary payload
    facts = {
        "period": "past_7_days",
        "commitments_total": total_commitments,
        "commitments_completed": completed_commitments,
        "commitments_overdue": overdue_commitments,
        "commitment_completion_rate": completion_rate,
        "completion_time_slots": {
            "morning_before_12pm": morning_completions,
            "afternoon_12pm_to_6pm": afternoon_completions,
            "evening_after_6pm": evening_completions,
        },
        "active_goals_count": active_goals,
        "check_ins_past_7_days": check_ins_count,
    }

    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("insights")
    prompt = f"Authoritative weekly data for user:\n{facts}"
    start_time = time.time()
    success = False
    failure_category = None

    try:
        ai_response = provider.generate_structured(
            prompt=prompt,
            schema=INSIGHTS_SCHEMA,
            system_prompt=INSIGHTS_PROMPT_V1,
            model=model,
        )
        success = True
        return {
            "facts": facts,
            "insights": ai_response,
        }
    except Exception as e:
        failure_category = e.__class__.__name__
        raise
    finally:
        latency_ms = int((time.time() - start_time) * 1000)
        record_ai_metric(
            feature="insights",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )
