from datetime import date
from zoneinfo import ZoneInfo

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient

from apps.goals.models import Goal, GoalCheckIn, GoalEvent
from apps.goals.services import create_goal, record_check_in
from apps.outbox.models import OutboxEvent

User = get_user_model()

GOALS_URL = "/api/v1/goals/"
LOGIN_URL = "/api/v1/auth/login/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
UNAUTHENTICATED_MISSING = {
    "error": {
        "code": "UNAUTHENTICATED",
        "message": "Authentication credentials were not provided.",
    }
}
GOAL_FIELDS = {
    "id",
    "title",
    "description",
    "status",
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
    "source",
    "paused_at",
    "completed_at",
    "cancelled_at",
    "created_at",
    "updated_at",
    "is_ended",
    "progress",
    "current_streak",
}
CHECKIN_FIELDS = {
    "id",
    "period_date",
    "status",
    "value",
    "note",
    "checked_at",
    "created_at",
    "updated_at",
}
HIDDEN_FIELDS = {
    "created_by",
    "events",
    "check_ins",
    "password",
    "metadata",
    "published_at",
    "attempts",
    "last_error",
    "payload",
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
        email="arjun@example.com",
        name="Arjun",
        password=STRONG_PASSWORD,
        timezone="Asia/Kolkata",
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul@example.com",
        name="Rahul",
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


def _action(goal_id, name):
    return f"{GOALS_URL}{goal_id}/{name}/"


def _check_ins(goal_id):
    return f"{GOALS_URL}{goal_id}/check-ins/"


def _assert_goal_payload(body):
    assert set(body) == GOAL_FIELDS
    for hidden in HIDDEN_FIELDS:
        assert hidden not in body
    assert isinstance(body["is_ended"], bool)
    assert isinstance(body["current_streak"], int)
    assert set(body["progress"]) == {
        "current_period",
        "week_progress",
        "consistency_percent",
    }
    assert "required" in body["progress"]["current_period"]
    assert "completed" in body["progress"]["current_period"]


def _assert_checkin_payload(body):
    assert set(body) == CHECKIN_FIELDS
    for hidden in HIDDEN_FIELDS:
        assert hidden not in body


def _list_items(response):
    body = response.json()
    assert "results" in body
    return body["results"]


def _create_daily(creator, **overrides):
    fields = {
        "creator": creator,
        "title": "Read every day",
        "start_date": date(2026, 8, 10),
        "recurrence_kind": Goal.RecurrenceKind.DAILY,
    }
    fields.update(overrides)
    return create_goal(**fields)


@pytest.mark.django_db
def test_create_requires_authentication(client):
    response = client.post(GOALS_URL, {"title": "Study"}, format="json")

    assert response.status_code == 401
    assert response.json() == UNAUTHENTICATED_MISSING


@pytest.mark.django_db
def test_create_success_snapshots_timezone_and_writes_event_and_outbox(client, arjun):
    _auth(client, _token(client, arjun.email))
    response = client.post(
        GOALS_URL,
        {
            "title": "Study DSA 5 days every week",
            "weekdays": [5, 1, 1, 3],
            "recurrence_kind": "WEEKLY_DAYS",
            "created_by": str(arjun.id),
            "status": "COMPLETED",
            "source": "IMPORT",
        },
        format="json",
    )

    assert response.status_code == 201
    body = response.json()
    _assert_goal_payload(body)
    assert body["title"] == "Study DSA 5 days every week"
    assert body["status"] == Goal.Status.ACTIVE
    assert body["source"] == Goal.Source.MANUAL
    assert body["timezone"] == "Asia/Kolkata"
    assert body["weekdays"] == [1, 3, 5]
    goal = Goal.objects.get(id=body["id"])
    assert goal.created_by == arjun
    assert goal.events.get().event_type == GoalEvent.EventType.CREATED
    outbox = OutboxEvent.objects.get(aggregate_id=goal.id)
    assert outbox.event_type == "goal.created"
    assert "title" not in outbox.payload


@pytest.mark.django_db
def test_create_validation_errors(client, arjun):
    _auth(client, _token(client, arjun.email))
    missing = client.post(GOALS_URL, {}, format="json")
    invalid = client.post(
        GOALS_URL,
        {"title": "Bad", "recurrence_kind": "DAILY", "times_per_period": 3},
        format="json",
    )

    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "VALIDATION_ERROR"
    assert invalid.status_code == 400
    assert Goal.objects.count() == 0


@pytest.mark.django_db
def test_list_is_owner_only_and_hides_terminal_unless_filtered(client, arjun, rahul):
    mine = _create_daily(arjun)
    completed = _create_daily(arjun, title="Done")
    _create_daily(rahul, title="Rahul's")
    from apps.goals.services import complete_goal

    complete_goal(actor=arjun, goal_id=completed.id)

    _auth(client, _token(client, arjun.email))
    items = _list_items(client.get(GOALS_URL))
    ids = {item["id"] for item in items}
    assert str(mine.id) in ids
    assert str(completed.id) not in ids
    for item in items:
        _assert_goal_payload(item)

    completed_items = _list_items(client.get(GOALS_URL, {"status": "COMPLETED"}))
    assert {item["id"] for item in completed_items} == {str(completed.id)}

    daily = _list_items(client.get(GOALS_URL, {"recurrence_kind": "DAILY"}))
    binary = _list_items(client.get(GOALS_URL, {"tracking_kind": "BINARY"}))
    assert str(mine.id) in {item["id"] for item in daily}
    assert str(mine.id) in {item["id"] for item in binary}


@pytest.mark.django_db
def test_list_paginates(client, arjun):
    for index in range(21):
        _create_daily(arjun, title=f"Goal {index}")
    _auth(client, _token(client, arjun.email))
    first = client.get(GOALS_URL)
    second = client.get(GOALS_URL, {"page": 2})

    assert first.status_code == 200
    assert first.json()["count"] == 21
    assert len(first.json()["results"]) == 20
    assert len(second.json()["results"]) == 1


@pytest.mark.django_db
def test_detail_owner_ok_other_user_404(client, arjun, rahul):
    goal = _create_daily(arjun)
    _auth(client, _token(client, arjun.email))
    ok = client.get(_detail(goal.id))
    assert ok.status_code == 200
    _assert_goal_payload(ok.json())
    assert ok.json()["id"] == str(goal.id)
    assert "events" not in ok.json()

    _auth(client, _token(client, rahul.email))
    missing = client.get(_detail(goal.id))
    assert missing.status_code == 404
    assert missing.json()["error"]["code"] == "GOAL_NOT_FOUND"


@pytest.mark.django_db
def test_patch_safe_fields_lock_and_noop(client, arjun, rahul):
    goal = _create_daily(arjun)
    _auth(client, _token(client, arjun.email))
    noop = client.patch(_detail(goal.id), {}, format="json")
    assert noop.status_code == 200
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.UPDATED
    ).count() == 0

    updated = client.patch(
        _detail(goal.id),
        {
            "title": "Keep reading",
            "status": "COMPLETED",
            "created_by": str(rahul.id),
        },
        format="json",
    )
    assert updated.status_code == 200
    assert updated.json()["title"] == "Keep reading"
    assert updated.json()["status"] == Goal.Status.ACTIVE
    goal.refresh_from_db()
    assert goal.created_by == arjun
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.UPDATED
    ).count() == 1
    assert OutboxEvent.objects.filter(
        aggregate_id=goal.id, event_type="goal.updated"
    ).count() == 1

    schedule = client.patch(
        _detail(goal.id),
        {"recurrence_kind": "WEEKLY_DAYS", "weekdays": [1, 2, 3, 4, 5]},
        format="json",
    )
    assert schedule.status_code == 200
    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
    )
    locked = client.patch(
        _detail(goal.id),
        {"recurrence_kind": "DAILY"},
        format="json",
    )
    tz_locked = client.patch(
        _detail(goal.id),
        {"timezone": "UTC"},
        format="json",
    )
    assert locked.status_code == 409
    assert locked.json()["error"]["code"] == "GOAL_SCHEDULE_LOCKED"
    assert tz_locked.status_code == 409
    assert tz_locked.json()["error"]["code"] == "GOAL_TIMEZONE_LOCKED"

    _auth(client, _token(client, rahul.email))
    other = client.patch(_detail(goal.id), {"title": "Hacked"}, format="json")
    assert other.status_code == 404
    assert other.json()["error"]["code"] == "GOAL_NOT_FOUND"


