import time
from typing import Any, Dict, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import CHAT_SUMMARY_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature
from apps.goals.models import ChatMessage, Goal

CHAT_SUMMARY_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "summary": {"type": "STRING"},
        "key_decisions": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
        "agreed_actions": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
        "important_dates": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
        "open_questions": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
    },
    "required": ["summary", "key_decisions", "agreed_actions", "important_dates", "open_questions"],
}


def _heuristic_chat_summary(messages: list, goal_title: str) -> Dict[str, Any]:
    """Fallback deterministic summary of chat messages when AI is unavailable."""
    if not messages:
        return {
            "summary": "No messages in this shared goal room.",
            "key_decisions": [],
            "agreed_actions": [],
            "important_dates": [],
            "open_questions": [],
        }

    senders = list(dict.fromkeys(getattr(m.sender, "name", None) or getattr(m.sender, "email", "Member") for m in messages if hasattr(m, "sender")))
    senders_str = ", ".join(senders[:3]) if senders else "Members"
    return {
        "summary": f"{senders_str} shared {len(messages)} updates and discussion notes regarding {goal_title}.",
        "key_decisions": [],
        "agreed_actions": [],
        "important_dates": [],
        "open_questions": [],
    }


def summarize_goal_chat(
    goal: Goal,
    limit: int = 50,
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Generates an executive summary of recent chat messages in a shared goal."""
    messages = list(
        ChatMessage.objects.filter(goal=goal)
        .order_by("-created_at")[:limit]
    )
    # Reverse to chronological order
    messages.reverse()

    if not messages:
        return {
            "summary": "No messages to summarize yet.",
            "key_decisions": [],
            "agreed_actions": [],
            "important_dates": [],
            "open_questions": [],
        }

    formatted_messages = []
    for m in messages:
        sender_name = m.sender.name or m.sender.email
        formatted_messages.append(f"[{m.created_at.strftime('%Y-%m-%d %H:%M')}] {sender_name}: {m.body}")

    transcript = "\n".join(formatted_messages)
    prompt = f"Goal Title: {goal.title}\nChat Transcript ({len(messages)} messages):\n{transcript}"

    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("chat_summary")
    start_time = time.time()
    success = False
    failure_category = None

    try:
        result = provider.generate_structured(
            prompt=prompt,
            schema=CHAT_SUMMARY_SCHEMA,
            system_prompt=CHAT_SUMMARY_PROMPT_V1,
            model=model,
        )
        success = True
        return result
    except Exception as e:
        failure_category = e.__class__.__name__
        return _heuristic_chat_summary(messages, goal.title)
    finally:
        latency_ms = int((time.time() - start_time) * 1000)
        record_ai_metric(
            feature="chat_summary",
            model=model,
            prompt_version=PROMPT_VERSION_V1,
            latency_ms=latency_ms,
            success=success,
            failure_category=failure_category,
        )
