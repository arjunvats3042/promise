import logging
import re
import time
from typing import Any, Dict, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import GOAL_BUILDER_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature

logger = logging.getLogger("promise")

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


def _heuristic_goal_builder(prompt: str, timezone: str = "Asia/Kolkata") -> Dict[str, Any]:
    """Resilient rule-based goal suggestion fallback when AI provider is unavailable."""
    text = prompt.strip()
    lower = text.lower()

    recurrence_kind = "DAILY"
    # Check if input is too vague (no cadence words, no numeric quantity)
    cadence_keywords = [
        "daily", "everyday", "every day", "every", "roz", "har din", "har roz",
        "weekday", "weekend", "week", "hafte", "month", "mahina", "mon", "tue",
        "wed", "thu", "fri", "sat", "sun", "times a", "days a", "x a",
    ]
    has_cadence = any(kw in lower for kw in cadence_keywords)
    has_quantity = bool(re.search(r"\b\d+\s*(?:mins?|minutes?|hours?|hrs?|pages?|liters?|ltrs?|times?|km|steps?)\b", lower))

    if not has_cadence and not has_quantity and ("more" in lower or "start" in lower or "want" in lower or "wanna" in lower or len(text.split()) <= 4):
        return {
            "status": "NEEDS_CLARIFICATION",
            "title": text.strip().title()[:80],
            "description": f"Practice: {text}",
            "recurrence_kind": None,
            "weekdays": None,
            "period_unit": None,
            "times_per_period": None,
            "tracking_kind": None,
            "target_value": None,
            "target_unit": None,
            "clarification_question": "How often or for how long would you like to practice this each week (e.g. daily, 3 times a week, or 30 minutes)?",
            "reasoning": "Goal description needs a frequency or duration target to schedule properly.",
        }

    weekdays = None
    period_unit = None
    times_per_period = None

    if "weekday" in lower or "somwar se shukrawar" in lower or "mon-fri" in lower:
        recurrence_kind = "WEEKLY_DAYS"
        weekdays = [0, 1, 2, 3, 4]
    elif "weekend" in lower or "shaniwar ravivar" in lower or "sat-sun" in lower or "sat sun" in lower:
        recurrence_kind = "WEEKLY_DAYS"
        weekdays = [5, 6]
    else:
        day_map = {
            "monday": 0, "somwar": 0,
            "tuesday": 1, "mangalwar": 1,
            "wednesday": 2, "budhwar": 2,
            "thursday": 3, "guruwar": 3,
            "friday": 4, "shukrawar": 4,
            "saturday": 5, "shaniwar": 5,
            "sunday": 6, "ravivar": 6,
        }
        found_days = set()
        for day_name, day_idx in day_map.items():
            if re.search(rf"\b{day_name}\b", lower):
                found_days.add(day_idx)

        if found_days:
            recurrence_kind = "WEEKLY_DAYS"
            weekdays = sorted(list(found_days))
        else:
            n_per_week = re.search(r"(\d+)\s*(?:days?|times?|x)\s*(?:a|per)\s*week", lower)
            if n_per_week:
                recurrence_kind = "N_PER_PERIOD"
                period_unit = "WEEK"
                times_per_period = int(n_per_week.group(1))
            elif "weekly" in lower or "hafte me" in lower:
                recurrence_kind = "N_PER_PERIOD"
                period_unit = "WEEK"
                times_per_period = 3
            else:
                recurrence_kind = "DAILY"

    tracking_kind = "BINARY"
    target_value = None
    target_unit = None

    unit_match = re.search(
        r"(\d+(?:\.\d+)?)\s*(mins?|minutes?|hours?|hrs?|liters?|ltrs?|pages?|steps?|km|kms?|glasses|reps|pushups)\b",
        lower,
    )
    if unit_match:
        val = float(unit_match.group(1))
        unit = unit_match.group(2).lower()
        if unit.startswith("min"):
            unit = "minutes"
        elif unit.startswith("hr") or unit.startswith("hour"):
            unit = "hours"
        elif unit.startswith("liter") or unit.startswith("ltr"):
            unit = "liters"
        elif unit.startswith("page"):
            unit = "pages"
        elif unit.startswith("step"):
            unit = "steps"
        elif unit.startswith("km"):
            unit = "km"

        tracking_kind = "COUNT"
        target_value = val
        target_unit = unit
    elif recurrence_kind == "N_PER_PERIOD" and times_per_period:
        target_value = float(times_per_period)
        target_unit = "times"

    title = re.sub(
        r"^(?:i\s+)?(?:wanna|want\s+to|need\s+to|have\s+to|plan\s+to|like\s+to|start|do)\s+",
        "",
        text,
        flags=re.IGNORECASE,
    )
    title_cleaned = re.sub(
        r"\b(?:everyday|every\s+day|daily|every\s+weekday|every\s+weekend|every\s+week|\d+\s*days?\s*a\s*week|\d+\s*times?\s*a\s*week|for\s+a\s+month|for\s+\d+\s*days?)\b",
        "",
        title,
        flags=re.IGNORECASE,
    ).strip()
    title_cleaned = re.sub(r"\s+", " ", title_cleaned).strip()
    if not title_cleaned:
        title_cleaned = text[:60]

    title_cleaned = title_cleaned.title()
    rec_label = recurrence_kind.lower().replace("_", " ")

    return {
        "status": "READY",
        "title": title_cleaned[:80],
        "description": f"Practice: {text}",
        "recurrence_kind": recurrence_kind,
        "weekdays": weekdays if recurrence_kind == "WEEKLY_DAYS" else None,
        "period_unit": period_unit if recurrence_kind == "N_PER_PERIOD" else None,
        "times_per_period": times_per_period if recurrence_kind == "N_PER_PERIOD" else None,
        "tracking_kind": tracking_kind,
        "target_value": target_value,
        "target_unit": target_unit,
        "clarification_question": None,
        "reasoning": f"Configured {rec_label} practice from your description.",
    }


def build_goal_suggestion(
    user_prompt: str,
    timezone: str = "Asia/Kolkata",
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Generates a structured goal suggestion or asks for clarification with resilient fallback."""
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
        logger.warning(
            f"Goal builder Gemini provider failed ({failure_category}): {e}. "
            "Engaging resilient deterministic rule-based goal suggestion fallback."
        )
        return _heuristic_goal_builder(user_prompt, timezone)
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
