import fakeredis
import pytest

from apps.core.redis import incr_with_ttl


@pytest.fixture
def redis_client(monkeypatch):
    client = fakeredis.FakeRedis(decode_responses=True)
    monkeypatch.setattr("apps.core.redis.get_redis_client", lambda: client)
    return client


def test_incr_with_ttl_starts_at_one_and_sets_ttl(redis_client):
    count = incr_with_ttl("promise:ratelimit:test", 60)

    assert count == 1
    assert redis_client.get("promise:ratelimit:test") == "1"
    assert redis_client.ttl("promise:ratelimit:test") == 60


def test_incr_with_ttl_increments_without_resetting_ttl(redis_client):
    incr_with_ttl("promise:ratelimit:test", 60)
    redis_client.expire("promise:ratelimit:test", 40)

    count = incr_with_ttl("promise:ratelimit:test", 60)

    assert count == 2
    assert redis_client.ttl("promise:ratelimit:test") == 40
