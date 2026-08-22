from dataclasses import dataclass
from datetime import timedelta
import logging
import uuid

from django.contrib.auth import get_user_model
from django.contrib.auth.hashers import check_password, make_password
from django.db import IntegrityError, transaction
from django.utils import timezone

import hashlib
import secrets

from apps.authentication.access_tokens import issue_access_token
from apps.authentication.denylist import deny_access_session
from apps.authentication.exceptions import (
    AuthenticationCredentialsError,
    EmailAlreadyExistsError,
    EmailVerificationRequiredForLinkingError,
    InvalidCurrentPasswordError,
    InvalidOrExpiredTokenError,
    PasswordAlreadySetError,
    PasswordRequiredToUnlinkError,
    RefreshSecretDecryptionError,
    RefreshSessionRevokedError,
    RefreshTokenExpiredError,
    RefreshTokenInvalidError,
)
from apps.authentication.google import verify_google_id_token
from apps.authentication.models import (
    AuthSession,
    EmailChangeToken,
    EmailVerificationToken,
    PasswordResetToken,
    SecurityEvent,
)
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


def record_security_event(user, event_type, *, device_name="", platform="unknown", ip_address=None, user_agent="", metadata=None):
    """Records a lightweight, privacy-sanitized security event."""
    try:
        clean_metadata = {k: v for k, v in (metadata or {}).items() if not any(s in k.lower() for s in ["password", "token", "secret", "fcm", "jwt", "key"])}
        return SecurityEvent.objects.create(
            user=user,
            event_type=event_type,
            device_name=device_name or "",
            platform=platform if platform in ["android", "ios", "web"] else "unknown",
            ip_address=ip_address,
            user_agent=(user_agent or "")[:512],
            metadata=clean_metadata,
        )
    except Exception as exc:
        logger.error("Failed to record security event %s for user %s: %s", event_type, getattr(user, "id", "-"), exc)
        return None


@dataclass(frozen=True)
class AuthenticationResult:
    user: User
    session: AuthSession
    access_token: str
    refresh_token: str


def canonicalize_email(email):
    return email.strip().lower()


def google_login_or_register(*, id_token, device_name="", platform="android", device_id="", ip_address=None, user_agent=""):
    claims = verify_google_id_token(id_token)
    sub = claims["sub"]
    email = canonicalize_email(claims["email"])
    name = claims.get("name") or email.split("@")[0]

    with transaction.atomic():
        # Case A: User already linked by google_sub
        user = User.objects.filter(google_sub=sub).first()
        if user is not None:
            if not user.is_active:
                raise AuthenticationCredentialsError("Account is inactive.")
            return _create_session_and_tokens(
                user,
                device_name=device_name,
                platform=platform,
                device_id=device_id,
                is_google=True,
                ip_address=ip_address,
                user_agent=user_agent,
            )

        # Case B: User with matching email
        existing_user = User.objects.filter(email__iexact=email).first()
        if existing_user is not None:
            if not existing_user.is_active:
                raise AuthenticationCredentialsError("Account is inactive.")
            if not existing_user.email_verified:
                # Do NOT silently attach Google identity to unverified Promise email
                raise EmailVerificationRequiredForLinkingError()
            existing_user.google_sub = sub
            existing_user.save(update_fields=["google_sub", "updated_at"])
            record_security_event(
                existing_user,
                SecurityEvent.EventType.GOOGLE_LINKED,
                device_name=device_name,
                platform=platform,
                ip_address=ip_address,
                user_agent=user_agent,
            )
            return _create_session_and_tokens(
                existing_user,
                device_name=device_name,
                platform=platform,
                device_id=device_id,
                is_google=True,
                ip_address=ip_address,
                user_agent=user_agent,
            )

        # Case C: New user creation from Google
        user = User.objects.create_user(
            email=email,
            password=None,
            name=name,
            email_verified=True,
            google_sub=sub,
        )
        return _create_session_and_tokens(
            user,
            device_name=device_name,
            platform=platform,
            device_id=device_id,
            is_google=True,
            ip_address=ip_address,
            user_agent=user_agent,
        )


def request_email_verification(*, user):
    raw_token = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    now = timezone.now()
    with transaction.atomic():
        EmailVerificationToken.objects.filter(user=user, used_at__isnull=True).update(used_at=now)
        EmailVerificationToken.objects.create(
            user=user,
            token_hash=token_hash,
            expires_at=now + timedelta(hours=24),
        )
    return raw_token


