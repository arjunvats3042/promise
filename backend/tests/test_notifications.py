import datetime
import uuid
import pytest
from django.db import IntegrityError
from django.utils import timezone as django_timezone

from apps.notifications.models import Reminder, UserNotificationPreferences
from apps.notifications.services import (
    cancel_reminders_for_entity,
    get_or_create_preferences,
    mark_reminder_dispatched,
    mark_reminder_failed,
    mark_reminder_suppressed,
    schedule_reminder,
)
from apps.users.models import User


@pytest.fixture
def test_user(db):
    return User.objects.create_user(
        email="ada@example.com",
        name="Ada Lovelace",
        timezone="Europe/London",
        password="correct-horse-battery-staple",
    )


@pytest.fixture
def other_user(db):
    return User.objects.create_user(
        email="charles@example.com",
        name="Charles Babbage",
        timezone="America/New_York",
        password="correct-horse-battery-staple",
    )


# ---------------------------------------------------------------------------
# Notification Preferences Tests
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_preferences_defaults_and_creation(test_user):
    prefs = get_or_create_preferences(test_user)
    assert prefs.user == test_user
    assert prefs.enabled is True
    assert prefs.commitments_due_soon is True
    assert prefs.commitments_due_now is True
    assert prefs.commitments_overdue is True
    assert prefs.goals_daily_reminder is True
    assert prefs.goals_daily_reminder_time == datetime.time(8, 30)
    assert prefs.goals_evening_reminder is True
    assert prefs.goals_evening_reminder_time == datetime.time(20, 30)
    assert prefs.quiet_hours_enabled is True
    assert prefs.quiet_hours_start == datetime.time(22, 0)
    assert prefs.quiet_hours_end == datetime.time(8, 0)


@pytest.mark.django_db
def test_preferences_persistence_and_update(test_user):
    prefs = get_or_create_preferences(test_user)
    prefs.commitments_due_soon = False
    prefs.quiet_hours_start = datetime.time(23, 0)
    prefs.save()

    reloaded = UserNotificationPreferences.objects.get(user=test_user)
    assert reloaded.commitments_due_soon is False
    assert reloaded.quiet_hours_start == datetime.time(23, 0)


@pytest.mark.django_db
def test_one_preference_record_per_user(test_user):
    UserNotificationPreferences.objects.create(user=test_user)
    with pytest.raises(IntegrityError):
        UserNotificationPreferences.objects.create(user=test_user)


# ---------------------------------------------------------------------------
# Reminder Identity & Idempotency Tests
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_reminder_identity_key_computation(test_user):
    cid = uuid.uuid4()
    target_dt = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)
    key = Reminder.compute_identity_key(
        user_id=test_user.id,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        target=target_dt,
    )
    assert key == f"{test_user.id}:COMMITMENT:{cid}:commitment.due_soon:{target_dt.isoformat()}"


@pytest.mark.django_db
def test_reminder_duplicate_identity_rejected_at_db_level(test_user):
    cid = uuid.uuid4()
    target_dt = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)
    key = Reminder.compute_identity_key(test_user.id, "COMMITMENT", cid, "commitment.due_soon", target_dt)

    Reminder.objects.create(
        user=test_user,
        entity_type=Reminder.EntityType.COMMITMENT,
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=target_dt - datetime.timedelta(hours=1),
        target_timestamp=target_dt,
        identity_key=key,
    )

    with pytest.raises(IntegrityError):
        Reminder.objects.create(
            user=test_user,
            entity_type=Reminder.EntityType.COMMITMENT,
            entity_id=cid,
            event_type="commitment.due_soon",
            scheduled_for=target_dt - datetime.timedelta(hours=1),
            target_timestamp=target_dt,
            identity_key=key,
        )


@pytest.mark.django_db
def test_different_targets_allow_distinct_reminders(test_user):
    cid = uuid.uuid4()
    target1 = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)
    target2 = datetime.datetime(2026, 8, 23, 14, 0, tzinfo=datetime.timezone.utc)

    rem1, created1 = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=target1 - datetime.timedelta(hours=1),
        target_timestamp=target1,
    )
    rem2, created2 = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=target2 - datetime.timedelta(hours=1),
        target_timestamp=target2,
    )

    assert created1 is True
    assert created2 is True
    assert rem1.id != rem2.id
    assert rem1.identity_key != rem2.identity_key


