import re
import time
from typing import Any, Dict, List, Optional

from apps.ai.observability import record_ai_metric
from apps.ai.prompts import PROMISE_SUPPORT_BOT_PROMPT_V1, PROMPT_VERSION_V1
from apps.ai.providers.base import AIProvider
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.routing import get_model_for_feature

SUPPORT_BOT_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "answer": {"type": "STRING"},
        "suggested_followups": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
        "is_off_topic": {"type": "BOOLEAN"},
    },
    "required": ["answer", "suggested_followups", "is_off_topic"],
}


def _heuristic_support_bot(question: str) -> Dict[str, Any]:
    """Offline heuristic FAQ knowledge base fallback when AI service is unreachable."""
    lower = question.lower().strip()

    # 1. Off-topic detection
    off_topic_patterns = [
        r"\b(?:weather|cricket|football|world cup|capital of|movie|recipe|cook|python code|javascript|joke|song|president|prime minister)\b",
        r"\b(?:who won|who is the|calculate|solve for x|integral|derivative)\b",
    ]
    if any(re.search(pat, lower) for pat in off_topic_patterns) and not any(kw in lower for kw in ["promise", "goal", "commitment", "habit", "widget"]):
        return {
            "answer": "I'm here exclusively as your Promise guide! I can help you with anything about managing commitments, habits, shared goals, widgets, offline sync, voice intent, or privacy. How can I help you with Promise today?",
            "suggested_followups": [
                "What is the difference between a Goal and a Commitment?",
                "How does offline sync work?",
                "How do I create a Shared Goal?",
            ],
            "is_off_topic": True,
        }

    # 2. Commitment vs Goal
    if "difference" in lower or ("commitment" in lower and "goal" in lower) or "kya antar" in lower:
        return {
            "answer": "**Commitments vs. Goals in Promise:**\n\n- **Commitments**: One-time tasks with a specific deadline (e.g., *'Submit project report by Friday 5 PM'*). They have precision timing and urgency badges (Overdue, Imminent, Upcoming).\n- **Goals**: Recurring daily or weekly practices designed to build lasting consistency (e.g., *'Read 20 pages daily'*, *'Gym 4x/week'*).",
            "suggested_followups": [
                "How do Shared Goals work?",
                "How does Voice / Brain dump parsing work?",
                "How do check-in streaks work?",
            ],
            "is_off_topic": False,
        }

    # 3. Shared Goals
    if "shared" in lower or "invite" in lower or "friend" in lower or "teammate" in lower:
        return {
            "answer": "**How Shared Goals Work:**\n\n1. **Convert or Create**: Turn any existing goal into a Shared Goal from its details screen.\n2. **Invite Partners**: Search for friends or teammates by their registered Promise email.\n3. **Group Momentum**: Once accepted, all members check in together, track group streaks, and discuss progress in the private group chat with member avatars.",
            "suggested_followups": [
                "Can teammates see my private commitments?",
                "How does the group chat summary work?",
                "What is the difference between a Goal and a Commitment?",
            ],
            "is_off_topic": False,
        }

    # 4. Offline sync & Widget
    if "offline" in lower or "widget" in lower or "internet" in lower or "bina internet" in lower:
        return {
            "answer": "**Local-First Architecture & Offline Sync:**\n\n- **100% Offline Ready**: Your commitments, habit check-ins, and the Android Glance Home Screen widget work instantly without an internet connection.\n- **Automatic Sync**: Any actions you perform offline are stored in the local SQLite database and silently synchronized to the cloud as soon as you reconnect.",
            "suggested_followups": [
                "How do I add the Promise Widget to my Home Screen?",
                "What data is shared with AI?",
                "When does Promise send notifications?",
            ],
            "is_off_topic": False,
        }

    # 5. Voice / AI Parsing
    if "voice" in lower or "thought" in lower or "parse" in lower or "speech" in lower or "hindi" in lower or "hinglish" in lower:
        return {
            "answer": "**Voice & Natural Language Intent Engine:**\n\n- **Speak or Type Freely**: Talk or dump unstructured thoughts in English, Hindi, or Hinglish (e.g., *'kal subah 9 baje mummy ko call karna hai aur roz gym jana hai'*).\n- **Clean Decomposition**: Promise AI heals transcription slips, cleans scheduling prepositions from titles, and automatically creates separate commitments and recurring goals.\n- **Always in Your Control**: Nothing is created until you review and confirm the parsed cards.",
            "suggested_followups": [
                "What is the difference between a Goal and a Commitment?",
                "Is my voice data private?",
                "How do notifications work?",
            ],
            "is_off_topic": False,
        }

    # 6. Privacy & Security
    if "privacy" in lower or "security" in lower or "data" in lower or "delete" in lower or "account" in lower:
        return {
            "answer": "**Privacy & Data Security at Promise:**\n\n- **Zero AI Training**: Your private commitments, goals, and account details are **never** used to train AI models.\n- **Secure Google Authentication**: Sign in securely with Google OAuth without storing passwords.\n- **Instant Account Deletion**: You can permanently delete and anonymize your account at any time from Profile → Account Management.",
            "suggested_followups": [
                "How does offline sync work?",
                "When does Promise send notifications?",
                "What is the difference between a Goal and a Commitment?",
            ],
            "is_off_topic": False,
        }

    # Default general fallback
    return {
        "answer": "Promise is your calm, local-first productivity companion for managing one-time commitments and building recurring daily habits.\n\nYou can track commitments with deadlines, build consistency with daily/weekly goals, collaborate with teammates in Shared Goals, and use natural voice capture in English, Hindi, or Hinglish.",
        "suggested_followups": [
            "What is the difference between a Goal and a Commitment?",
            "How do Shared Goals work?",
            "How does offline sync work?",
        ],
        "is_off_topic": False,
    }


def ask_promise_support_bot(
    question: str,
    conversation_history: Optional[List[Dict[str, str]]] = None,
    timezone: str = "Asia/Kolkata",
    provider: Optional[AIProvider] = None,
) -> Dict[str, Any]:
    """Answers user queries regarding the Promise application using Gemini with fallback."""
    if provider is None:
        provider = GeminiProvider()

    model = get_model_for_feature("support_bot") if hasattr(get_model_for_feature, "__call__") else "gemini-2.5-flash"
    
    # Format conversation context
    context_lines = [f"User Timezone: {timezone}"]
    if conversation_history:
        context_lines.append("Recent Conversation History:")
        for msg in conversation_history[-6:]:
            role = msg.get("role", "user")
            text = msg.get("text", "")
            context_lines.append(f"- {role.upper()}: {text}")

    context_lines.append(f"Current User Question: {question.strip()}")
    full_prompt = "\n".join(context_lines)

    start_time = time.time()
    success = False
    failure_category = None

    try:
        result = provider.generate_structured(
            prompt=full_prompt,
            schema=SUPPORT_BOT_SCHEMA,
            system_prompt=PROMISE_SUPPORT_BOT_PROMPT_V1,
            model=model if isinstance(model, str) else "gemini-2.5-flash",
        )
        if isinstance(result, dict) and "answer" in result:
            success = True
            return result
        return _heuristic_support_bot(question)
    except Exception as e:
        failure_category = e.__class__.__name__
        # Resilient fallback so user always gets an instant answer
        return _heuristic_support_bot(question)
    finally:
        latency_ms = int((time.time() - start_time) * 1000)
        try:
            record_ai_metric(
                feature="support_bot",
                model=str(model),
                prompt_version=PROMPT_VERSION_V1,
                latency_ms=latency_ms,
                success=success,
                failure_category=failure_category,
            )
        except Exception:
            pass
