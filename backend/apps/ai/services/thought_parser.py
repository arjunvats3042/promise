import datetime
import re
import time
from typing import Any, Dict, List, Optional
import zoneinfo

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


def _normalize_parsed_items(result: Dict[str, Any], timezone_str: str = "Asia/Kolkata") -> Dict[str, Any]:
    """Sanitizes and normalizes items so commitments conform to Promise API validation rules."""
    from django.utils.dateparse import parse_date, parse_datetime

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
                    if parsed_dt is None:
                        parsed_d = parse_date(due_at_clean)
                        if parsed_d is not None:
                            try:
                                tz = zoneinfo.ZoneInfo(timezone_str)
                            except Exception:
                                tz = datetime.timezone.utc
                            local_dt = datetime.datetime(parsed_d.year, parsed_d.month, parsed_d.day, 23, 59, 0, tzinfo=tz)
                            parsed_dt = local_dt.astimezone(datetime.timezone.utc)
                            item["due_precision"] = "DAY"

                    if parsed_dt is not None:
                        if parsed_dt.tzinfo is None:
                            try:
                                tz = zoneinfo.ZoneInfo(timezone_str)
                            except Exception:
                                tz = datetime.timezone.utc
                            parsed_dt = parsed_dt.replace(tzinfo=tz).astimezone(datetime.timezone.utc)
                        else:
                            parsed_dt = parsed_dt.astimezone(datetime.timezone.utc)

                        item["due_at"] = parsed_dt.strftime("%Y-%m-%dT%H:%M:%SZ")
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
                    item["due_at"] = None
                    item["due_precision"] = None
            else:
                # If AI didn't return due_at, check if title contains relative date/time
                extracted = _extract_date_time_from_text(item.get("title", ""), timezone_str)
                if extracted:
                    item["due_at"] = extracted[0]
                    item["due_precision"] = extracted[1]
                else:
                    item["due_at"] = None
                    item["due_precision"] = None
        normalized.append(item)
    return {"items": normalized}


