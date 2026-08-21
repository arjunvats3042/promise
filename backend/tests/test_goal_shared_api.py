from datetime import date, datetime
from unittest.mock import patch
from zoneinfo import ZoneInfo

import pytest
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient

from apps.goals.models import GoalCheckIn, GoalParticipant
from apps.goals.rate_limits import GOAL_WRITE_LIMIT, GOAL_WRITE_WINDOW, goal_user_key
from apps.goals.services import (
    accept_invitation,
    create_goal,
    invite_participant,
    leave_goal,
)
from apps.core.redis import increment_rate_limit

User = get_user_model()

GOALS_URL = "/api/v1/goals/"
LOGIN_URL = "/api/v1/auth/login/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"

PREVIEW_FIELDS = {
    "id",
    "title",
    "description",
    "timezone",
    "start_date",
    "end_date",
    "recurrence_kind",
    "weekdays",
    "period_unit",
    "times_per_period",
    "tracking_kind",
    "target_value",
    "target_unit",
    "inviter_user_id",
    "inviter_name",
    "invitation_status",
    "invitation_expires_at",
}
SHARED_EXTRA = {
    "collective_progress",
    "participants",
    "membership_role",
    "membership_status",
}
HIDDEN = {
    "progress",
    "current_streak",
    "collective_progress",
    "participants",
    "check_ins",
    "events",
    "password",
}


@pytest.fixture(autouse=True)
def auth_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = (
        "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
    )
    settings.JWT_SIGNING_KEY = TEST_SIGNING_KEY
    settings.JWT_SIGNING_KEY_PREVIOUS = ""
    settings.JWT_KEY_ID = "test-key"
    settings.JWT_KEY_ID_PREVIOUS = ""
    settings.JWT_ISSUER = "promise-api"
    settings.JWT_AUDIENCE = "promise-client"
    settings.JWT_ALGORITHM = "HS256"
    settings.JWT_ACCESS_TTL_SECONDS = 900
    settings.JWT_LEEWAY_SECONDS = 30


@pytest.fixture
def client():
    return APIClient()


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun-api-shared@example.com",
        name="Arjun",
        password=STRONG_PASSWORD,
        timezone="Asia/Kolkata",
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul-api-shared@example.com",
        name="Rahul",
        password=STRONG_PASSWORD,
        timezone="UTC",
    )


@pytest.fixture
def neha(db):
    return User.objects.create_user(
        email="neha-api-shared@example.com",
        name="Neha",
        password=STRONG_PASSWORD,
        timezone="UTC",
    )


def _token(client, email):
    response = client.post(
        LOGIN_URL,
        {"email": email, "password": STRONG_PASSWORD},
        format="json",
    )
    assert response.status_code == 200
    return response.json()["tokens"]["access_token"]


def _auth(client, token):
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")


def _detail(goal_id):
    return f"{GOALS_URL}{goal_id}/"


def _participants(goal_id):
    return f"{GOALS_URL}{goal_id}/participants/"


def _participant(goal_id, user_id):
    return f"{GOALS_URL}{goal_id}/participants/{user_id}/"


def _accept(goal_id):
    return f"{GOALS_URL}{goal_id}/participants/accept/"


def _decline(goal_id):
    return f"{GOALS_URL}{goal_id}/participants/decline/"


def _leave(goal_id):
    return f"{GOALS_URL}{goal_id}/leave/"


def _check_ins(goal_id):
    return f"{GOALS_URL}{goal_id}/check-ins/"


def _daily(creator, **overrides):
    fields = {
        "creator": creator,
        "title": "Read every day",
        "start_date": date(2026, 8, 10),
        "recurrence_kind": "DAILY",
    }
    fields.update(overrides)
    return create_goal(**fields)


def _aware(year, month, day, hour, minute, tz_name):
    return datetime(year, month, day, hour, minute, tzinfo=ZoneInfo(tz_name))


