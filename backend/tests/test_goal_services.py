import json
import threading
from contextlib import contextmanager
from datetime import date, datetime, timedelta
from unittest.mock import patch
from zoneinfo import ZoneInfo

import pytest
from django.contrib.auth import get_user_model
from django.db import connection
from django.utils import timezone

from apps.goals.exceptions import (
    GoalInvalidCheckInError,
    GoalInvalidTransitionError,
    GoalNotFoundError,
    GoalScheduleLockedError,
    GoalTimezoneLockedError,
    GoalValidationError,
)
from apps.goals.models import Goal, GoalCheckIn, GoalEvent
from apps.goals.services import (
    can_view,
    cancel_goal,
    complete_goal,
    create_goal,
    get_visible_goal,
    goal_progress,
    goal_streak,
    is_period_expected,
    pause_goal,
    record_check_in,
    resume_goal,
    update_check_in,
    update_goal,
)
from apps.outbox.models import OutboxEvent

User = get_user_model()


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun@example.com",
        name="Arjun",
        password="a-secure-password",
        timezone="Asia/Kolkata",
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul@example.com",
        name="Rahul",
        password="a-secure-password",
        timezone="UTC",
    )


def _n_per_week(creator, **overrides):
    fields = {
        "creator": creator,
        "title": "Study DSA 5 days every week",
        "start_date": date(2026, 8, 10),
        "recurrence_kind": Goal.RecurrenceKind.N_PER_PERIOD,
        "period_unit": Goal.PeriodUnit.WEEK,
        "times_per_period": 5,
    }
    fields.update(overrides)
    return create_goal(**fields)


def _daily(creator, **overrides):
    fields = {
        "creator": creator,
        "title": "Read every day",
        "start_date": date(2026, 8, 10),
        "recurrence_kind": Goal.RecurrenceKind.DAILY,
    }
    fields.update(overrides)
    return create_goal(**fields)


def _weekdays(creator, **overrides):
    fields = {
        "creator": creator,
        "title": "Study weekdays",
        "start_date": date(2026, 8, 10),
        "recurrence_kind": Goal.RecurrenceKind.WEEKLY_DAYS,
        "weekdays": [1, 2, 3, 4, 5],
    }
    fields.update(overrides)
    return create_goal(**fields)


def _aware(year, month, day, hour, minute, tz_name):
    return datetime(year, month, day, hour, minute, tzinfo=ZoneInfo(tz_name))


@contextmanager
def _clock(dt):
    with patch("django.utils.timezone.now", return_value=dt):
        yield


def _event_types(goal):
    return list(goal.events.order_by("created_at").values_list("event_type", flat=True))


def _outbox_types(goal):
    return list(
        OutboxEvent.objects.filter(aggregate_id=goal.id)
        .order_by("created_at")
        .values_list("event_type", flat=True)
    )


@pytest.mark.django_db
def test_create_goal_snapshots_timezone_and_writes_created_event(arjun):
    goal = _n_per_week(arjun, timezone=None)
    arjun.timezone = "America/New_York"
    arjun.save(update_fields=["timezone"])
    goal.refresh_from_db()

    assert goal.created_by == arjun
    assert goal.status == Goal.Status.ACTIVE
    assert goal.timezone == "Asia/Kolkata"
    assert goal.source == Goal.Source.MANUAL
    assert _event_types(goal) == [GoalEvent.EventType.CREATED]
    assert _outbox_types(goal) == ["goal.created"]
    payload = OutboxEvent.objects.get(aggregate_id=goal.id).payload
    assert payload["goal_id"] == str(goal.id)
    assert payload["created_by_user_id"] == str(arjun.id)
    assert "title" not in payload
    assert "password" not in json.dumps(payload)


@pytest.mark.django_db
def test_create_rejects_blank_title_and_invalid_timezone(arjun):
    with pytest.raises(GoalValidationError):
        _n_per_week(arjun, title="  ")
    with pytest.raises(GoalValidationError):
        _n_per_week(arjun, timezone="Not/A_Zone")
    assert Goal.objects.count() == 0
    assert OutboxEvent.objects.count() == 0


