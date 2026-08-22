import uuid
from datetime import date

import pytest
from django.contrib.auth import get_user_model
from django.core.exceptions import ValidationError
from django.db import IntegrityError
from django.db.models.deletion import ProtectedError
from django.utils import timezone

from apps.goals.models import Goal, GoalCheckIn, GoalEvent, GoalParticipant

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


def _create_goal(created_by, **overrides):
    fields = {
        "created_by": created_by,
        "title": "Study DSA 5 days every week",
        "start_date": date(2026, 8, 20),
        "recurrence_kind": Goal.RecurrenceKind.N_PER_PERIOD,
        "period_unit": Goal.PeriodUnit.WEEK,
        "times_per_period": 5,
    }
    fields.update(overrides)
    goal = Goal.objects.create(**fields)
    GoalParticipant.objects.create(
        goal=goal,
        user=created_by,
        role=GoalParticipant.Role.OWNER,
        status=GoalParticipant.Status.ACTIVE,
        joined_at=timezone.now(),
    )
    return goal


def _owner_participant(goal):
    return goal.participants.get(role=GoalParticipant.Role.OWNER)


def _create_check_in(goal, *, created_by, period_date, status=GoalCheckIn.Status.COMPLETED, **overrides):
    fields = {
        "goal": goal,
        "participant": _owner_participant(goal),
        "created_by": created_by,
        "period_date": period_date,
        "status": status,
        "checked_at": timezone.now(),
    }
    fields.update(overrides)
    return GoalCheckIn.objects.create(**fields)


@pytest.mark.django_db
def test_goal_id_is_uuid_v4(arjun):
    goal = _create_goal(arjun)

    assert isinstance(goal.id, uuid.UUID)
    assert goal.id.version == 4


@pytest.mark.django_db
def test_goal_owner_is_created_by_and_table_is_goals(arjun):
    goal = _create_goal(arjun, title="Gym 4 times per week")

    assert goal.created_by == arjun
    assert goal.title == "Gym 4 times per week"
    assert goal.description == ""
    assert Goal._meta.db_table == "goals"


@pytest.mark.django_db
def test_goal_timezone_snapshots_owner_timezone_at_create(arjun):
    goal = _create_goal(arjun)
    arjun.timezone = "America/New_York"
    arjun.save(update_fields=["timezone"])
    goal.refresh_from_db()

    assert goal.timezone == "Asia/Kolkata"


@pytest.mark.django_db
def test_goal_explicit_timezone_is_kept(arjun):
    goal = _create_goal(arjun, timezone="Europe/London")

    assert goal.timezone == "Europe/London"


@pytest.mark.django_db
def test_goal_rejects_invalid_timezone(arjun):
    with pytest.raises(ValidationError):
        _create_goal(arjun, timezone="Not/A_Zone")


@pytest.mark.django_db
def test_goal_default_status_is_active(arjun):
    goal = _create_goal(arjun)

    assert goal.status == Goal.Status.ACTIVE
    assert set(Goal.Status.values) == {
        Goal.Status.ACTIVE,
        Goal.Status.PAUSED,
        Goal.Status.COMPLETED,
        Goal.Status.CANCELLED,
    }
    assert "MISSED" not in Goal.Status.values
    assert "OVERDUE" not in Goal.Status.values


@pytest.mark.django_db
def test_goal_save_does_not_change_status(arjun):
    goal = _create_goal(arjun, status=Goal.Status.PAUSED, paused_at=timezone.now())
    goal.title = "Still paused"
    goal.save()
    goal.refresh_from_db()

    assert goal.status == Goal.Status.PAUSED


@pytest.mark.django_db
def test_goal_daily_recurrence_shape(arjun):
    goal = _create_goal(
        arjun,
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        weekdays=[],
        period_unit=None,
        times_per_period=None,
    )

    assert goal.recurrence_kind == Goal.RecurrenceKind.DAILY
    assert list(goal.weekdays) == []
    assert goal.period_unit is None
    assert goal.times_per_period is None


@pytest.mark.django_db
def test_goal_weekly_days_recurrence_shape(arjun):
    goal = _create_goal(
        arjun,
        recurrence_kind=Goal.RecurrenceKind.WEEKLY_DAYS,
        weekdays=[1, 2, 3, 4, 5],
        period_unit=None,
        times_per_period=None,
    )

    assert goal.recurrence_kind == Goal.RecurrenceKind.WEEKLY_DAYS
    assert list(goal.weekdays) == [1, 2, 3, 4, 5]
    assert goal.period_unit is None
    assert goal.times_per_period is None


