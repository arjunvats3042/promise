from django.conf import settings

# Feature model classification
FAST_FEATURES = {
    "goal_builder",
    "commitment_refiner",
    "thought_parser",
    "command_parser",
    "support_bot",
}

REASONING_FEATURES = {
    "insights",
    "planner",
    "reflection",
    "shared_goal_summary",
    "chat_summary",
}


def get_model_for_feature(feature_name: str) -> str:
    """Returns configured model name based on task complexity."""
    if feature_name in FAST_FEATURES:
        return getattr(settings, "GEMINI_FAST_MODEL", "gemini-3.7-flash")
    return getattr(settings, "GEMINI_DEFAULT_MODEL", "gemini-3.7-flash")
