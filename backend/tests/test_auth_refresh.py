import logging
import threading
import uuid
from datetime import timedelta

import pytest
from django.contrib.auth import get_user_model
from django.db import connection
from django.utils import timezone
from rest_framework.test import APIClient

from apps.authentication.access_tokens import decode_access_token
from apps.authentication.models import AuthSession
from apps.authentication.tokens import refresh_secret_matches

User = get_user_model()

REFRESH_URL = "/api/v1/auth/refresh/"
LOGIN_URL = "/api/v1/auth/login/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
TEST_ENCRYPTION_KEY = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
TEST_OTHER_ENCRYPTION_KEY = "MTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTE="
TOKEN_INVALID = {
    "error": {
        "code": "TOKEN_INVALID",
        "message": "Refresh token is invalid.",
    }
}
TOKEN_EXPIRED = {
    "error": {
        "code": "TOKEN_EXPIRED",
        "message": "Refresh token has expired.",
    }
}
SESSION_REVOKED = {
    "error": {
        "code": "SESSION_REVOKED",
        "message": "Session is no longer valid.",
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
def login_tokens(client, user):
    response = client.post(
        LOGIN_URL,
        {"email": user.email, "password": STRONG_PASSWORD},
        format="json",
    )
    assert response.status_code == 200
    return response.json()["tokens"]


def _refresh(client, refresh_token):
    return client.post(
        REFRESH_URL,
        {"refresh_token": refresh_token},
        format="json",
    )


def _split(refresh_token):
    session_id, secret = refresh_token.split(".", 1)
    return uuid.UUID(session_id), secret


def _assert_token_envelope(body):
    assert set(body) == {"tokens"}
    assert set(body["tokens"]) == {
        "access_token",
        "refresh_token",
        "token_type",
        "expires_in",
    }
    assert body["tokens"]["token_type"] == "Bearer"
    assert body["tokens"]["expires_in"] == 900
    return body["tokens"]


@pytest.mark.django_db
def test_successful_refresh_rotates_secret_and_keeps_session(client, user, login_tokens):
    ciphertext_before = AuthSession.objects.get().current_refresh_secret_ciphertext
    before = timezone.now()
    response = _refresh(client, login_tokens["refresh_token"])
    after = timezone.now()

    assert response.status_code == 200
    tokens = _assert_token_envelope(response.json())
    session = AuthSession.objects.get()
    old_id, old_secret = _split(login_tokens["refresh_token"])
    new_id, new_secret = _split(tokens["refresh_token"])
    old_access = decode_access_token(login_tokens["access_token"])
    new_access = decode_access_token(tokens["access_token"])

    assert AuthSession.objects.count() == 1
    assert new_id == old_id == session.id
    assert new_secret != old_secret
    assert refresh_secret_matches(new_secret, session.refresh_token_hmac)
    assert refresh_secret_matches(old_secret, session.previous_token_hmac)
    assert old_secret not in {
        session.refresh_token_hmac,
        session.previous_token_hmac,
        session.current_refresh_secret_ciphertext,
    }
    assert new_secret not in {
        session.refresh_token_hmac,
        session.previous_token_hmac,
        session.current_refresh_secret_ciphertext,
    }
    assert session.current_refresh_secret_ciphertext
    assert session.current_refresh_secret_ciphertext != ciphertext_before
    assert session.previous_rotated_at is not None
    assert before <= session.last_used_at <= after
    assert before <= session.previous_rotated_at <= after
    assert session.expires_at - session.last_used_at == timedelta(days=30)
    assert new_access.user_id == old_access.user_id == user.id
    assert new_access.session_id == old_access.session_id == session.id
    assert new_access.jti != old_access.jti


@pytest.mark.django_db
def test_old_refresh_token_is_rejected_after_rotation_outside_grace(
    client, user, login_tokens
):
    first = _refresh(client, login_tokens["refresh_token"])
    assert first.status_code == 200
    session = AuthSession.objects.get()
    session.previous_rotated_at = timezone.now() - timedelta(seconds=31)
    session.save(update_fields=["previous_rotated_at"])

    response = _refresh(client, login_tokens["refresh_token"])
    session.refresh_from_db()

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID
    assert session.revoked_at is not None
    assert session.revoked_reason == AuthSession.RevokedReason.REUSE


@pytest.mark.django_db
def test_grace_retry_returns_current_refresh_token_and_new_access_jwt(
    client, user, login_tokens
):
    rotated = _refresh(client, login_tokens["refresh_token"]).json()["tokens"]
    session_before = AuthSession.objects.get()
    hmac_before = session_before.refresh_token_hmac
    ciphertext_before = session_before.current_refresh_secret_ciphertext
    expires_before = session_before.expires_at
    absolute_before = session_before.absolute_expires_at

    response = _refresh(client, login_tokens["refresh_token"])
    session = AuthSession.objects.get()
    tokens = _assert_token_envelope(response.json())
    grace_access = decode_access_token(tokens["access_token"])
    rotated_access = decode_access_token(rotated["access_token"])

    assert response.status_code == 200
    assert tokens["refresh_token"] == rotated["refresh_token"]
    assert grace_access.jti != rotated_access.jti
    assert grace_access.user_id == user.id
    assert grace_access.session_id == session.id
    assert session.refresh_token_hmac == hmac_before
    assert session.current_refresh_secret_ciphertext == ciphertext_before
    assert session.expires_at == expires_before
    assert session.absolute_expires_at == absolute_before
    assert session.revoked_at is None
    assert AuthSession.objects.count() == 1


@pytest.mark.django_db
def test_refresh_rejects_missing_and_empty_token(client, user):
    missing = client.post(REFRESH_URL, {}, format="json")
    empty = _refresh(client, "")

    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "VALIDATION_ERROR"
    assert empty.status_code == 400
    assert empty.json()["error"]["code"] == "VALIDATION_ERROR"


@pytest.mark.django_db
@pytest.mark.parametrize(
    "token",
    [
        "not-a-token",
        "abc.def",
        f"{uuid.uuid4()}.",
        f".{uuid.uuid4()}",
        f"{uuid.uuid4()}.one.two",
    ],
)
def test_refresh_rejects_malformed_token(client, token):
    response = _refresh(client, token)

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID


@pytest.mark.django_db
def test_refresh_rejects_unknown_session(client, user):
    response = _refresh(client, f"{uuid.uuid4()}.a-secret-value")

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID
    assert AuthSession.objects.count() == 0


@pytest.mark.django_db
def test_refresh_rejects_revoked_session(client, user, login_tokens):
    session = AuthSession.objects.get()
    session.revoked_at = timezone.now()
    session.revoked_reason = AuthSession.RevokedReason.LOGOUT
    session.save(update_fields=["revoked_at", "revoked_reason"])

    response = _refresh(client, login_tokens["refresh_token"])

    assert response.status_code == 401
    assert response.json() == SESSION_REVOKED


@pytest.mark.django_db
def test_refresh_rejects_idle_expired_session(client, user, login_tokens):
    session = AuthSession.objects.get()
    session.expires_at = timezone.now() - timedelta(seconds=1)
    session.save(update_fields=["expires_at"])

    response = _refresh(client, login_tokens["refresh_token"])

    assert response.status_code == 401
    assert response.json() == TOKEN_EXPIRED
    assert AuthSession.objects.get().revoked_at is None


@pytest.mark.django_db
def test_refresh_rejects_absolute_expired_session(client, user, login_tokens):
    session = AuthSession.objects.get()
    session.absolute_expires_at = timezone.now() - timedelta(seconds=1)
    session.save(update_fields=["absolute_expires_at"])

    response = _refresh(client, login_tokens["refresh_token"])

    assert response.status_code == 401
    assert response.json() == TOKEN_EXPIRED


@pytest.mark.django_db
def test_idle_extension_is_capped_by_absolute_expiry(client, user, login_tokens):
    session = AuthSession.objects.get()
    session.absolute_expires_at = timezone.now() + timedelta(days=2)
    session.save(update_fields=["absolute_expires_at"])

    response = _refresh(client, login_tokens["refresh_token"])
    session.refresh_from_db()

    assert response.status_code == 200
    assert session.expires_at == session.absolute_expires_at
    assert session.expires_at - session.last_used_at < timedelta(days=30)


@pytest.mark.django_db
def test_wrong_refresh_secret_revokes_session(client, user, login_tokens):
    session_id, _secret = _split(login_tokens["refresh_token"])
    response = _refresh(client, f"{session_id}.definitely-not-the-secret")
    session = AuthSession.objects.get()

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID
    assert session.revoked_at is not None
    assert session.revoked_reason == AuthSession.RevokedReason.REUSE


@pytest.mark.django_db
def test_refresh_response_never_exposes_sensitive_values(client, user, login_tokens):
    response = _refresh(client, login_tokens["refresh_token"])
    session = AuthSession.objects.get()
    rendered = str(response.json())
    _session_id, new_secret = _split(response.json()["tokens"]["refresh_token"])

    assert "user" not in response.json()
    assert user.password not in rendered
    assert session.refresh_token_hmac not in rendered
    assert session.previous_token_hmac not in rendered
    assert session.current_refresh_secret_ciphertext not in rendered
    assert new_secret != session.current_refresh_secret_ciphertext


@pytest.mark.django_db
def test_multiple_refreshes_produce_unique_secrets_and_jtis(client, user, login_tokens):
    first = _refresh(client, login_tokens["refresh_token"]).json()["tokens"]
    second = _refresh(client, first["refresh_token"]).json()["tokens"]

    assert first["refresh_token"] != second["refresh_token"]
    assert decode_access_token(first["access_token"]).jti != decode_access_token(
        second["access_token"]
    ).jti
    assert AuthSession.objects.count() == 1


@pytest.mark.django_db
def test_encrypt_decrypt_roundtrip_and_wrong_key_fails_safely(settings):
    from apps.authentication.tokens import (
        decrypt_refresh_secret,
        encrypt_refresh_secret,
    )

    secret = "current-refresh-secret"
    ciphertext = encrypt_refresh_secret(secret)

    assert ciphertext != secret
    assert decrypt_refresh_secret(ciphertext) == secret

    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = TEST_OTHER_ENCRYPTION_KEY
    with pytest.raises(Exception):
        decrypt_refresh_secret(ciphertext)


@pytest.mark.django_db
def test_grace_retry_with_wrong_encryption_key_is_generic_failure(
    client, user, login_tokens, settings
):
    _refresh(client, login_tokens["refresh_token"])
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = TEST_OTHER_ENCRYPTION_KEY

    response = _refresh(client, login_tokens["refresh_token"])

    assert response.status_code == 401
    assert response.json() == TOKEN_INVALID
    assert "traceback" not in str(response.json()).lower()


@pytest.mark.django_db
def test_reuse_logging_does_not_include_secrets(client, user, login_tokens, caplog):
    _refresh(client, login_tokens["refresh_token"])
    session = AuthSession.objects.get()
    session.previous_rotated_at = timezone.now() - timedelta(seconds=31)
    session.save(update_fields=["previous_rotated_at"])
    _session_id, old_secret = _split(login_tokens["refresh_token"])

    with caplog.at_level(logging.INFO, logger="promise"):
        _refresh(client, login_tokens["refresh_token"])

    assert old_secret not in caplog.text
    assert session.refresh_token_hmac not in caplog.text
    assert session.current_refresh_secret_ciphertext not in caplog.text
    assert TEST_ENCRYPTION_KEY not in caplog.text


@pytest.mark.django_db(transaction=True)
def test_concurrent_refresh_does_not_double_rotate(user, login_tokens):
    refresh_token = login_tokens["refresh_token"]
    results = []
    barrier = threading.Barrier(2)

    def worker():
        thread_client = APIClient()
        barrier.wait()
        try:
            results.append(_refresh(thread_client, refresh_token))
        finally:
            connection.close()

    threads = [threading.Thread(target=worker) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    statuses = sorted(response.status_code for response in results)
    bodies = [response.json() for response in results if response.status_code == 200]
    session = AuthSession.objects.get()
    refresh_tokens = {body["tokens"]["refresh_token"] for body in bodies}
    access_jtis = {
        decode_access_token(body["tokens"]["access_token"]).jti for body in bodies
    }

    assert statuses == [200, 200]
    assert len(refresh_tokens) == 1
    assert len(access_jtis) == 2
    assert AuthSession.objects.count() == 1
    assert session.revoked_at is None
    _sid, current_secret = _split(next(iter(refresh_tokens)))
    assert refresh_secret_matches(current_secret, session.refresh_token_hmac)
