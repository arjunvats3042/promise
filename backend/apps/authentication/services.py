from dataclasses import dataclass
from datetime import timedelta
import logging
import uuid

from django.contrib.auth import get_user_model
from django.contrib.auth.hashers import check_password, make_password
from django.db import IntegrityError, transaction
from django.utils import timezone

from apps.authentication.access_tokens import issue_access_token
from apps.authentication.denylist import deny_access_session
from apps.authentication.exceptions import (
    AuthenticationCredentialsError,
    EmailAlreadyExistsError,
    RefreshSecretDecryptionError,
    RefreshSessionRevokedError,
    RefreshTokenExpiredError,
    RefreshTokenInvalidError,
)
from apps.authentication.models import AuthSession
from apps.authentication.tokens import (
    compose_refresh_token,
    decrypt_refresh_secret,
    encrypt_refresh_secret,
    generate_refresh_secret,
    hash_refresh_secret,
    refresh_secret_matches,
)

User = get_user_model()
logger = logging.getLogger("promise")

REFRESH_IDLE_LIFETIME = timedelta(days=30)
SESSION_ABSOLUTE_LIFETIME = timedelta(days=90)
REFRESH_REUSE_GRACE = timedelta(seconds=30)
_DUMMY_PASSWORD_HASH = make_password("internal-dummy-password")


@dataclass(frozen=True)
class AuthenticationResult:
    user: User
    session: AuthSession
    access_token: str
    refresh_token: str


def canonicalize_email(email):
    return email.strip().lower()


def register_user(*, email, password, name):
    email = canonicalize_email(email)
    if User.objects.filter(email__iexact=email).exists():
        raise EmailAlreadyExistsError()

    with transaction.atomic():
        try:
            with transaction.atomic():
                user = User.objects.create_user(
                    email=email,
                    password=password,
                    name=name,
                )
        except IntegrityError:
            raise EmailAlreadyExistsError() from None

        return _create_session_and_tokens(user)


def login_user(*, email, password):
    email = canonicalize_email(email)
    user = User.objects.filter(email__iexact=email).first()

    if user is None:
        check_password(password, _DUMMY_PASSWORD_HASH)
        raise AuthenticationCredentialsError()

    password_matches = user.check_password(password)
    if not password_matches or not user.is_active:
        raise AuthenticationCredentialsError()

    with transaction.atomic():
        return _create_session_and_tokens(user)


def refresh_tokens(*, refresh_token):
    session_id, secret = _parse_refresh_token(refresh_token)
    reused_session_id = None
    with transaction.atomic():
        session = (
            AuthSession.objects.select_related("user")
            .select_for_update()
            .filter(id=session_id)
            .first()
        )
        if session is None:
            raise RefreshTokenInvalidError()
        if session.is_revoked():
            raise RefreshSessionRevokedError()
        if session.is_expired():
            raise RefreshTokenExpiredError()
        if not session.user.is_active:
            raise RefreshTokenInvalidError()

        if refresh_secret_matches(secret, session.refresh_token_hmac):
            return _rotate_refresh_token(session)

        if refresh_secret_matches(secret, session.previous_token_hmac):
            if _within_reuse_grace(session):
                return _grace_retry(session)

        _revoke_for_reuse(session)
        reused_session_id = session.id

    if reused_session_id is not None:
        deny_access_session(reused_session_id)
    raise RefreshTokenInvalidError()


def logout_session(*, user, refresh_token, device_id=None):
    session_id, secret = _parse_refresh_token(refresh_token)
    with transaction.atomic():
        session = (
            AuthSession.objects.select_for_update()
            .filter(id=session_id, user=user)
            .first()
        )
        if session is None:
            raise RefreshTokenInvalidError()
        if not (
            refresh_secret_matches(secret, session.refresh_token_hmac)
            or refresh_secret_matches(secret, session.previous_token_hmac)
        ):
            raise RefreshTokenInvalidError()
        if not session.is_revoked():
            session.revoked_at = timezone.now()
            session.revoked_reason = AuthSession.RevokedReason.LOGOUT
            session.save(update_fields=["revoked_at", "revoked_reason", "updated_at"])
        if device_id:
            from apps.notifications.models import UserDevice
            UserDevice.objects.filter(user=user, device_id=device_id).update(is_active=False)
    deny_access_session(session_id)


