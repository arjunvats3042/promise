import logging
import uuid

import fakeredis
import pytest
from django.contrib.auth import get_user_model
from redis.exceptions import ConnectionError as RedisConnectionError
from rest_framework.test import APIClient

from apps.authentication.denylist import (
    SESSION_DENYLIST_KEY_PREFIX,
    deny_access_session,
    session_denylist_key,
    session_denylist_ttl_seconds,
)
from apps.authentication.models import AuthSession

User = get_user_model()

ME_URL = "/api/v1/auth/me/"
LOGIN_URL = "/api/v1/auth/login/"
LOGOUT_URL = "/api/v1/auth/logout/"
LOGOUT_ALL_URL = "/api/v1/auth/logout-all/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
TEST_ENCRYPTION_KEY = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
UNAUTHENTICATED_INVALID = {
    "error": {
        "code": "UNAUTHENTICATED",
        "message": "Authentication failed.",
    }
}


@pytest.fixture(autouse=True)
def auth_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = TEST_ENCRYPTION_KEY
    settings.JWT_SIGNING_KEY = TEST_SIGNING_KEY
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
def user(db):
    return User.objects.create_user(
        email="alex@example.com",
        name="Alex",
        password=STRONG_PASSWORD,
    )


def _login(client, email):
    response = client.post(
        LOGIN_URL,
        {"email": email, "password": STRONG_PASSWORD},
        format="json",
    )
    assert response.status_code == 200
    return response.json()["tokens"]


def _me(client, access_token):
    return client.get(ME_URL, HTTP_AUTHORIZATION=f"Bearer {access_token}")


def _logout(client, tokens):
    return client.post(
        LOGOUT_URL,
        {"refresh_token": tokens["refresh_token"]},
        format="json",
        HTTP_AUTHORIZATION=f"Bearer {tokens['access_token']}",
    )


@pytest.mark.django_db
def test_valid_jwt_with_active_session_is_authenticated(client, user, fake_redis):
    tokens = _login(client, user.email)

    response = _me(client, tokens["access_token"])

    assert response.status_code == 200
    assert response.json()["user"]["email"] == user.email
    session = AuthSession.objects.get()
    assert fake_redis.exists(session_denylist_key(session.id)) == 0


@pytest.mark.django_db
def test_denied_sid_is_rejected_while_postgres_session_is_still_active(
    client, user, fake_redis
):
    tokens = _login(client, user.email)
    session = AuthSession.objects.get()
    deny_access_session(session.id)

    response = _me(client, tokens["access_token"])
    session.refresh_from_db()

    assert session.revoked_at is None
    assert session.is_active() is True
    assert response.status_code == 401
    assert response.json() == UNAUTHENTICATED_INVALID


@pytest.mark.django_db
def test_logout_writes_denylist_key_with_ttl(client, user, fake_redis):
    tokens = _login(client, user.email)
    session = AuthSession.objects.get()

    response = _logout(client, tokens)
    key = session_denylist_key(session.id)

    assert response.status_code == 204
    assert fake_redis.get(key) == "1"
    assert fake_redis.ttl(key) == session_denylist_ttl_seconds()
    assert tokens["access_token"] not in fake_redis.get(key)
    assert tokens["refresh_token"] not in str(fake_redis.get(key))


@pytest.mark.django_db
def test_logout_all_writes_denylist_keys_for_each_session(client, user, fake_redis):
    first = _login(client, user.email)
    second = _login(client, user.email)
    session_ids = list(AuthSession.objects.filter(user=user).values_list("id", flat=True))

    response = client.post(
        LOGOUT_ALL_URL,
        {},
        format="json",
        HTTP_AUTHORIZATION=f"Bearer {first['access_token']}",
    )

    assert response.status_code == 204
    assert len(session_ids) == 2
    for session_id in session_ids:
        key = session_denylist_key(session_id)
        assert fake_redis.get(key) == "1"
        assert fake_redis.ttl(key) == session_denylist_ttl_seconds()


@pytest.mark.django_db
def test_denylist_key_expires(fake_redis, settings):
    settings.JWT_ACCESS_TTL_SECONDS = 1
    settings.JWT_LEEWAY_SECONDS = 0
    session_id = uuid.uuid4()
    deny_access_session(session_id)
    key = session_denylist_key(session_id)

    assert fake_redis.ttl(key) == 1
    fake_redis.expire(key, 0)

    assert fake_redis.exists(key) == 0
    assert fake_redis.get(key) is None


@pytest.mark.django_db
def test_redis_unavailable_on_read_falls_back_to_postgres_allow(
    client, user, monkeypatch
):
    tokens = _login(client, user.email)

    def raise_down():
        raise RedisConnectionError("redis down")

    monkeypatch.setattr("apps.core.redis.get_redis_client", raise_down)
    monkeypatch.setattr(
        "apps.authentication.denylist.get_redis_client",
        raise_down,
    )

    response = _me(client, tokens["access_token"])

    assert response.status_code == 200
    assert AuthSession.objects.get().revoked_at is None


@pytest.mark.django_db
def test_redis_unavailable_on_logout_still_revokes_in_postgres(
    client, user, monkeypatch, caplog
):
    tokens = _login(client, user.email)

    def raise_down():
        raise RedisConnectionError("redis down")

    monkeypatch.setattr("apps.core.redis.get_redis_client", raise_down)
    monkeypatch.setattr(
        "apps.authentication.denylist.get_redis_client",
        raise_down,
    )

    with caplog.at_level(logging.INFO, logger="promise"):
        logout_response = _logout(client, tokens)
        me_response = _me(client, tokens["access_token"])

    assert logout_response.status_code == 204
    assert AuthSession.objects.get().revoked_at is not None
    assert me_response.status_code == 401
    assert me_response.json() == UNAUTHENTICATED_INVALID
    assert tokens["access_token"] not in caplog.text
    assert tokens["refresh_token"] not in caplog.text
    assert TEST_SIGNING_KEY not in caplog.text


@pytest.mark.django_db
def test_revoked_postgres_session_fails_even_without_denylist_key(
    client, user, fake_redis
):
    tokens = _login(client, user.email)
    session = AuthSession.objects.get()
    _logout(client, tokens)
    fake_redis.flushall()

    response = _me(client, tokens["access_token"])

    assert fake_redis.exists(session_denylist_key(session.id)) == 0
    assert response.status_code == 401
    assert response.json() == UNAUTHENTICATED_INVALID


@pytest.mark.django_db
def test_denylist_key_uses_sid_prefix_not_jti():
    session_id = uuid.uuid4()
    key = session_denylist_key(session_id)

    assert key == f"{SESSION_DENYLIST_KEY_PREFIX}{session_id}"
    assert "jti" not in key
    assert "deny:sid" in key or "denylist:sid" in key