def confirm_email_verification(*, token):
    if not token or not isinstance(token, str):
        raise InvalidOrExpiredTokenError()
    token_hash = hashlib.sha256(token.strip().encode("utf-8")).hexdigest()
    now = timezone.now()
    with transaction.atomic():
        record = (
            EmailVerificationToken.objects.select_related("user")
            .select_for_update()
            .filter(token_hash=token_hash, used_at__isnull=True, expires_at__gt=now)
            .first()
        )
        if record is None:
            raise InvalidOrExpiredTokenError()
        record.used_at = now
        record.save(update_fields=["used_at", "updated_at"])
        user = record.user
        user.email_verified = True
        user.save(update_fields=["email_verified", "updated_at"])
        record_security_event(user, SecurityEvent.EventType.EMAIL_VERIFIED)
        return user


def request_password_reset(*, email):
    email = canonicalize_email(email)
    user = User.objects.filter(email__iexact=email, is_active=True).first()
    if user is None:
        # Constant-time protection
        check_password("dummy", _DUMMY_PASSWORD_HASH)
        return None
    raw_token = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    now = timezone.now()
    with transaction.atomic():
        PasswordResetToken.objects.filter(user=user, used_at__isnull=True).update(used_at=now)
        PasswordResetToken.objects.create(
            user=user,
            token_hash=token_hash,
            expires_at=now + timedelta(hours=1),
        )
    return raw_token


def confirm_password_reset(*, token, new_password):
    if not token or not isinstance(token, str):
        raise InvalidOrExpiredTokenError()
    token_hash = hashlib.sha256(token.strip().encode("utf-8")).hexdigest()
    now = timezone.now()
    with transaction.atomic():
        record = (
            PasswordResetToken.objects.select_related("user")
            .select_for_update()
            .filter(token_hash=token_hash, used_at__isnull=True, expires_at__gt=now)
            .first()
        )
        if record is None:
            raise InvalidOrExpiredTokenError()
        record.used_at = now
        record.save(update_fields=["used_at", "updated_at"])
        user = record.user
        user.set_password(new_password)
        user.save(update_fields=["password", "updated_at"])
        record_security_event(user, SecurityEvent.EventType.PASSWORD_RESET)
        # Revoke all active sessions on password change
        logout_all_sessions(user=user)
        return user


def set_user_password(*, user, password):
    if user.has_usable_password():
        raise PasswordAlreadySetError()
    user.set_password(password)
    user.save(update_fields=["password", "updated_at"])
    record_security_event(user, SecurityEvent.EventType.PASSWORD_CHANGED, metadata={"action": "set_initial_password"})
    return user


def change_user_password(*, user, old_password, new_password):
    if not user.check_password(old_password):
        raise InvalidCurrentPasswordError()
    user.set_password(new_password)
    user.save(update_fields=["password", "updated_at"])
    record_security_event(user, SecurityEvent.EventType.PASSWORD_CHANGED)
    return user


def delete_user_account(*, user):
    now = timezone.now()
    with transaction.atomic():
        record_security_event(user, SecurityEvent.EventType.ACCOUNT_DELETION_REQUESTED)
        # Anonymize user identity
        user.name = "Former Participant"
        user.email = f"deleted_{user.id}@deleted.promise.app"
        user.google_sub = None
        user.set_unusable_password()
        user.is_active = False
        user.save(update_fields=["name", "email", "google_sub", "password", "is_active", "updated_at"])

        # Revoke all sessions and deactivate devices
        logout_all_sessions(user=user)

        # Cancel personal commitments and goals
        user.created_commitments.filter(status__in=["PENDING", "WAITING", "SNOOZED"]).update(
            status="CANCELLED",
            cancelled_at=now,
            updated_at=now,
        )
        user.created_goals.filter(is_shared=False, status__in=["ACTIVE", "PAUSED"]).update(
            status="CANCELLED",
            cancelled_at=now,
            updated_at=now,
        )
        # Update participant membership on shared goals to LEFT
        user.goal_participations.filter(status__in=["ACTIVE", "INVITED"]).update(
            status="LEFT",
            left_at=now,
            updated_at=now,
        )