@pytest.mark.django_db
def test_create_daily_weekly_days_and_n_per_period_shapes(arjun):
    daily = _daily(arjun)
    weekdays = _weekdays(arjun, weekdays=[5, 1, 1, 3])
    n_per = _n_per_week(arjun)

    assert daily.weekdays == []
    assert daily.period_unit is None
    assert daily.times_per_period is None
    assert weekdays.weekdays == [1, 3, 5]
    assert weekdays.period_unit is None
    assert n_per.period_unit == Goal.PeriodUnit.WEEK
    assert n_per.times_per_period == 5
    assert n_per.weekdays == []


@pytest.mark.django_db
def test_create_rejects_invalid_recurrence_and_tracking(arjun):
    with pytest.raises(GoalValidationError):
        _daily(arjun, times_per_period=3)
    with pytest.raises(GoalValidationError):
        _weekdays(arjun, weekdays=[])
    with pytest.raises(GoalValidationError):
        _weekdays(arjun, weekdays=[8])
    with pytest.raises(GoalValidationError):
        _n_per_week(arjun, period_unit=None, times_per_period=None)
    with pytest.raises(GoalValidationError):
        _n_per_week(arjun, times_per_period=0)
    with pytest.raises(GoalValidationError):
        _n_per_week(arjun, weekdays=[1, 2])
    with pytest.raises(GoalValidationError):
        _daily(arjun, tracking_kind=Goal.TrackingKind.COUNT, target_value=None)
    with pytest.raises(GoalValidationError):
        _daily(arjun, tracking_kind=Goal.TrackingKind.BINARY, target_value=20)
    assert Goal.objects.count() == 0


@pytest.mark.django_db
def test_create_rolls_back_if_outbox_fails(arjun):
    with patch(
        "apps.goals.services.record_outbox_event",
        side_effect=RuntimeError("outbox failed"),
    ):
        with pytest.raises(RuntimeError):
            _n_per_week(arjun)

    assert Goal.objects.count() == 0
    assert GoalEvent.objects.count() == 0
    assert OutboxEvent.objects.count() == 0


@pytest.mark.django_db
def test_recurrence_update_allowed_before_check_in_and_locked_after(arjun):
    goal = _daily(arjun)
    update_goal(
        actor=arjun,
        goal_id=goal.id,
        recurrence_kind=Goal.RecurrenceKind.WEEKLY_DAYS,
        weekdays=[1, 2, 3, 4, 5],
    )
    goal.refresh_from_db()
    assert goal.recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.UPDATED
    ).count() == 1

    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
    )
    with pytest.raises(GoalScheduleLockedError):
        update_goal(
            actor=arjun,
            goal_id=goal.id,
            recurrence_kind=Goal.RecurrenceKind.DAILY,
        )
    with pytest.raises(GoalTimezoneLockedError):
        update_goal(actor=arjun, goal_id=goal.id, timezone="UTC")
    goal.refresh_from_db()
    assert goal.recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS
    assert goal.timezone == "Asia/Kolkata"


@pytest.mark.django_db
def test_update_title_is_allowed_after_check_in_and_noop_writes_no_event(arjun):
    goal = _daily(arjun)
    update_goal(actor=arjun, goal_id=goal.id, title="Read every day")
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.UPDATED
    ).count() == 0

    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
    )
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    update_goal(actor=arjun, goal_id=goal.id, title="Keep reading")
    goal.refresh_from_db()
    assert goal.title == "Keep reading"
    assert _outbox_types(goal) == ["goal.updated"]


