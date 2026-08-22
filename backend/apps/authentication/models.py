from django.conf import settings
from django.db import models
from django.utils import timezone

from apps.core.models import BaseModel, UUIDBaseModel


class AuthSession(UUIDBaseModel):
    """One authenticated device/session for a user.

    Session id is BaseModel.id (UUID). Refresh tokens are opaque
    `{session_id}.{secret}`. HMAC-SHA256 of the secret is the canonical
    verifier. The current secret is also stored as authenticated-encrypted
    ciphertext for 30-second retry-grace recovery only.
    """

    class Platform(models.TextChoices):
        ANDROID = "android", "android"
        IOS = "ios", "ios"
        WEB = "web", "web"
        UNKNOWN = "unknown", "unknown"

    class RevokedReason(models.TextChoices):
        LOGOUT = "logout", "logout"
        LOGOUT_ALL = "logout_all", "logout_all"
        REUSE = "reuse", "reuse"
        PASSWORD_CHANGE = "password_change", "password_change"
        ADMIN = "admin", "admin"
        EXPIRED = "expired", "expired"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="auth_sessions",
    )
    refresh_token_hmac = models.CharField(max_length=64)
    current_refresh_secret_ciphertext = models.TextField(blank=True, default="")
    previous_token_hmac = models.CharField(max_length=64, null=True, blank=True)
    previous_rotated_at = models.DateTimeField(null=True, blank=True)
    last_used_at = models.DateTimeField()
    expires_at = models.DateTimeField()
    absolute_expires_at = models.DateTimeField()
    revoked_at = models.DateTimeField(null=True, blank=True)
    revoked_reason = models.CharField(
        max_length=32,
        choices=RevokedReason.choices,
        null=True,
        blank=True,
    )
    device_name = models.CharField(max_length=128, blank=True, default="")
    device_id = models.CharField(max_length=128, blank=True, default="")
    platform = models.CharField(
        max_length=16,
        choices=Platform.choices,
        default=Platform.UNKNOWN,
    )
    app_version = models.CharField(max_length=32, blank=True, default="")
    ip_address = models.GenericIPAddressField(null=True, blank=True)
    user_agent = models.CharField(max_length=512, blank=True, default="")

    class Meta:
        db_table = "auth_sessions"
        indexes = [
            models.Index(fields=["user", "revoked_at"]),
            models.Index(fields=["expires_at"]),
        ]

    def __str__(self):
        return f"AuthSession {self.id}"

    def is_revoked(self):
        return self.revoked_at is not None

    def is_expired(self, at=None):
        now = at if at is not None else timezone.now()
        return now >= self.expires_at or now >= self.absolute_expires_at

    def is_active(self, at=None):
        if self.is_revoked() or self.is_expired(at=at):
            return False
        return self.user.is_active


class EmailVerificationToken(UUIDBaseModel):
    """Secure hashed single-use token for email verification."""

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="email_verification_tokens",
    )
    token_hash = models.CharField(max_length=64, db_index=True)
    expires_at = models.DateTimeField()
    used_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "email_verification_tokens"
        indexes = [
            models.Index(fields=["user", "expires_at"]),
        ]

    def __str__(self):
        return f"EmailVerificationToken(user_id={self.user_id}, used={self.used_at is not None})"

    def is_valid(self, at=None):
        now = at if at is not None else timezone.now()
        return self.used_at is None and now < self.expires_at


class PasswordResetToken(UUIDBaseModel):
    """Secure hashed single-use token for password reset."""

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="password_reset_tokens",
    )
    token_hash = models.CharField(max_length=64, db_index=True)
    expires_at = models.DateTimeField()
    used_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "password_reset_tokens"
        indexes = [
            models.Index(fields=["user", "expires_at"]),
        ]

    def __str__(self):
        return f"PasswordResetToken(user_id={self.user_id}, used={self.used_at is not None})"

    def is_valid(self, at=None):
        now = at if at is not None else timezone.now()
        return self.used_at is None and now < self.expires_at


class SecurityEvent(UUIDBaseModel):
    """Audit record for authentication and account security actions.

    Strict privacy: Never stores passwords, access tokens, refresh tokens, Google ID tokens,
    FCM tokens, verification secrets, private chat content, or private check-in notes.
    """

    class EventType(models.TextChoices):
        LOGIN = "LOGIN", "Login"
        GOOGLE_LOGIN = "GOOGLE_LOGIN", "Google Login"
        PASSWORD_CHANGED = "PASSWORD_CHANGED", "Password Changed"
        PASSWORD_RESET = "PASSWORD_RESET", "Password Reset"
        EMAIL_VERIFIED = "EMAIL_VERIFIED", "Email Verified"
        GOOGLE_LINKED = "GOOGLE_LINKED", "Google Account Linked"
        GOOGLE_UNLINKED = "GOOGLE_UNLINKED", "Google Account Unlinked"
        SESSION_REVOKED = "SESSION_REVOKED", "Session Revoked"
        LOGOUT_ALL = "LOGOUT_ALL", "All Sessions Revoked"
        EMAIL_CHANGE_REQUESTED = "EMAIL_CHANGE_REQUESTED", "Email Change Requested"
        EMAIL_CHANGED = "EMAIL_CHANGED", "Email Changed"
        ACCOUNT_DELETION_REQUESTED = "ACCOUNT_DELETION_REQUESTED", "Account Deletion Requested"
        NEW_DEVICE_LOGIN = "NEW_DEVICE_LOGIN", "New Device Login"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="security_events",
    )
    event_type = models.CharField(max_length=32, choices=EventType.choices, db_index=True)
    device_name = models.CharField(max_length=128, blank=True, default="")
    platform = models.CharField(
        max_length=16,
        choices=AuthSession.Platform.choices,
        default=AuthSession.Platform.UNKNOWN,
    )
    ip_address = models.GenericIPAddressField(null=True, blank=True)
    user_agent = models.CharField(max_length=512, blank=True, default="")
    metadata = models.JSONField(default=dict, blank=True)

    class Meta:
        db_table = "security_events"
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["user", "-created_at"]),
            models.Index(fields=["event_type", "-created_at"]),
        ]

    def __str__(self):
        return f"SecurityEvent(user_id={self.user_id}, type={self.event_type}, at={self.created_at})"


class EmailChangeToken(UUIDBaseModel):
    """Secure single-use token for verifying and completing email address change."""

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="email_change_tokens",
    )
    new_email = models.EmailField(db_index=True)
    token_hash = models.CharField(max_length=64, db_index=True)
    expires_at = models.DateTimeField()
    used_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "email_change_tokens"
        indexes = [
            models.Index(fields=["user", "expires_at"]),
            models.Index(fields=["token_hash"]),
        ]

    def __str__(self):
        return f"EmailChangeToken(user_id={self.user_id}, new_email={self.new_email}, used={self.used_at is not None})"

    def is_valid(self, at=None):
        now = at if at is not None else timezone.now()
        return self.used_at is None and now < self.expires_at
