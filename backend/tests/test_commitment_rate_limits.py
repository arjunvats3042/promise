import logging

import pytest
from django.contrib.auth import get_user_model
from redis.exceptions import ConnectionError as RedisConnectionError
from rest_framework.test import APIClient

from apps.commitments.models import Commitment
from apps.commitments.rate_limits import (
    COMMITMENT_WRITE_LIMIT,
    COMMITMENT_WRITE_WINDOW,
    commitment_user_key,
)
from apps.commitments.services import create_commitment
from apps.core.redis import increment_rate_limit

User = get_user_model()

COMMITMENTS_URL = "/api/v1/commitments/"
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
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul@example.com",
        name="Rahul",
        password=STRONG_PASSWORD,
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
def test_commitment_first_mutation_is_allowed(client, arjun, fake_redis):
    _auth(client, _token(client, arjun.email))
    response = client.post(COMMITMENTS_URL, {"title": "Ship rate limits"}, format="json")

    assert response.status_code == 201
    assert fake_redis.get(commitment_user_key(arjun.id)) == "1"
    assert all("@" not in key for key in fake_redis.scan_iter("promise:ratelimit:commitment:*"))


@pytest.mark.django_db
def test_commitment_allows_exactly_limit_then_429(client, arjun, fake_redis, monkeypatch):
    _auth(client, _token(client, arjun.email))
    _fill(commitment_user_key(arjun.id), COMMITMENT_WRITE_WINDOW, COMMITMENT_WRITE_LIMIT - 1)
    allowed = client.post(COMMITMENTS_URL, {"title": "Allowed"}, format="json")
    assert allowed.status_code == 201

    def fail_if_called(**kwargs):
        raise AssertionError("domain mutation must not run after rate limiting")

    monkeypatch.setattr("apps.commitments.views.create_commitment", fail_if_called)
    blocked = client.post(COMMITMENTS_URL, {"title": "Blocked"}, format="json")
    _assert_rate_limited(blocked)


@pytest.mark.django_db
def test_commitment_gets_are_not_rate_limited(client, arjun, fake_redis):
    commitment = create_commitment(creator=arjun, title="Keep listing")
    _auth(client, _token(client, arjun.email))
    _fill(commitment_user_key(arjun.id), COMMITMENT_WRITE_WINDOW, COMMITMENT_WRITE_LIMIT)

    listing = client.get(COMMITMENTS_URL)
    detail = client.get(f"{COMMITMENTS_URL}{commitment.id}/")

    assert listing.status_code == 200
    assert detail.status_code == 200
    assert listing.json()["results"][0]["id"] == str(commitment.id)
    assert fake_redis.get(commitment_user_key(arjun.id)) == str(COMMITMENT_WRITE_LIMIT)


@pytest.mark.django_db
def test_commitment_users_have_independent_buckets(client, arjun, rahul, fake_redis):
    _fill(commitment_user_key(arjun.id), COMMITMENT_WRITE_WINDOW, COMMITMENT_WRITE_LIMIT)
    _auth(client, _token(client, rahul.email))
    rahul_create = client.post(COMMITMENTS_URL, {"title": "Rahul's"}, format="json")
    assert rahul_create.status_code == 201

    _auth(client, _token(client, arjun.email))
    arjun_blocked = client.post(COMMITMENTS_URL, {"title": "Arjun blocked"}, format="json")
    _assert_rate_limited(arjun_blocked)
    assert fake_redis.get(commitment_user_key(rahul.id)) == "1"


@pytest.mark.django_db
def test_commitment_actions_share_write_bucket(client, arjun, fake_redis):
    commitment = create_commitment(creator=arjun, title="Complete me")
    _auth(client, _token(client, arjun.email))
    _fill(commitment_user_key(arjun.id), COMMITMENT_WRITE_WINDOW, COMMITMENT_WRITE_LIMIT)

    response = client.post(f"{COMMITMENTS_URL}{commitment.id}/complete/", format="json")
    _assert_rate_limited(response)
    commitment.refresh_from_db()
    assert commitment.status == Commitment.Status.PENDING


@pytest.mark.django_db
def test_commitment_patch_and_other_actions_share_write_bucket(client, arjun, fake_redis):
    commitment = create_commitment(creator=arjun, title="Keep title")
    _auth(client, _token(client, arjun.email))
    _fill(commitment_user_key(arjun.id), COMMITMENT_WRITE_WINDOW, COMMITMENT_WRITE_LIMIT)
    detail = f"{COMMITMENTS_URL}{commitment.id}/"

    patch = client.patch(detail, {"title": "Changed"}, format="json")
    snooze = client.post(f"{detail}snooze/", {}, format="json")
    unsnooze = client.post(f"{detail}unsnooze/", format="json")
    wait = client.post(f"{detail}wait/", format="json")
    cancel = client.post(f"{detail}cancel/", format="json")

    for response in (patch, snooze, unsnooze, wait, cancel):
        _assert_rate_limited(response)
    commitment.refresh_from_db()
    assert commitment.title == "Keep title"
    assert commitment.status == Commitment.Status.PENDING


@pytest.mark.django_db
def test_commitment_other_user_consumes_own_bucket_not_owner(client, arjun, rahul, fake_redis):
    commitment = create_commitment(creator=arjun, title="Arjun only")
    _fill(commitment_user_key(rahul.id), COMMITMENT_WRITE_WINDOW, COMMITMENT_WRITE_LIMIT)
    _auth(client, _token(client, rahul.email))

    response = client.post(f"{COMMITMENTS_URL}{commitment.id}/complete/", format="json")
    _assert_rate_limited(response)
    assert fake_redis.get(commitment_user_key(arjun.id)) is None


@pytest.mark.django_db
def test_commitment_unauthenticated_mutation_is_not_rate_limited(client, fake_redis):
    response = client.post(COMMITMENTS_URL, {"title": "No auth"}, format="json")

    assert response.status_code == 401
    assert list(fake_redis.scan_iter("promise:ratelimit:commitment:*")) == []


@pytest.mark.django_db
def test_commitment_redis_unavailable_fails_open(client, arjun, monkeypatch, caplog):
    _auth(client, _token(client, arjun.email))

    def raise_down():
        raise RedisConnectionError("redis down")

    monkeypatch.setattr("apps.core.redis.get_redis_client", raise_down)
    with caplog.at_level(logging.WARNING, logger="promise"):
        response = client.post(COMMITMENTS_URL, {"title": "Still works"}, format="json")

    assert response.status_code == 201
    assert "redis down" not in response.content.decode()
    assert "redis down" not in caplog.text


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
def test_live_redis_commitment_write_limit(client, arjun, monkeypatch):
    _auth(client, _token(client, arjun.email))
    redis_client = _live_redis_or_skip(monkeypatch)
    key = commitment_user_key(arjun.id)
    try:
        _fill(key, COMMITMENT_WRITE_WINDOW, COMMITMENT_WRITE_LIMIT)
        assert redis_client.ttl(key) > 0
        blocked = client.post(COMMITMENTS_URL, {"title": "Live blocked"}, format="json")
        _assert_rate_limited(blocked)
        assert int(redis_client.get(key)) == COMMITMENT_WRITE_LIMIT + 1
    finally:
        redis_client.delete(key)
        assert redis_client.exists(key) == 0
