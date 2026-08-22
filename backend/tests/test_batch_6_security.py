import hashlib
import uuid
from datetime import timedelta
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework import status

from apps.authentication.models import AuthSession, EmailChangeToken, SecurityEvent
from apps.authentication.services import (
    canonicalize_email,
    confirm_email_change,
    link_google_account,
    login_user,
    register_user,
    request_email_change,
    logout_all_sessions,
    revoke_other_sessions,
    revoke_user_session,
    unlink_google_account,
)

User = get_user_model()


@pytest.fixture
def auth_user(db):
    return User.objects.create_user(
        email="security.user@example.com",
        password="SecurePassword123!",
        name="Security Tester",
        email_verified=True,
    )


@pytest.fixture
def other_user(db):
    return User.objects.create_user(
        email="other.user@example.com",
        password="OtherPassword123!",
        name="Other Tester",
        email_verified=True,
    )


@pytest.mark.django_db
def test_list_sessions_returns_own_sessions_only(client, auth_user, other_user):
    """Verify user can only view their own sessions with safe metadata."""
    # Create 2 sessions for auth_user
    res1 = login_user(email=auth_user.email, password="SecurePassword123!", device_name="Pixel 9", platform="android", device_id="dev-1")
    res2 = login_user(email=auth_user.email, password="SecurePassword123!", device_name="MacBook", platform="web", device_id="dev-2")

    # Create 1 session for other_user
    res_other = login_user(email=other_user.email, password="OtherPassword123!", device_name="iPhone", platform="ios", device_id="dev-3")

    client.defaults["HTTP_AUTHORIZATION"] = f"Bearer {res1.access_token}"
    response = client.get("/api/v1/auth/sessions/")

    assert response.status_code == status.HTTP_200_OK
    sessions = response.json()["sessions"]
    assert len(sessions) == 2

    # Verify fields exposed
    session_ids = [s["id"] for s in sessions]
    assert str(res1.session.id) in session_ids
    assert str(res2.session.id) in session_ids
    assert str(res_other.session.id) not in session_ids

    first = next(s for s in sessions if s["id"] == str(res1.session.id))
    assert first["device_name"] == "Pixel 9"
    assert first["platform"] == "android"
    assert first["is_current"] is True

    # Verify no tokens or secrets exposed
    content_str = response.content.decode()
    assert "refresh_token_hmac" not in content_str
    assert "secret" not in content_str


@pytest.mark.django_db
def test_revoke_session_and_unrelated_session_404(client, auth_user, other_user):
    """Verify user can revoke their session, but unrelated session returns 404."""
    res1 = login_user(email=auth_user.email, password="SecurePassword123!", device_name="Pixel 9", platform="android")
    res_other = login_user(email=other_user.email, password="OtherPassword123!", device_name="iPhone", platform="ios")

    client.defaults["HTTP_AUTHORIZATION"] = f"Bearer {res1.access_token}"

    # Try revoking other user's session -> 404
    res_404 = client.delete(f"/api/v1/auth/sessions/{res_other.session.id}/")
    assert res_404.status_code == status.HTTP_404_NOT_FOUND

    # Revoke own session
    res_delete = client.delete(f"/api/v1/auth/sessions/{res1.session.id}/")
    assert res_delete.status_code == status.HTTP_204_NO_CONTENT

    # Session is now marked revoked in DB
    session_db = AuthSession.objects.get(id=res1.session.id)
    assert session_db.is_revoked() is True

    # Revoked session cannot refresh
    refresh_res = client.post("/api/v1/auth/refresh/", {"refresh_token": res1.refresh_token})
    assert refresh_res.status_code == status.HTTP_401_UNAUTHORIZED


@pytest.mark.django_db
def test_revoke_all_sessions(client, auth_user):
    """Verify revoke-all revokes all sessions."""
    res1 = login_user(email=auth_user.email, password="SecurePassword123!", device_name="Device 1")
    res2 = login_user(email=auth_user.email, password="SecurePassword123!", device_name="Device 2")

    client.defaults["HTTP_AUTHORIZATION"] = f"Bearer {res1.access_token}"
    res_revoke_all = client.post("/api/v1/auth/sessions/revoke-all/", {"except_current": False})
    assert res_revoke_all.status_code == status.HTTP_204_NO_CONTENT

    assert AuthSession.objects.filter(user=auth_user, revoked_at__isnull=True).count() == 0


