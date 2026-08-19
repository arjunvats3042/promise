import logging
import uuid

import pytest
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient

from apps.authentication.access_tokens import decode_access_token
from apps.authentication.models import AuthSession

User = get_user_model()

LOGOUT_URL = "/api/v1/auth/logout/"
LOGOUT_ALL_URL = "/api/v1/auth/logout-all/"
REFRESH_URL = "/api/v1/auth/refresh/"
LOGIN_URL = "/api/v1/auth/login/"
ME_URL = "/api/v1/auth/me/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
TEST_ENCRYPTION_KEY = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
TOKEN_INVALID = {
    "error": {
        "code": "TOKEN_INVALID",
        "message": "Refresh token is invalid.",
    }
}
SESSION_REVOKED = {
    "error": {
        "code": "SESSION_REVOKED",
        "message": "Session is no longer valid.",
    }
}
UNAUTHENTICATED_MISSING = {
    "error": {
        "code": "UNAUTHENTICATED",
        "message": "Authentication credentials were not provided.",
    }
}
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


@pytest.fixture
def other_user(db):
    return User.objects.create_user(
        email="sam@example.com",
        name="Sam",
        password=STRONG_PASSWORD,
    )


def _login(client, email, password=STRONG_PASSWORD):
    response = client.post(
        LOGIN_URL,
        {"email": email, "password": password},
        format="json",
    )
    assert response.status_code == 200
    return response.json()["tokens"]


def _auth(access_token):
    return {"HTTP_AUTHORIZATION": f"Bearer {access_token}"}


def _logout(client, access_token, refresh_token):
    return client.post(
        LOGOUT_URL,
        {"refresh_token": refresh_token},
        format="json",
        **_auth(access_token),
    )


def _logout_all(client, access_token):
    return client.post(LOGOUT_ALL_URL, {}, format="json", **_auth(access_token))


def _refresh(client, refresh_token):
    return client.post(
        REFRESH_URL,
        {"refresh_token": refresh_token},
        format="json",
    )


def _me(client, access_token):
    return client.get(ME_URL, **_auth(access_token))


def _assert_empty_204(response):
    assert response.status_code == 204
    assert response.content == b""


@pytest.mark.django_db
def test_logout_revokes_own_session_and_returns_204(client, user):
    tokens = _login(client, user.email)
    session = AuthSession.objects.get()

    response = _logout(client, tokens["access_token"], tokens["refresh_token"])
    session.refresh_from_db()

    _assert_empty_204(response)
    assert session.revoked_at is not None
    assert session.revoked_reason == AuthSession.RevokedReason.LOGOUT
    assert AuthSession.objects.count() == 1


@pytest.mark.django_db
def test_refresh_fails_after_logout(client, user):
    tokens = _login(client, user.email)

    _assert_empty_204(
        _logout(client, tokens["access_token"], tokens["refresh_token"])
    )
    response = _refresh(client, tokens["refresh_token"])

    assert response.status_code == 401
    assert response.json() == SESSION_REVOKED


@pytest.mark.django_db
def test_logout_is_idempotent_for_already_revoked_own_session(client, user):
    first = _login(client, user.email)
    second = _login(client, user.email)
    target = AuthSession.objects.exclude(
        id=decode_access_token(first["access_token"]).session_id
    ).get()

    first_logout = _logout(client, first["access_token"], second["refresh_token"])
    second_logout = _logout(client, first["access_token"], second["refresh_token"])
    target.refresh_from_db()

    _assert_empty_204(first_logout)
    _assert_empty_204(second_logout)
    assert target.revoked_reason == AuthSession.RevokedReason.LOGOUT
    assert AuthSession.objects.filter(revoked_at__isnull=True).count() == 1


@pytest.mark.django_db
def test_user_cannot_logout_another_users_session(client, user, other_user):
    own = _login(client, user.email)
    other = _login(client, other_user.email)
    other_session = AuthSession.objects.get(
        user=other_user,
    )

    response = _logout(client, own["access_token"], other["refresh_token"])
    other_session.refresh_from_db()

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID
    assert other_session.revoked_at is None
    assert AuthSession.objects.filter(user=user, revoked_at__isnull=True).count() == 1


@pytest.mark.django_db
def test_logout_wrong_secret_does_not_reveal_or_revoke(client, user):
    tokens = _login(client, user.email)
    session_id = str(AuthSession.objects.get().id)

    response = _logout(
        client,
        tokens["access_token"],
        f"{session_id}.definitely-not-the-secret",
    )
    session = AuthSession.objects.get()

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID
    assert session.revoked_at is None


@pytest.mark.django_db
def test_logout_rejects_malformed_refresh_token(client, user):
    tokens = _login(client, user.email)

    response = _logout(client, tokens["access_token"], "not-a-token")

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID
    assert AuthSession.objects.get().revoked_at is None


@pytest.mark.django_db
def test_logout_unknown_session_is_generic_failure(client, user):
    tokens = _login(client, user.email)

    response = _logout(
        client,
        tokens["access_token"],
        f"{uuid.uuid4()}.a-secret-value",
    )

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID
    assert AuthSession.objects.get().revoked_at is None


@pytest.mark.django_db
def test_logout_missing_refresh_token_is_validation_error(client, user):
    tokens = _login(client, user.email)

    response = client.post(LOGOUT_URL, {}, format="json", **_auth(tokens["access_token"]))

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


