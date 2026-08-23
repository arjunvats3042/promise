"""AI Environment & Health Validator.

Provides safe configuration status checks for Google Gemini API keys and provider models.
Privacy Guarantee: NEVER prints, logs, or returns raw key values.
"""

from typing import Any, Dict
from django.conf import settings


def validate_ai_environment() -> Dict[str, Any]:
    """Inspects Gemini API configuration status safely.

    Returns:
        Dict reporting whether key_1, key_2, key_3 are 'configured' or 'missing',
        along with configured model, timeout, and max output token settings.
    """
    import os

    key_1 = str(os.environ.get("GEMINI_API_KEY_1") or getattr(settings, "GEMINI_API_KEY_1", "")).strip()
    key_2 = str(os.environ.get("GEMINI_API_KEY_2") or getattr(settings, "GEMINI_API_KEY_2", "")).strip()
    key_3 = str(os.environ.get("GEMINI_API_KEY_3") or getattr(settings, "GEMINI_API_KEY_3", "")).strip()

    model = getattr(settings, "GEMINI_DEFAULT_MODEL", "gemini-3.5-flash")
    timeout = getattr(settings, "GEMINI_TIMEOUT_SECONDS", 15)
    max_tokens = getattr(settings, "GEMINI_MAX_OUTPUT_TOKENS", 1024)

    status = {
        "key_1": "configured" if key_1 else "missing",
        "key_2": "configured" if key_2 else "missing",
        "key_3": "configured" if key_3 else "missing",
        "active_key_count": sum(1 for k in [key_1, key_2, key_3] if k),
        "default_model": model,
        "timeout_seconds": timeout,
        "max_output_tokens": max_tokens,
    }

    return status
