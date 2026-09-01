import time
from typing import Any, Dict, List, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import PLANNER_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature
from apps.commitments.models import Commitment

PLANNER_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "planned_order": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "commitment_id": {"type": "STRING"},
                    "suggested_time_slot": {"type": "STRING", "enum": ["Morning", "Afternoon", "Evening"]},
                    "priority_rank": {"type": "INTEGER"},
                    "is_fixed_deadline": {"type": "BOOLEAN"},
                    "note": {"type": "STRING"},
                },
                "required": ["commitment_id", "suggested_time_slot", "priority_rank"],
            },
        },
        "conflict_notes": {"type": "STRING"},
        "summary_advice": {"type": "STRING"},
    },
    "required": ["planned_order", "summary_advice"],
}


def _heuristic_planner(commitments: list) -> Dict[str, Any]:
    """Deterministic scheduling heuristic when AI is unavailable."""
    planned = []
    slots = ["Morning", "Afternoon", "Evening"]
    for idx, c in enumerate(commitments):
        slot = slots[min(idx % 3, 2)]
        planned.append({
            "commitment_id": str(c.id),
            "suggested_time_slot": slot,
            "priority_rank": idx + 1,
            "is_fixed_deadline": c.due_at is not None,
            "note": "Scheduled based on due date urgency.",
        })
    return {
        "planned_order": planned,
        "conflict_notes": None,
        "summary_advice": "Focus on the morning tasks first, then transition smoothly into afternoon obligations.",
    }


def plan_commitments(
    user,
    commitment_ids: Optional[List[str]] = None,
    user_prompt: Optional[str] = None,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Generates a suggested scheduling order for open commitments respecting immutable fixed deadlines."""
    qs = Commitment.objects.filter(created_by=user, status=Commitment.Status.PENDING)
    if commitment_ids:
        qs = qs.filter(id__in=commitment_ids)

    commitments = list(qs.order_by("due_at", "-created_at")[:15])
    if not commitments:
        return {
            "planned_order": [],
            "conflict_notes": None,
            "summary_advice": "You have no open commitments to plan.",
        }

    items_data = [
        {
            "id": str(c.id),
            "title": c.title,
            "due_at": c.due_at.isoformat() if c.due_at else None,
            "due_precision": c.due_precision,
        }
        for c in commitments
    ]

    prompt = (
        f"User request: {user_prompt or 'Help me organize my day'}\n"
        f"Available commitments to plan:\n{items_data}"
    )

    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("planner")
    start_time = time.time()
    success = False
    failure_category = None

    try:
        result = provider.generate_structured(
            prompt=prompt,
            schema=PLANNER_SCHEMA,
            system_prompt=PLANNER_PROMPT_V1,
            model=model,
        )
        success = True
        return result
    except Exception as e:
        failure_category = e.__class__.__name__
        return _heuristic_planner(commitments)
    finally:
        latency_ms = int((time.time() - start_time) * 1000)
        record_ai_metric(
            feature="planner",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )
