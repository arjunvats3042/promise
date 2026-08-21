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
    GoalAlreadyParticipantError,
    GoalCannotRemoveOwnerError,
    GoalInvalidParticipantStateError,
    GoalInvalidTransitionError,
    GoalInviteExpiredError,
    GoalInviteRevokedError,
    GoalNotFoundError,
    GoalOwnerCannotLeaveError,
    GoalValidationError,
)
from apps.goals.models import Goal, GoalCheckIn, GoalEvent, GoalParticipant
from apps.goals.services import (
    accept_invitation,
    can_check_in,
    can_invite,
    can_leave,
    can_manage_goal,
    can_remove_participant,
    can_view,
    cancel_goal,
    collective_progress,
    create_goal,
    decline_invitation,
    get_participant,
    get_visible_goal,
    goal_progress,
    goal_streak,
    individual_progress,
    invite_participant,
    is_invite_preview,
    leave_goal,
    list_goal_check_ins,
    list_visible_goals,
    record_check_in,
    remove_participant,
    revoke_invitation,
    update_goal,
)
from apps.outbox.models import OutboxEvent

User = get_user_model()


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun-shared@example.com",
        name="Arjun",
        password="a-secure-password",
        timezone="Asia/Kolkata",
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul-shared@example.com",
        name="Rahul",
        password="a-secure-password",
        timezone="UTC",
    )


@pytest.fixture
def neha(db):
    return User.objects.create_user(
        email="neha-shared@example.com",
        name="Neha",
        password="a-secure-password",
        timezone="UTC",
    )


def _daily(creator, **overrides):
    fields = {
        "creator": creator,
        "title": "Read every day",
        "start_date": date(2026, 8, 10),
        "recurrence_kind": Goal.RecurrenceKind.DAILY,
    }
    fields.update(overrides)
    return create_goal(**fields)


def _aware(year, month, day, hour, minute, tz_name):
    return datetime(year, month, day, hour, minute, tzinfo=ZoneInfo(tz_name))


@contextmanager
def _clock(dt):
    with patch("django.utils.timezone.now", return_value=dt):
        yield


def _invite_and_accept(owner, goal, invitee):
    invite_participant(actor=owner, goal_id=goal.id, user_id=invitee.id)
    return accept_invitation(actor=invitee, goal_id=goal.id)


def _outbox_types(goal):
    return list(
        OutboxEvent.objects.filter(aggregate_id=goal.id)
        .order_by("created_at")
        .values_list("event_type", flat=True)
    )


@pytest.mark.django_db
def test_invite_accept_decline_revoke_remove_leave(arjun, rahul):
    goal = _daily(arjun)
    invited = invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert invited.status == GoalParticipant.Status.INVITED
    assert is_invite_preview(rahul, goal) is True
    assert can_view(rahul, goal) is True
    assert can_check_in(rahul, goal) is False

    again = invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert again.id == invited.id
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.PARTICIPANT_INVITED
    ).count() == 1

    joined = accept_invitation(actor=rahul, goal_id=goal.id)
    assert joined.status == GoalParticipant.Status.ACTIVE
    assert can_check_in(rahul, goal) is True
    assert can_leave(rahul, goal) is True
    assert can_manage_goal(rahul, goal) is False

    left = leave_goal(actor=rahul, goal_id=goal.id)
    assert left.status == GoalParticipant.Status.LEFT
    assert can_view(rahul, goal) is False

    reopened = invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert reopened.status == GoalParticipant.Status.INVITED
    decline_invitation(actor=rahul, goal_id=goal.id)
    assert get_participant(goal, rahul).status == GoalParticipant.Status.DECLINED

    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    revoke_invitation(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert get_participant(goal, rahul).status == GoalParticipant.Status.REMOVED

    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    accept_invitation(actor=rahul, goal_id=goal.id)
    remove_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert get_participant(goal, rahul).status == GoalParticipant.Status.REMOVED


@pytest.mark.django_db
def test_invite_rejects_unknown_user_self_and_terminal(arjun, rahul):
    goal = _daily(arjun)
    with pytest.raises(GoalValidationError):
        invite_participant(
            actor=arjun,
            goal_id=goal.id,
            user_id="00000000-0000-4000-8000-000000000099",
        )
    with pytest.raises(GoalInvalidParticipantStateError):
        invite_participant(actor=arjun, goal_id=goal.id, user_id=arjun.id)

    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    accept_invitation(actor=rahul, goal_id=goal.id)
    with pytest.raises(GoalAlreadyParticipantError):
        invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)

    cancel_goal(actor=arjun, goal_id=goal.id)
    with pytest.raises(GoalInvalidTransitionError):
        invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)


@pytest.mark.django_db
def test_owner_cannot_leave_or_be_removed(arjun, rahul):
    goal = _daily(arjun)
    with pytest.raises(GoalOwnerCannotLeaveError):
        leave_goal(actor=arjun, goal_id=goal.id)
    with pytest.raises(GoalCannotRemoveOwnerError):
        remove_participant(actor=arjun, goal_id=goal.id, user_id=arjun.id)
    assert can_invite(arjun, goal) is True
    assert can_remove_participant(arjun, goal) is True


@pytest.mark.django_db
def test_invite_expiry_and_revoked_accept(arjun, rahul):
    goal = _daily(arjun)
    with _clock(_aware(2026, 8, 10, 9, 0, "Asia/Kolkata")):
        invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    with _clock(_aware(2026, 8, 25, 9, 0, "Asia/Kolkata")):
        with pytest.raises(GoalInviteExpiredError):
            accept_invitation(actor=rahul, goal_id=goal.id)

    with _clock(_aware(2026, 8, 26, 9, 0, "Asia/Kolkata")):
        invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
        revoke_invitation(actor=arjun, goal_id=goal.id, user_id=rahul.id)
        with pytest.raises(GoalInviteRevokedError):
            accept_invitation(actor=rahul, goal_id=goal.id)


@pytest.mark.django_db
def test_authorization_matrix(arjun, rahul, neha):
    goal = _daily(arjun)
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)

    assert can_view(arjun, goal) is True
    assert can_manage_goal(arjun, goal) is True
    assert is_invite_preview(rahul, goal) is True
    assert can_view(neha, goal) is False
    with pytest.raises(GoalNotFoundError):
        get_visible_goal(viewer=neha, goal_id=goal.id)
    with pytest.raises(GoalNotFoundError):
        update_goal(actor=rahul, goal_id=goal.id, title="Hacked")

    accept_invitation(actor=rahul, goal_id=goal.id)
    assert goal.id in {g.id for g in list_visible_goals(viewer=rahul)}
    assert can_check_in(rahul, goal) is True

    leave_goal(actor=rahul, goal_id=goal.id)
    assert can_view(rahul, goal) is False
    with pytest.raises(GoalNotFoundError):
        record_check_in(
            actor=rahul,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )


@pytest.mark.django_db
def test_check_in_participant_invariant_and_personal_compat(arjun, rahul):
    goal = _daily(arjun)
    with _clock(_aware(2026, 8, 10, 12, 0, "Asia/Kolkata")):
        own = record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )
    assert own.participant.user_id == arjun.id
    assert own.created_by_id == arjun.id
    assert own.participant_id == get_participant(goal, arjun).id

    _invite_and_accept(arjun, goal, rahul)
    with _clock(_aware(2026, 8, 11, 12, 0, "Asia/Kolkata")):
        other = record_check_in(
            actor=rahul,
            goal_id=goal.id,
            period_date=date(2026, 8, 11),
            status=GoalCheckIn.Status.COMPLETED,
        )
    assert other.participant.user_id == rahul.id
    assert GoalCheckIn.objects.filter(goal=goal, period_date=date(2026, 8, 11)).count() == 1

    event = GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.CHECKIN_RECORDED
    ).latest("created_at")
    assert "participant_id" in event.metadata
    assert OutboxEvent.objects.filter(
        aggregate_id=goal.id, event_type="goal.checkin.created"
    ).latest("created_at").payload["participant_id"] == str(other.participant_id)


@pytest.mark.django_db
def test_invited_cannot_see_check_in_history(arjun, rahul):
    goal = _daily(arjun)
    with _clock(_aware(2026, 8, 10, 12, 0, "Asia/Kolkata")):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    with pytest.raises(GoalNotFoundError):
        list_goal_check_ins(viewer=rahul, goal_id=goal.id)


