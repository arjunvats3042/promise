import datetime
import hashlib
import json
import uuid
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient

from apps.authentication.access_tokens import issue_access_token
from apps.authentication.exceptions import (
    EmailVerificationRequiredForLinkingError,
    InvalidCurrentPasswordError,
    InvalidOrExpiredTokenError,
    PasswordAlreadySetError,
)
from apps.authentication.models import (
    AuthSession,
    EmailVerificationToken,
    PasswordResetToken,
)
from apps.authentication.services import (
    change_user_password,
    confirm_email_verification,
    confirm_password_reset,
    delete_user_account,
    google_login_or_register,
    request_email_verification,
    request_password_reset,
    set_user_password,
)
from apps.commitments.models import Commitment
from apps.commitments.services import create_commitment, get_visible_commitment
from apps.goals.models import Goal, GoalParticipant
from apps.goals.services import create_goal, get_visible_goal

User = get_user_model()


@pytest.fixture
def client():
    return APIClient()


@pytest.fixture
def verified_user(db):
    return User.objects.create_user(
        email="verified@example.com",
        name="Verified User",
        password="ValidPassword123!",
        email_verified=True,
    )


@pytest.fixture
def unverified_user(db):
    return User.objects.create_user(
        email="unverified@example.com",
        name="Unverified User",
        password="ValidPassword123!",
        email_verified=False,
    )


# ==============================================================================
# 1. Google Authentication & Account Linking Tests
# ==============================================================================

@pytest.mark.django_db
def test_google_login_creates_new_verified_user():
    claims = {
        "sub": "google-user-12345",
        "email": "newgoogle@example.com",
        "name": "Google User",
        "email_verified": True,
    }
    with patch("apps.authentication.services.verify_google_id_token", return_value=claims):
        result = google_login_or_register(id_token="valid-token")

    user = result.user
    assert user.email == "newgoogle@example.com"
    assert user.name == "Google User"
    assert user.email_verified is True
    assert user.google_sub == "google-user-12345"
    assert not user.has_usable_password()
    assert result.access_token is not None
    assert result.refresh_token is not None


@pytest.mark.django_db
def test_google_login_auto_links_verified_promise_account(verified_user):
    claims = {
        "sub": "google-user-verified",
        "email": verified_user.email,
        "name": "Updated Name",
        "email_verified": True,
    }
    with patch("apps.authentication.services.verify_google_id_token", return_value=claims):
        result = google_login_or_register(id_token="valid-token")

    verified_user.refresh_from_db()
    assert result.user.id == verified_user.id
    assert verified_user.google_sub == "google-user-verified"
    assert verified_user.has_usable_password()


@pytest.mark.django_db
def test_google_login_rejects_linking_to_unverified_promise_account(unverified_user):
    claims = {
        "sub": "google-user-unverified",
        "email": unverified_user.email,
        "name": "Unverified Link",
        "email_verified": True,
    }
    with patch("apps.authentication.services.verify_google_id_token", return_value=claims):
        with pytest.raises(EmailVerificationRequiredForLinkingError):
            google_login_or_register(id_token="valid-token")


@pytest.mark.django_db
def test_google_login_existing_linked_user():
    user = User.objects.create_user(
        email="linked@example.com",
        name="Linked User",
        email_verified=True,
        google_sub="google-sub-999",
    )
    claims = {
        "sub": "google-sub-999",
        "email": "changed_email_at_google@example.com",
        "name": "Linked User",
        "email_verified": True,
    }
    with patch("apps.authentication.services.verify_google_id_token", return_value=claims):
        result = google_login_or_register(id_token="valid-token")

    assert result.user.id == user.id


# ==============================================================================
# 2. Email Verification Flow Tests
# ==============================================================================

@pytest.mark.django_db
def test_email_verification_lifecycle(unverified_user):
    assert unverified_user.email_verified is False
    raw_token = request_email_verification(user=unverified_user)
    assert raw_token is not None

    # Token should be stored as SHA-256 hash
    token_record = EmailVerificationToken.objects.get(user=unverified_user, used_at__isnull=True)
    expected_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    assert token_record.token_hash == expected_hash
    assert token_record.is_valid()

    # Confirm verification
    verified = confirm_email_verification(token=raw_token)
    assert verified.email_verified is True
    unverified_user.refresh_from_db()
    assert unverified_user.email_verified is True

    # Reusing token must fail
    with pytest.raises(InvalidOrExpiredTokenError):
        confirm_email_verification(token=raw_token)