@pytest.mark.django_db
def test_logout_requires_access_token(client, user):
    tokens = _login(client, user.email)

    response = client.post(
        LOGOUT_URL,
        {"refresh_token": tokens["refresh_token"]},
        format="json",
    )

    assert response.status_code == 401
    assert response.json() == UNAUTHENTICATED_MISSING
    assert AuthSession.objects.get().revoked_at is None


@pytest.mark.django_db
def test_logout_all_revokes_every_own_session(client, user):
    first = _login(client, user.email)
    second = _login(client, user.email)

    response = _logout_all(client, first["access_token"])

    _assert_empty_204(response)
    assert AuthSession.objects.filter(user=user).count() == 2
    assert AuthSession.objects.filter(user=user, revoked_at__isnull=True).count() == 0
    assert set(
        AuthSession.objects.filter(user=user).values_list("revoked_reason", flat=True)
    ) == {AuthSession.RevokedReason.LOGOUT_ALL}


@pytest.mark.django_db
def test_refresh_fails_for_all_sessions_after_logout_all(client, user):
    first = _login(client, user.email)
    second = _login(client, user.email)

    _assert_empty_204(_logout_all(client, first["access_token"]))

    assert _refresh(client, first["refresh_token"]).json() == SESSION_REVOKED
    assert _refresh(client, second["refresh_token"]).json() == SESSION_REVOKED


@pytest.mark.django_db
def test_logout_all_is_idempotent_for_already_revoked_sessions(client, user):
    first = _login(client, user.email)
    second = _login(client, user.email)
    _logout(client, first["access_token"], second["refresh_token"])

    response = _logout_all(client, first["access_token"])
    first_session = AuthSession.objects.get(
        id=decode_access_token(first["access_token"]).session_id
    )
    second_session = AuthSession.objects.get(
        id=decode_access_token(second["access_token"]).session_id
    )

    _assert_empty_204(response)
    assert second_session.revoked_reason == AuthSession.RevokedReason.LOGOUT
    assert first_session.revoked_reason == AuthSession.RevokedReason.LOGOUT_ALL
    assert first_session.revoked_at is not None
    assert second_session.revoked_at is not None


@pytest.mark.django_db
def test_logout_all_does_not_touch_another_users_sessions(client, user, other_user):
    own = _login(client, user.email)
    other = _login(client, other_user.email)

    _assert_empty_204(_logout_all(client, own["access_token"]))
    other_session = AuthSession.objects.get(user=other_user)

    assert AuthSession.objects.get(user=user).revoked_at is not None
    assert other_session.revoked_at is None
    assert _refresh(client, other["refresh_token"]).status_code == 200


@pytest.mark.django_db
def test_logout_all_requires_access_token(client, user):
    _login(client, user.email)

    response = client.post(LOGOUT_ALL_URL, {}, format="json")

    assert response.status_code == 401
    assert response.json() == UNAUTHENTICATED_MISSING
    assert AuthSession.objects.get().revoked_at is None


@pytest.mark.django_db
def test_me_rejects_access_jwt_after_logout_without_redis(client, user):
    tokens = _login(client, user.email)
    claims = decode_access_token(tokens["access_token"])

    _assert_empty_204(
        _logout(client, tokens["access_token"], tokens["refresh_token"])
    )
    me_response = _me(client, tokens["access_token"])

    assert decode_access_token(tokens["access_token"]).jti == claims.jti
    assert me_response.status_code == 401
    assert me_response.json() == UNAUTHENTICATED_INVALID


@pytest.mark.django_db
def test_logout_response_never_exposes_tokens_or_hashes(client, user):
    tokens = _login(client, user.email)
    session = AuthSession.objects.get()

    response = _logout(client, tokens["access_token"], tokens["refresh_token"])
    session.refresh_from_db()
    rendered = str(response.content)

    _assert_empty_204(response)
    assert tokens["refresh_token"] not in rendered
    assert session.refresh_token_hmac not in rendered
    assert session.current_refresh_secret_ciphertext not in rendered


@pytest.mark.django_db
def test_logout_logging_does_not_include_secrets(client, user, caplog):
    tokens = _login(client, user.email)
    session = AuthSession.objects.get()

    with caplog.at_level(logging.INFO, logger="promise"):
        _logout(client, tokens["access_token"], tokens["refresh_token"])

    assert tokens["refresh_token"] not in caplog.text
    assert tokens["access_token"] not in caplog.text
    assert session.refresh_token_hmac not in caplog.text
    assert TEST_ENCRYPTION_KEY not in caplog.text
    assert TEST_SIGNING_KEY not in caplog.text


@pytest.mark.django_db
def test_logout_does_not_create_or_rotate_tokens(client, user):
    tokens = _login(client, user.email)
    session = AuthSession.objects.get()
    hmac_before = session.refresh_token_hmac
    ciphertext_before = session.current_refresh_secret_ciphertext

    _logout(client, tokens["access_token"], tokens["refresh_token"])
    session.refresh_from_db()

    assert AuthSession.objects.count() == 1
    assert session.refresh_token_hmac == hmac_before
    assert session.current_refresh_secret_ciphertext == ciphertext_before
    assert session.previous_token_hmac is None