@pytest.mark.django_db
def test_invited_preview_and_active_shared_detail(client, arjun, rahul, neha):
    goal = _daily(arjun)
    _auth(client, _token(client, arjun.email))
    invited = client.post(
        _participants(goal.id), {"user_id": str(rahul.id)}, format="json"
    )
    assert invited.status_code == 201
    assert invited.json()["status"] == GoalParticipant.Status.INVITED

    _auth(client, _token(client, rahul.email))
    preview = client.get(_detail(goal.id))
    assert preview.status_code == 200
    body = preview.json()
    assert set(body) == PREVIEW_FIELDS
    for field in HIDDEN:
        if field in ("progress", "current_streak", "collective_progress", "participants"):
            assert field not in body
    assert body["invitation_status"] == GoalParticipant.Status.INVITED
    assert body["inviter_user_id"] == str(arjun.id)

    roster = client.get(_participants(goal.id))
    assert roster.status_code == 404

    accept = client.post(_accept(goal.id), {}, format="json")
    assert accept.status_code == 200
    shared = accept.json()
    assert SHARED_EXTRA.issubset(set(shared))
    assert shared["membership_role"] == GoalParticipant.Role.PARTICIPANT
    assert shared["collective_progress"]["current_period"]["required_participants"] >= 1

    _auth(client, _token(client, neha.email))
    assert client.get(_detail(goal.id)).status_code == 404


@pytest.mark.django_db
def test_invite_duplicate_reinvite_and_errors(client, arjun, rahul):
    goal = _daily(arjun)
    _auth(client, _token(client, arjun.email))
    first = client.post(
        _participants(goal.id), {"user_id": str(rahul.id)}, format="json"
    )
    second = client.post(
        _participants(goal.id), {"user_id": str(rahul.id)}, format="json"
    )
    assert first.status_code == 201
    assert second.status_code == 200
    assert first.json()["id"] == second.json()["id"]

    self_invite = client.post(
        _participants(goal.id), {"user_id": str(arjun.id)}, format="json"
    )
    assert self_invite.status_code == 409
    assert self_invite.json()["error"]["code"] == "GOAL_INVALID_PARTICIPANT_STATE"

    missing = client.post(
        _participants(goal.id),
        {"user_id": "00000000-0000-4000-8000-000000000099"},
        format="json",
    )
    assert missing.status_code == 400

    _auth(client, _token(client, rahul.email))
    client.post(_accept(goal.id), {}, format="json")
    _auth(client, _token(client, arjun.email))
    already = client.post(
        _participants(goal.id), {"user_id": str(rahul.id)}, format="json"
    )
    assert already.status_code == 409
    assert already.json()["error"]["code"] == "GOAL_ALREADY_PARTICIPANT"

    client.post(f"{GOALS_URL}{goal.id}/cancel/", {}, format="json")
    terminal = client.post(
        _participants(goal.id), {"user_id": str(rahul.id)}, format="json"
    )
    assert terminal.status_code == 409


@pytest.mark.django_db
def test_accept_expiry_revoked_and_idempotent(client, arjun, rahul):
    goal = _daily(arjun)
    with patch(
        "django.utils.timezone.now",
        return_value=_aware(2026, 8, 10, 9, 0, "Asia/Kolkata"),
    ):
        invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)

    _auth(client, _token(client, rahul.email))
    with patch(
        "django.utils.timezone.now",
        return_value=_aware(2026, 8, 25, 9, 0, "Asia/Kolkata"),
    ):
        expired = client.post(_accept(goal.id), {}, format="json")
    assert expired.status_code == 409
    assert expired.json()["error"]["code"] == "GOAL_INVITE_EXPIRED"

    with patch(
        "django.utils.timezone.now",
        return_value=_aware(2026, 8, 26, 9, 0, "Asia/Kolkata"),
    ):
        invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    _auth(client, _token(client, arjun.email))
    client.delete(_participant(goal.id, rahul.id))
    _auth(client, _token(client, rahul.email))
    revoked = client.post(_accept(goal.id), {}, format="json")
    assert revoked.status_code == 409
    assert revoked.json()["error"]["code"] == "GOAL_INVITE_REVOKED"

    with patch(
        "django.utils.timezone.now",
        return_value=_aware(2026, 8, 27, 9, 0, "Asia/Kolkata"),
    ):
        invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
        first = client.post(_accept(goal.id), {}, format="json")
        second = client.post(_accept(goal.id), {}, format="json")
    assert first.status_code == 200
    assert second.status_code == 200
    assert second.json()["membership_status"] == GoalParticipant.Status.ACTIVE