def _extract_date_time_from_text(text: str, timezone_str: str) -> Optional[tuple[str, str]]:
    """Extracts relative date and time from natural text like 'after 3 days', 'in afternoon', 'tomorrow at 12pm'."""
    lower = text.lower()
    try:
        tz = zoneinfo.ZoneInfo(timezone_str)
    except Exception:
        tz = datetime.timezone.utc

    now_local = datetime.datetime.now(tz)
    target_date = None
    target_time = None
    precision = "DAY"

    WORD_NUMBERS = {
        "a": 1, "an": 1, "one": 1, "two": 2, "three": 3, "four": 4,
        "five": 5, "six": 6, "seven": 7, "eight": 8, "nine": 9, "ten": 10,
    }

    # 1. Detect relative offsets (e.g. "after 3 days", "3 days after", "in 2 weeks", "2 weeks later", "in an hour")
    offset_match = re.search(
        r"\b(?:in|after)\s+(a|an|one|two|three|four|five|six|seven|eight|nine|ten|\d+)\s*(days?|weeks?|months?|hours?|hrs?|minutes?|mins?)\b"
        r"|\b(a|an|one|two|three|four|five|six|seven|eight|nine|ten|\d+)\s*(days?|weeks?|months?|hours?|hrs?|minutes?|mins?)\s+(?:after|later|from\s+now)\b",
        lower,
    )
    if offset_match:
        amt_str = offset_match.group(1) or offset_match.group(3)
        unit_str = offset_match.group(2) or offset_match.group(4)
        amount = int(amt_str) if amt_str and amt_str.isdigit() else WORD_NUMBERS.get(amt_str or "", 1)

        if unit_str.startswith("day"):
            target_date = now_local.date() + datetime.timedelta(days=amount)
            precision = "DAY"
        elif unit_str.startswith("week"):
            target_date = now_local.date() + datetime.timedelta(weeks=amount)
            precision = "DAY"
        elif unit_str.startswith("month"):
            target_date = now_local.date() + datetime.timedelta(days=amount * 30)
            precision = "DAY"
        elif unit_str.startswith("hour") or unit_str.startswith("hr"):
            future = now_local + datetime.timedelta(hours=amount)
            target_date = future.date()
            target_time = datetime.time(future.hour, future.minute)
            precision = "HOUR"
        elif unit_str.startswith("min"):
            future = now_local + datetime.timedelta(minutes=amount)
            target_date = future.date()
            target_time = datetime.time(future.hour, future.minute)
            precision = "HOUR"

    # 2. Detect day keywords or Month & Day
    if target_date is None:
        MONTH_MAP = {
            "jan": 1, "january": 1,
            "feb": 2, "february": 2,
            "mar": 3, "march": 3,
            "apr": 4, "april": 4,
            "may": 5,
            "jun": 6, "june": 6,
            "jul": 7, "july": 7,
            "aug": 8, "august": 8,
            "sep": 9, "september": 9,
            "oct": 10, "october": 10,
            "nov": 11, "november": 11,
            "dec": 12, "december": 12,
        }

        # Check "1st of September", "15th Oct"
        dm_match = re.search(
            r"\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b",
            lower,
        )
        # Check "September 1st", "Oct 15"
        md_match = re.search(
            r"\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+(\d{1,2})(?:st|nd|rd|th)?\b",
            lower,
        )

        if dm_match:
            day = int(dm_match.group(1))
            month_str = dm_match.group(2)
            month = MONTH_MAP.get(month_str, 1)
            year = now_local.year
            candidate = datetime.date(year, month, min(day, 28))
            if candidate < now_local.date():
                year += 1
            try:
                target_date = datetime.date(year, month, day)
            except ValueError:
                target_date = datetime.date(year, month, 28)
            precision = "DAY"
        elif md_match:
            month_str = md_match.group(1)
            day = int(md_match.group(2))
            month = MONTH_MAP.get(month_str, 1)
            year = now_local.year
            candidate = datetime.date(year, month, min(day, 28))
            if candidate < now_local.date():
                year += 1
            try:
                target_date = datetime.date(year, month, day)
            except ValueError:
                target_date = datetime.date(year, month, 28)
            precision = "DAY"
        elif "day after tomorrow" in lower:
            target_date = now_local.date() + datetime.timedelta(days=2)
        elif "tomorrow" in lower:
            target_date = now_local.date() + datetime.timedelta(days=1)
        elif "tonight" in lower:
            target_date = now_local.date()
            target_time = datetime.time(21, 0)
            precision = "HOUR"
        elif "today" in lower:
            target_date = now_local.date()
        elif "next week" in lower:
            target_date = now_local.date() + datetime.timedelta(days=7)
        elif "weekend" in lower:
            days_ahead = (5 - now_local.weekday()) % 7
            if days_ahead == 0:
                days_ahead = 7
            target_date = now_local.date() + datetime.timedelta(days=days_ahead)

    # 3. Detect named periods of day (e.g. "in afternoon", "afternoon", "in morning", "evening", "at night")
    if "afternoon" in lower:
        if target_time is None:
            target_time = datetime.time(14, 0)
        precision = "HOUR"
    elif "morning" in lower:
        if target_time is None:
            target_time = datetime.time(9, 0)
        precision = "HOUR"
    elif "evening" in lower:
        if target_time is None:
            target_time = datetime.time(18, 0)
        precision = "HOUR"
    elif "night" in lower and "tonight" not in lower:
        if target_time is None:
            target_time = datetime.time(21, 0)
        precision = "HOUR"
    elif "noon" in lower:
        if target_time is None:
            target_time = datetime.time(12, 0)
        precision = "HOUR"

    # 4. Detect explicit clock time: e.g. "12:00 p.m.", "12:00 pm", "12 pm", "5:30 am", "5pm", "17:00"
    time_match = re.search(r"\b(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?|am|pm)?\b", lower)
    if time_match:
        raw_hour = int(time_match.group(1))
        raw_min = int(time_match.group(2)) if time_match.group(2) else 0
        ampm = time_match.group(3)

        if ampm:
            ampm_clean = ampm.replace(".", "")
            if ampm_clean == "pm" and raw_hour < 12:
                raw_hour += 12
            elif ampm_clean == "am" and raw_hour == 12:
                raw_hour = 0
            target_time = datetime.time(raw_hour, raw_min)
            precision = "HOUR"
        elif ":" in time_match.group(0) and raw_hour <= 23 and raw_min <= 59:
            target_time = datetime.time(raw_hour, raw_min)
            precision = "HOUR"

    if target_date is not None or target_time is not None:
        final_date = target_date or now_local.date()
        final_time = target_time or datetime.time(18, 0)
        local_target = datetime.datetime.combine(final_date, final_time, tzinfo=tz)
        utc_target = local_target.astimezone(datetime.timezone.utc)
        return utc_target.strftime("%Y-%m-%dT%H:%M:%SZ"), precision

    return None