@pytest.mark.django_db
def test_different_event_types_allow_distinct_reminders(test_user):
    cid = uuid.uuid4()
    target = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)

    rem1, created1 = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=target - datetime.timedelta(hours=1),
        target_timestamp=target,
    )
    rem2, created2 = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_now",
        scheduled_for=target,
        target_timestamp=target,
    )

    assert created1 is True
    assert created2 is True
    assert rem1.id != rem2.id


@pytest.mark.django_db
def test_different_users_allow_distinct_reminders(test_user, other_user):
    gid = uuid.uuid4()
    period = "2026-08-22"
    now = django_timezone.now()

    rem1, created1 = schedule_reminder(
        user=test_user,
        entity_type="GOAL",
        entity_id=gid,
        event_type="goal.today_practice",
        scheduled_for=now,
        target_period=period,
    )
    rem2, created2 = schedule_reminder(
        user=other_user,
        entity_type="GOAL",
        entity_id=gid,
        event_type="goal.today_practice",
        scheduled_for=now,
        target_period=period,
    )

    assert created1 is True
    assert created2 is True
    assert rem1.identity_key != rem2.identity_key


# ---------------------------------------------------------------------------
# Concurrency & Lifecycle Resolution Tests
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_idempotent_schedule_reminder_returns_existing(test_user):
    cid = uuid.uuid4()
    target = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)
    scheduled = target - datetime.timedelta(hours=1)

    rem1, created1 = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=scheduled,
        target_timestamp=target,
    )
    assert created1 is True

    rem2, created2 = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=scheduled,
        target_timestamp=target,
    )
    assert created2 is False
    assert rem1.id == rem2.id
    assert Reminder.objects.count() == 1


@pytest.mark.django_db
def test_cancel_reminders_for_entity(test_user):
    cid = uuid.uuid4()
    target = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)

    schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=target - datetime.timedelta(hours=1),
        target_timestamp=target,
    )
    schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_now",
        scheduled_for=target,
        target_timestamp=target,
    )

    cancelled_count = cancel_reminders_for_entity("COMMITMENT", cid, reason="RESOLVED")
    assert cancelled_count == 2

    active_reminders = Reminder.objects.filter(entity_id=cid, status=Reminder.ReminderStatus.SCHEDULED)
    assert active_reminders.count() == 0

    all_reminders = Reminder.objects.filter(entity_id=cid)
    assert all_reminders.count() == 2
    for r in all_reminders:
        assert r.status == Reminder.ReminderStatus.CANCELLED
        assert r.cancellation_reason == "RESOLVED"
        assert r.cancelled_at is not None


@pytest.mark.django_db
def test_mark_reminder_dispatched(test_user):
    cid = uuid.uuid4()
    target = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=target - datetime.timedelta(hours=1),
        target_timestamp=target,
    )

    dispatched = mark_reminder_dispatched(reminder.id)
    assert dispatched is not None
    assert dispatched.status == Reminder.ReminderStatus.DISPATCHED
    assert dispatched.dispatched_at is not None
    assert dispatched.attempts == 1


@pytest.mark.django_db
def test_mark_reminder_suppressed(test_user):
    cid = uuid.uuid4()
    target = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=target - datetime.timedelta(hours=1),
        target_timestamp=target,
    )

    suppressed = mark_reminder_suppressed(reminder.id, reason="QUIET_HOURS")
    assert suppressed is not None
    assert suppressed.status == Reminder.ReminderStatus.SUPPRESSED
    assert suppressed.cancellation_reason == "QUIET_HOURS"


@pytest.mark.django_db
def test_mark_reminder_failed(test_user):
    cid = uuid.uuid4()
    target = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=datetime.timezone.utc)
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=cid,
        event_type="commitment.due_soon",
        scheduled_for=target - datetime.timedelta(hours=1),
        target_timestamp=target,
    )

    failed = mark_reminder_failed(reminder.id, error="Network timeout to FCM")
    assert failed is not None
    assert failed.status == Reminder.ReminderStatus.FAILED
    assert failed.attempts == 1
    assert failed.last_error == "Network timeout to FCM"