def register_user(*, email, password, name, device_name="", platform="unknown", device_id="", ip_address=None, user_agent=""):
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

        try:
            from apps.analytics.events import EVENT_ONBOARDING_STARTED, EVENT_REGISTER_SUCCESS
            from apps.analytics.services import record_analytics_event

            record_analytics_event(event_name=EVENT_REGISTER_SUCCESS, user=user, platform=platform)
            record_analytics_event(event_name=EVENT_ONBOARDING_STARTED, user=user, platform=platform)
        except Exception:
            pass

        return _create_session_and_tokens(
            user,
            device_name=device_name,
            platform=platform,
            device_id=device_id,
            ip_address=ip_address,
            user_agent=user_agent,
        )


def login_user(*, email, password, device_name="", platform="unknown", device_id="", ip_address=None, user_agent=""):
    email = canonicalize_email(email)
    user = User.objects.filter(email__iexact=email).first()

    if user is None:
        check_password(password, _DUMMY_PASSWORD_HASH)
        raise AuthenticationCredentialsError()

    password_matches = user.check_password(password)
    if not password_matches or not user.is_active:
        raise AuthenticationCredentialsError()

    with transaction.atomic():
        return _create_session_and_tokens(
            user,
            device_name=device_name,
            platform=platform,
            device_id=device_id,
            ip_address=ip_address,
            user_agent=user_agent,
        )


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
            record_security_event(user, SecurityEvent.EventType.SESSION_REVOKED, device_name=session.device_name, platform=session.platform)
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
            record_security_event(user, SecurityEvent.EventType.LOGOUT_ALL)
        from apps.notifications.models import UserDevice
        UserDevice.objects.filter(user=user).update(is_active=False)
    for session_id in session_ids:
        deny_access_session(session_id)


def list_user_sessions(user):
    """Returns active, non-revoked sessions for user ordered by recency."""
    now = timezone.now()
    return AuthSession.objects.filter(
        user=user,
        revoked_at__isnull=True,
        expires_at__gt=now,
        absolute_expires_at__gt=now,
    ).order_by("-last_used_at")


def revoke_user_session(user, session_id):
    """Revokes a specific session belonging to user."""
    now = timezone.now()
    with transaction.atomic():
        session = AuthSession.objects.select_for_update().filter(id=session_id, user=user).first()
        if session is None:
            raise AuthSession.DoesNotExist("Session does not exist.")
        if not session.is_revoked():
            session.revoked_at = now
            session.revoked_reason = AuthSession.RevokedReason.LOGOUT
            session.save(update_fields=["revoked_at", "revoked_reason", "updated_at"])
            record_security_event(user, SecurityEvent.EventType.SESSION_REVOKED, device_name=session.device_name, platform=session.platform)
    deny_access_session(session_id)
    return session


def revoke_other_sessions(user, current_session_id=None):
    """Revokes all sessions for user except current_session_id."""
    now = timezone.now()
    with transaction.atomic():
        qs = AuthSession.objects.select_for_update().filter(user=user, revoked_at__isnull=True)
        if current_session_id:
            qs = qs.exclude(id=current_session_id)
        session_ids = list(qs.values_list("id", flat=True))
        if session_ids:
            AuthSession.objects.filter(id__in=session_ids).update(
                revoked_at=now,
                revoked_reason=AuthSession.RevokedReason.LOGOUT_ALL,
                updated_at=now,
            )
            record_security_event(user, SecurityEvent.EventType.LOGOUT_ALL)
    for sid in session_ids:
        deny_access_session(sid)


def link_google_account(user, id_token):
    """Links Google account to an existing user."""
    claims = verify_google_id_token(id_token)
    sub = claims["sub"]
    if not claims.get("email_verified", False):
        raise AuthenticationCredentialsError("Google email must be verified to link.")

    with transaction.atomic():
        if User.objects.filter(google_sub=sub).exclude(id=user.id).exists():
            raise AuthenticationCredentialsError("Google account is already linked to another user.")
        user.google_sub = sub
        user.save(update_fields=["google_sub", "updated_at"])
        record_security_event(user, SecurityEvent.EventType.GOOGLE_LINKED)
        return user


def unlink_google_account(user):
    """Unlinks Google account if the user has a usable password fallback."""
    if not user.has_usable_password():
        raise PasswordRequiredToUnlinkError()
    with transaction.atomic():
        user.google_sub = None
        user.save(update_fields=["google_sub", "updated_at"])
        record_security_event(user, SecurityEvent.EventType.GOOGLE_UNLINKED)
        return user