@pytest.mark.django_db
def test_collective_and_individual_progress(arjun, rahul):
    goal = _daily(arjun)
    with _clock(_aware(2026, 8, 10, 9, 0, "Asia/Kolkata")):
        _invite_and_accept(arjun, goal, rahul)
    at = _aware(2026, 8, 12, 18, 0, "Asia/Kolkata")
    with _clock(at):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 12),
            status=GoalCheckIn.Status.COMPLETED,
        )
    goal = Goal.objects.prefetch_related("check_ins", "events", "participants").get(
        id=goal.id
    )
    owner = get_participant(goal, arjun)
    member = get_participant(goal, rahul)
    individual = individual_progress(goal, owner, at=at)
    assert individual["current_period"]["completed"] == 1
    assert individual_progress(goal, member, at=at)["current_period"]["completed"] == 0

    collective = collective_progress(goal, at=at)
    assert collective["current_period"]["required_participants"] == 2
    assert collective["current_period"]["completed_participants"] == 1


@pytest.mark.django_db
def test_collective_count_and_n_per_week(arjun, rahul):
    goal = create_goal(
        creator=arjun,
        title="Pages",
        start_date=date(2026, 8, 10),
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=10,
    )
    with _clock(_aware(2026, 8, 10, 8, 0, "Asia/Kolkata")):
        _invite_and_accept(arjun, goal, rahul)
    at = _aware(2026, 8, 10, 18, 0, "Asia/Kolkata")
    with _clock(at):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
            value=10,
        )
        record_check_in(
            actor=rahul,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
            value=5,
        )
    goal = Goal.objects.prefetch_related("check_ins", "events", "participants").get(
        id=goal.id
    )
    collective = collective_progress(goal, at=at)["current_period"]
    assert collective["value_sum"] == 10
    assert collective["target_sum"] == 20

    n_goal = create_goal(
        creator=arjun,
        title="Five a week",
        start_date=date(2026, 8, 10),
        recurrence_kind=Goal.RecurrenceKind.N_PER_PERIOD,
        period_unit=Goal.PeriodUnit.WEEK,
        times_per_period=2,
    )
    with _clock(_aware(2026, 8, 10, 8, 0, "Asia/Kolkata")):
        _invite_and_accept(arjun, n_goal, rahul)
    at2 = _aware(2026, 8, 12, 18, 0, "Asia/Kolkata")
    with _clock(at2):
        record_check_in(
            actor=arjun,
            goal_id=n_goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )
        record_check_in(
            actor=arjun,
            goal_id=n_goal.id,
            period_date=date(2026, 8, 11),
            status=GoalCheckIn.Status.COMPLETED,
        )
        record_check_in(
            actor=rahul,
            goal_id=n_goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )
    n_goal = Goal.objects.prefetch_related(
        "check_ins", "events", "participants"
    ).get(id=n_goal.id)
    n_collective = collective_progress(n_goal, at=at2)["current_period"]
    assert n_collective["required"] == 4
    assert n_collective["completed"] == 3


@pytest.mark.django_db
def test_join_mid_goal_and_leave_freezes_streak(arjun, rahul):
    goal = _daily(arjun)
    with _clock(_aware(2026, 8, 10, 12, 0, "Asia/Kolkata")):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )
    with _clock(_aware(2026, 8, 12, 9, 0, "Asia/Kolkata")):
        _invite_and_accept(arjun, goal, rahul)
        record_check_in(
            actor=rahul,
            goal_id=goal.id,
            period_date=date(2026, 8, 12),
            status=GoalCheckIn.Status.COMPLETED,
        )
    goal = Goal.objects.prefetch_related("check_ins", "events", "participants").get(
        id=goal.id
    )
    member = get_participant(goal, rahul)
    at = _aware(2026, 8, 12, 18, 0, "Asia/Kolkata")
    assert goal_streak(goal, at=at, participant=member) == 1

    with _clock(_aware(2026, 8, 13, 9, 0, "Asia/Kolkata")):
        record_check_in(
            actor=rahul,
            goal_id=goal.id,
            period_date=date(2026, 8, 13),
            status=GoalCheckIn.Status.COMPLETED,
        )
        leave_goal(actor=rahul, goal_id=goal.id)
    goal = Goal.objects.prefetch_related("check_ins", "events", "participants").get(
        id=goal.id
    )
    member = get_participant(goal, rahul)
    frozen = goal_streak(
        goal, at=_aware(2026, 8, 20, 12, 0, "Asia/Kolkata"), participant=member
    )
    assert frozen == 2


