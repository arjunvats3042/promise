"""Goal mutation rate-limit buckets (Redis counters; fail open)."""

from apps.core.redis import increment_rate_limit
from config.exceptions import RateLimitedError

GOAL_WRITE_WINDOW = 60
GOAL_WRITE_LIMIT = 60
GOAL_CHECKIN_WINDOW = 60
GOAL_CHECKIN_LIMIT = 30


def goal_user_key(user_id):
    return f"promise:ratelimit:goal:user:{user_id}"


def goal_checkin_user_key(user_id):
    return f"promise:ratelimit:goal_checkin:user:{user_id}"


def enforce_goal_write_rate_limit(user):
    _enforce(
        [
            (
                goal_user_key(user.id),
                GOAL_WRITE_WINDOW,
                GOAL_WRITE_LIMIT,
            )
        ]
    )


def enforce_goal_checkin_rate_limit(user):
    _enforce(
        [
            (
                goal_checkin_user_key(user.id),
                GOAL_CHECKIN_WINDOW,
                GOAL_CHECKIN_LIMIT,
            )
        ]
    )


def _enforce(buckets):
    retry_after = None
    for key, window, limit in buckets:
        result = increment_rate_limit(key, window)
        if result["count"] > limit:
            wait = result["ttl"] if result["ttl"] > 0 else window
            if retry_after is None or wait > retry_after:
                retry_after = wait
    if retry_after is not None:
        raise RateLimitedError(retry_after=retry_after)
