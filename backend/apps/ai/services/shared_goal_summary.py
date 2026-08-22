from datetime import datetime, timedelta, timezone as tz
import time
from typing import Any, Dict, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import PROMPT_VERSION_V1, SHARED_GOAL_PROMPT_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature
from apps.goals.models import Goal, GoalCheckIn, GoalParticipant

SHARED_GOAL_SUMMARY_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "group_summary": {"type": "STRING"},
        "collective_completion_rate": {"type": "STRING"},
        "encouragement": {"type": "STRING"},
    },
    "required": ["group_summary", "collective_completion_rate", "encouragement"],
}


def generate_shared_goal_weekly_summary(
    goal: Goal,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Generates a neutral collective weekly summary for a shared goal without ranking individual members."""
    now = datetime.now(tz=tz.utc)
    seven_days_ago = now - timedelta(days=7)

    active_participants_count = GoalParticipant.objects.filter(
        goal=goal,
        status=GoalParticipant.Status.ACTIVE,
    ).count()

    check_ins = GoalCheckIn.objects.filter(
        goal=goal,
        created_at__gte=seven_days_ago,
    )
    total_checkins_this_week = check_ins.count()

    group_facts = {
        "goal_title": goal.title,
        "active_members_count": active_participants_count,
        "total_checkins_this_week": total_checkins_this_week,
        "period": "past_7_days",
    }

    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("shared_goal_summary")
    prompt = f"Collective group facts for shared goal:\n{group_facts}"
    start_time = time.time()
    success = False
    failure_category = None

    try:
        ai_response = provider.generate_structured(
            prompt=prompt,
            schema=SHARED_GOAL_SUMMARY_SCHEMA,
            system_prompt=SHARED_GOAL_PROMPT_V1,
            model=model,
        )
        success = True
        return {
            "facts": group_facts,
            "summary": ai_response,
        }
    except Exception as e:
        failure_category = e.__class__.__name__
        raise
    finally:
        latency_ms = int((time.time() - start_time) * 1000)
        record_ai_metric(
            feature="shared_goal_summary",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )
