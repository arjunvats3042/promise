import logging
import time
from datetime import datetime, timedelta, timezone as tz
from typing import Any, Dict, List, Optional

from django.db.models import Q

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import INSIGHTS_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature
from apps.commitments.models import Commitment
from apps.goals.models import Goal, GoalCheckIn

logger = logging.getLogger("promise")

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


def generate_deterministic_fallback_insights(facts: Dict[str, Any]) -> Dict[str, Any]:
    """Constructs factual, concise insights entirely from calculated backend metrics when AI is unavailable."""
    total = facts.get("commitments_total", 0)
    completed = facts.get("commitments_completed", 0)
    rate_pct = int(round(facts.get("commitment_completion_rate", 0.0) * 100))
    slots = facts.get("completion_time_slots", {})
    morning = slots.get("morning_before_12pm", 0)
    afternoon = slots.get("afternoon_12pm_to_6pm", 0)
    evening = slots.get("evening_after_6pm", 0)
    check_ins = facts.get("check_ins_past_7_days", 0)
    active_goals = facts.get("active_goals_count", 0)

    patterns: List[str] = []

    if total == 0 and check_ins == 0:
        summary = "No commitments or check-ins recorded in the past 7 days."
        patterns = ["Create a commitment or practice goal to begin tracking weekly patterns."]
        suggestion = "Start with one small daily commitment."
    elif total > 0 and completed == total:
        summary = f"All {total} commitments were completed this week."
        patterns.append(f"100% completion rate achieved across {total} commitments.")
        if check_ins > 0:
            patterns.append(f"{check_ins} practice check-ins completed.")
        suggestion = "Maintain this steady consistency."
    elif total > 0:
        summary = f"{completed} of {total} commitments were completed this week ({rate_pct}% completion rate)."
        # Identify top time slot
        max_slot = "evening"
        max_val = evening
        if morning > max_val:
            max_slot = "morning"
            max_val = morning
        if afternoon > max_val:
            max_slot = "afternoon"
            max_val = afternoon

        if max_val > 0:
            patterns.append(f"Evening commitments had the highest completion count." if max_slot == "evening" else f"Most completions occurred in the {max_slot}.")
        if check_ins > 0:
            patterns.append(f"{check_ins} practice check-ins logged across active goals.")
        suggestion = "Consistent daily execution supports long-term momentum."
    else:
        summary = f"{check_ins} practice check-ins logged across {active_goals} active goals."
        patterns.append(f"Active engagement with {active_goals} practice goals.")
        suggestion = "Add focused commitments to complement your practice goals."

    return {
        "summary": summary,
        "observed_patterns": patterns,
        "constructive_suggestion": suggestion,
    }


def generate_weekly_insights(
    user,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Extracts factual weekly metrics and generates a strictly grounded AI summary with deterministic fallback."""
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
    is_fallback = False
    insights_content: Dict[str, Any]

    try:
        ai_response = provider.generate_structured(
            prompt=prompt,
            schema=INSIGHTS_SCHEMA,
            system_prompt=INSIGHTS_PROMPT_V1,
            model=model,
            feature="insights",
        )
        if (
            isinstance(ai_response, dict)
            and isinstance(ai_response.get("summary"), str)
            and ai_response["summary"].strip()
        ):
            insights_content = {
                "summary": ai_response["summary"].strip(),
                "observed_patterns": ai_response.get("observed_patterns", []) if isinstance(ai_response.get("observed_patterns"), list) else [],
                "constructive_suggestion": str(ai_response.get("constructive_suggestion", "")).strip(),
            }
            success = True
        else:
            raise ValueError("Malformed AI insights response structure")
    except Exception as exc:
        failure_category = exc.__class__.__name__
        is_fallback = True
        logger.warning(
            "Gemini failed to generate weekly insights, using deterministic fallback",
            extra={"error": str(exc), "user_id": str(getattr(user, "id", "anonymous"))},
        )
        insights_content = generate_deterministic_fallback_insights(facts)
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

    return {
        "facts": facts,
        "insights": insights_content,
        "is_fallback": is_fallback,
        "generated_at": now.isoformat(),
        "period_start": seven_days_ago.isoformat(),
        "period_end": now.isoformat(),
    }
