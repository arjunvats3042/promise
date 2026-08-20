import logging

import pytest
from django.contrib.auth import get_user_model
from redis.exceptions import ConnectionError as RedisConnectionError
from rest_framework.test import APIClient

from apps.core.redis import increment_rate_limit
from apps.goals.models import Goal
from apps.goals.rate_limits import (
    GOAL_CHECKIN_LIMIT,
    GOAL_CHECKIN_WINDOW,
    GOAL_WRITE_LIMIT,
    GOAL_WRITE_WINDOW,
    goal_checkin_user_key,
    goal_user_key,
)
from apps.goals.services import create_goal

User = get_user_model()

GOALS_URL = "/api/v1/goals/"
LOGIN_URL = "/api/v1/auth/login/"
STRONG_PASSWORD = "correct-horse-battery-staple"
RATE_LIMITED = {
    "error": {
        "code": "RATE_LIMITED",
        "message": "Too many requests. Please try again later.",
    }
}


@pytest.fixture(autouse=True)
def auth_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = (
        "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
    )
    settings.JWT_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
    settings.JWT_SIGNING_KEY_PREVIOUS = ""
    settings.JWT_KEY_ID = "test-key"
    settings.JWT_KEY_ID_PREVIOUS = ""
    settings.JWT_ISSUER = "promise-api"
    settings.JWT_AUDIENCE = "promise-client"
    settings.JWT_ALGORITHM = "HS256"
    settings.JWT_ACCESS_TTL_SECONDS = 900
    settings.JWT_LEEWAY_SECONDS = 30


@pytest.fixture
def client():
    return APIClient()


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun@example.com",
        name="Arjun",
        password=STRONG_PASSWORD,
        timezone="Asia/Kolkata",
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul@example.com",
        name="Rahul",
        password=STRONG_PASSWORD,
        timezone="UTC",
    )


def _fill(key, window, count):
    last = None
    for _ in range(count):
        last = increment_rate_limit(key, window)
    return last


def _token(client, email):
    response = client.post(
        LOGIN_URL,
        {"email": email, "password": STRONG_PASSWORD},
        format="json",
    )
    assert response.status_code == 200
    return response.json()["tokens"]["access_token"]


def _auth(client, token):
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")


def _assert_rate_limited(response):
    assert response.status_code == 429
    assert response.json() == RATE_LIMITED
    retry_after = response.headers.get("Retry-After")
    assert retry_after is not None
    assert int(retry_after) > 0
    assert "redis" not in response.content.decode().lower()
    return int(retry_after)


@pytest.mark.django_db
def test_goal_allows_exactly_write_limit_then_429(client, arjun, fake_redis, monkeypatch):
    _auth(client, _token(client, arjun.email))
    _fill(goal_user_key(arjun.id), GOAL_WRITE_WINDOW, GOAL_WRITE_LIMIT - 1)
    allowed = client.post(
        GOALS_URL,
        {"title": "Allowed goal", "recurrence_kind": "DAILY"},
        format="json",
    )
    assert allowed.status_code == 201

    def fail_if_called(**kwargs):
        raise AssertionError("domain mutation must not run after rate limiting")

    monkeypatch.setattr("apps.goals.views.create_goal", fail_if_called)
    blocked = client.post(
        GOALS_URL,
        {"title": "Blocked goal", "recurrence_kind": "DAILY"},
        format="json",
    )
    _assert_rate_limited(blocked)


@pytest.mark.django_db
def test_goal_gets_are_not_rate_limited(client, arjun, fake_redis):
    goal = create_goal(creator=arjun, title="Stay visible", recurrence_kind="DAILY")
    _auth(client, _token(client, arjun.email))
    _fill(goal_user_key(arjun.id), GOAL_WRITE_WINDOW, GOAL_WRITE_LIMIT)

    listing = client.get(GOALS_URL)
    detail = client.get(f"{GOALS_URL}{goal.id}/")

    assert listing.status_code == 200
    assert detail.status_code == 200
    assert listing.json()["results"][0]["id"] == str(goal.id)
    assert fake_redis.get(goal_user_key(arjun.id)) == str(GOAL_WRITE_LIMIT)