@pytest.mark.django_db
def test_pause_resume_complete_cancel_and_idempotency(arjun):
    goal = _daily(arjun)
    pause_goal(actor=arjun, goal_id=goal.id)
    goal.refresh_from_db()
    assert goal.status == Goal.Status.PAUSED
    assert goal.paused_at is not None
    pause_goal(actor=arjun, goal_id=goal.id)
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.PAUSED
    ).count() == 1

    resume_goal(actor=arjun, goal_id=goal.id)
    goal.refresh_from_db()
    assert goal.status == Goal.Status.ACTIVE
    assert goal.paused_at is None
    resume_goal(actor=arjun, goal_id=goal.id)
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.RESUMED
    ).count() == 1

    complete_goal(actor=arjun, goal_id=goal.id)
    goal.refresh_from_db()
    assert goal.status == Goal.Status.COMPLETED
    assert goal.completed_at is not None
    complete_goal(actor=arjun, goal_id=goal.id)
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.COMPLETED
    ).count() == 1
    with pytest.raises(GoalInvalidTransitionError):
        pause_goal(actor=arjun, goal_id=goal.id)
    with pytest.raises(GoalInvalidTransitionError):
        cancel_goal(actor=arjun, goal_id=goal.id)

    other = _daily(arjun, title="Cancel me")
    cancel_goal(actor=arjun, goal_id=other.id)
    other.refresh_from_db()
    assert other.status == Goal.Status.CANCELLED
    cancel_goal(actor=arjun, goal_id=other.id)
    assert GoalEvent.objects.filter(
        goal=other, event_type=GoalEvent.EventType.CANCELLED
    ).count() == 1
    with pytest.raises(GoalInvalidTransitionError):
        complete_goal(actor=arjun, goal_id=other.id)


@pytest.mark.django_db
def test_complete_from_paused_clears_paused_at(arjun):
    goal = _daily(arjun)
    pause_goal(actor=arjun, goal_id=goal.id)
    complete_goal(actor=arjun, goal_id=goal.id)
    goal.refresh_from_db()
    assert goal.status == Goal.Status.COMPLETED
    assert goal.paused_at is None


@pytest.mark.django_db
def test_unrelated_user_cannot_view_or_mutate(arjun, rahul):
    goal = _daily(arjun)
    assert can_view(arjun, goal) is True
    assert can_view(rahul, goal) is False
    with pytest.raises(GoalNotFoundError):
        get_visible_goal(viewer=rahul, goal_id=goal.id)
    with pytest.raises(GoalNotFoundError):
        pause_goal(actor=rahul, goal_id=goal.id)
    with pytest.raises(GoalNotFoundError):
        record_check_in(
            actor=rahul,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )


@pytest.mark.django_db
def test_binary_and_count_check_ins_and_retries(arjun):
    binary = _daily(arjun)
    first = record_check_in(
        actor=arjun,
        goal_id=binary.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        note="done",
    )
    same = record_check_in(
        actor=arjun,
        goal_id=binary.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        note="done",
    )
    assert first.id == same.id
    assert GoalCheckIn.objects.filter(goal=binary).count() == 1
    assert GoalEvent.objects.filter(
        goal=binary, event_type=GoalEvent.EventType.CHECKIN_RECORDED
    ).count() == 1

    skipped = record_check_in(
        actor=arjun,
        goal_id=binary.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.SKIPPED,
    )
    assert skipped.id == first.id
    assert skipped.status == GoalCheckIn.Status.SKIPPED
    assert GoalEvent.objects.filter(
        goal=binary, event_type=GoalEvent.EventType.CHECKIN_UPDATED
    ).count() == 1

    with pytest.raises(GoalInvalidCheckInError):
        record_check_in(
            actor=arjun,
            goal_id=binary.id,
            period_date=date(2026, 8, 11),
            status=GoalCheckIn.Status.COMPLETED,
            value=1,
        )

    counted = _daily(
        arjun,
        title="Read 20 pages",
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=20,
        target_unit="pages",
    )
    row = record_check_in(
        actor=arjun,
        goal_id=counted.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        value=15,
    )
    assert row.value == 15
    with pytest.raises(GoalInvalidCheckInError):
        record_check_in(
            actor=arjun,
            goal_id=counted.id,
            period_date=date(2026, 8, 11),
            status=GoalCheckIn.Status.COMPLETED,
            value=None,
        )
    updated = update_check_in(
        actor=arjun,
        goal_id=counted.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        value=20,
    )
    assert updated.value == 20
    assert timezone.is_aware(updated.checked_at)


@pytest.mark.django_db
def test_check_in_rejects_future_paused_and_wrong_weekday(arjun):
    weekdays = _weekdays(arjun)
    with pytest.raises(GoalInvalidCheckInError):
        record_check_in(
            actor=arjun,
            goal_id=weekdays.id,
            period_date=date(2099, 1, 1),
            status=GoalCheckIn.Status.COMPLETED,
        )
    with pytest.raises(GoalInvalidCheckInError):
        record_check_in(
            actor=arjun,
            goal_id=weekdays.id,
            period_date=date(2026, 8, 15),
            status=GoalCheckIn.Status.COMPLETED,
        )
    pause_goal(actor=arjun, goal_id=weekdays.id)
    with pytest.raises(GoalInvalidTransitionError):
        record_check_in(
            actor=arjun,
            goal_id=weekdays.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )


