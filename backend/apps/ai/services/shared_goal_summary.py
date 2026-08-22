from datetime import datetime, timedelta, timezone as tz
from typing import Any, Dict, Optional

from rest_framework.exceptions import NotFound

from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.goals.models import Goal, GoalCheckIn, GoalParticipant

SHARED_GOAL_SUMMARY_SYSTEM_PROMPT = """You are an AI group dynamics assistant for Promise.
Your job is to generate a neutral, encouraging weekly summary of a shared group goal.

Strict Rules:
1. NEVER rank participants or compare members against each other.
2. NEVER single out or identify the "weakest" or least active member.
3. NEVER use shame, guilt, or pressure tactics.
4. Focus strictly on the collective group effort, group milestones, and encouragement.
5. All numerical claims MUST strictly match the supplied data facts.
"""

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
    user,
    goal_id: str,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Generates an encouraging, neutral weekly summary for an active shared goal participant."""
    try:
        goal = Goal.objects.get(id=goal_id, is_shared=True)
    except Goal.DoesNotExist:
        raise NotFound("Shared goal not found.")

    # Strict authorization: User must be an ACTIVE participant
    is_active = GoalParticipant.objects.filter(
        goal=goal,
        user=user,
        status=GoalParticipant.Status.ACTIVE,
    ).exists()
    if not is_active:
        raise NotFound("Shared goal not found.")

    now = datetime.now(tz=tz.utc)
    seven_days_ago = now - timedelta(days=7)

    active_participants = GoalParticipant.objects.filter(
        goal=goal,
        status=GoalParticipant.Status.ACTIVE,
    )
    total_active_members = active_participants.count()

    total_checkins_this_week = GoalCheckIn.objects.filter(
        goal=goal,
        created_at__gte=seven_days_ago,
    ).count()

    # Aggregate facts (anonymous, no per-member breakdown passed to AI)
    facts = {
        "goal_title": goal.title,
        "active_members_count": total_active_members,
        "total_group_check_ins_this_week": total_checkins_this_week,
        "recurrence_kind": goal.recurrence_kind,
        "period": "past_7_days",
    }

    if provider is None:
        provider = GeminiProvider()

    prompt = f"Collective group goal statistics:\n{facts}"
    ai_summary = provider.generate_structured(
        prompt=prompt,
        schema=SHARED_GOAL_SUMMARY_SCHEMA,
        system_prompt=SHARED_GOAL_SUMMARY_SYSTEM_PROMPT,
    )

    return {
        "facts": facts,
        "summary": ai_summary,
    }