@pytest.mark.django_db
def test_goal_users_have_independent_buckets(client, arjun, rahul, fake_redis):
    _fill(goal_user_key(arjun.id), GOAL_WRITE_WINDOW, GOAL_WRITE_LIMIT)
    _auth(client, _token(client, rahul.email))
    rahul_create = client.post(
        GOALS_URL,
        {"title": "Rahul goal", "recurrence_kind": "DAILY"},
        format="json",
    )
    assert rahul_create.status_code == 201

    _auth(client, _token(client, arjun.email))
    blocked = client.post(
        GOALS_URL,
        {"title": "Arjun blocked", "recurrence_kind": "DAILY"},
        format="json",
    )
    _assert_rate_limited(blocked)


@pytest.mark.django_db
def test_goal_lifecycle_shares_write_bucket(client, arjun, fake_redis):
    goal = create_goal(creator=arjun, title="Pause me", recurrence_kind="DAILY")
    _auth(client, _token(client, arjun.email))
    _fill(goal_user_key(arjun.id), GOAL_WRITE_WINDOW, GOAL_WRITE_LIMIT)

    response = client.post(f"{GOALS_URL}{goal.id}/pause/", format="json")
    _assert_rate_limited(response)
    goal.refresh_from_db()
    assert goal.status == Goal.Status.ACTIVE


@pytest.mark.django_db
def test_goal_patch_and_other_lifecycle_share_write_bucket(client, arjun, fake_redis):
    goal = create_goal(creator=arjun, title="Keep title", recurrence_kind="DAILY")
    _auth(client, _token(client, arjun.email))
    _fill(goal_user_key(arjun.id), GOAL_WRITE_WINDOW, GOAL_WRITE_LIMIT)
    detail = f"{GOALS_URL}{goal.id}/"

    patch = client.patch(detail, {"title": "Changed"}, format="json")
    resume = client.post(f"{detail}resume/", format="json")
    complete = client.post(f"{detail}complete/", format="json")
    cancel = client.post(f"{detail}cancel/", format="json")

    for response in (patch, resume, complete, cancel):
        _assert_rate_limited(response)
    goal.refresh_from_db()
    assert goal.title == "Keep title"
    assert goal.status == Goal.Status.ACTIVE


@pytest.mark.django_db
def test_goal_checkin_allows_exactly_limit_then_429(client, arjun, fake_redis, monkeypatch):
    goal = create_goal(creator=arjun, title="Check in", recurrence_kind="DAILY")
    _auth(client, _token(client, arjun.email))
    _fill(goal_checkin_user_key(arjun.id), GOAL_CHECKIN_WINDOW, GOAL_CHECKIN_LIMIT - 1)
    allowed = client.post(
        f"{GOALS_URL}{goal.id}/check-ins/",
        {"status": "COMPLETED"},
        format="json",
    )
    assert allowed.status_code in {200, 201}

    def fail_if_called(**kwargs):
        raise AssertionError("check-in must not run after rate limiting")

    monkeypatch.setattr("apps.goals.views.record_check_in", fail_if_called)
    blocked = client.post(
        f"{GOALS_URL}{goal.id}/check-ins/",
        {"status": "COMPLETED"},
        format="json",
    )
    _assert_rate_limited(blocked)


@pytest.mark.django_db
def test_goal_checkin_gets_are_not_rate_limited(client, arjun, fake_redis):
    goal = create_goal(creator=arjun, title="List check-ins", recurrence_kind="DAILY")
    _auth(client, _token(client, arjun.email))
    _fill(goal_checkin_user_key(arjun.id), GOAL_CHECKIN_WINDOW, GOAL_CHECKIN_LIMIT)

    response = client.get(f"{GOALS_URL}{goal.id}/check-ins/")
    assert response.status_code == 200
    assert fake_redis.get(goal_checkin_user_key(arjun.id)) == str(GOAL_CHECKIN_LIMIT)


@pytest.mark.django_db
def test_goal_write_and_checkin_buckets_are_independent(client, arjun, fake_redis):
    goal = create_goal(creator=arjun, title="Separate buckets", recurrence_kind="DAILY")
    _auth(client, _token(client, arjun.email))
    _fill(goal_user_key(arjun.id), GOAL_WRITE_WINDOW, GOAL_WRITE_LIMIT)

    check_in = client.post(
        f"{GOALS_URL}{goal.id}/check-ins/",
        {"status": "COMPLETED"},
        format="json",
    )
    assert check_in.status_code in {200, 201}
    assert fake_redis.get(goal_checkin_user_key(arjun.id)) == "1"
    assert fake_redis.get(goal_user_key(arjun.id)) == str(GOAL_WRITE_LIMIT)

    blocked_write = client.post(f"{GOALS_URL}{goal.id}/pause/", format="json")
    _assert_rate_limited(blocked_write)