@pytest.mark.django_db
def test_goal_n_per_period_recurrence_shape(arjun):
    goal = _create_goal(arjun)

    assert goal.recurrence_kind == Goal.RecurrenceKind.N_PER_PERIOD
    assert goal.period_unit == Goal.PeriodUnit.WEEK
    assert goal.times_per_period == 5
    assert list(goal.weekdays) == []


@pytest.mark.django_db
def test_goal_daily_rejects_times_per_period(arjun):
    with pytest.raises(IntegrityError):
        _create_goal(
            arjun,
            recurrence_kind=Goal.RecurrenceKind.DAILY,
            weekdays=[],
            period_unit=None,
            times_per_period=3,
        )


@pytest.mark.django_db
def test_goal_weekly_days_requires_weekdays(arjun):
    with pytest.raises(IntegrityError):
        _create_goal(
            arjun,
            recurrence_kind=Goal.RecurrenceKind.WEEKLY_DAYS,
            weekdays=[],
            period_unit=None,
            times_per_period=None,
        )


@pytest.mark.django_db
def test_goal_weekly_days_rejects_invalid_weekday(arjun):
    with pytest.raises((IntegrityError, ValidationError)):
        _create_goal(
            arjun,
            recurrence_kind=Goal.RecurrenceKind.WEEKLY_DAYS,
            weekdays=[1, 8],
            period_unit=None,
            times_per_period=None,
        )


@pytest.mark.django_db
def test_goal_n_per_period_requires_period_fields(arjun):
    with pytest.raises(IntegrityError):
        _create_goal(
            arjun,
            recurrence_kind=Goal.RecurrenceKind.N_PER_PERIOD,
            weekdays=[],
            period_unit=None,
            times_per_period=None,
        )


@pytest.mark.django_db
def test_goal_n_per_period_rejects_zero_times(arjun):
    with pytest.raises(IntegrityError):
        _create_goal(
            arjun,
            recurrence_kind=Goal.RecurrenceKind.N_PER_PERIOD,
            weekdays=[],
            period_unit=Goal.PeriodUnit.WEEK,
            times_per_period=0,
        )


@pytest.mark.django_db
def test_goal_n_per_period_rejects_weekdays(arjun):
    with pytest.raises(IntegrityError):
        _create_goal(
            arjun,
            recurrence_kind=Goal.RecurrenceKind.N_PER_PERIOD,
            weekdays=[1, 3],
            period_unit=Goal.PeriodUnit.WEEK,
            times_per_period=5,
        )


@pytest.mark.django_db
def test_goal_end_date_may_be_null_and_cannot_precede_start(arjun):
    open_ended = _create_goal(arjun, end_date=None)
    bounded = _create_goal(
        arjun,
        title="Bounded",
        end_date=date(2026, 9, 20),
    )

    assert open_ended.end_date is None
    assert bounded.end_date == date(2026, 9, 20)
    with pytest.raises(IntegrityError):
        _create_goal(
            arjun,
            title="Invalid dates",
            start_date=date(2026, 8, 20),
            end_date=date(2026, 8, 19),
        )


@pytest.mark.django_db
def test_goal_binary_and_count_tracking(arjun):
    binary = _create_goal(arjun)
    count = _create_goal(
        arjun,
        title="Read 20 pages per day",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        weekdays=[],
        period_unit=None,
        times_per_period=None,
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=20,
        target_unit="pages",
    )

    assert binary.tracking_kind == Goal.TrackingKind.BINARY
    assert binary.target_value is None
    assert binary.target_unit == ""
    assert count.tracking_kind == Goal.TrackingKind.COUNT
    assert count.target_value == 20
    assert count.target_unit == "pages"
    assert binary.source == Goal.Source.MANUAL
    assert "SHARED" not in Goal.Source.values


@pytest.mark.django_db
def test_goal_basemodel_timestamps_are_timezone_aware(arjun):
    goal = _create_goal(arjun)

    assert timezone.is_aware(goal.created_at)
    assert timezone.is_aware(goal.updated_at)
    assert goal.paused_at is None
    assert goal.completed_at is None
    assert goal.cancelled_at is None


@pytest.mark.django_db
def test_check_in_id_is_uuid_v4(arjun):
    goal = _create_goal(arjun)
    check_in = _create_check_in(goal, created_by=arjun, period_date=date(2026, 8, 20))

    assert isinstance(check_in.id, uuid.UUID)
    assert check_in.id.version == 4
    assert GoalCheckIn._meta.db_table == "goal_check_ins"
    assert check_in.participant == _owner_participant(goal)


