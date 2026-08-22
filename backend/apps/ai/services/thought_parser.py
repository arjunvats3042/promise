import time
from typing import Any, Dict, List, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import PROMPT_VERSION_V1, THOUGHT_PARSER_PROMPT_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature

THOUGHT_PARSER_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "items": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "type": {"type": "STRING", "enum": ["commitment", "goal"]},
                    "title": {"type": "STRING"},
                    "description": {"type": "STRING"},
                    "confidence": {"type": "STRING", "enum": ["HIGH", "MEDIUM", "LOW"]},
                    "due_at": {"type": "STRING"},
                    "due_precision": {"type": "STRING", "enum": ["MINUTE", "HOUR", "DAY"]},
                    "recurrence_kind": {"type": "STRING", "enum": ["DAILY", "WEEKLY_DAYS", "N_PER_PERIOD"]},
                    "weekdays": {"type": "ARRAY", "items": {"type": "INTEGER"}},
                    "tracking_kind": {"type": "STRING", "enum": ["BINARY", "COUNT"]},
                    "target_value": {"type": "NUMBER"},
                    "target_unit": {"type": "STRING"},
                },
                "required": ["type", "title"],
            },
        },
    },
    "required": ["items"],
}


def parse_thought_into_promises(
    user_thought: str,
    timezone: str = "UTC",
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Decomposes a brain dump into proposed commitments and goals with confidence ratings."""
    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("thought_parser")
    sanitized_prompt = (
        f"User timezone: {timezone}\n"
        f"User unstructured thought:\n{user_thought.strip()}"
    )
    start_time = time.time()
    success = False
    failure_category = None

    try:
        result = provider.generate_structured(
            prompt=sanitized_prompt,
            schema=THOUGHT_PARSER_SCHEMA,
            system_prompt=THOUGHT_PARSER_PROMPT_V1,
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
            feature="thought_parser",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )
