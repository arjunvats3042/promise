import fakeredis
import pytest


@pytest.fixture(autouse=True)
def fake_redis(monkeypatch):
    client = fakeredis.FakeRedis(decode_responses=True)
    monkeypatch.setattr("apps.core.redis.get_redis_client", lambda: client)
    monkeypatch.setattr(
        "apps.authentication.denylist.get_redis_client",
        lambda: client,
    )
    return client
