"""Authentication rate-limit buckets (Redis counters; fail open)."""

import hashlib
import uuid

from apps.authentication.models import AuthSession
from apps.authentication.services import canonicalize_email
from apps.core.redis import increment_rate_limit
from config.exceptions import RateLimitedError

LOGIN_IP_WINDOW = 900
LOGIN_IP_LIMIT = 20
LOGIN_EMAIL_WINDOW = 900
LOGIN_EMAIL_LIMIT = 10
REGISTER_IP_WINDOW = 3600
REGISTER_IP_LIMIT = 5
REGISTER_EMAIL_WINDOW = 3600
REGISTER_EMAIL_LIMIT = 10
REFRESH_SESSION_WINDOW = 900
REFRESH_SESSION_LIMIT = 30
REFRESH_USER_WINDOW = 900
REFRESH_USER_LIMIT = 120
REFRESH_FALLBACK_WINDOW = 900
REFRESH_FALLBACK_LIMIT = 30


def client_ip_identifier(request):
    ip = request.META.get("REMOTE_ADDR") or "unknown"
    if not isinstance(ip, str) or not ip or "@" in ip or any(character.isspace() for character in ip):
        return "unknown"
    return ip


def email_rate_limit_hash(email):
    normalized = canonicalize_email(email)
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def login_ip_key(ip):
    return f"promise:ratelimit:login:ip:{ip}"


def login_email_key(email):
    return f"promise:ratelimit:login:email:{email_rate_limit_hash(email)}"


def register_ip_key(ip):
    return f"promise:ratelimit:register:ip:{ip}"


def register_email_key(email):
    return f"promise:ratelimit:register:email:{email_rate_limit_hash(email)}"


def refresh_session_key(session_id):
    return f"promise:ratelimit:refresh:session:{session_id}"


def refresh_user_key(user_id):
    return f"promise:ratelimit:refresh:user:{user_id}"


def refresh_fallback_ip_key(ip):
    return f"promise:ratelimit:refresh:ip:{ip}"


def enforce_login_rate_limits(request, email):
    ip = client_ip_identifier(request)
    _enforce(
        [
            (login_ip_key(ip), LOGIN_IP_WINDOW, LOGIN_IP_LIMIT),
            (login_email_key(email), LOGIN_EMAIL_WINDOW, LOGIN_EMAIL_LIMIT),
        ]
    )


def enforce_register_rate_limits(request, email):
    ip = client_ip_identifier(request)
    _enforce(
        [
            (register_ip_key(ip), REGISTER_IP_WINDOW, REGISTER_IP_LIMIT),
            (register_email_key(email), REGISTER_EMAIL_WINDOW, REGISTER_EMAIL_LIMIT),
        ]
    )


def enforce_refresh_rate_limits(request, refresh_token):
    ip = client_ip_identifier(request)
    session_id = _session_id_from_refresh_token(refresh_token)
    user_id = None
    if session_id is not None:
        user_id = (
            AuthSession.objects.filter(id=session_id)
            .values_list("user_id", flat=True)
            .first()
        )
    if user_id is None:
        _enforce(
            [
                (
                    refresh_fallback_ip_key(ip),
                    REFRESH_FALLBACK_WINDOW,
                    REFRESH_FALLBACK_LIMIT,
                )
            ]
        )
        return
    _enforce(
        [
            (refresh_session_key(session_id), REFRESH_SESSION_WINDOW, REFRESH_SESSION_LIMIT),
            (refresh_user_key(user_id), REFRESH_USER_WINDOW, REFRESH_USER_LIMIT),
        ]
    )


def _session_id_from_refresh_token(refresh_token):
    if not refresh_token or "." not in refresh_token:
        return None
    parts = refresh_token.split(".")
    if len(parts) != 2 or not parts[0] or not parts[1]:
        return None
    try:
        return uuid.UUID(parts[0])
    except (ValueError, TypeError, AttributeError):
        return None


def _enforce(buckets):
    retry_after = None
    for key, window, limit in buckets:
        result = increment_rate_limit(key, window)
        if result["count"] > limit:
            wait = result["ttl"] if result["ttl"] > 0 else window
            if retry_after is None or wait > retry_after:
                retry_after = wait
    if retry_after is not None:
        raise RateLimitedError(retry_after=retry_after)
