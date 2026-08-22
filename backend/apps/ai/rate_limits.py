"""AI endpoint rate-limiting (Redis counters; fail open)."""

from apps.core.redis import increment_rate_limit
from config.exceptions import RateLimitedError

AI_USER_WINDOW = 60
AI_USER_LIMIT = 30


def ai_user_key(user_id: str, feature: str = "general") -> str:
    return f"promise:ratelimit:ai:{feature}:user:{user_id}"


def enforce_ai_rate_limit(user, feature: str = "general"):
    """Enforces rate limit for AI operations per user."""
    key = ai_user_key(str(user.id), feature)
    result = increment_rate_limit(key, AI_USER_WINDOW)
    if result["count"] > AI_USER_LIMIT:
        wait = result["ttl"] if result["ttl"] > 0 else AI_USER_WINDOW
        raise RateLimitedError(retry_after=wait)
