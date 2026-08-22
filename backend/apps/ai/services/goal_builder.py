import time
from typing import Any, Dict, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import GOAL_BUILDER_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature

GOAL_BUILDER_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "status": {"type": "STRING", "enum": ["READY", "NEEDS_CLARIFICATION"]},
        "clarification_question": {"type": "STRING"},
        "title": {"type": "STRING"},
        "description": {"type": "STRING"},
        "recurrence_kind": {"type": "STRING", "enum": ["DAILY", "WEEKLY_DAYS", "N_PER_PERIOD"]},
        "weekdays": {"type": "ARRAY", "items": {"type": "INTEGER"}},
        "period_unit": {"type": "STRING", "enum": ["DAY", "WEEK", "MONTH"]},
        "times_per_period": {"type": "INTEGER"},
        "tracking_kind": {"type": "STRING", "enum": ["BINARY", "COUNT"]},
        "target_value": {"type": "NUMBER"},
        "target_unit": {"type": "STRING"},
        "reasoning": {"type": "STRING"},
    },
    "required": ["status", "reasoning"],
}


def build_goal_suggestion(
    user_prompt: str,
    timezone: str = "UTC",
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Generates a structured goal suggestion or asks for clarification."""
    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("goal_builder")
    sanitized_prompt = f"User timezone: {timezone}\nUser goal request: {user_prompt.strip()}"
    start_time = time.time()
    success = False
    failure_category = None

    try:
        result = provider.generate_structured(
            prompt=sanitized_prompt,
            schema=GOAL_BUILDER_SCHEMA,
            system_prompt=GOAL_BUILDER_PROMPT_V1,
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
            feature="goal_builder",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )
