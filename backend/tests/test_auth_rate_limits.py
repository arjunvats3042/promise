import logging
import uuid

import pytest
from django.contrib.auth import get_user_model
from redis.exceptions import ConnectionError as RedisConnectionError
from rest_framework.test import APIClient

from apps.authentication.rate_limits import (
    LOGIN_EMAIL_LIMIT,
    LOGIN_EMAIL_WINDOW,
    LOGIN_IP_LIMIT,
    LOGIN_IP_WINDOW,
    REFRESH_FALLBACK_LIMIT,
    REFRESH_FALLBACK_WINDOW,
    REFRESH_SESSION_LIMIT,
    REFRESH_SESSION_WINDOW,
    REFRESH_USER_LIMIT,
    REFRESH_USER_WINDOW,
    REGISTER_EMAIL_LIMIT,
    REGISTER_EMAIL_WINDOW,
    REGISTER_IP_LIMIT,
    REGISTER_IP_WINDOW,
    login_email_key,
    login_ip_key,
    refresh_fallback_ip_key,
    refresh_session_key,
    refresh_user_key,
    register_email_key,
    register_ip_key,
)
from apps.core.redis import increment_rate_limit

User = get_user_model()

LOGIN_URL = "/api/v1/auth/login/"
REGISTER_URL = "/api/v1/auth/register/"
REFRESH_URL = "/api/v1/auth/refresh/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_ENCRYPTION_KEY = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
RATE_LIMITED = {
    "error": {
        "code": "RATE_LIMITED",
        "message": "Too many requests. Please try again later.",
    }
}
AUTHENTICATION_FAILED = {
    "error": {
        "code": "AUTHENTICATION_FAILED",
        "message": "Invalid email or password.",
    }
}


@pytest.fixture(autouse=True)
def auth_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = TEST_ENCRYPTION_KEY
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
def user(db):
    return User.objects.create_user(
        email="alex@example.com",
        name="Alex",
        password=STRONG_PASSWORD,
    )


def _fill(key, window, count):
    last = None
    for _ in range(count):
        last = increment_rate_limit(key, window)
    return last


def _login(client, *, email="alex@example.com", password=STRONG_PASSWORD, ip="127.0.0.1"):
    return client.post(
        LOGIN_URL,
        {"email": email, "password": password},
        format="json",
        REMOTE_ADDR=ip,
    )


def _register(client, *, email, password=STRONG_PASSWORD, name="User Name", ip="127.0.0.1"):
    return client.post(
        REGISTER_URL,
        {"email": email, "password": password, "name": name},
        format="json",
        REMOTE_ADDR=ip,
    )


def _refresh(client, refresh_token, *, ip="127.0.0.1"):
    return client.post(
        REFRESH_URL,
        {"refresh_token": refresh_token},
        format="json",
        REMOTE_ADDR=ip,
    )


def _assert_rate_limited(response):
    assert response.status_code == 429
    assert response.json() == RATE_LIMITED
    retry_after = response.headers.get("Retry-After")
    assert retry_after is not None
    assert int(retry_after) > 0
    assert "redis" not in response.content.decode().lower()
    return int(retry_after)


def _rate_keys(fake_redis):
    return sorted(fake_redis.scan_iter("promise:ratelimit:*"))


def _login_tokens(client, user, *, ip="127.0.0.1"):
    response = _login(client, email=user.email, ip=ip)
    assert response.status_code == 200
    return response.json()["tokens"]


@pytest.mark.django_db
def test_login_first_attempt_is_allowed(client, user, fake_redis):
    response = _login(client, email=user.email)

    assert response.status_code == 200
    assert fake_redis.get(login_email_key(user.email)) == "1"
    assert fake_redis.get(login_ip_key("127.0.0.1")) == "1"


@pytest.mark.django_db
def test_login_wrong_password_counts(client, user, fake_redis):
    response = _login(client, email=user.email, password="wrong-password-value")

    assert response.status_code == 401
    assert response.json() == AUTHENTICATION_FAILED
    assert fake_redis.get(login_email_key(user.email)) == "1"


@pytest.mark.django_db
def test_login_unknown_email_counts(client, fake_redis):
    response = _login(client, email="missing@example.com")

    assert response.status_code == 401
    assert response.json() == AUTHENTICATION_FAILED
    assert fake_redis.get(login_email_key("missing@example.com")) == "1"
    assert all("missing@example.com" not in key for key in _rate_keys(fake_redis))


@pytest.mark.django_db
def test_login_inactive_account_counts(client, user, fake_redis):
    user.is_active = False
    user.save(update_fields=["is_active"])

    response = _login(client, email=user.email)

    assert response.status_code == 401
    assert response.json() == AUTHENTICATION_FAILED
    assert fake_redis.get(login_email_key(user.email)) == "1"


@pytest.mark.django_db
def test_login_normalized_email_uses_same_bucket(client, user, fake_redis):
    _login(client, email="  Alex@Example.COM  ")
    _login(client, email="alex@example.com", password="wrong-password-value")

    assert fake_redis.get(login_email_key("alex@example.com")) == "2"
    assert login_email_key("  Alex@Example.COM  ") == login_email_key("alex@example.com")


@pytest.mark.django_db
def test_login_ip_and_email_buckets_are_independent(client, user, fake_redis):
    _fill(login_email_key(user.email), LOGIN_EMAIL_WINDOW, LOGIN_EMAIL_LIMIT)
    allowed_other_email = _login(
        client,
        email="other@example.com",
        password="wrong-password-value",
        ip="127.0.0.1",
    )
    blocked_same_email = _login(client, email=user.email, ip="203.0.113.10")

    assert allowed_other_email.status_code == 401
    _assert_rate_limited(blocked_same_email)

    _fill(login_ip_key("198.51.100.9"), LOGIN_IP_WINDOW, LOGIN_IP_LIMIT)
    blocked_ip = _login(
        client,
        email="fresh@example.com",
        password="wrong-password-value",
        ip="198.51.100.9",
    )
    allowed_other_ip = _login(
        client,
        email="fresh@example.com",
        password="wrong-password-value",
        ip="198.51.100.10",
    )
    _assert_rate_limited(blocked_ip)
    assert allowed_other_ip.status_code == 401


@pytest.mark.django_db
def test_login_allows_exactly_the_email_limit_then_429(client, user, fake_redis, monkeypatch):
    _fill(login_email_key(user.email), LOGIN_EMAIL_WINDOW, LOGIN_EMAIL_LIMIT - 1)
    allowed = _login(client, email=user.email)
    assert allowed.status_code == 200

    def fail_if_called(**kwargs):
        raise AssertionError("password verification must not run after rate limiting")

    monkeypatch.setattr("apps.authentication.views.login_user", fail_if_called)
    blocked = _login(client, email=user.email)
    _assert_rate_limited(blocked)


@pytest.mark.django_db
def test_login_does_not_trust_x_forwarded_for(client, user, fake_redis):
    _fill(login_ip_key("8.8.8.8"), LOGIN_IP_WINDOW, LOGIN_IP_LIMIT)
    response = client.post(
        LOGIN_URL,
        {"email": user.email, "password": STRONG_PASSWORD},
        format="json",
        REMOTE_ADDR="127.0.0.1",
        HTTP_X_FORWARDED_FOR="8.8.8.8",
    )

    assert response.status_code == 200
    assert fake_redis.get(login_ip_key("127.0.0.1")) == "1"


@pytest.mark.django_db
def test_register_ip_limit_returns_429(client, fake_redis, monkeypatch):
    ip = "203.0.113.20"
    _fill(register_ip_key(ip), REGISTER_IP_WINDOW, REGISTER_IP_LIMIT)

    def fail_if_called(**kwargs):
        raise AssertionError("registration must not run after rate limiting")

    monkeypatch.setattr("apps.authentication.views.register_user", fail_if_called)
    response = _register(client, email="new-user@example.com", ip=ip)
    _assert_rate_limited(response)
    assert User.objects.count() == 0


