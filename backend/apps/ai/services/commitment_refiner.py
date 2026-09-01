import logging
import re
import time
from typing import Any, Dict, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import COMMITMENT_REFINER_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature

logger = logging.getLogger("promise")

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


def _heuristic_commitment_refiner(
    user_prompt: str,
    timezone: str = "Asia/Kolkata",
    current_time_iso: Optional[str] = None,
) -> Dict[str, Any]:
    """Resilient rule-based commitment refiner fallback when AI provider is unavailable."""
    from apps.ai.services.thought_parser import _clean_title_heuristic, _extract_date_time_from_text

    lower = user_prompt.lower()
    extracted = _extract_date_time_from_text(user_prompt, timezone)
    title = _clean_title_heuristic(user_prompt)
    if not title:
        title = user_prompt.strip()[:60].title()

    vague_terms = ["soon", "later", "sometime", "jaldi", "kabhi", "eventually"]
    is_vague = any(re.search(rf"\b{term}\b", lower) for term in vague_terms) and not extracted

    if is_vague:
        return {
            "status": "NEEDS_CLARIFICATION",
            "current_interpretation": f"Commitment: {title}",
            "missing_information": "Exact deadline or target completion time.",
            "clarifying_question": "When would you like to have this completed by (e.g. tomorrow at 5pm, or Friday)?",
            "refined_title": title[:80],
            "refined_description": "",
            "suggested_due_at": None,
            "suggested_due_precision": None,
            "reasoning": "A deadline is required to schedule timely reminders.",
        }

    return {
        "status": "READY",
        "current_interpretation": f"One-time commitment: {title}",
        "missing_information": None,
        "clarifying_question": None,
        "refined_title": title[:80],
        "refined_description": "",
        "suggested_due_at": extracted[0] if extracted else None,
        "suggested_due_precision": extracted[1] if extracted else None,
        "reasoning": "Extracted commitment title and deadline from your input.",
    }


def refine_commitment(
    user_prompt: str,
    timezone: str = "Asia/Kolkata",
    current_time_iso: Optional[str] = None,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Refines a commitment thought with explicit ambiguity detection and resilient fallback."""
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
        logger.warning(
            f"Commitment refiner Gemini provider failed ({failure_category}): {e}. "
            "Engaging resilient deterministic rule-based commitment refiner fallback."
        )
        return _heuristic_commitment_refiner(user_prompt, timezone, current_time_iso)
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
