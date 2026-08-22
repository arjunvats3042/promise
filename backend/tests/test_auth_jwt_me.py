import uuid
from datetime import timedelta

import jwt
import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient, APIRequestFactory

from apps.authentication.access_tokens import issue_access_token
from apps.authentication.models import AuthSession
from apps.authentication.tokens import generate_refresh_secret, hash_refresh_secret

User = get_user_model()

ME_URL = "/api/v1/auth/me/"
REGISTER_URL = "/api/v1/auth/register/"
LOGIN_URL = "/api/v1/auth/login/"
HEALTH_URL = "/api/v1/health/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
TEST_OTHER_SIGNING_KEY = "test-only-other-jwt-signing-key-not-prod"
TEST_KEY_ID = "test-key"
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
def jwt_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = (
        "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
    )
    settings.JWT_SIGNING_KEY = TEST_SIGNING_KEY
    settings.JWT_SIGNING_KEY_PREVIOUS = ""
    settings.JWT_KEY_ID = TEST_KEY_ID
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
def session(user):
    now = timezone.now()
    return AuthSession.objects.create(
        user=user,
        refresh_token_hmac=hash_refresh_secret(generate_refresh_secret()),
        last_used_at=now,
        expires_at=now + timedelta(days=30),
        absolute_expires_at=now + timedelta(days=90),
    )


@pytest.fixture
def access_token(user, session):
    return issue_access_token(user, session)


def _forge_token(settings, session, user, *, claims=None, omit=(), key=None, kid=None):
    now = int(timezone.now().timestamp())
    payload = {
        "sub": str(user.id),
        "sid": str(session.id),
        "jti": str(uuid.uuid4()),
        "iat": now,
        "exp": now + 900,
        "iss": settings.JWT_ISSUER,
        "aud": settings.JWT_AUDIENCE,
        "typ": "access",
    }
    if claims:
        payload.update(claims)
    for name in omit:
        payload.pop(name, None)
    return jwt.encode(
        payload,
        key if key is not None else TEST_SIGNING_KEY,
        algorithm="HS256",
        headers={"kid": TEST_KEY_ID if kid is None else kid},
    )


def _me(client, token=None, authorization=None):
    headers = {}
    if authorization is not None:
        headers["HTTP_AUTHORIZATION"] = authorization
    elif token is not None:
        headers["HTTP_AUTHORIZATION"] = f"Bearer {token}"
    return client.get(ME_URL, **headers)


def _assert_generic_unauthenticated(response, expected):
    assert response.status_code == 401
    assert response.json() == expected
    rendered = str(response.json()).lower()
    assert "signature" not in rendered
    assert TEST_SIGNING_KEY.lower() not in rendered
    assert "traceback" not in rendered


@pytest.mark.django_db
def test_me_without_authorization_header_returns_401(client, user, session):
    response = _me(client)

    _assert_generic_unauthenticated(response, UNAUTHENTICATED_MISSING)


@pytest.mark.django_db
@pytest.mark.parametrize(
    "authorization",
    [
        "Bearer",
        "Bearer token extra",
        "NotBearer sometoken",
        "Bearer ",
        "Token abc",
    ],
)
def test_me_with_malformed_authorization_header_returns_401(client, authorization):
    response = _me(client, authorization=authorization)

    _assert_generic_unauthenticated(response, UNAUTHENTICATED_INVALID)


@pytest.mark.django_db
def test_me_with_invalid_jwt_returns_401(client):
    response = _me(client, token="not-a-jwt")

    _assert_generic_unauthenticated(response, UNAUTHENTICATED_INVALID)


@pytest.mark.django_db
def test_me_with_expired_jwt_returns_401(client, user, session, settings):
    now = int(timezone.now().timestamp())
    token = _forge_token(
        settings,
        session,
        user,
        claims={"iat": now - 2000, "exp": now - 1000},
    )

    response = _me(client, token=token)

    _assert_generic_unauthenticated(response, UNAUTHENTICATED_INVALID)


