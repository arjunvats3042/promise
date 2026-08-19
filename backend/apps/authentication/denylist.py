"""Access-token session denylist (Redis hint; PostgreSQL remains source of truth)."""

import logging

from django.conf import settings
from redis.exceptions import RedisError

from apps.core.redis import get_redis_client

logger = logging.getLogger("promise")

SESSION_DENYLIST_KEY_PREFIX = "promise:auth:denylist:sid:"
_DENIED_MARKER = "1"


def session_denylist_key(session_id):
    return f"{SESSION_DENYLIST_KEY_PREFIX}{session_id}"


def session_denylist_ttl_seconds():
    return settings.JWT_ACCESS_TTL_SECONDS + settings.JWT_LEEWAY_SECONDS


def deny_access_session(session_id):
    """Best-effort sid denylist write. Never stores tokens or secrets."""
    try:
        get_redis_client().set(
            session_denylist_key(session_id),
            _DENIED_MARKER,
            ex=session_denylist_ttl_seconds(),
        )
    except RedisError:
        logger.warning(
            "Redis denylist write failed session_id=%s; PostgreSQL remains source of truth",
            session_id,
        )


def is_session_denied(session_id):
    """Return True if sid is denylisted. Redis failure is treated as not denied."""
    try:
        return bool(get_redis_client().exists(session_denylist_key(session_id)))
    except RedisError:
        logger.warning(
            "Redis denylist lookup failed session_id=%s; falling back to PostgreSQL",
            session_id,
        )
        return False
