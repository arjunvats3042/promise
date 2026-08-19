"""Synchronous Redis helpers for denylist, counters, and short-lived keys.

The client is configured only from REDIS_URL. Callers that must stay
available when Redis is down should catch RedisError.
"""

from django.conf import settings
from redis import Redis

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