@pytest.mark.django_db(transaction=True)
def test_concurrent_check_ins_do_not_duplicate_period(arjun):
    goal = _daily(arjun)
    barrier = threading.Barrier(2)
    errors = []

    def worker():
        barrier.wait()
        try:
            record_check_in(
                actor=arjun,
                goal_id=goal.id,
                period_date=date(2026, 8, 10),
                status=GoalCheckIn.Status.COMPLETED,
            )
        except Exception as exc:
            errors.append(exc)
        finally:
            connection.close()

    threads = [threading.Thread(target=worker) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert errors == []
    assert GoalCheckIn.objects.filter(goal=goal).count() == 1
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.CHECKIN_RECORDED
    ).count() == 1


@pytest.mark.django_db
def test_expected_periods_respect_recurrence_bounds_and_pause(arjun):
    daily = _daily(arjun, end_date=date(2026, 8, 12))
    weekdays = _weekdays(arjun)
    n_per = _n_per_week(arjun)
    at = _aware(2026, 8, 12, 12, 0, "Asia/Kolkata")

    assert is_period_expected(daily, date(2026, 8, 10), at=at) is True
    assert is_period_expected(daily, date(2026, 8, 9), at=at) is False
    assert is_period_expected(daily, date(2026, 8, 13), at=at) is False
    assert is_period_expected(weekdays, date(2026, 8, 10), at=at) is True
    assert is_period_expected(weekdays, date(2026, 8, 15), at=at) is False
    assert is_period_expected(n_per, date(2026, 8, 15), at=at) is True

    paused_at = _aware(2026, 8, 12, 15, 0, "Asia/Kolkata")
    with _clock(paused_at):
        pause_goal(actor=arjun, goal_id=daily.id)
    assert is_period_expected(daily, date(2026, 8, 12), at=paused_at) is False
    assert is_period_expected(daily, date(2026, 8, 11), at=paused_at) is True


@pytest.mark.django_db
def test_progress_binary_count_partial_and_paused(arjun):
    daily = _daily(arjun, start_date=date(2026, 8, 10), end_date=date(2026, 8, 14))
    at = _aware(2026, 8, 12, 12, 0, "Asia/Kolkata")
    empty = goal_progress(daily, at=at)
    assert empty["current_period"]["required"] == 1
    assert empty["current_period"]["completed"] == 0
    assert empty["consistency_percent"] == 0

    record_check_in(
        actor=arjun,
        goal_id=daily.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
    )
    record_check_in(
        actor=arjun,
        goal_id=daily.id,
        period_date=date(2026, 8, 11),
        status=GoalCheckIn.Status.SKIPPED,
    )
    progress = goal_progress(daily, at=at)
    assert progress["week_progress"]["completed"] >= 1
    assert progress["week_progress"]["required"] >= 1

    counted = _daily(
        arjun,
        title="Pages",
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=20,
        start_date=date(2026, 8, 10),
    )
    record_check_in(
        actor=arjun,
        goal_id=counted.id,
        period_date=date(2026, 8, 12),
        status=GoalCheckIn.Status.COMPLETED,
        value=15,
    )
    count_progress = goal_progress(counted, at=at)
    assert count_progress["current_period"]["value"] == 15
    assert count_progress["current_period"]["target_value"] == 20
    assert count_progress["current_period"]["completed"] == 0

    n_per = _n_per_week(arjun, start_date=date(2026, 8, 10), times_per_period=5)
    record_check_in(
        actor=arjun,
        goal_id=n_per.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
    )
    record_check_in(
        actor=arjun,
        goal_id=n_per.id,
        period_date=date(2026, 8, 11),
        status=GoalCheckIn.Status.COMPLETED,
    )
    week = goal_progress(n_per, at=at)["current_period"]
    assert week["required"] == 5
    assert week["completed"] == 2

    paused_at = _aware(2026, 8, 12, 18, 0, "Asia/Kolkata")
    with _clock(paused_at):
        pause_goal(actor=arjun, goal_id=daily.id)
    paused = goal_progress(daily, at=paused_at)
    assert paused["current_period"]["required"] == 0