@pytest.mark.django_db
def test_check_in_unique_goal_participant_and_period_date(arjun):
    goal = _create_goal(arjun)
    _create_check_in(goal, created_by=arjun, period_date=date(2026, 8, 20))

    with pytest.raises(IntegrityError):
        _create_check_in(
            goal,
            created_by=arjun,
            period_date=date(2026, 8, 20),
            status=GoalCheckIn.Status.SKIPPED,
        )


@pytest.mark.django_db
def test_check_in_same_date_allowed_on_different_goals(arjun):
    first = _create_goal(arjun)
    second = _create_goal(arjun, title="Other goal")
    _create_check_in(first, created_by=arjun, period_date=date(2026, 8, 20))
    other = _create_check_in(second, created_by=arjun, period_date=date(2026, 8, 20))

    assert other.period_date == date(2026, 8, 20)


@pytest.mark.django_db
def test_check_in_statuses_and_binary_count_value(arjun):
    goal = _create_goal(arjun)
    binary = _create_check_in(
        goal,
        created_by=arjun,
        period_date=date(2026, 8, 20),
        value=None,
        note="",
    )
    skipped = _create_check_in(
        goal,
        created_by=arjun,
        period_date=date(2026, 8, 21),
        status=GoalCheckIn.Status.SKIPPED,
        value=None,
    )
    counted = _create_check_in(
        goal,
        created_by=arjun,
        period_date=date(2026, 8, 22),
        value=20,
        note="chapter 3",
    )

    assert binary.value is None
    assert skipped.status == GoalCheckIn.Status.SKIPPED
    assert counted.value == 20
    assert counted.note == "chapter 3"
    assert set(GoalCheckIn.Status.values) == {
        GoalCheckIn.Status.COMPLETED,
        GoalCheckIn.Status.SKIPPED,
    }
    assert "MISSED" not in GoalCheckIn.Status.values
    assert timezone.is_aware(binary.checked_at)
    assert timezone.is_aware(binary.created_at)


@pytest.mark.django_db
def test_check_in_belongs_to_goal(arjun):
    goal = _create_goal(arjun)
    check_in = _create_check_in(goal, created_by=arjun, period_date=date(2026, 8, 20))

    assert check_in.goal == goal
    assert list(goal.check_ins.all()) == [check_in]


@pytest.mark.django_db
def test_event_id_is_uuid_v4_with_nullable_actor(arjun, rahul):
    goal = _create_goal(arjun)
    event = GoalEvent.objects.create(
        goal=goal,
        actor=rahul,
        event_type=GoalEvent.EventType.CREATED,
        metadata={"fields": ["title"]},
    )
    system_event = GoalEvent.objects.create(
        goal=goal,
        actor=None,
        event_type=GoalEvent.EventType.CHECKIN_RECORDED,
    )

    event.refresh_from_db()
    system_event.refresh_from_db()

    assert isinstance(event.id, uuid.UUID)
    assert event.id.version == 4
    assert event.goal == goal
    assert event.actor == rahul
    assert event.event_type == GoalEvent.EventType.CREATED
    assert event.metadata == {"fields": ["title"]}
    assert system_event.actor is None
    assert system_event.metadata == {}
    assert timezone.is_aware(event.created_at)
    assert GoalEvent._meta.db_table == "goal_events"
    assert set(GoalEvent.EventType.values) == {
        GoalEvent.EventType.CREATED,
        GoalEvent.EventType.UPDATED,
        GoalEvent.EventType.PAUSED,
        GoalEvent.EventType.RESUMED,
        GoalEvent.EventType.COMPLETED,
        GoalEvent.EventType.CANCELLED,
        GoalEvent.EventType.CHECKIN_RECORDED,
        GoalEvent.EventType.CHECKIN_UPDATED,
        GoalEvent.EventType.PARTICIPANT_INVITED,
        GoalEvent.EventType.PARTICIPANT_JOINED,
        GoalEvent.EventType.PARTICIPANT_DECLINED,
        GoalEvent.EventType.PARTICIPANT_LEFT,
        GoalEvent.EventType.PARTICIPANT_REMOVED,
        GoalEvent.EventType.GOAL_OWNERSHIP_TRANSFERRED,
        GoalEvent.EventType.PARTICIPANT_REINVITED,
    }


