import pytest
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient

from apps.core.redis import increment_rate_limit
from apps.users.rate_limits import (
    USER_LOOKUP_USER_LIMIT,
    USER_LOOKUP_USER_WINDOW,
    user_lookup_user_key,
)

User = get_user_model()

LOOKUP_URL = "/api/v1/users/lookup/"
LOGIN_URL = "/api/v1/auth/login/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
LOOKUP_FIELDS = {"id", "name", "email"}
UNAUTHENTICATED = {
    "error": {
        "code": "UNAUTHENTICATED",
        "message": "Authentication credentials were not provided.",
    }
}


@pytest.fixture(autouse=True)
def auth_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = (
        "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
    )
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


@pytest.mark.django_db
def test_lookup_requires_authentication(client, rahul):
    response = client.get(LOOKUP_URL, {"email": rahul.email})
    assert response.status_code == 401
    assert response.json() == UNAUTHENTICATED


@pytest.mark.django_db
def test_lookup_success_normalized_email(client, arjun, rahul):
    _auth(client, _token(client, arjun.email))
    response = client.get(LOOKUP_URL, {"email": "  Rahul@Example.COM "})

    assert response.status_code == 200
    body = response.json()
    assert set(body) == LOOKUP_FIELDS
    assert body["id"] == str(rahul.id)
    assert body["name"] == "Rahul"
    assert body["email"] == "rahul@example.com"
    assert "timezone" not in body
    assert "password" not in body
    assert "is_staff" not in body
    assert "created_at" not in body


@pytest.mark.django_db
def test_lookup_missing_user_is_safe_404(client, arjun):
    _auth(client, _token(client, arjun.email))
    response = client.get(LOOKUP_URL, {"email": "nobody@example.com"})

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "USER_NOT_FOUND"
    assert response.json()["error"]["message"] == "User was not found."


@pytest.mark.django_db
def test_lookup_inactive_user_is_not_found(client, arjun, rahul):
    rahul.is_active = False
    rahul.save(update_fields=["is_active"])
    _auth(client, _token(client, arjun.email))
    response = client.get(LOOKUP_URL, {"email": rahul.email})

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "USER_NOT_FOUND"


@pytest.mark.django_db
def test_lookup_requires_email_query(client, arjun):
    _auth(client, _token(client, arjun.email))
    response = client.get(LOOKUP_URL)

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


@pytest.mark.django_db
def test_lookup_rejects_invalid_email(client, arjun):
    _auth(client, _token(client, arjun.email))
    response = client.get(LOOKUP_URL, {"email": "not-an-email"})

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


@pytest.mark.django_db
def test_lookup_rate_limited_per_user(client, arjun, rahul):
    _auth(client, _token(client, arjun.email))
    for _ in range(USER_LOOKUP_USER_LIMIT):
        increment_rate_limit(user_lookup_user_key(arjun.id), USER_LOOKUP_USER_WINDOW)

    response = client.get(LOOKUP_URL, {"email": rahul.email})
    assert response.status_code == 429
    assert response.json()["error"]["code"] == "RATE_LIMITED"
    assert response.headers.get("Retry-After") is not None


@pytest.mark.django_db
def test_lookup_does_not_log_email(client, arjun, rahul, caplog):
    import logging

    _auth(client, _token(client, arjun.email))
    with caplog.at_level(logging.DEBUG):
        response = client.get(LOOKUP_URL, {"email": rahul.email})
    assert response.status_code == 200
    joined = " ".join(record.getMessage() for record in caplog.records)
    assert rahul.email not in joined
    assert "rahul@example.com" not in joined.lower()
