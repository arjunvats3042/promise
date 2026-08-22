from datetime import datetime, timedelta, timezone as tz
from typing import Any, Dict, Optional

from django.db.models import Count, Q

from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.commitments.models import Commitment
from apps.goals.models import Goal, GoalCheckIn

INSIGHTS_SYSTEM_PROMPT = """You are an AI insights assistant for Promise.
Your job is to analyze purely factual weekly statistics provided in the prompt and generate:
1. `summary`: A concise 1-2 sentence overview of the user's progress this week.
2. `key_patterns`: An array of 1 to 3 factual observations based strictly on the supplied data.
3. `constructive_suggestion`: A practical, supportive tip to help the user maintain momentum.

Strict Rules:
- NEVER invent, extrapolate, or hallucinate statistics not present in the prompt.
- Never shame or judge the user.
- All numbers cited in your response MUST match the supplied input data exactly.
"""

INSIGHTS_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "summary": {"type": "STRING"},
        "key_patterns": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
        "constructive_suggestion": {"type": "STRING"},
    },
    "required": ["summary", "key_patterns", "constructive_suggestion"],
}


def generate_weekly_insights(
    user,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Extracts factual weekly metrics for the user and generates an AI summary."""
    now = datetime.now(tz=tz.utc)
    seven_days_ago = now - timedelta(days=7)

    # 1. Commitments metrics
    commitments_qs = Commitment.objects.filter(
        created_by=user,
        created_at__gte=seven_days_ago,
    )
    total_commitments = commitments_qs.count()
    completed_commitments = commitments_qs.filter(status=Commitment.Status.COMPLETED).count()
    missed_commitments = commitments_qs.filter(
        Q(status=Commitment.Status.PENDING) & Q(due_at__lt=now)
    ).count()

    # 2. Goals metrics
    active_goals = Goal.objects.filter(
        created_by=user,
        status=Goal.Status.ACTIVE,
    ).count()

    check_ins_count = GoalCheckIn.objects.filter(
        created_by=user,
        created_at__gte=seven_days_ago,
    ).count()

    # Build factual summary payload
    facts = {
        "period": "past_7_days",
        "commitments": {
            "total": total_commitments,
            "completed": completed_commitments,
            "missed": missed_commitments,
        },
        "goals": {
            "active_goals_count": active_goals,
            "check_ins_past_7_days": check_ins_count,
        },
    }

    if provider is None:
        provider = GeminiProvider()

    prompt = f"Factual weekly data for user:\n{facts}"
    ai_response = provider.generate_structured(
        prompt=prompt,
        schema=INSIGHTS_SCHEMA,
        system_prompt=INSIGHTS_SYSTEM_PROMPT,
    )

    return {
        "facts": facts,
        "insights": ai_response,
    }