@pytest.mark.django_db
def test_email_verification_expired_token(unverified_user):
    raw_token = request_email_verification(user=unverified_user)
    token_record = EmailVerificationToken.objects.get(user=unverified_user)
    token_record.expires_at = timezone.now() - datetime.timedelta(seconds=1)
    token_record.save(update_fields=["expires_at"])

    with pytest.raises(InvalidOrExpiredTokenError):
        confirm_email_verification(token=raw_token)


# ==============================================================================
# 3. Password Reset Flow Tests
# ==============================================================================

@pytest.mark.django_db
def test_password_reset_lifecycle(verified_user):
    raw_token = request_password_reset(email=verified_user.email)
    assert raw_token is not None

    # Verify non-enumeration for non-existent email
    dummy_token = request_password_reset(email="nonexistent@example.com")
    assert dummy_token is None

    # Token record stored as SHA-256
    token_record = PasswordResetToken.objects.get(user=verified_user, used_at__isnull=True)
    expected_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    assert token_record.token_hash == expected_hash

    # Confirm password reset
    new_password = "BrandNewSecurePassword999!"
    user = confirm_password_reset(token=raw_token, new_password=new_password)
    assert user.check_password(new_password)

    # Reusing reset token must fail
    with pytest.raises(InvalidOrExpiredTokenError):
        confirm_password_reset(token=raw_token, new_password="AnotherPassword!")


# ==============================================================================
# 4. Password & Account Management Tests
# ==============================================================================

@pytest.mark.django_db
def test_set_and_change_password():
    google_user = User.objects.create_user(
        email="googleonly@example.com",
        name="Google Only",
        email_verified=True,
        google_sub="google-111",
    )
    assert not google_user.has_usable_password()

    # Setting password succeeds
    set_user_password(user=google_user, password="NewInitialPassword123!")
    google_user.refresh_from_db()
    assert google_user.has_usable_password()
    assert google_user.check_password("NewInitialPassword123!")

    # Cannot set password again if usable password already exists
    with pytest.raises(PasswordAlreadySetError):
        set_user_password(user=google_user, password="SecondPassword123!")

    # Changing password with correct old password
    change_user_password(
        user=google_user,
        old_password="NewInitialPassword123!",
        new_password="ChangedPassword456!",
    )
    google_user.refresh_from_db()
    assert google_user.check_password("ChangedPassword456!")

    # Changing password with incorrect old password fails
    with pytest.raises(InvalidCurrentPasswordError):
        change_user_password(
            user=google_user,
            old_password="WrongOldPassword!",
            new_password="FailedPassword789!",
        )


@pytest.mark.django_db
def test_delete_user_account_anonymizes_and_preserves_history(verified_user):
    goal = create_goal(
        creator=verified_user,
        title="Personal Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=datetime.date(2026, 8, 1),
    )
    commitment = create_commitment(
        creator=verified_user,
        title="Personal Task",
    )

    delete_user_account(user=verified_user)
    verified_user.refresh_from_db()
    goal.refresh_from_db()
    commitment.refresh_from_db()

    assert not verified_user.is_active
    assert verified_user.name == "Former Participant"
    assert "deleted_" in verified_user.email
    assert not verified_user.has_usable_password()
    assert verified_user.google_sub is None
    assert goal.status == Goal.Status.CANCELLED
    assert commitment.status == Commitment.Status.CANCELLED


# ==============================================================================
# 5. Dual ID Lookups & URL Resolution
# ==============================================================================

@pytest.mark.django_db
def test_goal_and_commitment_dual_id_lookup(verified_user):
    goal = create_goal(
        creator=verified_user,
        title="Dual ID Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=datetime.date(2026, 8, 1),
    )
    commitment = create_commitment(
        creator=verified_user,
        title="Dual ID Commitment",
    )

    # Lookup by UUID
    found_goal_uuid = get_visible_goal(viewer=verified_user, goal_id=goal.id)
    assert found_goal_uuid.id == goal.id

    found_comm_uuid = get_visible_commitment(viewer=verified_user, commitment_id=commitment.id)
    assert found_comm_uuid.id == commitment.id

    # Lookup by numeric_id (integer and numeric string)
    assert goal.numeric_id is not None
    found_goal_num = get_visible_goal(viewer=verified_user, goal_id=goal.numeric_id)
    assert found_goal_num.id == goal.id

    found_goal_str = get_visible_goal(viewer=verified_user, goal_id=str(goal.numeric_id))
    assert found_goal_str.id == goal.id

    assert commitment.numeric_id is not None
    found_comm_num = get_visible_commitment(viewer=verified_user, commitment_id=commitment.numeric_id)
    assert found_comm_num.id == commitment.id

    found_comm_str = get_visible_commitment(viewer=verified_user, commitment_id=str(commitment.numeric_id))
    assert found_comm_str.id == commitment.id
