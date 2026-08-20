"""Commitment mutation rate-limit buckets (Redis counters; fail open)."""

from apps.core.redis import increment_rate_limit
from config.exceptions import RateLimitedError

COMMITMENT_WRITE_WINDOW = 60
COMMITMENT_WRITE_LIMIT = 60


def commitment_user_key(user_id):
    return f"promise:ratelimit:commitment:user:{user_id}"


def enforce_commitment_write_rate_limit(user):
    _enforce(
        [
            (
                commitment_user_key(user.id),
                COMMITMENT_WRITE_WINDOW,
                COMMITMENT_WRITE_LIMIT,
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