@pytest.mark.django_db
def test_deleting_goal_cascades_check_ins_and_events(arjun):
    goal = _create_goal(arjun)
    _create_check_in(goal, created_by=arjun, period_date=date(2026, 8, 20))
    GoalEvent.objects.create(
        goal=goal,
        actor=arjun,
        event_type=GoalEvent.EventType.CREATED,
    )
    goal_id = goal.id
    goal.delete()

    assert Goal.objects.filter(id=goal_id).exists() is False
    assert GoalCheckIn.objects.filter(goal_id=goal_id).exists() is False
    assert GoalEvent.objects.filter(goal_id=goal_id).exists() is False
    assert GoalParticipant.objects.filter(goal_id=goal_id).exists() is False


@pytest.mark.django_db
def test_deleting_actor_sets_event_actor_null(arjun, rahul):
    goal = _create_goal(arjun)
    event = GoalEvent.objects.create(
        goal=goal,
        actor=rahul,
        event_type=GoalEvent.EventType.COMPLETED,
    )
    rahul.delete()
    event.refresh_from_db()

    assert event.actor_id is None
    assert Goal.objects.filter(id=goal.id).exists() is True


@pytest.mark.django_db
def test_deleting_created_by_user_is_protected(arjun):
    goal = _create_goal(arjun)

    with pytest.raises(ProtectedError):
        arjun.delete()

    assert Goal.objects.filter(id=goal.id).exists() is True


@pytest.mark.django_db
def test_goal_creates_owner_active_participant(arjun):
    goal = _create_goal(arjun)
    owner = _owner_participant(goal)

    assert owner.user == arjun
    assert owner.role == GoalParticipant.Role.OWNER
    assert owner.status == GoalParticipant.Status.ACTIVE
    assert owner.joined_at is not None
    assert owner.invited_at is None
    assert owner.left_at is None
    assert GoalParticipant._meta.db_table == "goal_participants"


@pytest.mark.django_db
def test_goal_participant_unique_per_user(arjun):
    goal = _create_goal(arjun)

    with pytest.raises(IntegrityError):
        GoalParticipant.objects.create(
            goal=goal,
            user=arjun,
            role=GoalParticipant.Role.PARTICIPANT,
            status=GoalParticipant.Status.INVITED,
            invited_at=timezone.now(),
        )


@pytest.mark.django_db
def test_check_in_same_period_allowed_for_different_participants(arjun, rahul):
    goal = _create_goal(arjun)
    other = GoalParticipant.objects.create(
        goal=goal,
        user=rahul,
        role=GoalParticipant.Role.PARTICIPANT,
        status=GoalParticipant.Status.ACTIVE,
        joined_at=timezone.now(),
    )
    _create_check_in(goal, created_by=arjun, period_date=date(2026, 8, 20))
    second = GoalCheckIn.objects.create(
        goal=goal,
        participant=other,
        created_by=rahul,
        period_date=date(2026, 8, 20),
        status=GoalCheckIn.Status.COMPLETED,
        checked_at=timezone.now(),
    )

    assert second.participant == other
    assert GoalCheckIn.objects.filter(goal=goal, period_date=date(2026, 8, 20)).count() == 2


@pytest.mark.django_db
def test_deleting_participant_user_is_protected(arjun, rahul):
    goal = _create_goal(arjun)
    GoalParticipant.objects.create(
        goal=goal,
        user=rahul,
        role=GoalParticipant.Role.PARTICIPANT,
        status=GoalParticipant.Status.ACTIVE,
        joined_at=timezone.now(),
    )

    with pytest.raises(ProtectedError):
        rahul.delete()


@pytest.mark.django_db
def test_deleting_participant_cascades_check_ins(arjun):
    goal = _create_goal(arjun)
    owner = _owner_participant(goal)
    check_in = _create_check_in(goal, created_by=arjun, period_date=date(2026, 8, 20))
    check_in_id = check_in.id
    owner.delete()

    assert GoalCheckIn.objects.filter(id=check_in_id).exists() is False
    assert Goal.objects.filter(id=goal.id).exists() is True


@pytest.mark.django_db
def test_create_goal_service_creates_owner_participant(arjun):
    from apps.goals.services import create_goal

    goal = create_goal(
        creator=arjun,
        title="Walk daily",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    owner = goal.participants.get()

    assert owner.role == GoalParticipant.Role.OWNER
    assert owner.status == GoalParticipant.Status.ACTIVE
    assert owner.user == arjun