def logout_all_sessions(*, user):
    now = timezone.now()
    with transaction.atomic():
        session_ids = list(
            AuthSession.objects.select_for_update()
            .filter(user=user, revoked_at__isnull=True)
            .values_list("id", flat=True)
        )
        if session_ids:
            AuthSession.objects.filter(id__in=session_ids).update(
                revoked_at=now,
                revoked_reason=AuthSession.RevokedReason.LOGOUT_ALL,
                updated_at=now,
            )
        from apps.notifications.models import UserDevice
        UserDevice.objects.filter(user=user).update(is_active=False)
    for session_id in session_ids:
        deny_access_session(session_id)


def _create_session_and_tokens(user):
    now = timezone.now()
    refresh_secret = generate_refresh_secret()
    session = AuthSession.objects.create(
        user=user,
        refresh_token_hmac=hash_refresh_secret(refresh_secret),
        current_refresh_secret_ciphertext=encrypt_refresh_secret(refresh_secret),
        last_used_at=now,
        expires_at=now + REFRESH_IDLE_LIFETIME,
        absolute_expires_at=now + SESSION_ABSOLUTE_LIFETIME,
    )
    access_token = issue_access_token(user, session)
    refresh_token = compose_refresh_token(session.id, refresh_secret)
    return AuthenticationResult(
        user=user,
        session=session,
        access_token=access_token,
        refresh_token=refresh_token,
    )


def _rotate_refresh_token(session):
    now = timezone.now()
    new_secret = generate_refresh_secret()
    session.previous_token_hmac = session.refresh_token_hmac
    session.previous_rotated_at = now
    session.refresh_token_hmac = hash_refresh_secret(new_secret)
    session.current_refresh_secret_ciphertext = encrypt_refresh_secret(new_secret)
    session.last_used_at = now
    session.expires_at = min(now + REFRESH_IDLE_LIFETIME, session.absolute_expires_at)
    session.save(
        update_fields=[
            "previous_token_hmac",
            "previous_rotated_at",
            "refresh_token_hmac",
            "current_refresh_secret_ciphertext",
            "last_used_at",
            "expires_at",
            "updated_at",
        ]
    )
    return AuthenticationResult(
        user=session.user,
        session=session,
        access_token=issue_access_token(session.user, session),
        refresh_token=compose_refresh_token(session.id, new_secret),
    )


def _grace_retry(session):
    try:
        current_secret = decrypt_refresh_secret(session.current_refresh_secret_ciphertext)
    except RefreshSecretDecryptionError:
        raise RefreshTokenInvalidError() from None
    return AuthenticationResult(
        user=session.user,
        session=session,
        access_token=issue_access_token(session.user, session),
        refresh_token=compose_refresh_token(session.id, current_secret),
    )


def _revoke_for_reuse(session):
    session.revoked_at = timezone.now()
    session.revoked_reason = AuthSession.RevokedReason.REUSE
    session.save(update_fields=["revoked_at", "revoked_reason", "updated_at"])
    logger.warning(
        "Refresh token reuse detected session_id=%s user_id=%s",
        session.id,
        session.user_id,
    )


def _within_reuse_grace(session):
    if session.previous_rotated_at is None:
        return False
    return timezone.now() - session.previous_rotated_at <= REFRESH_REUSE_GRACE


def _parse_refresh_token(refresh_token):
    if not refresh_token or "." not in refresh_token:
        raise RefreshTokenInvalidError()
    parts = refresh_token.split(".")
    if len(parts) != 2 or not parts[0] or not parts[1]:
        raise RefreshTokenInvalidError()
    try:
        session_id = uuid.UUID(parts[0])
    except ValueError:
        raise RefreshTokenInvalidError() from None
    return session_id, parts[1]