def request_email_change(user, new_email, current_password=None):
    """Initiates email change by sending a single-use verification token to new_email."""
    new_email = canonicalize_email(new_email)
    if new_email == user.email.lower():
        raise AuthenticationCredentialsError("New email must be different from current email.")
    if user.has_usable_password():
        if not current_password or not user.check_password(current_password):
            raise InvalidCurrentPasswordError()

    if User.objects.filter(email__iexact=new_email, is_active=True).exclude(id=user.id).exists():
        raise EmailAlreadyExistsError()

    raw_token = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    now = timezone.now()

    with transaction.atomic():
        EmailChangeToken.objects.filter(user=user, used_at__isnull=True).update(used_at=now)
        EmailChangeToken.objects.create(
            user=user,
            new_email=new_email,
            token_hash=token_hash,
            expires_at=now + timedelta(hours=24),
        )
        record_security_event(user, SecurityEvent.EventType.EMAIL_CHANGE_REQUESTED)

    return raw_token


def confirm_email_change(token):
    """Confirms and activates new email address using single-use token."""
    if not token or not isinstance(token, str):
        raise InvalidOrExpiredTokenError()
    token_hash = hashlib.sha256(token.strip().encode("utf-8")).hexdigest()
    now = timezone.now()

    with transaction.atomic():
        record = (
            EmailChangeToken.objects.select_related("user")
            .select_for_update()
            .filter(token_hash=token_hash, used_at__isnull=True, expires_at__gt=now)
            .first()
        )
        if record is None:
            raise InvalidOrExpiredTokenError()

        if User.objects.filter(email__iexact=record.new_email, is_active=True).exclude(id=record.user_id).exists():
            record.used_at = now
            record.save(update_fields=["used_at", "updated_at"])
            raise EmailAlreadyExistsError()

        user = record.user
        user.email = record.new_email
        user.email_verified = True
        user.save(update_fields=["email", "email_verified", "updated_at"])

        record.used_at = now
        record.save(update_fields=["used_at", "updated_at"])
        record_security_event(user, SecurityEvent.EventType.EMAIL_CHANGED)
        return user


def _create_session_and_tokens(user, device_name="", platform="unknown", device_id="", is_google=False, ip_address=None, user_agent=""):
    now = timezone.now()
    refresh_secret = generate_refresh_secret()

    is_new_device = False
    if device_id:
        is_new_device = not AuthSession.objects.filter(user=user, device_id=device_id).exists()

    session = AuthSession.objects.create(
        user=user,
        refresh_token_hmac=hash_refresh_secret(refresh_secret),
        current_refresh_secret_ciphertext=encrypt_refresh_secret(refresh_secret),
        last_used_at=now,
        expires_at=now + REFRESH_IDLE_LIFETIME,
        absolute_expires_at=now + SESSION_ABSOLUTE_LIFETIME,
        device_name=device_name or "",
        device_id=device_id or "",
        platform=platform if platform in ["android", "ios", "web"] else "unknown",
        ip_address=ip_address,
        user_agent=user_agent or "",
    )

    event_type = SecurityEvent.EventType.GOOGLE_LOGIN if is_google else SecurityEvent.EventType.LOGIN
    record_security_event(
        user,
        event_type,
        device_name=device_name,
        platform=platform,
        ip_address=ip_address,
        user_agent=user_agent,
    )
    if is_new_device:
        record_security_event(
            user,
            SecurityEvent.EventType.NEW_DEVICE_LOGIN,
            device_name=device_name,
            platform=platform,
            ip_address=ip_address,
            user_agent=user_agent,
        )

    access_token = issue_access_token(user, session)
    refresh_token = compose_refresh_token(session.id, refresh_secret)

    # Server-Side Authoritative Analytics Event Emission
    try:
        from apps.analytics.events import (
            EVENT_GOOGLE_LOGIN_SUCCESS,
            EVENT_LOGIN_SUCCESS,
        )
        from apps.analytics.services import record_analytics_event

        an_event = EVENT_GOOGLE_LOGIN_SUCCESS if is_google else EVENT_LOGIN_SUCCESS
        record_analytics_event(
            event_name=an_event,
            user=user,
            platform=platform if platform in ["android", "ios", "web"] else "unknown",
        )
    except Exception:
        pass

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