def _split_compound_thoughts(text: str) -> List[str]:
    """Intelligently decomposes continuous speech and run-on sentences into individual task clauses."""
    cleaned = text.strip()
    if not cleaned:
        return []

    # 1. Coarse split by newlines, bullets, and list numbers
    coarse_chunks = re.split(r"\r?\n|•|\*|(?<=\d)\.\s+", cleaned)
    fine_chunks = []

    for chunk in coarse_chunks:
        chunk = re.sub(r"^(?:[-*•]|\d+[.)]\s+)", "", chunk.strip()).strip()
        if not chunk:
            continue

        # 2. Split on modal task boundaries or coordinating conjunctions:
        # e.g. "and I have to", "also I need to", "and I must", "and call", "and send"
        task_split_pattern = (
            r"(?:\s+(?:and\s+)?(?:also\s+)?(?:then\s+)?(?:i\s+(?:have\s+to|need\s+to|must|want\s+to|will|should|gotta|plan\s+to))\s+)"
            r"|(?:\s+(?:and\s+also|and\s+then|plus\s+i)\s+)"
            r"|(?:\s*,\s*(?:and\s+)?(?:call|send|email|buy|pay|meet|submit|write|finish|clean|visit|schedule|pick\s+up|order|remind|workout|exercise|meditate|drink|read)\b)"
            r"|(?:\s+and\s+(?:call|send|email|buy|pay|meet|submit|write|finish|clean|visit|schedule|pick\s+up|order|remind|workout|exercise|meditate|drink|read)\b)"
        )
        sub_tasks = re.split(task_split_pattern, chunk, flags=re.IGNORECASE)
        for sub in sub_tasks:
            if sub and sub.strip() and len(sub.strip()) >= 3:
                cleaned_sub = re.sub(
                    r"^(?:and\s+|also\s+|then\s+|plus\s+|so\s+)?(?:i\s+(?:have\s+to|need\s+to|must|want\s+to|will|should|gotta|plan\s+to)\s+)?",
                    "",
                    sub.strip(),
                    flags=re.IGNORECASE,
                ).strip()
                if cleaned_sub and len(cleaned_sub) >= 3:
                    fine_chunks.append(cleaned_sub)
                elif sub.strip():
                    fine_chunks.append(sub.strip())

    return fine_chunks if fine_chunks else [cleaned]


def _heuristic_parse_thought(user_thought: str, timezone: str) -> Dict[str, Any]:
    """Resilient rule-based thought parser fallback when AI provider is unavailable."""
    raw_tasks = _split_compound_thoughts(user_thought)
    items = []

    goal_keywords = {
        "daily", "every day", "everyday", "habit", "gym", "workout",
        "water", "meditat", "read", "exercise", "walk", "stretch",
        "practice", "routine", "weekly",
    }

    for task_str in raw_tasks:
        task_str = task_str.strip().lstrip("-*•0123456789. ")
        if not task_str or len(task_str) < 3:
            continue

        lower_task = task_str.lower()
        is_goal = any(kw in lower_task for kw in goal_keywords)

        if is_goal:
            items.append({
                "type": "goal",
                "title": _clean_title_heuristic(task_str)[:120],
                "description": "",
                "confidence": "MEDIUM",
                "recurrence_kind": "DAILY",
                "tracking_kind": "BINARY",
            })
        else:
            extracted_due = _extract_date_time_from_text(task_str, timezone)
            items.append({
                "type": "commitment",
                "title": _clean_title_heuristic(task_str)[:120],
                "description": "",
                "confidence": "HIGH" if extracted_due else "MEDIUM",
                "due_at": extracted_due[0] if extracted_due else None,
                "due_precision": extracted_due[1] if extracted_due else None,
            })

    return {"items": items}


def _clean_title_heuristic(text: str) -> str:
    cleaned = text
    patterns = [
        r"\b(?:in|after)\s+(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|\d+)\s*(?:days?|weeks?|months?|hours?|hrs?|minutes?|mins?)\b",
        r"\b(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|\d+)\s*(?:days?|weeks?|months?|hours?|hrs?|minutes?|mins?)\s+(?:after|later|from\s+now)\b",
        r"\b(?:on\s+)?(?:\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b",
        r"\b(?:on\s+)?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+(?:\d{1,2})(?:st|nd|rd|th)?\b",
        r"\bday after tomorrow\b",
        r"\btomorrow\b",
        r"\btonight\b",
        r"\btoday\b",
        r"\bthis weekend\b",
        r"\bnext week\b",
        r"\b(?:in\s+the\s+|in\s+)?afternoon\b",
        r"\b(?:in\s+the\s+|in\s+)?morning\b",
        r"\b(?:in\s+the\s+|in\s+)?evening\b",
        r"\b(?:at\s+)?night\b",
        r"\b(?:at\s+)?noon\b",
        r"\b(?:at|by|on)\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b",
        r"\b(?:at|by)\s+\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?|am|pm)?\b",
        r"\b\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?|am|pm)\b",
    ]
    for p in patterns:
        cleaned = re.sub(p, "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\b(?:at|by|on|for|in|due|and|also|then|plus|so)\s*$", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    if len(cleaned) < 2:
        cleaned = text.strip()
    return cleaned[0].upper() + cleaned[1:] if cleaned else text


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
        return _normalize_parsed_items(result, timezone)
    except Exception as e:
        failure_category = e.__class__.__name__
        fallback_result = _heuristic_parse_thought(user_thought, timezone)
        if fallback_result.get("items"):
            return _normalize_parsed_items(fallback_result, timezone)
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
