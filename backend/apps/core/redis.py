"""Synchronous Redis helpers for denylist, counters, and short-lived keys.

The client is configured only from REDIS_URL. Callers that must stay
available when Redis is down should catch RedisError.
"""

import logging

from django.conf import settings
from redis import Redis
from redis.exceptions import RedisError

logger = logging.getLogger("promise")

RATE_LIMIT_KEY_PREFIX = "promise:ratelimit:"
_MAX_RATE_LIMIT_KEY_LENGTH = 256
_RATE_LIMIT_INCR_EXPIRE = """
local n = redis.call('INCR', KEYS[1])
if n == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
local ttl = redis.call('TTL', KEYS[1])
return {n, ttl}
"""

_client = None


def get_redis_client():
    global _client
    if _client is None:
        _client = Redis.from_url(
            settings.REDIS_URL,
            decode_responses=True,
            socket_connect_timeout=0.2,
            socket_timeout=0.2,
        )
    return _client


def incr_with_ttl(key, ttl_seconds):
    """Increment a counter and set TTL when the key is created."""
    client = get_redis_client()
    count = int(client.incr(key))
    if count == 1:
        client.expire(key, int(ttl_seconds))
    return count


def increment_rate_limit(key, window_seconds):
    """Atomically INCR a rate-limit key and set TTL on first hit.

    Returns {"count": int, "ttl": int}. Does not decide allow/deny.
    Redis failure returns {"count": 0, "ttl": 0} (fail open).
    """
    _validate_rate_limit_key(key)
    ttl_seconds = _validate_window_seconds(window_seconds)
    try:
        raw = get_redis_client().eval(
            _RATE_LIMIT_INCR_EXPIRE,
            1,
            key,
            ttl_seconds,
        )
        return {"count": int(raw[0]), "ttl": int(raw[1])}
    except (RedisError, TypeError, ValueError, IndexError, KeyError):
        logger.warning("Redis rate-limit increment failed; failing open")
        return {"count": 0, "ttl": 0}


def _validate_rate_limit_key(key):
    if not isinstance(key, str):
        raise ValueError("Rate-limit key must be a string.")
    if not key.startswith(RATE_LIMIT_KEY_PREFIX):
        raise ValueError("Rate-limit key must use the promise:ratelimit: prefix.")
    if key == RATE_LIMIT_KEY_PREFIX:
        raise ValueError("Rate-limit key must include a bucket suffix.")
    if len(key) > _MAX_RATE_LIMIT_KEY_LENGTH:
        raise ValueError("Rate-limit key is too long.")
    if "@" in key or any(character.isspace() for character in key):
        raise ValueError("Rate-limit key is invalid.")


def _validate_window_seconds(window_seconds):
    if type(window_seconds) is not int or window_seconds < 1:
        raise ValueError("Rate-limit window must be a positive integer.")
    return window_seconds