@pytest.mark.django_db
def test_register_email_limit_and_normalized_bucket(client, fake_redis):
    email_key = register_email_key("new-user@example.com")
    assert email_key == register_email_key("  New-User@Example.COM  ")
    _fill(email_key, REGISTER_EMAIL_WINDOW, REGISTER_EMAIL_LIMIT)

    response = _register(
        client,
        email="  New-User@Example.COM  ",
        ip="203.0.113.21",
    )
    _assert_rate_limited(response)
    assert User.objects.count() == 0
    assert all("@" not in key for key in _rate_keys(fake_redis))


@pytest.mark.django_db
def test_register_under_limit_still_creates_user(client, fake_redis):
    response = _register(client, email="new-user@example.com")

    assert response.status_code == 201
    assert User.objects.filter(email="new-user@example.com").exists()
    assert fake_redis.get(register_email_key("new-user@example.com")) == "1"


@pytest.mark.django_db
def test_refresh_session_bucket_returns_429(client, user, fake_redis, monkeypatch):
    tokens = _login_tokens(client, user)
    session_id = uuid.UUID(tokens["refresh_token"].split(".", 1)[0])
    _fill(
        refresh_session_key(session_id),
        REFRESH_SESSION_WINDOW,
        REFRESH_SESSION_LIMIT,
    )

    def fail_if_called(**kwargs):
        raise AssertionError("token rotation must not run after rate limiting")

    monkeypatch.setattr("apps.authentication.views.refresh_tokens", fail_if_called)
    response = _refresh(client, tokens["refresh_token"])
    _assert_rate_limited(response)
    secret = tokens["refresh_token"].split(".", 1)[1]
    assert all(secret not in key for key in _rate_keys(fake_redis))
    assert all(tokens["refresh_token"] not in key for key in _rate_keys(fake_redis))


@pytest.mark.django_db
def test_refresh_user_bucket_returns_429(client, user, fake_redis):
    tokens = _login_tokens(client, user)
    _fill(refresh_user_key(user.id), REFRESH_USER_WINDOW, REFRESH_USER_LIMIT)

    response = _refresh(client, tokens["refresh_token"])
    _assert_rate_limited(response)


@pytest.mark.django_db
def test_refresh_allows_exactly_session_limit_then_429(client, user, fake_redis):
    tokens = _login_tokens(client, user)
    session_id = uuid.UUID(tokens["refresh_token"].split(".", 1)[0])
    _fill(
        refresh_session_key(session_id),
        REFRESH_SESSION_WINDOW,
        REFRESH_SESSION_LIMIT - 1,
    )

    allowed = _refresh(client, tokens["refresh_token"])
    assert allowed.status_code == 200
    blocked = _refresh(client, allowed.json()["tokens"]["refresh_token"])
    _assert_rate_limited(blocked)


@pytest.mark.django_db
def test_malformed_refresh_uses_bounded_ip_fallback(client, fake_redis):
    ip = "203.0.113.30"
    for index in range(5):
        response = _refresh(client, f"not-a-token-{index}", ip=ip)
        assert response.status_code == 401

    keys = _rate_keys(fake_redis)
    assert keys == [refresh_fallback_ip_key(ip)]
    assert fake_redis.get(refresh_fallback_ip_key(ip)) == "5"
    assert all("not-a-token" not in key for key in keys)


@pytest.mark.django_db
def test_unknown_refresh_session_does_not_create_session_keys(client, fake_redis):
    ip = "203.0.113.31"
    for _ in range(5):
        response = _refresh(client, f"{uuid.uuid4()}.aabbccdd", ip=ip)
        assert response.status_code == 401

    keys = _rate_keys(fake_redis)
    assert keys == [refresh_fallback_ip_key(ip)]
    assert not any(":session:" in key or ":user:" in key for key in keys)


@pytest.mark.django_db
def test_malformed_refresh_ip_limit_returns_429(client, fake_redis):
    ip = "203.0.113.32"
    _fill(refresh_fallback_ip_key(ip), REFRESH_FALLBACK_WINDOW, REFRESH_FALLBACK_LIMIT)
    response = _refresh(client, "not-a-token", ip=ip)
    _assert_rate_limited(response)


@pytest.mark.django_db
def test_login_redis_unavailable_fails_closed(client, user, monkeypatch, caplog):
    def raise_down():
        raise RedisConnectionError("redis down")

    monkeypatch.setattr("apps.core.redis.get_redis_client", raise_down)
    with caplog.at_level(logging.WARNING, logger="promise"):
        response = _login(client, email=user.email)

    assert response.status_code == 429
    assert "redis down" not in response.content.decode()
    assert user.email not in caplog.text
    assert "redis down" not in caplog.text


@pytest.mark.django_db
def test_refresh_redis_unavailable_fails_closed(client, user, monkeypatch, caplog):
    tokens = _login_tokens(client, user)

    def raise_down():
        raise RedisConnectionError("redis down")

    monkeypatch.setattr("apps.core.redis.get_redis_client", raise_down)
    with caplog.at_level(logging.WARNING, logger="promise"):
        response = _refresh(client, tokens["refresh_token"])

    assert response.status_code == 429
    secret = tokens["refresh_token"].split(".", 1)[1]
    assert secret not in caplog.text
    assert tokens["refresh_token"] not in response.content.decode()
    assert response.status_code != 500


def test_login_email_key_never_contains_plaintext_email():
    key = login_email_key("alex@example.com")
    assert key.startswith("promise:ratelimit:login:email:")
    assert "@" not in key
    assert "alex" not in key
    assert "example.com" not in key


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
def test_live_redis_login_limit_and_ttl(client, user, monkeypatch):
    redis_client = _live_redis_or_skip(monkeypatch)
    ip = "203.0.113.80"
    email = user.email
    ip_key = login_ip_key(ip)
    email_key = login_email_key(email)
    try:
        _fill(email_key, LOGIN_EMAIL_WINDOW, LOGIN_EMAIL_LIMIT)
        assert int(redis_client.get(email_key)) == LOGIN_EMAIL_LIMIT
        assert redis_client.ttl(email_key) > 0
        blocked = _login(client, email=email, ip=ip)
        _assert_rate_limited(blocked)
        assert int(redis_client.get(email_key)) == LOGIN_EMAIL_LIMIT + 1
        assert int(redis_client.get(ip_key)) == 1
        assert redis_client.ttl(ip_key) > 0
        assert redis_client.ttl(email_key) <= LOGIN_EMAIL_WINDOW
    finally:
        redis_client.delete(ip_key, email_key)
        assert redis_client.exists(ip_key, email_key) == 0


@pytest.mark.django_db
def test_live_redis_refresh_session_limit(client, user, monkeypatch):
    redis_client = _live_redis_or_skip(monkeypatch)
    tokens = _login_tokens(client, user, ip="203.0.113.81")
    session_id = uuid.UUID(tokens["refresh_token"].split(".", 1)[0])
    session_key = refresh_session_key(session_id)
    user_key = refresh_user_key(user.id)
    login_keys = [login_ip_key("203.0.113.81"), login_email_key(user.email)]
    try:
        _fill(session_key, REFRESH_SESSION_WINDOW, REFRESH_SESSION_LIMIT)
        blocked = _refresh(client, tokens["refresh_token"], ip="203.0.113.81")
        _assert_rate_limited(blocked)
        assert int(redis_client.get(session_key)) == REFRESH_SESSION_LIMIT + 1
        assert redis_client.ttl(session_key) > 0
    finally:
        redis_client.delete(session_key, user_key, *login_keys)
