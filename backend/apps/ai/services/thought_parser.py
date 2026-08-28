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


def _normalize_parsed_items(result: Dict[str, Any]) -> Dict[str, Any]:
    """Sanitizes and normalizes items so commitments conform to Promise API validation rules."""
    from django.utils.dateparse import parse_datetime

    items = result.get("items", [])
    normalized = []
    for item in items:
        if not isinstance(item, dict):
            continue
        item_type = item.get("type", "commitment")
        if item_type == "commitment":
            due_at = item.get("due_at")
            due_precision = item.get("due_precision")

            # Clean and validate due_at
            if due_at and isinstance(due_at, str) and due_at.strip():
                due_at_clean = due_at.strip()
                try:
                    parsed_dt = parse_datetime(due_at_clean)
                    if parsed_dt is not None:
                        item["due_at"] = parsed_dt.isoformat()
                        prec_upper = (due_precision or "").upper()
                        if prec_upper in ("MINUTE", "HOUR", "DATETIME"):
                            item["due_precision"] = "HOUR"
                        elif prec_upper in ("DAY", "DATE"):
                            item["due_precision"] = "DAY"
                        else:
                            item["due_precision"] = "HOUR" if (parsed_dt.hour != 0 or parsed_dt.minute != 0) else "DAY"
                    else:
                        item["due_at"] = None
                        item["due_precision"] = None
                except Exception:
                    # Non-parseable date string: clear both to avoid API 400 rejection
                    item["due_at"] = None
                    item["due_precision"] = None
            else:
                item["due_at"] = None
                item["due_precision"] = None
        normalized.append(item)
    return {"items": normalized}


def _heuristic_parse_thought(user_thought: str, timezone: str) -> Dict[str, Any]:
    """Resilient rule-based thought parser fallback when AI provider is unavailable."""
    import re
    cleaned = user_thought.strip()
    raw_lines = re.split(r"\r?\n|•|\*|(?<=\d)\.\s+", cleaned)
    items = []

    goal_keywords = {
        "daily", "every day", "everyday", "habit", "gym", "workout",
        "water", "meditat", "read", "exercise", "walk", "stretch",
        "practice", "routine", "weekly",
    }

    for line in raw_lines:
        line_str = line.strip().lstrip("-*•0123456789. ")
        if not line_str or len(line_str) < 3:
            continue

        lower_line = line_str.lower()
        is_goal = any(kw in lower_line for kw in goal_keywords)

        if is_goal:
            items.append({
                "type": "goal",
                "title": line_str[:120],
                "description": "",
                "confidence": "MEDIUM",
                "recurrence_kind": "DAILY",
                "tracking_kind": "BINARY",
            })
        else:
            items.append({
                "type": "commitment",
                "title": line_str[:120],
                "description": "",
                "confidence": "MEDIUM",
                "due_at": None,
                "due_precision": None,
            })

    if not items and cleaned:
        items.append({
            "type": "commitment",
            "title": cleaned[:120],
            "description": cleaned if len(cleaned) > 120 else "",
            "confidence": "LOW",
            "due_at": None,
            "due_precision": None,
        })

    return {"items": items}


def parse_thought_into_promises(
    user_thought: str,
    timezone: str = "Asia/Kolkata",
    current_time_iso: Optional[str] = None,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Decomposes a brain dump into proposed commitments and goals with confidence ratings."""
    if provider is None:
        provider = GeminiProvider()

    if current_time_iso is None:
        from django.utils import timezone as django_tz
        current_time_iso = django_tz.now().isoformat()

    model = get_model_for_feature("thought_parser")
    sanitized_prompt = (
        f"User timezone: {timezone}\n"
        f"Reference current time (UTC): {current_time_iso}\n"
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
        return _normalize_parsed_items(result)
    except Exception as e:
        failure_category = e.__class__.__name__
        # Graceful heuristic fallback: ensure user's brain dump is never lost due to network or provider hiccups
        fallback_result = _heuristic_parse_thought(user_thought, timezone)
        if fallback_result.get("items"):
            return _normalize_parsed_items(fallback_result)
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