@pytest.mark.django_db
def test_lifecycle_actions_idempotent_invalid_and_unauthorized(client, arjun, rahul):
    goal = _create_daily(arjun)
    other = _create_daily(arjun, title="Cancel me")
    _auth(client, _token(client, arjun.email))

    pause = client.post(_action(goal.id, "pause"), {}, format="json")
    pause_again = client.post(_action(goal.id, "pause"), {}, format="json")
    assert pause.status_code == 200
    assert pause_again.status_code == 200
    assert pause.json()["status"] == Goal.Status.PAUSED
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.PAUSED
    ).count() == 1
    paused_checkin = client.post(
        _check_ins(goal.id),
        {"period_date": "2026-08-10", "status": "COMPLETED"},
        format="json",
    )
    assert paused_checkin.status_code == 409
    assert paused_checkin.json()["error"]["code"] == "GOAL_INVALID_TRANSITION"

    resume = client.post(_action(goal.id, "resume"), {}, format="json")
    resume_again = client.post(_action(goal.id, "resume"), {}, format="json")
    assert resume.status_code == 200
    assert resume_again.status_code == 200
    assert resume.json()["status"] == Goal.Status.ACTIVE

    complete = client.post(_action(goal.id, "complete"), {}, format="json")
    complete_again = client.post(_action(goal.id, "complete"), {}, format="json")
    cancel_completed = client.post(_action(goal.id, "cancel"), {}, format="json")
    assert complete.status_code == 200
    assert complete_again.status_code == 200
    assert complete.json()["status"] == Goal.Status.COMPLETED
    assert complete.json()["completed_at"] is not None
    assert cancel_completed.status_code == 409
    assert cancel_completed.json()["error"]["code"] == "GOAL_INVALID_TRANSITION"

    cancel = client.post(_action(other.id, "cancel"), {}, format="json")
    cancel_again = client.post(_action(other.id, "cancel"), {}, format="json")
    complete_cancelled = client.post(_action(other.id, "complete"), {}, format="json")
    assert cancel.status_code == 200
    assert cancel_again.status_code == 200
    assert cancel.json()["status"] == Goal.Status.CANCELLED
    assert complete_cancelled.status_code == 409

    _auth(client, _token(client, rahul.email))
    forbidden = client.post(_action(goal.id, "pause"), {}, format="json")
    assert forbidden.status_code == 404
    assert forbidden.json()["error"]["code"] == "GOAL_NOT_FOUND"


