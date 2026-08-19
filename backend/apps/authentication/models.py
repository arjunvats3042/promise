from django.conf import settings
from django.db import models
from django.utils import timezone

from apps.core.models import BaseModel


class AuthSession(BaseModel):
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
