import uuid
from datetime import timedelta

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.authentication.models import AuthSession
from apps.authentication.tokens import (
    generate_refresh_secret,
    hash_refresh_secret,
    refresh_secret_matches,
)

User = get_user_model()


@pytest.fixture(autouse=True)
def refresh_token_pepper(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"


@pytest.fixture
def user(db):
    return User.objects.create_user(
        email="alex@example.com",
        name="Alex",
        password="a-secure-password",
    )


def _future_session_times():
    now = timezone.now()
    return {
        "last_used_at": now,
        "expires_at": now + timedelta(days=30),
        "absolute_expires_at": now + timedelta(days=90),
    }


def _create_session(user, secret=None, **overrides):
    if secret is None:
        secret = generate_refresh_secret()
    fields = _future_session_times()
    fields.update(overrides)
    session = AuthSession.objects.create(
        user=user,
        refresh_token_hmac=hash_refresh_secret(secret),
        **fields,
    )
    return session, secret


@pytest.mark.django_db
def test_auth_session_can_be_created_for_a_user(user):
    session, _secret = _create_session(user)

    assert session.pk is not None
    assert session.user == user
    assert AuthSession.objects.filter(user=user).count() == 1


@pytest.mark.django_db
def test_auth_session_id_is_uuid_v4(user):
    session, _secret = _create_session(user)

    assert isinstance(session.id, uuid.UUID)
    assert session.id.version == 4


@pytest.mark.django_db
def test_raw_refresh_token_is_not_stored(user):
    session, secret = _create_session(user)
    session.refresh_from_db()
    field_names = {field.name for field in AuthSession._meta.get_fields()}

    assert "refresh_token" not in field_names
    assert secret not in {
        session.refresh_token_hmac,
        session.previous_token_hmac,
        session.current_refresh_secret_ciphertext,
    }
    assert session.refresh_token_hmac == hash_refresh_secret(secret)
    assert session.refresh_token_hmac != secret


@pytest.mark.django_db
def test_refresh_secret_verification_succeeds(user):
    session, secret = _create_session(user)

    assert refresh_secret_matches(secret, session.refresh_token_hmac) is True


@pytest.mark.django_db
def test_incorrect_refresh_secret_fails(user):
    session, secret = _create_session(user)
    other = generate_refresh_secret()

    assert other != secret
    assert refresh_secret_matches(other, session.refresh_token_hmac) is False


@pytest.mark.django_db
def test_revoked_at_makes_session_inactive(user):
    session, _secret = _create_session(user)

    assert session.revoked_at is None
    assert session.is_active() is True

    session.revoked_at = timezone.now()
    session.revoked_reason = "logout"
    session.save()

    assert session.is_revoked() is True
    assert session.is_active() is False


@pytest.mark.django_db
def test_refresh_expiry_makes_session_inactive(user):
    now = timezone.now()
    session, _secret = _create_session(
        user,
        last_used_at=now,
        expires_at=now - timedelta(seconds=1),
        absolute_expires_at=now + timedelta(days=90),
    )

    assert session.is_expired() is True
    assert session.is_active() is False


@pytest.mark.django_db
def test_absolute_expiry_makes_session_inactive(user):
    now = timezone.now()
    session, _secret = _create_session(
        user,
        last_used_at=now,
        expires_at=now + timedelta(days=30),
        absolute_expires_at=now - timedelta(seconds=1),
    )

    assert session.is_expired() is True
    assert session.is_active() is False


@pytest.mark.django_db
def test_session_timestamps_are_timezone_aware(user):
    session, _secret = _create_session(user)

    assert timezone.is_aware(session.created_at)
    assert timezone.is_aware(session.updated_at)
    assert timezone.is_aware(session.last_used_at)
    assert timezone.is_aware(session.expires_at)
    assert timezone.is_aware(session.absolute_expires_at)


@pytest.mark.django_db
def test_user_session_relationship(user):
    session, _secret = _create_session(user)

    assert list(user.auth_sessions.all()) == [session]
    assert session.user_id == user.id


@pytest.mark.django_db
def test_previous_token_hmac_can_be_stored_for_reuse_detection(user):
    previous_secret = generate_refresh_secret()
    current_secret = generate_refresh_secret()
    session, _ = _create_session(
        user,
        secret=current_secret,
        previous_token_hmac=hash_refresh_secret(previous_secret),
        previous_rotated_at=timezone.now(),
    )

    assert refresh_secret_matches(current_secret, session.refresh_token_hmac)
    assert refresh_secret_matches(previous_secret, session.previous_token_hmac)
    assert session.previous_rotated_at is not None
    assert timezone.is_aware(session.previous_rotated_at)


@pytest.mark.django_db
def test_session_string_does_not_include_token_material(user):
    session, secret = _create_session(user)
    rendered = str(session)

    assert secret not in rendered
    assert session.refresh_token_hmac not in rendered
    assert str(session.id) in rendered