@pytest.mark.django_db
def test_decline_leave_remove(client, arjun, rahul, neha):
    goal = _daily(arjun)
    _auth(client, _token(client, arjun.email))
    client.post(_participants(goal.id), {"user_id": str(rahul.id)}, format="json")

    _auth(client, _token(client, rahul.email))
    declined = client.post(_decline(goal.id), {}, format="json")
    again = client.post(_decline(goal.id), {}, format="json")
    assert declined.status_code == 200
    assert again.status_code == 200
    assert declined.json()["status"] == GoalParticipant.Status.DECLINED
    assert client.get(_detail(goal.id)).status_code == 404

    _auth(client, _token(client, arjun.email))
    client.post(_participants(goal.id), {"user_id": str(rahul.id)}, format="json")
    _auth(client, _token(client, rahul.email))
    client.post(_accept(goal.id), {}, format="json")
    left = client.post(_leave(goal.id), {}, format="json")
    left_again = client.post(_leave(goal.id), {}, format="json")
    assert left.status_code == 200
    assert left_again.status_code == 200
    assert left.json()["status"] == GoalParticipant.Status.LEFT

    _auth(client, _token(client, arjun.email))
    owner_leave = client.post(_leave(goal.id), {}, format="json")
    assert owner_leave.status_code == 409
    assert owner_leave.json()["error"]["code"] == "GOAL_OWNER_CANNOT_LEAVE"

    client.post(_participants(goal.id), {"user_id": str(rahul.id)}, format="json")
    _auth(client, _token(client, rahul.email))
    client.post(_accept(goal.id), {}, format="json")
    _auth(client, _token(client, arjun.email))
    removed = client.delete(_participant(goal.id, rahul.id))
    assert removed.status_code == 200
    assert removed.json()["status"] == GoalParticipant.Status.REMOVED
    cannot_owner = client.delete(_participant(goal.id, arjun.id))
    assert cannot_owner.status_code == 409
    assert cannot_owner.json()["error"]["code"] == "GOAL_CANNOT_REMOVE_OWNER"

    _auth(client, _token(client, rahul.email))
    forbidden = client.delete(_participant(goal.id, arjun.id))
    assert forbidden.status_code == 404


@pytest.mark.django_db
def test_shared_check_in_and_forged_participant_ignored(client, arjun, rahul):
    goal = _daily(arjun)
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    accept_invitation(actor=rahul, goal_id=goal.id)

    _auth(client, _token(client, rahul.email))
    created = client.post(
        _check_ins(goal.id),
        {
            "period_date": "2026-08-10",
            "status": "COMPLETED",
            "participant_id": str(GoalParticipant.objects.get(goal=goal, user=arjun).id),
            "created_by": str(arjun.id),
        },
        format="json",
    )
    assert created.status_code == 201
    check_in = GoalCheckIn.objects.get(id=created.json()["id"])
    assert check_in.participant.user_id == rahul.id
    assert check_in.created_by_id == rahul.id

    identical = client.post(
        _check_ins(goal.id),
        {"period_date": "2026-08-10", "status": "COMPLETED"},
        format="json",
    )
    assert identical.status_code == 200

    leave_goal(actor=rahul, goal_id=goal.id)
    rejected = client.post(
        _check_ins(goal.id),
        {"period_date": "2026-08-11", "status": "COMPLETED"},
        format="json",
    )
    assert rejected.status_code == 404


@pytest.mark.django_db
def test_list_includes_invited_preview_not_left(client, arjun, rahul):
    goal = _daily(arjun)
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    _auth(client, _token(client, rahul.email))
    items = client.get(GOALS_URL).json()["results"]
    match = next(item for item in items if item["id"] == str(goal.id))
    assert set(match) == PREVIEW_FIELDS

    client.post(_accept(goal.id), {}, format="json")
    client.post(_leave(goal.id), {}, format="json")
    ids = {item["id"] for item in client.get(GOALS_URL).json()["results"]}
    assert str(goal.id) not in ids


@pytest.mark.django_db
def test_shared_write_rate_limit_applies_to_invite(client, arjun, rahul):
    goal = _daily(arjun)
    _auth(client, _token(client, arjun.email))
    for _ in range(GOAL_WRITE_LIMIT):
        increment_rate_limit(goal_user_key(arjun.id), GOAL_WRITE_WINDOW)
    limited = client.post(
        _participants(goal.id), {"user_id": str(rahul.id)}, format="json"
    )
    assert limited.status_code == 429
    assert limited.json()["error"]["code"] == "RATE_LIMITED"


@pytest.mark.django_db
def test_personal_goal_payload_unchanged(client, arjun):
    goal = _daily(arjun)
    _auth(client, _token(client, arjun.email))
    body = client.get(_detail(goal.id)).json()
    assert "collective_progress" not in body
    assert "participants" not in body
    assert "progress" in body
    assert "current_streak" in body
