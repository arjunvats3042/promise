import uuid
from datetime import timedelta

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient

from apps.authentication.access_tokens import decode_access_token
from apps.authentication.models import AuthSession
from apps.authentication.tokens import refresh_secret_matches

User = get_user_model()

REGISTER_URL = "/api/v1/auth/register/"
LOGIN_URL = "/api/v1/auth/login/"
STRONG_PASSWORD = "correct-horse-battery-staple"
AUTHENTICATION_FAILED = {
    "error": {
        "code": "AUTHENTICATION_FAILED",
        "message": "Invalid email or password.",
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
def existing_user(db):
    return User.objects.create_user(
        email="alex@example.com",
        name="Alex",
        password=STRONG_PASSWORD,
    )


def _register(client, **overrides):
    payload = {
        "email": " User@Example.COM ",
        "password": STRONG_PASSWORD,
        "name": "User Name",
    }
    payload.update(overrides)
    return client.post(REGISTER_URL, payload, format="json")


def _login(client, **overrides):
    payload = {
        "email": "alex@example.com",
        "password": STRONG_PASSWORD,
    }
    payload.update(overrides)
    return client.post(LOGIN_URL, payload, format="json")


def _assert_token_response(response, expected_status):
    assert response.status_code == expected_status
    body = response.json()
    assert set(body) == {"user", "tokens"}
    assert set(body["tokens"]) == {
        "access_token",
        "refresh_token",
        "token_type",
        "expires_in",
    }
    assert body["tokens"]["token_type"] == "Bearer"
    assert body["tokens"]["expires_in"] == 900
    assert body["tokens"]["access_token"]
    assert body["tokens"]["refresh_token"]
    return body


@pytest.mark.django_db
def test_registration_returns_201_and_creates_one_user_and_session(client):
    response = _register(client)
    body = _assert_token_response(response, 201)

    user = User.objects.get()
    session = AuthSession.objects.get()
    assert user.email == "user@example.com"
    assert session.user == user
    assert User.objects.count() == 1
    assert AuthSession.objects.count() == 1
    assert body["user"] == {
        "id": str(user.id),
        "email": "user@example.com",
        "name": "User Name",
        "timezone": "UTC",
        "email_verified": False,
        "has_password": True,
        "google_linked": False,
        "created_at": user.created_at.isoformat().replace("+00:00", "Z"),
    }


@pytest.mark.django_db
def test_registration_hashes_password_with_django_argon2(client):
    _register(client)
    user = User.objects.get()

    assert user.password != STRONG_PASSWORD
    assert user.password.startswith("argon2$")
    assert user.check_password(STRONG_PASSWORD)


@pytest.mark.django_db
def test_registration_refresh_token_matches_session_without_storing_raw_token(client):
    body = _register(client).json()
    session = AuthSession.objects.get()
    session_id, secret = body["tokens"]["refresh_token"].split(".", 1)

    assert uuid.UUID(session_id) == session.id
    assert refresh_secret_matches(secret, session.refresh_token_hmac)
    assert secret != session.refresh_token_hmac
    assert secret not in session.current_refresh_secret_ciphertext
    assert body["tokens"]["refresh_token"] not in {
        session.refresh_token_hmac,
        session.previous_token_hmac,
        session.current_refresh_secret_ciphertext,
    }


@pytest.mark.django_db
def test_registration_access_token_identifies_user_and_session(client):
    body = _register(client).json()
    user = User.objects.get()
    session = AuthSession.objects.get()
    claims = decode_access_token(body["tokens"]["access_token"])

    assert claims.user_id == user.id
    assert claims.session_id == session.id


@pytest.mark.django_db
def test_registration_session_expiries_follow_design(client):
    before = timezone.now()
    _register(client)
    after = timezone.now()
    session = AuthSession.objects.get()

    assert before <= session.last_used_at <= after
    assert session.expires_at - session.last_used_at == timedelta(days=30)
    assert session.absolute_expires_at - session.last_used_at == timedelta(days=90)
    assert session.revoked_at is None


@pytest.mark.django_db
def test_registration_response_never_exposes_sensitive_values(client):
    response = _register(client)
    user = User.objects.get()
    session = AuthSession.objects.get()
    rendered = str(response.json())

    assert STRONG_PASSWORD not in rendered
    assert user.password not in rendered
    assert session.refresh_token_hmac not in rendered
    assert session.current_refresh_secret_ciphertext not in rendered
    assert "password" not in response.json()["user"]
    assert "is_superuser" not in response.json()["user"]


@pytest.mark.django_db
def test_duplicate_email_is_rejected_with_conflict(client):
    _register(client, email="duplicate@example.com")
    response = _register(client, email="duplicate@example.com")

    assert response.status_code == 409
    assert response.json() == {
        "error": {
            "code": "EMAIL_ALREADY_EXISTS",
            "message": "A user with this email already exists.",
        }
    }
    assert User.objects.count() == 1
    assert AuthSession.objects.count() == 1


@pytest.mark.django_db
def test_duplicate_email_with_different_casing_is_rejected(client):
    _register(client, email="duplicate@example.com")
    response = _register(client, email="DUPLICATE@EXAMPLE.COM")

    assert response.status_code == 409
    assert response.json()["error"]["code"] == "EMAIL_ALREADY_EXISTS"
    assert User.objects.count() == 1


@pytest.mark.django_db
def test_registration_rejects_invalid_email(client):
    response = _register(client, email="not-an-email")

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"
    assert "email" in response.json()["error"]["details"]
    assert User.objects.count() == 0


@pytest.mark.django_db
def test_registration_rejects_email_longer_than_model_limit(client):
    long_email = f"{'a' * 64}@{'b' * 63}.{'c' * 63}.{'d' * 62}"
    assert len(long_email) == 255

    response = _register(client, email=long_email)

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"
    assert "email" in response.json()["error"]["details"]
    assert User.objects.count() == 0


@pytest.mark.django_db
@pytest.mark.parametrize("password", ["short", "password", "12345678"])
def test_registration_rejects_invalid_password(client, password):
    response = _register(client, password=password)

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"
    assert "password" in response.json()["error"]["details"]
    assert User.objects.count() == 0


@pytest.mark.django_db
@pytest.mark.parametrize("field", ["email", "password", "name"])
def test_registration_rejects_missing_required_fields(client, field):
    payload = {
        "email": "user@example.com",
        "password": STRONG_PASSWORD,
        "name": "User Name",
    }
    payload.pop(field)

    response = client.post(REGISTER_URL, payload, format="json")

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"
    assert field in response.json()["error"]["details"]
    assert User.objects.count() == 0


@pytest.mark.django_db
def test_successful_login_returns_user_and_creates_one_new_session(client, existing_user):
    response = _login(client)
    body = _assert_token_response(response, 200)

    session = AuthSession.objects.get()
    assert session.user == existing_user
    assert AuthSession.objects.count() == 1
    assert body["user"]["id"] == str(existing_user.id)
    assert body["user"]["email"] == existing_user.email
    assert body["user"]["name"] == existing_user.name


@pytest.mark.django_db
def test_login_is_case_insensitive(client, existing_user):
    response = _login(client, email=" ALEX@EXAMPLE.COM ")

    assert response.status_code == 200
    assert response.json()["user"]["id"] == str(existing_user.id)


@pytest.mark.django_db
def test_login_tokens_correspond_to_created_session(client, existing_user):
    body = _login(client).json()
    session = AuthSession.objects.get()
    session_id, secret = body["tokens"]["refresh_token"].split(".", 1)
    claims = decode_access_token(body["tokens"]["access_token"])

    assert uuid.UUID(session_id) == session.id
    assert refresh_secret_matches(secret, session.refresh_token_hmac)
    assert session.current_refresh_secret_ciphertext
    assert secret not in session.current_refresh_secret_ciphertext
    assert claims.user_id == existing_user.id
    assert claims.session_id == session.id


@pytest.mark.django_db
def test_login_response_never_exposes_sensitive_values(client, existing_user):
    response = _login(client)
    session = AuthSession.objects.get()
    rendered = str(response.json())

    assert STRONG_PASSWORD not in rendered
    assert existing_user.password not in rendered
    assert session.refresh_token_hmac not in rendered
    assert session.current_refresh_secret_ciphertext not in rendered
    assert "password" not in response.json()["user"]
    assert "is_superuser" not in response.json()["user"]


@pytest.mark.django_db
def test_wrong_password_is_rejected_generically(client, existing_user):
    response = _login(client, password="wrong-password")

    assert response.status_code == 401
    assert response.json() == AUTHENTICATION_FAILED
    assert AuthSession.objects.count() == 0


@pytest.mark.django_db
def test_nonexistent_email_is_rejected_with_same_error(client):
    response = _login(client, email="missing@example.com")

    assert response.status_code == 401
    assert response.json() == AUTHENTICATION_FAILED
    assert AuthSession.objects.count() == 0


@pytest.mark.django_db
def test_inactive_user_is_rejected_with_same_error(client, existing_user):
    existing_user.is_active = False
    existing_user.save(update_fields=["is_active"])

    response = _login(client)

    assert response.status_code == 401
    assert response.json() == AUTHENTICATION_FAILED
    assert AuthSession.objects.count() == 0


@pytest.mark.django_db
def test_login_failure_does_not_reveal_account_existence(client, existing_user):
    wrong_password = _login(client, password="wrong-password")
    missing_email = _login(client, email="missing@example.com")
    existing_user.is_active = False
    existing_user.save(update_fields=["is_active"])
    inactive = _login(client)

    assert wrong_password.status_code == missing_email.status_code == inactive.status_code == 401
    assert wrong_password.json() == missing_email.json() == inactive.json()
