import logging
from typing import Any, Dict, List, Optional
from django.conf import settings
import jwt
from jwt import PyJWKClient

from apps.authentication.exceptions import (
    AuthenticationCredentialsError,
)

logger = logging.getLogger("promise")

GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"]

_jwk_client: Optional[PyJWKClient] = None


def get_jwk_client() -> PyJWKClient:
    global _jwk_client
    if _jwk_client is None:
        _jwk_client = PyJWKClient(GOOGLE_JWKS_URL, cache_keys=True, max_cached_keys=10)
    return _jwk_client


def get_allowed_audiences() -> List[str]:
    audiences = getattr(settings, "GOOGLE_OAUTH_CLIENT_IDS", [])
    if isinstance(audiences, str):
        audiences = [a.strip() for a in audiences.split(",") if a.strip()]
    web_id = getattr(settings, "GOOGLE_CLIENT_ID_WEB", "")
    android_id = getattr(settings, "GOOGLE_CLIENT_ID_ANDROID", "")
    res = list(audiences)
    if web_id and web_id not in res:
        res.append(web_id)
    if android_id and android_id not in res:
        res.append(android_id)
    return res


def verify_google_id_token(id_token_str: str) -> Dict[str, Any]:
    """Cryptographically verifies a Google ID Token (JWT).
    
    Verifies:
    - Signature with Google's public certs (RS256)
    - Issuer is accounts.google.com
    - Audience matches configured OAuth Client IDs (if configured)
    - Token is not expired
    - sub (subject), email, and email_verified are present and valid
    """
    if not id_token_str or not isinstance(id_token_str, str):
        raise AuthenticationCredentialsError("Invalid Google ID token format.")

    try:
        jwk_client = get_jwk_client()
        signing_key = jwk_client.get_signing_key_from_jwt(id_token_str)
        allowed_audiences = get_allowed_audiences()

        options = {
            "verify_signature": True,
            "verify_exp": True,
            "verify_iat": True,
            "verify_aud": bool(allowed_audiences),
            "require": ["sub", "email", "iss", "exp"],
        }

        decode_kwargs = {
            "algorithms": ["RS256"],
            "options": options,
        }
        if allowed_audiences:
            decode_kwargs["audience"] = allowed_audiences

        claims = jwt.decode(
            id_token_str,
            signing_key.key,
            **decode_kwargs,
        )
    except Exception as exc:
        logger.warning("Google ID token verification failed: %s", type(exc).__name__)
        raise AuthenticationCredentialsError("Google token verification failed.") from exc

    issuer = claims.get("iss")
    if issuer not in GOOGLE_ISSUERS:
        logger.warning("Invalid Google token issuer: %s", issuer)
        raise AuthenticationCredentialsError("Invalid Google token issuer.")

    email_verified = claims.get("email_verified")
    if email_verified is not True and email_verified != "true":
        logger.warning("Google email is unverified for sub=%s", claims.get("sub"))
        raise AuthenticationCredentialsError("Google email is not verified.")

    sub = claims.get("sub")
    email = claims.get("email")
    if not sub or not email:
        raise AuthenticationCredentialsError("Missing subject or email in Google token.")

    return claims
