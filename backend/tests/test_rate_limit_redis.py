import logging
import threading
import time
import uuid

import pytest
from redis.exceptions import ConnectionError as RedisConnectionError
from redis.exceptions import RedisError

from apps.core.redis import increment_rate_limit

KEY = "promise:ratelimit:test:phase72"


def test_first_increment_returns_count_one_and_sets_ttl(fake_redis):
    result = increment_rate_limit(KEY, 60)

    assert result == {"count": 1, "ttl": 60}
    assert fake_redis.get(KEY) == "1"
    assert fake_redis.ttl(KEY) == 60


def test_second_increment_increases_count_without_resetting_ttl(fake_redis):
    increment_rate_limit(KEY, 60)
    fake_redis.expire(KEY, 40)

    result = increment_rate_limit(KEY, 60)

    assert result["count"] == 2
    assert result["ttl"] == 40
    assert fake_redis.ttl(KEY) == 40


def test_ttl_decreases_naturally_across_increments(fake_redis):
    first = increment_rate_limit(KEY, 5)
    time.sleep(1)
    second = increment_rate_limit(KEY, 5)

    assert first["count"] == 1
    assert second["count"] == 2
    assert 0 < second["ttl"] < first["ttl"]


def test_concurrent_increments_remain_atomic(fake_redis):
    results = []

    def bump():
        results.append(increment_rate_limit(KEY, 60))

    threads = [threading.Thread(target=bump) for _ in range(20)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    counts = sorted(item["count"] for item in results)
    assert counts == list(range(1, 21))
    assert fake_redis.ttl(KEY) > 0
    assert fake_redis.ttl(KEY) <= 60


def test_invalid_ttl_is_rejected(fake_redis):
    for window in (0, -1, 1.5, True, None, "60"):
        with pytest.raises(ValueError):
            increment_rate_limit(KEY, window)
    assert fake_redis.exists(KEY) == 0


@pytest.mark.parametrize(
    "key",
    [
        "",
        "ratelimit:test",
        "promise:auth:denylist:sid:x",
        "promise:ratelimit:",
        "promise:ratelimit:user@example.com",
        "promise:ratelimit:has space",
        "promise:ratelimit:has\nnewline",
        None,
        123,
    ],
)
def test_invalid_key_is_rejected(fake_redis, key):
    with pytest.raises(ValueError):
        increment_rate_limit(key, 60)
    assert fake_redis.exists(KEY) == 0


def test_redis_unavailable_fails_open_without_logging_key(monkeypatch, caplog):
    def raise_down():
        raise RedisConnectionError("redis down")

    monkeypatch.setattr("apps.core.redis.get_redis_client", raise_down)

    with caplog.at_level(logging.WARNING, logger="promise"):
        result = increment_rate_limit(KEY, 60)

    assert result == {"count": 0, "ttl": 0}
    assert KEY not in caplog.text
    assert "redis down" not in caplog.text
    assert "failing open" in caplog.text.lower() or "fail open" in caplog.text.lower()


def test_malformed_script_result_fails_open(monkeypatch, caplog):
    class WeirdRedis:
        def eval(self, *args, **kwargs):
            return "not-a-pair"

    monkeypatch.setattr("apps.core.redis.get_redis_client", lambda: WeirdRedis())

    with caplog.at_level(logging.WARNING, logger="promise"):
        result = increment_rate_limit(KEY, 60)

    assert result == {"count": 0, "ttl": 0}
    assert KEY not in caplog.text


def test_first_increment_never_creates_a_permanent_key(fake_redis):
    result = increment_rate_limit(KEY, 30)

    assert result["count"] == 1
    assert result["ttl"] > 0
    assert fake_redis.ttl(KEY) > 0
    assert fake_redis.ttl(KEY) != -1


def test_increment_uses_single_eval_incr_and_expire(fake_redis):
    recorded = []
    original_eval = fake_redis.eval

    def tracking_eval(*args, **kwargs):
        recorded.append((args, kwargs))
        return original_eval(*args, **kwargs)

    fake_redis.eval = tracking_eval

    result = increment_rate_limit(KEY, 60)

    assert result["count"] == 1
    assert len(recorded) == 1
    script = recorded[0][0][0]
    assert "INCR" in script
    assert "EXPIRE" in script
    assert script.index("INCR") < script.index("EXPIRE")


def test_result_types_are_ints(fake_redis):
    result = increment_rate_limit(KEY, 60)

    assert result == {"count": 1, "ttl": 60}
    assert type(result["count"]) is int
    assert type(result["ttl"]) is int


def _live_redis_or_skip():
    from django.conf import settings
    from redis import Redis

    try:
        client = Redis.from_url(
            settings.REDIS_URL,
            decode_responses=True,
            socket_connect_timeout=0.2,
            socket_timeout=0.2,
        )
        client.ping()
    except RedisError:
        pytest.skip("Docker Redis is not available")
    return client


def test_live_redis_incr_and_ttl_are_atomic(monkeypatch):
    client = _live_redis_or_skip()
    monkeypatch.setattr("apps.core.redis.get_redis_client", lambda: client)
    key = f"promise:ratelimit:test:phase72:{uuid.uuid4()}"
    try:
        first = increment_rate_limit(key, 8)
        assert first["count"] == 1
        assert first["ttl"] > 0
        ttl_after_first = client.ttl(key)
        assert ttl_after_first > 0

        time.sleep(1)
        second = increment_rate_limit(key, 8)
        assert second["count"] == 2
        assert 0 < second["ttl"] < ttl_after_first
        assert second["ttl"] < 8
    finally:
        client.delete(key)
        assert client.exists(key) == 0


def test_live_redis_concurrent_increments_are_atomic(monkeypatch):
    client = _live_redis_or_skip()
    monkeypatch.setattr("apps.core.redis.get_redis_client", lambda: client)
    key = f"promise:ratelimit:test:phase72:{uuid.uuid4()}"
    results = []

    def bump():
        results.append(increment_rate_limit(key, 10))

    try:
        threads = [threading.Thread(target=bump) for _ in range(20)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        counts = sorted(item["count"] for item in results)
        assert counts == list(range(1, 21))
        assert client.ttl(key) > 0
        assert client.ttl(key) <= 10
    finally:
        client.delete(key)
        assert client.exists(key) == 0