@pytest.mark.django_db
def test_check_in_create_retry_update_and_list(client, arjun, rahul):
    goal = _create_daily(arjun)
    counted = _create_daily(
        arjun,
        title="Pages",
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=20,
    )
    _auth(client, _token(client, arjun.email))
    created = client.post(
        _check_ins(goal.id),
        {
            "period_date": "2026-08-10",
            "status": "COMPLETED",
            "note": "done",
        },
        format="json",
    )
    identical = client.post(
        _check_ins(goal.id),
        {
            "period_date": "2026-08-10",
            "status": "COMPLETED",
            "note": "done",
        },
        format="json",
    )
    changed = client.post(
        _check_ins(goal.id),
        {
            "period_date": "2026-08-10",
            "status": "SKIPPED",
        },
        format="json",
    )

    assert created.status_code == 201
    assert identical.status_code == 200
    assert changed.status_code == 200
    _assert_checkin_payload(created.json())
    assert created.json()["id"] == identical.json()["id"] == changed.json()["id"]
    assert changed.json()["status"] == GoalCheckIn.Status.SKIPPED
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.CHECKIN_RECORDED
    ).count() == 1
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.CHECKIN_UPDATED
    ).count() == 1
    assert OutboxEvent.objects.filter(
        aggregate_id=goal.id, event_type="goal.checkin.created"
    ).count() == 1
    assert OutboxEvent.objects.filter(
        aggregate_id=goal.id, event_type="goal.checkin.updated"
    ).count() == 1

    binary_value = client.post(
        _check_ins(goal.id),
        {
            "period_date": "2026-08-11",
            "status": "COMPLETED",
            "value": 1,
        },
        format="json",
    )
    future = client.post(
        _check_ins(goal.id),
        {"period_date": "2099-01-01", "status": "COMPLETED"},
        format="json",
    )
    count_ok = client.post(
        _check_ins(counted.id),
        {
            "period_date": "2026-08-10",
            "status": "COMPLETED",
            "value": 15,
        },
        format="json",
    )
    count_missing = client.post(
        _check_ins(counted.id),
        {"period_date": "2026-08-11", "status": "COMPLETED"},
        format="json",
    )
    assert binary_value.status_code == 400
    assert binary_value.json()["error"]["code"] == "GOAL_INVALID_CHECKIN"
    assert future.status_code == 400
    assert count_ok.status_code == 201
    assert count_ok.json()["value"] == 15
    assert count_missing.status_code == 400

    listed = client.get(_check_ins(goal.id))
    filtered = client.get(
        _check_ins(goal.id),
        {"start_date": "2026-08-10", "end_date": "2026-08-10", "status": "SKIPPED"},
    )
    assert listed.status_code == 200
    items = _list_items(listed)
    assert items[0]["period_date"] >= items[-1]["period_date"]
    _assert_checkin_payload(items[0])
    assert {item["period_date"] for item in _list_items(filtered)} == {"2026-08-10"}

    _auth(client, _token(client, rahul.email))
    other_list = client.get(_check_ins(goal.id))
    other_create = client.post(
        _check_ins(goal.id),
        {"period_date": "2026-08-12", "status": "COMPLETED"},
        format="json",
    )
    assert other_list.status_code == 404
    assert other_create.status_code == 404


@pytest.mark.django_db
def test_detail_exposes_derived_progress_and_streak(client, arjun):
    today = timezone.now().astimezone(ZoneInfo(arjun.timezone)).date()
    goal = _create_daily(arjun, start_date=today)
    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=today,
        status=GoalCheckIn.Status.COMPLETED,
    )
    _auth(client, _token(client, arjun.email))
    body = client.get(_detail(goal.id)).json()
    _assert_goal_payload(body)
    assert body["is_ended"] is False
    assert body["current_streak"] == 1
    assert body["progress"]["current_period"]["required"] == 1
    assert body["progress"]["current_period"]["completed"] == 1
    assert "password" not in str(body)
    assert "secret" not in str(body).lower()