@pytest.mark.django_db
def test_streaks_daily_weekday_n_per_week_pause_miss_and_repair(arjun):
    daily = _daily(arjun, start_date=date(2026, 8, 10))
    at = _aware(2026, 8, 13, 12, 0, "Asia/Kolkata")
    for day in (10, 11, 12):
        record_check_in(
            actor=arjun,
            goal_id=daily.id,
            period_date=date(2026, 8, day),
            status=GoalCheckIn.Status.COMPLETED,
        )
    assert goal_streak(daily, at=at) == 3

    paused_at = _aware(2026, 8, 13, 15, 0, "Asia/Kolkata")
    with _clock(paused_at):
        pause_goal(actor=arjun, goal_id=daily.id)
    assert goal_streak(daily, at=paused_at) == 3
    resumed_at = _aware(2026, 8, 13, 18, 0, "Asia/Kolkata")
    with _clock(resumed_at):
        resume_goal(actor=arjun, goal_id=daily.id)
        record_check_in(
            actor=arjun,
            goal_id=daily.id,
            period_date=date(2026, 8, 13),
            status=GoalCheckIn.Status.COMPLETED,
        )
    assert goal_streak(daily, at=resumed_at) == 4

    broken = _daily(arjun, title="Broken", start_date=date(2026, 8, 10))
    record_check_in(
        actor=arjun,
        goal_id=broken.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
    )
    assert goal_streak(broken, at=_aware(2026, 8, 12, 12, 0, "Asia/Kolkata")) == 0
    record_check_in(
        actor=arjun,
        goal_id=broken.id,
        period_date=date(2026, 8, 11),
        status=GoalCheckIn.Status.COMPLETED,
    )
    assert goal_streak(broken, at=_aware(2026, 8, 12, 12, 0, "Asia/Kolkata")) == 2

    weekdays = _weekdays(arjun, start_date=date(2026, 8, 10))
    record_check_in(
        actor=arjun,
        goal_id=weekdays.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
    )
    record_check_in(
        actor=arjun,
        goal_id=weekdays.id,
        period_date=date(2026, 8, 11),
        status=GoalCheckIn.Status.COMPLETED,
    )
    weekday_at = _aware(2026, 8, 15, 12, 0, "Asia/Kolkata")
    assert is_period_expected(weekdays, date(2026, 8, 15), at=weekday_at) is False
    assert goal_streak(weekdays, at=weekday_at) == 0

    n_per = _n_per_week(arjun, start_date=date(2026, 8, 3), times_per_period=2)
    for day in (3, 4, 10, 11):
        record_check_in(
            actor=arjun,
            goal_id=n_per.id,
            period_date=date(2026, 8, day),
            status=GoalCheckIn.Status.COMPLETED,
        )
    week_at = _aware(2026, 8, 12, 12, 0, "Asia/Kolkata")
    assert goal_streak(n_per, at=week_at) == 2


@pytest.mark.django_db
def test_streak_uses_goal_timezone_not_utc_date(arjun):
    goal = create_goal(
        creator=arjun,
        title="LA daily",
        timezone="America/Los_Angeles",
        start_date=date(2026, 8, 20),
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    utc_next_calendar_day = datetime(2026, 8, 21, 6, 30, tzinfo=ZoneInfo("UTC"))
    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 20),
        status=GoalCheckIn.Status.COMPLETED,
        at=utc_next_calendar_day,
    )
    assert is_period_expected(goal, date(2026, 8, 20), at=utc_next_calendar_day) is True
    assert goal_streak(goal, at=utc_next_calendar_day) == 1


@pytest.mark.django_db
def test_check_in_mutation_writes_one_event_and_one_outbox(arjun):
    goal = _daily(arjun)
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    GoalEvent.objects.filter(goal=goal).delete()
    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        note="secret note",
    )
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    outbox = OutboxEvent.objects.get(aggregate_id=goal.id)
    assert outbox.event_type == "goal.checkin.created"
    assert outbox.aggregate_type == "goal"
    assert "secret note" not in json.dumps(outbox.payload)
    assert "title" not in outbox.payload
    assert outbox.payload["period_date"] == "2026-08-10"