@pytest.mark.django_db
def test_security_events_recorded_and_listed(client, auth_user):
    """Verify security events are recorded on security actions and retrievable via API."""
    # Login records LOGIN and NEW_DEVICE_LOGIN
    res = login_user(
        email=auth_user.email,
        password="SecurePassword123!",
        device_name="Pixel 9",
        platform="android",
        device_id="unique-device-xyz",
    )

    client.defaults["HTTP_AUTHORIZATION"] = f"Bearer {res.access_token}"
    response = client.get("/api/v1/auth/security-events/")
    assert response.status_code == status.HTTP_200_OK

    events = response.json()["results"]
    event_types = [e["event_type"] for e in events]
    assert "LOGIN" in event_types
    assert "NEW_DEVICE_LOGIN" in event_types

    # Verify no credentials in metadata
    for ev in events:
        meta_str = str(ev.get("metadata", {}))
        assert "password" not in meta_str
        assert "token" not in meta_str
        assert "fcm" not in meta_str


@pytest.mark.django_db
def test_google_unlink_fails_without_password_succeeds_with_password(auth_user):
    """Verify user cannot unlink Google if no password exists, but can unlink after password is set."""
    # Google-only user with unusable password
    google_user = User.objects.create_user(
        email="google.only@example.com",
        password=None,
        name="Google User",
        google_sub="google-sub-12345",
        email_verified=True,
    )
    assert google_user.has_usable_password() is False

    # Attempt unlink without password -> raises
    with pytest.raises(Exception) as exc_info:
        unlink_google_account(google_user)
    assert "Cannot unlink Google without setting a password first" in str(exc_info.value)

    # Set password
    google_user.set_password("NewSecurePassword123!")
    google_user.save()
    assert google_user.has_usable_password() is True

    # Now unlink succeeds
    unlink_google_account(google_user)
    google_user.refresh_from_db()
    assert google_user.google_sub is None


@pytest.mark.django_db
def test_email_change_request_and_confirmation_flow(client, auth_user):
    """Verify email change workflow: request with password -> token hash -> confirm updates primary email."""
    client.defaults["HTTP_AUTHORIZATION"] = f"Bearer {login_user(email=auth_user.email, password='SecurePassword123!').access_token}"

    # 1. Request with wrong password -> fails
    bad_req = client.post("/api/v1/auth/email/change/", {
        "new_email": "new.verified@example.com",
        "current_password": "WrongPassword!",
    })
    assert bad_req.status_code in [status.HTTP_400_BAD_REQUEST, status.HTTP_401_UNAUTHORIZED]

    # 2. Request with correct password -> generates token
    with patch("apps.authentication.services.secrets.token_urlsafe", return_value="fixed-change-token-12345678"):
        req_res = client.post("/api/v1/auth/email/change/", {
            "new_email": "new.verified@example.com",
            "current_password": "SecurePassword123!",
        })
        assert req_res.status_code == status.HTTP_200_OK

    raw_token = "fixed-change-token-12345678"

    # Verify token stored as hash in DB, not plaintext
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    record = EmailChangeToken.objects.get(token_hash=token_hash)
    assert record.new_email == "new.verified@example.com"
    assert record.used_at is None

    # 3. Confirm with valid token
    confirm_res = client.post("/api/v1/auth/email/change/confirm/", {"token": raw_token})
    assert confirm_res.status_code == status.HTTP_200_OK

    auth_user.refresh_from_db()
    assert auth_user.email == "new.verified@example.com"
    assert auth_user.email_verified is True

    # 4. Token cannot be reused (single-use)
    reused_res = client.post("/api/v1/auth/email/change/confirm/", {"token": raw_token})
    assert reused_res.status_code in [status.HTTP_400_BAD_REQUEST, status.HTTP_401_UNAUTHORIZED]


@pytest.mark.django_db
def test_email_change_token_expiry(auth_user):
    """Verify expired email change token is rejected."""
    raw_token = request_email_change(auth_user, "expired.new@example.com", current_password="SecurePassword123!")
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()

    # Backdate expiration
    EmailChangeToken.objects.filter(token_hash=token_hash).update(
        expires_at=timezone.now() - timedelta(minutes=5)
    )

    with pytest.raises(Exception):
        confirm_email_change(raw_token)