@pytest.mark.django_db
def test_me_with_wrong_issuer_returns_401(client, user, session, settings):
    token = _forge_token(settings, session, user, claims={"iss": "other-api"})

    _assert_generic_unauthenticated(
        _me(client, token=token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_me_with_wrong_audience_returns_401(client, user, session, settings):
    token = _forge_token(settings, session, user, claims={"aud": "other-client"})

    _assert_generic_unauthenticated(
        _me(client, token=token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_me_with_wrong_token_type_returns_401(client, user, session, settings):
    token = _forge_token(settings, session, user, claims={"typ": "refresh"})

    _assert_generic_unauthenticated(
        _me(client, token=token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_me_with_invalid_signature_returns_401(client, user, session, settings):
    token = _forge_token(settings, session, user, key=TEST_OTHER_SIGNING_KEY)

    _assert_generic_unauthenticated(
        _me(client, token=token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_me_with_nonexistent_user_returns_401(client, user, session, settings):
    token = _forge_token(
        settings,
        session,
        user,
        claims={"sub": str(uuid.uuid4())},
    )

    _assert_generic_unauthenticated(
        _me(client, token=token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_me_with_inactive_user_returns_401(client, user, session, access_token):
    user.is_active = False
    user.save(update_fields=["is_active"])

    _assert_generic_unauthenticated(
        _me(client, token=access_token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_me_with_nonexistent_session_returns_401(client, user, session, access_token):
    session.delete()

    _assert_generic_unauthenticated(
        _me(client, token=access_token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_me_with_revoked_session_returns_401(client, user, session, access_token):
    session.revoked_at = timezone.now()
    session.revoked_reason = AuthSession.RevokedReason.LOGOUT
    session.save(update_fields=["revoked_at", "revoked_reason"])

    _assert_generic_unauthenticated(
        _me(client, token=access_token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_me_with_expired_session_returns_401(client, user, session, access_token):
    session.expires_at = timezone.now() - timedelta(seconds=1)
    session.save(update_fields=["expires_at"])

    _assert_generic_unauthenticated(
        _me(client, token=access_token),
        UNAUTHENTICATED_INVALID,
    )


@pytest.mark.django_db
def test_authenticate_returns_user_and_session_context(user, session, access_token):
    from apps.authentication.authentication import (
        AccessAuthenticationContext,
        JWTAccessAuthentication,
    )

    factory = APIRequestFactory()
    request = factory.get(ME_URL, HTTP_AUTHORIZATION=f"Bearer {access_token}")

    authenticated_user, context = JWTAccessAuthentication().authenticate(request)

    assert authenticated_user == user
    assert isinstance(context, AccessAuthenticationContext)
    assert context.session_id == session.id
    assert context.token_id
    assert not hasattr(context, "refresh_token")
    assert not hasattr(context, "refresh_token_hmac")


@pytest.mark.django_db
def test_me_with_valid_token_returns_current_user(client, user, session, access_token):
    response = _me(client, token=access_token)

    assert response.status_code == 200
    assert response.json() == {
        "user": {
            "id": str(user.id),
            "email": user.email,
            "name": user.name,
            "timezone": user.timezone,
            "email_verified": False,
            "has_password": True,
            "google_linked": False,
            "created_at": user.created_at.isoformat().replace("+00:00", "Z"),
        }
    }


@pytest.mark.django_db
def test_me_response_never_exposes_sensitive_values(client, user, session, access_token):
    response = _me(client, token=access_token)
    rendered = str(response.json())

    assert response.status_code == 200
    assert set(response.json()) == {"user"}
    assert STRONG_PASSWORD not in rendered
    assert user.password not in rendered
    assert session.refresh_token_hmac not in rendered
    assert "password" not in response.json()["user"]
    assert "is_superuser" not in response.json()["user"]
    assert "refresh_token" not in rendered
    assert "tokens" not in response.json()


@pytest.mark.django_db
def test_health_remains_public_without_authentication(client):
    response = client.get(HEALTH_URL)

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@pytest.mark.django_db
def test_register_remains_public_without_authentication(client):
    response = client.post(
        REGISTER_URL,
        {
            "email": "public@example.com",
            "password": STRONG_PASSWORD,
            "name": "Public User",
        },
        format="json",
    )

    assert response.status_code == 201
    assert response.json()["user"]["email"] == "public@example.com"


@pytest.mark.django_db
def test_login_remains_public_without_authentication(client, user):
    response = client.post(
        LOGIN_URL,
        {"email": user.email, "password": STRONG_PASSWORD},
        format="json",
    )

    assert response.status_code == 200
    assert response.json()["user"]["id"] == str(user.id)