@pytest.mark.django_db
def test_goal_other_user_consumes_own_bucket(client, arjun, rahul, fake_redis):
    goal = create_goal(creator=arjun, title="Arjun goal", recurrence_kind="DAILY")
    _fill(goal_user_key(rahul.id), GOAL_WRITE_WINDOW, GOAL_WRITE_LIMIT)
    _auth(client, _token(client, rahul.email))

    response = client.post(f"{GOALS_URL}{goal.id}/pause/", format="json")
    _assert_rate_limited(response)
    assert fake_redis.get(goal_user_key(arjun.id)) is None
    goal_keys = list(fake_redis.scan_iter("promise:ratelimit:goal*"))
    assert all("@" not in key for key in goal_keys)
    assert all(STRONG_PASSWORD not in key for key in goal_keys)
    assert all(str(arjun.id) not in key for key in goal_keys)


@pytest.mark.django_db
def test_goal_redis_unavailable_fails_open(client, arjun, monkeypatch, caplog):
    _auth(client, _token(client, arjun.email))

    def raise_down():
        raise RedisConnectionError("redis down")

    monkeypatch.setattr("apps.core.redis.get_redis_client", raise_down)
    with caplog.at_level(logging.WARNING, logger="promise"):
        response = client.post(
            GOALS_URL,
            {"title": "Still works", "recurrence_kind": "DAILY"},
            format="json",
        )

    assert response.status_code == 201
    assert "redis down" not in response.content.decode()
    assert "redis down" not in caplog.text


@pytest.mark.django_db
def test_goal_checkin_redis_unavailable_fails_open(client, arjun, monkeypatch, caplog):
    goal = create_goal(creator=arjun, title="Check in anyway", recurrence_kind="DAILY")
    _auth(client, _token(client, arjun.email))

    def raise_down():
        raise RedisConnectionError("redis down")

    monkeypatch.setattr("apps.core.redis.get_redis_client", raise_down)
    with caplog.at_level(logging.WARNING, logger="promise"):
        response = client.post(
            f"{GOALS_URL}{goal.id}/check-ins/",
            {"status": "COMPLETED"},
            format="json",
        )

    assert response.status_code in {200, 201}
    assert response.status_code != 500


def _live_redis_or_skip(monkeypatch):
    from django.conf import settings
    from redis import Redis
    from redis.exceptions import RedisError

    try:
        redis_client = Redis.from_url(
            settings.REDIS_URL,
            decode_responses=True,
            socket_connect_timeout=0.2,
            socket_timeout=0.2,
        )
        redis_client.ping()
    except RedisError:
        pytest.skip("Docker Redis is not available")
    monkeypatch.setattr("apps.core.redis.get_redis_client", lambda: redis_client)
    return redis_client


@pytest.mark.django_db
def test_live_redis_goal_and_checkin_limits(client, arjun, monkeypatch):
    goal = create_goal(creator=arjun, title="Live limits", recurrence_kind="DAILY")
    _auth(client, _token(client, arjun.email))
    redis_client = _live_redis_or_skip(monkeypatch)
    write_key = goal_user_key(arjun.id)
    checkin_key = goal_checkin_user_key(arjun.id)
    try:
        _fill(write_key, GOAL_WRITE_WINDOW, GOAL_WRITE_LIMIT)
        blocked_write = client.post(
            GOALS_URL,
            {"title": "Live blocked", "recurrence_kind": "DAILY"},
            format="json",
        )
        _assert_rate_limited(blocked_write)
        assert redis_client.ttl(write_key) > 0

        _fill(checkin_key, GOAL_CHECKIN_WINDOW, GOAL_CHECKIN_LIMIT)
        blocked_checkin = client.post(
            f"{GOALS_URL}{goal.id}/check-ins/",
            {"status": "COMPLETED"},
            format="json",
        )
        _assert_rate_limited(blocked_checkin)
        assert redis_client.ttl(checkin_key) > 0
    finally:
        redis_client.delete(write_key, checkin_key)
        assert redis_client.exists(write_key, checkin_key) == 0
