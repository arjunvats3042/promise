"""Refresh-token secret generation, HMAC verification, and grace encryption.

Opaque refresh tokens are `{session_id}.{secret}`. HMAC-SHA256 of the secret
(with AUTH_REFRESH_TOKEN_PEPPER) is the canonical stored verifier. The current
secret is also Fernet-encrypted with AUTH_REFRESH_TOKEN_ENCRYPTION_KEY for
30-second retry-grace recovery only. Never persist or log the raw secret,
composed token, ciphertext, or encryption key.
"""

import hashlib
import hmac
import secrets

from cryptography.fernet import Fernet
from cryptography.fernet import InvalidToken
from django.conf import settings

from apps.authentication.exceptions import RefreshSecretDecryptionError

REFRESH_SECRET_BYTES = 32


def generate_refresh_secret():
    """Return a URL-safe secret with at least 256 bits of entropy."""
    return secrets.token_urlsafe(REFRESH_SECRET_BYTES)


def compose_refresh_token(session_id, secret):
    """Compose the client token without persisting either combined value."""
    return f"{session_id}.{secret}"


def hash_refresh_secret(secret):
    """HMAC-SHA256 hex digest of the refresh secret using the server pepper."""
    pepper = settings.AUTH_REFRESH_TOKEN_PEPPER
    if not pepper:
        raise RuntimeError("AUTH_REFRESH_TOKEN_PEPPER is not configured.")
    return hmac.new(
        pepper.encode("utf-8"),
        secret.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def refresh_secret_matches(secret, stored_hmac):
    """Constant-time compare of a presented secret against a stored HMAC."""
    if not stored_hmac:
        return False
    return hmac.compare_digest(hash_refresh_secret(secret), stored_hmac)


def encrypt_refresh_secret(secret):
    """Return Fernet ciphertext of the current refresh secret."""
    return _refresh_secret_fernet().encrypt(secret.encode("utf-8")).decode("ascii")


def decrypt_refresh_secret(ciphertext):
    """Return the current refresh secret from Fernet ciphertext."""
    if not ciphertext:
        raise RefreshSecretDecryptionError()
    try:
        return _refresh_secret_fernet().decrypt(ciphertext.encode("ascii")).decode("utf-8")
    except (InvalidToken, ValueError, TypeError, UnicodeError):
        raise RefreshSecretDecryptionError() from None


def _refresh_secret_fernet():
    key = settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY
    if not key:
        raise RuntimeError("AUTH_REFRESH_TOKEN_ENCRYPTION_KEY is not configured.")
    try:
        return Fernet(key.encode("ascii"))
    except (ValueError, TypeError) as exc:
        raise RuntimeError("AUTH_REFRESH_TOKEN_ENCRYPTION_KEY is invalid.") from exc