@pytest.mark.django_db
def test_skipped_breaks_individual_streak(arjun):
    goal = _daily(arjun)
    owner = get_participant(goal, arjun)
    with _clock(_aware(2026, 8, 10, 12, 0, "Asia/Kolkata")):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )
    with _clock(_aware(2026, 8, 11, 12, 0, "Asia/Kolkata")):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 11),
            status=GoalCheckIn.Status.SKIPPED,
        )
    with _clock(_aware(2026, 8, 12, 12, 0, "Asia/Kolkata")):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 12),
            status=GoalCheckIn.Status.COMPLETED,
        )
    goal = Goal.objects.prefetch_related("check_ins", "events", "participants").get(
        id=goal.id
    )
    assert (
        goal_streak(
            goal,
            at=_aware(2026, 8, 12, 18, 0, "Asia/Kolkata"),
            participant=owner,
        )
        == 1
    )


@pytest.mark.django_db
def test_participant_events_and_outbox_and_noop_invite(arjun, rahul):
    goal = _daily(arjun)
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert _outbox_types(goal).count("goal.participant.invited") == 1
    payload = OutboxEvent.objects.filter(
        aggregate_id=goal.id, event_type="goal.participant.invited"
    ).latest("created_at").payload
    assert payload["participant_id"]
    assert payload["target_user_id"] == str(rahul.id)
    assert "title" not in payload
    assert "password" not in json.dumps(payload)

    before_events = GoalEvent.objects.filter(goal=goal).count()
    before_outbox = OutboxEvent.objects.filter(aggregate_id=goal.id).count()
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert GoalEvent.objects.filter(goal=goal).count() == before_events
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == before_outbox

    accept_invitation(actor=rahul, goal_id=goal.id)
    assert "goal.participant.joined" in _outbox_types(goal)


@pytest.mark.django_db
def test_invite_rolls_back_if_outbox_fails(arjun, rahul):
    goal = _daily(arjun)
    with patch(
        "apps.goals.services.record_outbox_event",
        side_effect=RuntimeError("outbox failed"),
    ):
        with pytest.raises(RuntimeError):
            invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert get_participant(goal, rahul) is None
    assert (
        GoalEvent.objects.filter(
            goal=goal, event_type=GoalEvent.EventType.PARTICIPANT_INVITED
        ).count()
        == 0
    )


@pytest.mark.django_db(transaction=True)
def test_concurrent_duplicate_invite(arjun, rahul):
    goal = _daily(arjun)
    errors = []

    def worker():
        try:
            invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
        except Exception as exc:  # noqa: BLE001
            errors.append(exc)
        finally:
            connection.close()

    threads = [threading.Thread(target=worker) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    assert GoalParticipant.objects.filter(goal=goal, user=rahul).count() == 1
    assert (
        GoalEvent.objects.filter(
            goal=goal, event_type=GoalEvent.EventType.PARTICIPANT_INVITED
        ).count()
        == 1
    )
    assert not any(
        isinstance(err, GoalAlreadyParticipantError) for err in errors
    )


@pytest.mark.django_db(transaction=True)
def test_accept_vs_revoke_race(arjun, rahul):
    goal = _daily(arjun)
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    results = []

    def accept():
        try:
            results.append(("accept", accept_invitation(actor=rahul, goal_id=goal.id)))
        except Exception as exc:  # noqa: BLE001
            results.append(("accept", exc))
        finally:
            connection.close()

    def revoke():
        try:
            results.append(
                (
                    "revoke",
                    revoke_invitation(actor=arjun, goal_id=goal.id, user_id=rahul.id),
                )
            )
        except Exception as exc:  # noqa: BLE001
            results.append(("revoke", exc))
        finally:
            connection.close()

    threads = [threading.Thread(target=accept), threading.Thread(target=revoke)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    participant = get_participant(goal, rahul)
    assert participant.status in (
        GoalParticipant.Status.ACTIVE,
        GoalParticipant.Status.REMOVED,
    )
    assert len(results) == 2


@pytest.mark.django_db
def test_personal_progress_unchanged_shape(arjun):
    goal = _daily(arjun)
    at = _aware(2026, 8, 10, 18, 0, "Asia/Kolkata")
    with _clock(at):
        record_check_in(
            actor=arjun,
            goal_id=goal.id,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
        )
    goal = Goal.objects.prefetch_related("check_ins", "events", "participants").get(
        id=goal.id
    )
    progress = goal_progress(goal, at=at)
    assert progress["current_period"]["required"] == 1
    assert progress["current_period"]["completed"] == 1
    assert progress["week_progress"]["completed"] == 1
    assert isinstance(progress["consistency_percent"], int)
    assert goal_streak(goal, at=at) == 1