@pytest.mark.django_db
def test_create_defaults_start_date_to_today_in_goal_timezone(arjun):
    at = _aware(2026, 8, 20, 1, 30, "UTC")
    with _clock(at):
        goal = create_goal(
            creator=arjun,
            title="Evening practice",
            recurrence_kind=Goal.RecurrenceKind.DAILY,
        )
    assert goal.start_date == date(2026, 8, 20)
    assert goal.timezone == "Asia/Kolkata"


@pytest.mark.django_db
def test_create_rejects_end_before_start_and_daily_with_weekdays(arjun):
    with pytest.raises(GoalValidationError):
        _daily(arjun, end_date=date(2026, 8, 9))
    with pytest.raises(GoalValidationError):
        _daily(arjun, weekdays=[1])
    with pytest.raises(GoalValidationError):
        _weekdays(arjun, period_unit=Goal.PeriodUnit.WEEK)
    with pytest.raises(GoalValidationError):
        _n_per_week(arjun, times_per_period=8)
    with pytest.raises(GoalValidationError):
        create_goal(
            creator=arjun,
            title="Monthly",
            start_date=date(2026, 8, 10),
            recurrence_kind=Goal.RecurrenceKind.N_PER_PERIOD,
            period_unit="MONTH",
            times_per_period=3,
        )


@pytest.mark.django_db
def test_count_zero_is_logged_but_not_success(arjun):
    goal = _daily(
        arjun,
        title="Pages",
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=20,
    )
    row = record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        value=0,
    )
    assert row.value == 0
    at = _aware(2026, 8, 10, 20, 0, "Asia/Kolkata")
    progress = goal_progress(goal, at=at)
    assert progress["current_period"]["completed"] == 0
    assert progress["current_period"]["value"] == 0
    assert goal_streak(goal, at=at) == 0


@pytest.mark.django_db
def test_check_in_rejects_before_start_and_after_end(arjun):
    goal = _daily(arjun, start_date=date(2026, 8, 10), end_date=date(2026, 8, 12))
    with pytest.raises(GoalInvalidCheckInError):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 9),
            status=GoalCheckIn.Status.COMPLETED,
        )
    with pytest.raises(GoalInvalidCheckInError):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 13),
            status=GoalCheckIn.Status.COMPLETED,
        )


@pytest.mark.django_db
def test_update_and_check_in_rejected_on_terminal_goal(arjun):
    goal = _daily(arjun)
    complete_goal(actor=arjun, goal_id=goal.id)
    with pytest.raises(GoalInvalidTransitionError):
        update_goal(actor=arjun, goal_id=goal.id, title="Nope")
    with pytest.raises(GoalInvalidTransitionError):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )
    with pytest.raises(GoalInvalidTransitionError):
        update_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )

    active = _daily(arjun, title="Still open")
    with pytest.raises(GoalInvalidCheckInError):
        update_check_in(
            actor=arjun,
            goal_id=active.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )


@pytest.mark.django_db
def test_end_date_cannot_precede_existing_check_in(arjun):
    goal = _daily(arjun, start_date=date(2026, 8, 10))
    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 12),
        status=GoalCheckIn.Status.COMPLETED,
    )
    with pytest.raises(GoalValidationError):
        update_goal(actor=arjun, goal_id=goal.id, end_date=date(2026, 8, 11))
    update_goal(actor=arjun, goal_id=goal.id, end_date=date(2026, 8, 12))
    goal.refresh_from_db()
    assert goal.end_date == date(2026, 8, 12)


@pytest.mark.django_db
def test_pause_noop_does_not_write_outbox(arjun):
    goal = _daily(arjun)
    with _clock(_aware(2026, 8, 12, 12, 0, "Asia/Kolkata")):
        pause_goal(actor=arjun, goal_id=goal.id)
    OutboxEvent.objects.filter(aggregate_id=goal.id, event_type="goal.paused").delete()
    pause_goal(actor=arjun, goal_id=goal.id)
    assert OutboxEvent.objects.filter(
        aggregate_id=goal.id, event_type="goal.paused"
    ).count() == 0
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.PAUSED
    ).count() == 1

