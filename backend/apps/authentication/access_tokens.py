"""Issue and verify short-lived access JWTs (HS256).

Refresh tokens are not JWTs; see tokens.py.
"""

import uuid
from dataclasses import dataclass
from datetime import datetime
from datetime import timezone as dt_timezone

import jwt
from django.conf import settings

from apps.authentication.exceptions import AccessTokenError

REQUIRED_CLAIMS = ("sub", "sid", "jti", "iat", "exp", "iss", "aud", "typ")


@dataclass(frozen=True)
class AccessTokenClaims:
    user_id: uuid.UUID
    session_id: uuid.UUID
    jti: str
    issued_at: datetime
    expires_at: datetime


def issue_access_token(user, session):
    """Return a signed access JWT for user + AuthSession. Does not persist."""
    signing_key = settings.JWT_SIGNING_KEY
    if not signing_key:
        raise RuntimeError("JWT_SIGNING_KEY is not configured.")
    if session.user_id != user.id:
        raise AccessTokenError()

    now = datetime.now(tz=dt_timezone.utc)
    iat = int(now.timestamp())
    payload = {
        "sub": str(user.id),
        "sid": str(session.id),
        "jti": str(uuid.uuid4()),
        "iat": iat,
        "exp": iat + settings.JWT_ACCESS_TTL_SECONDS,
        "iss": settings.JWT_ISSUER,
        "aud": settings.JWT_AUDIENCE,
        "typ": "access",
    }
    return jwt.encode(
        payload,
        signing_key,
        algorithm=settings.JWT_ALGORITHM,
        headers={"kid": settings.JWT_KEY_ID},
    )


def decode_access_token(token):
    """Verify an access JWT and return sub/sid identity claims.

    Does not load User/AuthSession or authorize resources.
    """
    key = _verification_key(token)
    try:
        payload = jwt.decode(
            token,
            key,
            algorithms=[settings.JWT_ALGORITHM],
            issuer=settings.JWT_ISSUER,
            audience=settings.JWT_AUDIENCE,
            leeway=settings.JWT_LEEWAY_SECONDS,
            options={"require": list(REQUIRED_CLAIMS)},
        )
    except jwt.InvalidTokenError:
        raise AccessTokenError() from None

    if payload.get("typ") != "access":
        raise AccessTokenError()

    user_id = _parse_uuid(payload.get("sub"))
    session_id = _parse_uuid(payload.get("sid"))
    jti = payload.get("jti")
    if not jti or not isinstance(jti, str):
        raise AccessTokenError()

    return AccessTokenClaims(
        user_id=user_id,
        session_id=session_id,
        jti=jti,
        issued_at=_aware_utc(payload["iat"]),
        expires_at=_aware_utc(payload["exp"]),
    )


def _verification_key(token):
    try:
        header = jwt.get_unverified_header(token)
    except jwt.InvalidTokenError:
        raise AccessTokenError() from None

    kid = header.get("kid")
    keys = {settings.JWT_KEY_ID: settings.JWT_SIGNING_KEY}
    previous_id = settings.JWT_KEY_ID_PREVIOUS
    previous_key = settings.JWT_SIGNING_KEY_PREVIOUS
    if previous_id and previous_key:
        keys[previous_id] = previous_key

    key = keys.get(kid)
    if not key:
        raise AccessTokenError()
    return key


def _parse_uuid(value):
    try:
        return uuid.UUID(str(value))
    except (ValueError, AttributeError, TypeError):
        raise AccessTokenError() from None


def _aware_utc(timestamp):
    return datetime.fromtimestamp(timestamp, tz=dt_timezone.utc)
