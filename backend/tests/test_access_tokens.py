import uuid
from datetime import timedelta
from datetime import timezone as dt_timezone

import jwt
import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.authentication.access_tokens import decode_access_token, issue_access_token
from apps.authentication.exceptions import AccessTokenError
from apps.authentication.models import AuthSession
from apps.authentication.tokens import generate_refresh_secret, hash_refresh_secret

User = get_user_model()

TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
TEST_OTHER_SIGNING_KEY = "test-only-other-jwt-signing-key-not-prod"
TEST_KEY_ID = "test-key"


@pytest.fixture(autouse=True)
def jwt_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.JWT_SIGNING_KEY = TEST_SIGNING_KEY
    settings.JWT_SIGNING_KEY_PREVIOUS = ""
    settings.JWT_KEY_ID = TEST_KEY_ID
    settings.JWT_KEY_ID_PREVIOUS = ""
    settings.JWT_ISSUER = "promise-api"
    settings.JWT_AUDIENCE = "promise-client"
    settings.JWT_ALGORITHM = "HS256"
    settings.JWT_ACCESS_TTL_SECONDS = 900


@pytest.fixture
def user(db):
    return User.objects.create_user(
        email="alex@example.com",
        name="Alex",
        password="a-secure-password",
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


def _forge_token(settings, session, user, *, claims=None, omit=(), key=None, kid=None, extra=None):
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
    if extra:
        payload.update(extra)
    for name in omit:
        payload.pop(name, None)
    headers = {"kid": TEST_KEY_ID if kid is None else kid}
    return jwt.encode(
        payload,
        key if key is not None else TEST_SIGNING_KEY,
        algorithm="HS256",
        headers=headers,
    )


def _unverified_payload(token):
    return jwt.decode(
        token,
        options={
            "verify_signature": False,
            "verify_aud": False,
            "verify_exp": False,
            "verify_iss": False,
        },
    )


@pytest.mark.django_db
def test_issue_access_token_contains_required_claims(user, session, settings):
    token = issue_access_token(user, session)
    payload = _unverified_payload(token)
    header = jwt.get_unverified_header(token)

    assert payload["sub"] == str(user.id)
    assert payload["sid"] == str(session.id)
    assert payload["typ"] == "access"
    assert payload["iss"] == "promise-api"
    assert payload["aud"] == "promise-client"
    assert payload["exp"] - payload["iat"] == 900
    uuid.UUID(payload["jti"])
    assert header["alg"] == "HS256"
    assert header["kid"] == TEST_KEY_ID


@pytest.mark.django_db
def test_access_token_jti_is_unique(user, session):
    first = _unverified_payload(issue_access_token(user, session))
    second = _unverified_payload(issue_access_token(user, session))

    assert first["jti"] != second["jti"]


@pytest.mark.django_db
def test_valid_access_token_is_accepted(user, session):
    token = issue_access_token(user, session)
    claims = decode_access_token(token)

    assert claims.user_id == user.id
    assert claims.session_id == session.id
    assert timezone.is_aware(claims.issued_at)
    assert timezone.is_aware(claims.expires_at)
    assert claims.expires_at - claims.issued_at == timedelta(seconds=900)
    assert claims.issued_at.tzinfo == dt_timezone.utc


@pytest.mark.django_db
def test_expired_token_is_rejected(user, session, settings):
    now = int(timezone.now().timestamp())
    token = _forge_token(
        settings,
        session,
        user,
        claims={"iat": now - 2000, "exp": now - 1000},
    )

    with pytest.raises(AccessTokenError):
        decode_access_token(token)


@pytest.mark.django_db
def test_malformed_token_is_rejected():
    with pytest.raises(AccessTokenError):
        decode_access_token("not-a-jwt")


@pytest.mark.django_db
def test_invalid_signature_is_rejected(user, session, settings):
    token = _forge_token(settings, session, user, key=TEST_OTHER_SIGNING_KEY)

    with pytest.raises(AccessTokenError):
        decode_access_token(token)


@pytest.mark.django_db
def test_invalid_issuer_is_rejected(user, session, settings):
    token = _forge_token(settings, session, user, claims={"iss": "other-api"})

    with pytest.raises(AccessTokenError):
        decode_access_token(token)


@pytest.mark.django_db
def test_invalid_audience_is_rejected(user, session, settings):
    token = _forge_token(settings, session, user, claims={"aud": "other-client"})

    with pytest.raises(AccessTokenError):
        decode_access_token(token)


@pytest.mark.django_db
def test_wrong_typ_is_rejected(user, session, settings):
    token = _forge_token(settings, session, user, claims={"typ": "refresh"})

    with pytest.raises(AccessTokenError):
        decode_access_token(token)


@pytest.mark.django_db
def test_missing_required_claim_is_rejected(user, session, settings):
    token = _forge_token(settings, session, user, omit=("sid",))

    with pytest.raises(AccessTokenError):
        decode_access_token(token)


@pytest.mark.django_db
def test_invalid_sub_is_rejected(user, session, settings):
    token = _forge_token(settings, session, user, claims={"sub": "not-a-uuid"})

    with pytest.raises(AccessTokenError):
        decode_access_token(token)


@pytest.mark.django_db
def test_invalid_sid_is_rejected(user, session, settings):
    token = _forge_token(settings, session, user, claims={"sid": "not-a-uuid"})

    with pytest.raises(AccessTokenError):
        decode_access_token(token)


@pytest.mark.django_db
def test_access_token_does_not_contain_sensitive_claims(user, session):
    refresh_secret = generate_refresh_secret()
    token = issue_access_token(user, session)
    payload = _unverified_payload(token)

    assert "password" not in payload
    assert "email" not in payload
    assert "name" not in payload
    assert "roles" not in payload
    assert "permissions" not in payload
    assert "refresh_token" not in payload
    assert refresh_secret not in str(payload)
    assert user.password not in str(payload)
    assert user.email not in str(payload)


@pytest.mark.django_db
def test_access_token_error_does_not_leak_crypto_details(user, session, settings):
    token = _forge_token(settings, session, user, key=TEST_OTHER_SIGNING_KEY)

    with pytest.raises(AccessTokenError) as captured:
        decode_access_token(token)

    message = str(captured.value).lower()
    assert "signature" not in message
    assert TEST_SIGNING_KEY.lower() not in message
    assert token not in str(captured.value)
