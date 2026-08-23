import time
from typing import Any, Dict, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import COMMITMENT_REFINER_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature

COMMITMENT_REFINER_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "status": {"type": "STRING", "enum": ["READY", "NEEDS_CLARIFICATION"]},
        "current_interpretation": {"type": "STRING"},
        "missing_information": {"type": "STRING"},
        "clarifying_question": {"type": "STRING"},
        "refined_title": {"type": "STRING"},
        "refined_description": {"type": "STRING"},
        "suggested_due_at": {"type": "STRING"},
        "suggested_due_precision": {"type": "STRING", "enum": ["MINUTE", "HOUR", "DAY"]},
        "reasoning": {"type": "STRING"},
    },
    "required": ["status", "current_interpretation", "refined_title", "reasoning"],
}


def refine_commitment(
    user_prompt: str,
    timezone: str = "Asia/Kolkata",
    current_time_iso: Optional[str] = None,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Refines a commitment thought with explicit ambiguity and missing information detection."""
    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("commitment_refiner")
    sanitized_prompt = (
        f"User timezone: {timezone}\n"
        f"Reference current time: {current_time_iso or '2026-08-22T12:00:00Z'}\n"
        f"Commitment idea: {user_prompt.strip()}"
    )
    start_time = time.time()
    success = False
    failure_category = None

    try:
        result = provider.generate_structured(
            prompt=sanitized_prompt,
            schema=COMMITMENT_REFINER_SCHEMA,
            system_prompt=COMMITMENT_REFINER_PROMPT_V1,
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
            feature="commitment_refiner",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )
