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
        "action_chips": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "label": {"type": "STRING"},
                    "action_type": {"type": "STRING"},
                },
                "required": ["label", "action_type"],
            },
        },
        "is_off_topic": {"type": "BOOLEAN"},
    },
    "required": ["answer", "suggested_followups", "is_off_topic"],
}


def _heuristic_support_bot(question: str) -> Dict[str, Any]:
    """Offline heuristic FAQ knowledge base fallback when AI service is unreachable."""
    lower = question.lower().strip()

    # 1. Goal Creation Guide ("Can you create a goal", "How to create a goal")
    if (("goal" in lower or "habit" in lower) and any(w in lower for w in ["create", "make", "add", "build", "set up", "setup", "banaye", "karein", "karna"])) or "create goal" in lower or "make goal" in lower:
        return {
            "answer": "I'm your Promise concierge guide! While I cannot directly create goals inside your account from this chat window, you can easily create goals in two quick ways:\n\n**Method 1: Manual Create (+ button)**\n1. Tap the **+** (Create) button on the bottom bar or Home tab.\n2. Select **Goal** (for recurring daily or weekly practices).\n3. Choose your frequency (**Daily**, **Specific Weekdays**, or **X times per period**).\n4. Set your target (yes/no check-in or count like pages/minutes) and tap **Save**.\n\n**Method 2: Voice Quick Capture (AI Thought Dump)**\n1. Tap the **Mic / Voice** button on your Home screen or widget.\n2. Speak or type freely in English, Hindi, or Hinglish (e.g., *'Roz subah 6 baje yoga karna hai'*).\n3. Promise AI parses your thought into a structured goal card for you to confirm in 1 tap!",
            "suggested_followups": [
                "What is the difference between a Goal and a Commitment?",
                "How does Voice Quick Capture work?",
                "How do Shared Goals work?",
            ],
            "action_chips": [
                {"label": "＋ Create Goal", "action_type": "CREATE_GOAL"},
                {"label": "🎙️ Voice Capture", "action_type": "OPEN_VOICE_CAPTURE"},
            ],
            "is_off_topic": False,
        }

    # 2. Commitment Creation Guide ("Can you create a commitment", "How to create commitment")
    if (("commitment" in lower or "task" in lower) and any(w in lower for w in ["create", "make", "add", "set up", "setup", "schedule", "new", "banaye", "karein"])) or "create commitment" in lower:
        return {
            "answer": "I'm your Promise concierge guide! While I cannot directly add tasks to your account from this chat, you can create commitments in two easy ways:\n\n**Method 1: Manual Create (+ button)**\n1. Tap the **+** (Create) button on the bottom bar.\n2. Select **Commitment** (for one-time tasks with deadlines).\n3. Enter the title, set an optional deadline, and choose your alert timing.\n4. Tap **Save**.\n\n**Method 2: Voice Quick Capture (AI Thought Dump)**\n1. Tap the **Mic / Voice** button.\n2. Speak or type in English, Hindi, or Hinglish (e.g., *'Kal dopahar 3 baje presentation bhejna hai'*).\n3. Promise AI automatically parses the deadline and creates a commitment card for your confirmation.",
            "suggested_followups": [
                "What is the difference between a Goal and a Commitment?",
                "When does Promise send notifications?",
                "How does offline sync work?",
            ],
            "action_chips": [
                {"label": "＋ Create Commitment", "action_type": "CREATE_COMMITMENT"},
                {"label": "🎙️ Voice Capture", "action_type": "OPEN_VOICE_CAPTURE"},
            ],
            "is_off_topic": False,
        }

    # 3. Check-ins & Habit Streaks
    if "check-in" in lower or "check in" in lower or "streak" in lower or "streaks" in lower or "complete goal" in lower or "mark done" in lower or "checkin" in lower:
        return {
            "answer": "**How to check in and build streaks:**\n\n- **From Home Feed**: Tap the check-in circle next to the goal on your today's feed.\n- **From Goals Tab**: Tap the goal card, enter your progress (if count-based), and submit.\n- **From Home Screen Widget**: Tap the check-in circle directly on your Android Glance widget without opening the app!\n- **Streak Progression**: Each on-time check-in maintains your active streak and logs proof on your monthly calendar.",
            "suggested_followups": [
                "How do Shared Goals work?",
                "How does the History Calendar work?",
                "What is the difference between a Goal and a Commitment?",
            ],
            "action_chips": [
                {"label": "🎯 View Goals", "action_type": "OPEN_SHARED_GOALS"},
            ],
            "is_off_topic": False,
        }

    # 4. Account Deletion (Direct, Step-by-Step)
    if "delete" in lower or "account delete" in lower or "khatam" in lower or "remove account" in lower or "wipe" in lower:
        return {
            "answer": "**How to permanently delete your account:**\n\n1. Open the **Profile** tab in the bottom navigation bar.\n2. Scroll down to the **Account Management** section.\n3. Tap **Delete account** (highlighted in red).\n4. Confirm deletion in the popup dialog.\n\n*Note: This immediately anonymizes your profile, cancels all active commitments, and permanently clears your local and cloud data.*",
            "suggested_followups": [
                "What is the difference between a Goal and a Commitment?",
                "How does offline sync work?",
                "Is my data private from AI?",
            ],
            "action_chips": [
                {"label": "👤 Account Management", "action_type": "OPEN_PROFILE"},
            ],
            "is_off_topic": False,
        }

    # 5. Commitment vs Goal
    if "difference" in lower or ("commitment" in lower and "goal" in lower) or "kya antar" in lower or "farak" in lower or "farq" in lower:
        return {
            "answer": "**Commitments vs. Goals in Promise:**\n\n- **Commitments**: One-time accountable tasks with optional deadlines (e.g., *'Submit project report by Friday 5 PM'*). They have precision timing and urgency badges (**Overdue**, **Imminent**, **Upcoming**).\n- **Goals**: Recurring daily or weekly practices designed to build lasting consistency (e.g., *'Read 20 pages daily'*, *'Gym 4x/week'*). Supports yes/no check-ins or numeric targets with streak tracking.",
            "suggested_followups": [
                "How do Shared Goals work?",
                "How does Voice / Brain dump parsing work?",
                "How do check-in streaks work?",
            ],
            "action_chips": [
                {"label": "＋ Create Goal", "action_type": "CREATE_GOAL"},
                {"label": "＋ Create Commitment", "action_type": "CREATE_COMMITMENT"},
            ],
            "is_off_topic": False,
        }

    # 6. Shared Goals & Invites
    if "shared" in lower or "invite" in lower or "friend" in lower or "teammate" in lower or "partner" in lower:
        return {
            "answer": "**How Shared Goals Work:**\n\n1. **Open or Create a Goal**: Tap on any goal from your Goals list, or create a new one.\n2. **Invite Teammates**: Tap **Invite** and enter your teammate's registered Promise email.\n3. **Group Accountability**: Once accepted, all members check in together, track group streaks, and discuss progress in the private group chat with member avatars.",
            "suggested_followups": [
                "Can teammates see my private commitments?",
                "How does the group chat summary work?",
                "What is the difference between a Goal and a Commitment?",
            ],
            "action_chips": [
                {"label": "👥 Shared Goals", "action_type": "OPEN_SHARED_GOALS"},
            ],
            "is_off_topic": False,
        }

    # 7. Offline sync & Local-first
    if "offline" in lower or "sync" in lower or "bina internet" in lower or "no internet" in lower:
        return {
            "answer": "**Local-First Architecture & Offline Sync:**\n\n- **100% Offline Capability**: You can create commitments, complete habit check-ins, and use the Home Screen widget without internet connection. Everything is saved instantly to your device's local database.\n- **Automatic Sync**: As soon as internet connectivity returns, Promise silently syncs all pending changes to the cloud in the background.",
            "suggested_followups": [
                "How do I add the Promise Widget to my Home Screen?",
                "What data is shared with AI?",
                "When does Promise send notifications?",
            ],
            "action_chips": [
                {"label": "＋ Create Commitment", "action_type": "CREATE_COMMITMENT"},
            ],
            "is_off_topic": False,
        }

    # 8. Home Screen Glance Widgets
    if "widget" in lower or "home screen" in lower:
        return {
            "answer": "**Adding Promise Home Screen Widgets:**\n\n1. Long-press any empty space on your Android home screen.\n2. Select **Widgets** and scroll to **Promise**.\n3. Choose from:\n   - **Goal Check-Ins**: 1-tap habit check-in directly from your launcher.\n   - **Commitments**: High-priority task list with overdue indicators.\n   - **Voice Capture**: 1-tap floating mic to record speech into structured tasks.",
            "suggested_followups": [
                "How does offline sync work?",
                "How does Voice / Brain dump parsing work?",
                "When does Promise send notifications?",
            ],
            "action_chips": [
                {"label": "🎯 View Goals", "action_type": "OPEN_SHARED_GOALS"},
            ],
            "is_off_topic": False,
        }

    # 9. Voice / AI Parsing
    if "voice" in lower or "thought" in lower or "parse" in lower or "speech" in lower or "mic" in lower or "hindi" in lower or "hinglish" in lower:
        return {
            "answer": "**Voice Quick Capture & Intent Engine:**\n\n- **Speak Freely in Any Language**: Speak or type in English, Hindi, or Hinglish (e.g., *'kal subah 8 baje client call aur roz gym'*).\n- **Intelligent Decomposition**: Promise AI cleans scheduling words from titles, parses exact dates and recurrence, and decomposes thoughts into separate commitments and goals.\n- **Always in Your Control**: Nothing is created until you review and confirm the parsed cards.",
            "suggested_followups": [
                "What is the difference between a Goal and a Commitment?",
                "Is my voice data private from AI?",
                "How do notifications work?",
            ],
            "action_chips": [
                {"label": "🎙️ Voice Capture", "action_type": "OPEN_VOICE_CAPTURE"},
            ],
            "is_off_topic": False,
        }

    # 10. Theme / Appearance
    if "theme" in lower or "dark mode" in lower or "light mode" in lower or "appearance" in lower or "dark" in lower or "light" in lower:
        return {
            "answer": "**Changing App Theme (Light / Dark Mode):**\n\n1. Go to the **Profile** tab in the bottom navigation bar.\n2. Scroll to the bottom to the **Appearance** section.\n3. Use the sliding segmented controller to toggle between **Light** (warm daytime brightness) and **Dark** (deep night contrast).",
            "suggested_followups": [
                "How do I add the Promise Widget to my Home Screen?",
                "How do notifications work?",
                "What is the difference between a Goal and a Commitment?",
            ],
            "action_chips": [
                {"label": "🎨 Appearance Settings", "action_type": "OPEN_THEME"},
            ],
            "is_off_topic": False,
        }

    # 11. Calendar History & Check-in Details
    if "calendar" in lower or "history" in lower or "check-in" in lower or "popover" in lower:
        return {
            "answer": "**Interactive History Calendar:**\n\n- **Inspect Past Dates**: Open any goal and tap any date between the start date and today to view a detailed popup with check-in timestamps, notes, logged values, and teammate statuses.\n- **Locked Boundaries**: Future dates and dates before the goal's start date are locked to ensure historical data integrity.",
            "suggested_followups": [
                "How do Shared Goals work?",
                "How do check-in streaks work?",
                "What is the difference between a Goal and a Commitment?",
            ],
            "action_chips": [
                {"label": "🎯 View Goals", "action_type": "OPEN_SHARED_GOALS"},
            ],
            "is_off_topic": False,
        }

    # 12. Notifications & Quiet Hours
    if "notification" in lower or "reminder" in lower or "quiet hours" in lower or "alert" in lower:
        return {
            "answer": "**Notifications & Quiet Hours:**\n\n- **Calm Alerts**: Promise sends timely reminders for morning overviews, imminent deadlines, and evening streak protection.\n- **Quiet Hours**: Configure undisturbed quiet hours from **Profile → Notifications** to mute alerts during rest hours.",
            "suggested_followups": [
                "How does offline sync work?",
                "What is the difference between a Goal and a Commitment?",
                "How do Shared Goals work?",
            ],
            "action_chips": [
                {"label": "🔔 Notification Settings", "action_type": "OPEN_NOTIFICATIONS"},
            ],
            "is_off_topic": False,
        }

    # 13. Privacy & Security
    if "privacy" in lower or "security" in lower or "data" in lower or "google" in lower:
        return {
            "answer": "**Privacy & Data Security at Promise:**\n\n- **Zero AI Training**: Your private commitments, goals, and account details are **never** used to train AI models.\n- **Secure Google Authentication**: Sign in securely with Google OAuth without storing passwords.\n- **Instant Account Deletion**: You can permanently delete and anonymize your account at any time from Profile → Account Management.",
            "suggested_followups": [
                "How do I delete my account?",
                "How does offline sync work?",
                "What is the difference between a Goal and a Commitment?",
            ],
            "action_chips": [
                {"label": "👤 Account Management", "action_type": "OPEN_PROFILE"},
            ],
            "is_off_topic": False,
        }

    # 14. Off-Topic / Gibberish / Random / Profanity / Non-App Questions (Polite Guardrail)
    return {
        "answer": "I'm here exclusively as your Promise app concierge!\n\nPlease ask me anything related to using the Promise app (such as commitments, daily habits, shared goals, widgets, offline sync, voice capture, or notifications), and I'll be glad to help.",
        "suggested_followups": [
            "What is the difference between a Goal and a Commitment?",
            "How do I invite teammates to a Shared Goal?",
            "How do I delete my account?",
        ],
        "action_chips": [],
        "is_off_topic": True,
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
