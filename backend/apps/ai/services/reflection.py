import time
from typing import Any, Dict, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import PROMPT_VERSION_V1, REFLECTION_PROMPT_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature
from apps.commitments.models import Commitment
from apps.goals.models import Goal

REFLECTION_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "reflection_summary": {"type": "STRING"},
        "suggested_adjustments": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
        "smaller_next_action": {"type": "STRING"},
    },
    "required": ["reflection_summary", "suggested_adjustments", "smaller_next_action"],
}


def reflect_on_stuck_item(
    user,
    item_type: str,
    item_id: Optional[str] = None,
    user_notes: Optional[str] = None,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Generates supportive, non-judgmental guidance and micro-steps for stuck goals/commitments."""
    item_context = ""
    if item_id:
        if item_type == "commitment":
            try:
                c = Commitment.objects.get(id=item_id, created_by=user)
                item_context = f"Commitment Title: {c.title}\nStatus: {c.status}\nDue: {c.due_at}"
            except Commitment.DoesNotExist:
                pass
        elif item_type == "goal":
            try:
                g = Goal.objects.get(id=item_id, created_by=user)
                item_context = f"Goal Title: {g.title}\nCadence: {g.recurrence_kind}\nTarget: {g.target_value} {g.target_unit}"
            except Goal.DoesNotExist:
                pass

    prompt = (
        f"Item Type: {item_type}\n"
        f"Context:\n{item_context}\n"
        f"User Reflection / Difficulty Notes:\n{user_notes or 'I have been struggling to complete this consistently.'}"
    )

    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("reflection")
    start_time = time.time()
    success = False
    failure_category = None

    try:
        result = provider.generate_structured(
            prompt=prompt,
            schema=REFLECTION_SCHEMA,
            system_prompt=REFLECTION_PROMPT_V1,
            model=model,
        )
        success = True
        return result
    except Exception as e:
        failure_category = e.__class__.__name__
        raise
    finally:
        latency_ms = int((time.time() - start_time) * 1000)
        record_ai_metric(
            feature="reflection",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )
