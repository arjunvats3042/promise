"""User lookup rate-limit buckets (Redis counters; fail open)."""

import hashlib

from apps.authentication.services import canonicalize_email
from apps.core.redis import increment_rate_limit
from config.exceptions import RateLimitedError

USER_LOOKUP_USER_WINDOW = 60
USER_LOOKUP_USER_LIMIT = 30
USER_LOOKUP_EMAIL_WINDOW = 60
USER_LOOKUP_EMAIL_LIMIT = 20


def user_lookup_user_key(user_id):
    return f"promise:ratelimit:user_lookup:user:{user_id}"


def user_lookup_email_key(email):
    normalized = canonicalize_email(email)
    digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    return f"promise:ratelimit:user_lookup:email:{digest}"


def enforce_user_lookup_rate_limit(user, email):
    _enforce(
        [
            (
                user_lookup_user_key(user.id),
                USER_LOOKUP_USER_WINDOW,
                USER_LOOKUP_USER_LIMIT,
            ),
            (
                user_lookup_email_key(email),
                USER_LOOKUP_EMAIL_WINDOW,
                USER_LOOKUP_EMAIL_LIMIT,
            ),
        ]
    )


def enforce_user_search_rate_limit(user):
    _enforce(
        [
            (
                user_lookup_user_key(user.id),
                USER_LOOKUP_USER_WINDOW,
                USER_LOOKUP_USER_LIMIT,
            ),
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
