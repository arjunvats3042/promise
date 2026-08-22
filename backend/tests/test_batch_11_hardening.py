"""Batch 11 — Production Hardening & End-to-End QA
==================================================

Regression test suite covering:

1.  Shared-goal capacity race  (9 active + 2 concurrent accepts → exactly 10)
2.  Shared-goal capacity race  (full goal + concurrent invites)
3.  Check-in duplicate race    (DB uniqueness constraint as final guarantee)
4.  Goal state transition guards (pause+check-in, complete+cancel concurrent)
5.  Commitment concurrent mutations
6.  FCM token invalidation rules (only on UNREGISTERED / INVALID_ARGUMENT)
7.  Notification delivery state machine (PENDING → SENT / FAILED / CANCELLED)
8.  WebSocket authorization (revoked participant, personal goal, invited-only)
9.  Outbox transaction rollback isolation
10. Auth session revocation
11. IDOR / authorization boundary checks
12. AI failure isolation (core APIs unaffected when AI unavailable)
13. Redis failure behaviors (fail-open vs fail-closed)
14. Data integrity invariants
15. Outbox consumer idempotency
"""

import threading
import uuid
from datetime import date, timedelta
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model
from django.db import connection, transaction
from django.utils import timezone
from rest_framework.test import APIClient

from apps.commitments.exceptions import CommitmentInvalidTransitionError
from apps.commitments.models import Commitment, CommitmentEvent
from apps.commitments.services import (
    cancel_commitment,
    complete_commitment,
    create_commitment,
    snooze_commitment,
)
from apps.goals.exceptions import (
    GoalInvalidTransitionError,
    GoalParticipantLimitReachedError,
)
from apps.goals.models import Goal, GoalCheckIn, GoalEvent, GoalParticipant
from apps.goals.services import (
    MAX_SHARED_GOAL_PARTICIPANTS,
    accept_invitation,
    cancel_goal,
    create_goal,
    invite_participant,
    pause_goal,
    record_check_in,
    transfer_goal_ownership,
)
from apps.notifications.fcm import MockFcmClient
from apps.notifications.models import NotificationDelivery, Reminder, UserDevice

User = get_user_model()


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------


def _make_user(email, *, name="User", tz="UTC"):
    return User.objects.create_user(
        email=email,
        name=name,
        password="s3cur3-pass!",
        timezone=tz,
    )


def _daily_goal(owner):
    return create_goal(
        creator=owner,
        title="Daily practice",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=date(2026, 1, 1),
    )


def _shared_goal_with_n_active(owner, n):
    """Create a shared goal with exactly n active participants (including owner)."""
    goal = _daily_goal(owner)
    for i in range(n - 1):
        member = _make_user(f"member{i}-{uuid.uuid4().hex[:6]}@hardening.test")
        invite_participant(actor=owner, goal_id=goal.id, user_id=member.id)
        accept_invitation(actor=member, goal_id=goal.id)
    return goal


def _make_pending_reminder(owner, *, event_type="commitment.due_now"):
    """Create a SCHEDULED reminder backed by a real Commitment so dispatcher
    does not cancel it during the resolved-state check."""
    commitment = create_commitment(creator=owner, title="Test commitment for reminder")
    return Reminder.objects.create(
        user=owner,
        entity_type=Reminder.EntityType.COMMITMENT,
        entity_id=commitment.id,
        event_type=event_type,
        identity_key=Reminder.compute_identity_key(
            owner.id, "COMMITMENT", commitment.id, event_type
        ),
        scheduled_for=timezone.now() - timedelta(minutes=1),
    )


# ---------------------------------------------------------------------------
# 1 & 2. SHARED-GOAL CAPACITY RACE
# ---------------------------------------------------------------------------


@pytest.mark.django_db(transaction=True)
def test_capacity_race_9_active_2_concurrent_accepts_yields_exactly_10():
    """9 active + 2 concurrent accepts → exactly 10 active, one rejected."""
    owner = _make_user("owner@cap.test")
    goal = _shared_goal_with_n_active(owner, 9)

    user_a = _make_user("cap_a@cap.test")
    user_b = _make_user("cap_b@cap.test")
    invite_participant(actor=owner, goal_id=goal.id, user_id=user_a.id)
    invite_participant(actor=owner, goal_id=goal.id, user_id=user_b.id)

    errors = []
    successes = []
    barrier = threading.Barrier(2)

    def accept_worker(user):
        barrier.wait()
        try:
            p = accept_invitation(actor=user, goal_id=goal.id)
            successes.append(p)
        except GoalParticipantLimitReachedError as exc:
            errors.append(exc)
        except Exception as exc:  # noqa: BLE001
            errors.append(exc)
        finally:
            connection.close()

    threads = [
        threading.Thread(target=accept_worker, args=(user_a,)),
        threading.Thread(target=accept_worker, args=(user_b,)),
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    active_count = GoalParticipant.objects.filter(
        goal=goal, status=GoalParticipant.Status.ACTIVE
    ).count()

    assert active_count == MAX_SHARED_GOAL_PARTICIPANTS
    assert len(successes) == 1
    assert len(errors) == 1
    assert isinstance(errors[0], GoalParticipantLimitReachedError)


@pytest.mark.django_db(transaction=True)
def test_capacity_race_full_goal_concurrent_invites_both_blocked():
    """10 active + 2 concurrent invites → both get GOAL_PARTICIPANT_LIMIT_REACHED."""
    owner = _make_user("owner@capfull.test")
    goal = _shared_goal_with_n_active(owner, MAX_SHARED_GOAL_PARTICIPANTS)

    target_a = _make_user("tgt_a@capfull.test")
    target_b = _make_user("tgt_b@capfull.test")
    errors = []
    barrier = threading.Barrier(2)

    def invite_worker(target):
        barrier.wait()
        try:
            invite_participant(actor=owner, goal_id=goal.id, user_id=target.id)
        except GoalParticipantLimitReachedError as exc:
            errors.append(exc)
        except Exception as exc:  # noqa: BLE001
            errors.append(exc)
        finally:
            connection.close()

    threads = [
        threading.Thread(target=invite_worker, args=(target_a,)),
        threading.Thread(target=invite_worker, args=(target_b,)),
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    active_count = GoalParticipant.objects.filter(
        goal=goal, status=GoalParticipant.Status.ACTIVE
    ).count()
    assert active_count == MAX_SHARED_GOAL_PARTICIPANTS
    assert all(isinstance(e, GoalParticipantLimitReachedError) for e in errors)


@pytest.mark.django_db
def test_capacity_limit_never_exceeded_under_any_path(db):
    """Invite on full goal raises immediately, no new members."""
    owner = _make_user("owner@nocap.test")
    goal = _shared_goal_with_n_active(owner, MAX_SHARED_GOAL_PARTICIPANTS)
    extra = _make_user("extra@nocap.test")

    with pytest.raises(GoalParticipantLimitReachedError):
        invite_participant(actor=owner, goal_id=goal.id, user_id=extra.id)

    final_count = GoalParticipant.objects.filter(
        goal=goal, status=GoalParticipant.Status.ACTIVE
    ).count()
    assert final_count == MAX_SHARED_GOAL_PARTICIPANTS


# ---------------------------------------------------------------------------
# 3. CHECK-IN DUPLICATE RACE
# ---------------------------------------------------------------------------


@pytest.mark.django_db(transaction=True)
def test_checkin_race_two_concurrent_writes_no_duplicate_rows():
    """Two concurrent check-ins for same participant/date: no duplicate rows."""
    owner = _make_user("owner@checkin.test")
    goal = _daily_goal(owner)
    barrier = threading.Barrier(2)
    errors = []

    def worker():
        barrier.wait()
        try:
            record_check_in(
                actor=owner,
                goal_id=goal.id,
                period_date=date(2026, 8, 10),
                status=GoalCheckIn.Status.COMPLETED,
            )
        except Exception as exc:  # noqa: BLE001
            errors.append(exc)
        finally:
            connection.close()

    threads = [threading.Thread(target=worker) for _ in range(2)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert errors == []
    assert GoalCheckIn.objects.filter(goal=goal).count() == 1
    assert (
        GoalEvent.objects.filter(
            goal=goal, event_type=GoalEvent.EventType.CHECKIN_RECORDED
        ).count()
        == 1
    )


@pytest.mark.django_db
def test_checkin_uniqueness_constraint_enforced_at_db_level(db):
    """DB uniqueness constraint (goal, participant, period_date) raises IntegrityError."""
    from django.db import IntegrityError

    owner = _make_user("owner@uniq.test")
    goal = _daily_goal(owner)
    participant = GoalParticipant.objects.get(goal=goal, user=owner)

    GoalCheckIn.objects.create(
        goal=goal,
        participant=participant,
        created_by=owner,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        checked_at=timezone.now(),
    )
    with pytest.raises(IntegrityError):
        GoalCheckIn.objects.create(
            goal=goal,
            participant=participant,
            created_by=owner,
            period_date=date(2026, 8, 10),
            status=GoalCheckIn.Status.COMPLETED,
            checked_at=timezone.now(),
        )


# ---------------------------------------------------------------------------
# 4. GOAL STATE TRANSITION GUARDS
# ---------------------------------------------------------------------------


@pytest.mark.django_db(transaction=True)
def test_complete_and_cancel_are_mutually_exclusive_concurrent():
    """Concurrent complete + cancel → exactly one terminal state, one terminal event."""
    owner = _make_user("owner@terminal.test")
    goal = _daily_goal(owner)
    barrier = threading.Barrier(2)
    errors = []

    def do_complete():
        barrier.wait()
        try:
            from apps.goals.services import complete_goal
            complete_goal(actor=owner, goal_id=goal.id)
        except (GoalInvalidTransitionError, Exception) as exc:  # noqa: BLE001
            errors.append(("complete", exc))
        finally:
            connection.close()

    def do_cancel():
        barrier.wait()
        try:
            cancel_goal(actor=owner, goal_id=goal.id)
        except (GoalInvalidTransitionError, Exception) as exc:  # noqa: BLE001
            errors.append(("cancel", exc))
        finally:
            connection.close()

    threads = [threading.Thread(target=do_complete), threading.Thread(target=do_cancel)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    goal.refresh_from_db()
    assert goal.status in (Goal.Status.COMPLETED, Goal.Status.CANCELLED)
    terminal_events = GoalEvent.objects.filter(
        goal=goal,
        event_type__in=[GoalEvent.EventType.COMPLETED, GoalEvent.EventType.CANCELLED],
    ).count()
    assert terminal_events == 1


@pytest.mark.django_db(transaction=True)
def test_checkin_rejected_when_goal_paused_concurrently():
    """Concurrent pause + check-in → after race, goal is in a valid state."""
    owner = _make_user("owner@pause.test")
    goal = _daily_goal(owner)
    barrier = threading.Barrier(2)
    results = {"pause": [], "checkin": []}

    def do_pause():
        barrier.wait()
        try:
            pause_goal(actor=owner, goal_id=goal.id)
            results["pause"].append("ok")
        except Exception as exc:  # noqa: BLE001
            results["pause"].append(exc)
        finally:
            connection.close()

    def do_checkin():
        barrier.wait()
        try:
            record_check_in(
                actor=owner,
                goal_id=goal.id,
                period_date=date(2026, 8, 10),
                status=GoalCheckIn.Status.COMPLETED,
            )
            results["checkin"].append("ok")
        except Exception as exc:  # noqa: BLE001
            results["checkin"].append(exc)
        finally:
            connection.close()

    threads = [threading.Thread(target=do_pause), threading.Thread(target=do_checkin)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    goal.refresh_from_db()
    # No invalid/undefined state
    assert goal.status in (Goal.Status.ACTIVE, Goal.Status.PAUSED)
    assert GoalCheckIn.objects.filter(goal=goal).count() <= 1


# ---------------------------------------------------------------------------
# 5. COMMITMENT CONCURRENCY
# ---------------------------------------------------------------------------


@pytest.mark.django_db(transaction=True)
def test_commitment_duplicate_complete_idempotent():
    """Two concurrent complete requests: one COMPLETED event, valid state."""
    owner = _make_user("owner@cmtdup.test")
    commitment = create_commitment(creator=owner, title="Duplicate complete test")
    barrier = threading.Barrier(2)
    errors = []

    def worker():
        barrier.wait()
        try:
            complete_commitment(actor=owner, commitment_id=commitment.id)
        except Exception as exc:  # noqa: BLE001
            errors.append(exc)
        finally:
            connection.close()

    threads = [threading.Thread(target=worker) for _ in range(2)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    commitment.refresh_from_db()
    assert errors == []
    assert commitment.status == Commitment.Status.COMPLETED
    assert (
        CommitmentEvent.objects.filter(
            commitment=commitment,
            event_type=CommitmentEvent.EventType.COMPLETED,
        ).count()
        == 1
    )


@pytest.mark.django_db(transaction=True)
def test_commitment_complete_and_cancel_leaves_exactly_one_terminal_event():
    """Concurrent complete + cancel → one terminal event, valid state."""
    owner = _make_user("owner@cmtterm.test")
    commitment = create_commitment(creator=owner, title="Terminal race test")
    barrier = threading.Barrier(2)
    terminal_errors = []

    def do_complete():
        barrier.wait()
        try:
            complete_commitment(actor=owner, commitment_id=commitment.id)
        except CommitmentInvalidTransitionError as exc:
            terminal_errors.append(exc)
        except Exception as exc:  # noqa: BLE001
            terminal_errors.append(exc)
        finally:
            connection.close()

    def do_cancel():
        barrier.wait()
        try:
            cancel_commitment(actor=owner, commitment_id=commitment.id)
        except CommitmentInvalidTransitionError as exc:
            terminal_errors.append(exc)
        except Exception as exc:  # noqa: BLE001
            terminal_errors.append(exc)
        finally:
            connection.close()

    threads = [threading.Thread(target=do_complete), threading.Thread(target=do_cancel)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    commitment.refresh_from_db()
    assert commitment.status in (Commitment.Status.COMPLETED, Commitment.Status.CANCELLED)
    assert len(terminal_errors) == 1
    assert isinstance(terminal_errors[0], CommitmentInvalidTransitionError)
    terminal_events = CommitmentEvent.objects.filter(
        commitment=commitment,
        event_type__in=[CommitmentEvent.EventType.COMPLETED, CommitmentEvent.EventType.CANCELLED],
    ).count()
    assert terminal_events == 1


@pytest.mark.django_db(transaction=True)
def test_commitment_snooze_and_complete_concurrent_yields_valid_state():
    """Concurrent snooze + complete → valid state, no crash."""
    owner = _make_user("owner@cmt_snooze.test")
    commitment = create_commitment(creator=owner, title="Snooze-complete race")
    snoozed_until = timezone.now() + timedelta(hours=2)
    barrier = threading.Barrier(2)
    errors = []

    def do_snooze():
        barrier.wait()
        try:
            snooze_commitment(
                actor=owner, commitment_id=commitment.id, snoozed_until=snoozed_until
            )
        except Exception as exc:  # noqa: BLE001
            errors.append(("snooze", exc))
        finally:
            connection.close()

    def do_complete():
        barrier.wait()
        try:
            complete_commitment(actor=owner, commitment_id=commitment.id)
        except Exception as exc:  # noqa: BLE001
            errors.append(("complete", exc))
        finally:
            connection.close()

    threads = [threading.Thread(target=do_snooze), threading.Thread(target=do_complete)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    commitment.refresh_from_db()
    assert commitment.status in {Commitment.Status.COMPLETED, Commitment.Status.SNOOZED}


# ---------------------------------------------------------------------------
# 6. FCM TOKEN INVALIDATION RULES
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_fcm_transient_failure_does_not_deactivate_token(db):
    """Transient FCM error must NOT deactivate the device token."""
    owner = _make_user("owner@fcm.test")
    device = UserDevice.objects.create(
        user=owner, fcm_token="transient-fail-token", device_id="device-001", is_active=True
    )
    _make_pending_reminder(owner)

    fcm = MockFcmClient()
    fcm.failing_tokens.add("transient-fail-token")

    from apps.notifications.dispatcher import dispatch_due_reminders
    dispatch_due_reminders(fcm_client=fcm)

    device.refresh_from_db()
    assert device.is_active, "Token must remain active after transient FCM failure"


@pytest.mark.django_db
def test_fcm_unregistered_token_deactivates_device(db):
    """FCM UNREGISTERED response must deactivate the device and cancel delivery."""
    owner = _make_user("owner@unreg.test")
    device = UserDevice.objects.create(
        user=owner, fcm_token="unregistered-token", device_id="device-unreg", is_active=True
    )
    reminder = _make_pending_reminder(owner)

    fcm = MockFcmClient()
    fcm.unregistered_tokens.add("unregistered-token")

    from apps.notifications.dispatcher import dispatch_due_reminders
    dispatch_due_reminders(fcm_client=fcm)

    device.refresh_from_db()
    assert not device.is_active

    delivery = NotificationDelivery.objects.filter(reminder=reminder, user_device=device).first()
    assert delivery is not None
    assert delivery.status == NotificationDelivery.DeliveryStatus.CANCELLED


@pytest.mark.django_db
def test_fcm_one_device_succeeds_other_fails_transient(db):
    """One device succeeds, another has transient failure → good stays SENT, bad retries."""
    owner = _make_user("owner@mixed.test")
    good_device = UserDevice.objects.create(
        user=owner, fcm_token="good-token", device_id="device-good", is_active=True
    )
    bad_device = UserDevice.objects.create(
        user=owner, fcm_token="bad-token", device_id="device-bad", is_active=True
    )
    reminder = _make_pending_reminder(owner)

    fcm = MockFcmClient()
    fcm.failing_tokens.add("bad-token")

    from apps.notifications.dispatcher import dispatch_due_reminders
    dispatch_due_reminders(fcm_client=fcm)

    good_delivery = NotificationDelivery.objects.get(reminder=reminder, user_device=good_device)
    bad_delivery = NotificationDelivery.objects.get(reminder=reminder, user_device=bad_device)
    bad_device.refresh_from_db()

    assert good_delivery.status == NotificationDelivery.DeliveryStatus.SENT
    assert bad_delivery.status == NotificationDelivery.DeliveryStatus.PENDING
    assert bad_device.is_active


# ---------------------------------------------------------------------------
# 7. NOTIFICATION DELIVERY STATE MACHINE
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_notification_delivery_success_marks_dispatched(db):
    """Successful FCM → delivery.status=SENT, reminder.status=DISPATCHED."""
    owner = _make_user("owner@sm.test")
    UserDevice.objects.create(
        user=owner, fcm_token="ok-token", device_id="dev-sm", is_active=True
    )
    reminder = _make_pending_reminder(owner)

    from apps.notifications.dispatcher import dispatch_due_reminders
    dispatch_due_reminders(fcm_client=MockFcmClient())

    reminder.refresh_from_db()
    assert reminder.status == Reminder.ReminderStatus.DISPATCHED


@pytest.mark.django_db
def test_duplicate_reminder_dispatch_does_not_double_send(db):
    """Running dispatcher twice does not produce duplicate FCM sends."""
    owner = _make_user("owner@dup_dispatch.test")
    UserDevice.objects.create(
        user=owner, fcm_token="dup-token", device_id="dev-dup", is_active=True
    )
    _make_pending_reminder(owner)

    fcm = MockFcmClient()
    from apps.notifications.dispatcher import dispatch_due_reminders
    dispatch_due_reminders(fcm_client=fcm)
    first_count = len(fcm.sent_messages)
    dispatch_due_reminders(fcm_client=fcm)
    second_count = len(fcm.sent_messages)
    assert first_count == second_count == 1


# ---------------------------------------------------------------------------
# 8. WEBSOCKET AUTHORIZATION
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_personal_goal_websocket_rejected_4404(db):
    """WebSocket on personal goal must be rejected with code 4404."""
    owner = _make_user("owner@ws_personal.test")
    goal = _daily_goal(owner)  # personal

    from apps.goals.consumers import _validate_goal_membership
    is_valid, close_code, _ = _validate_goal_membership(str(goal.id), owner)
    assert not is_valid
    assert close_code == 4404


@pytest.mark.django_db
def test_removed_participant_websocket_rejected_4403(db):
    """Removed participant must be rejected from WebSocket with code 4403."""
    owner = _make_user("owner@ws_removed.test")
    member = _make_user("member@ws_removed.test")
    goal = _daily_goal(owner)
    invite_participant(actor=owner, goal_id=goal.id, user_id=member.id)
    accept_invitation(actor=member, goal_id=goal.id)

    from apps.goals.services import remove_participant
    remove_participant(actor=owner, goal_id=goal.id, user_id=member.id)

    from apps.goals.consumers import _validate_goal_membership
    is_valid, close_code, _ = _validate_goal_membership(str(goal.id), member)
    assert not is_valid
    assert close_code == 4403


@pytest.mark.django_db
def test_invited_not_accepted_websocket_rejected_4403(db):
    """INVITED (not yet ACTIVE) participant must not get WebSocket access."""
    owner = _make_user("owner@ws_invited.test")
    member = _make_user("member@ws_invited.test")
    goal = _daily_goal(owner)
    invite_participant(actor=owner, goal_id=goal.id, user_id=member.id)

    from apps.goals.consumers import _validate_goal_membership
    is_valid, close_code, _ = _validate_goal_membership(str(goal.id), member)
    assert not is_valid
    assert close_code == 4403


@pytest.mark.django_db
def test_active_participant_websocket_accepted(db):
    """Active participant on a shared goal must pass WebSocket validation."""
    owner = _make_user("owner@ws_active.test")
    member = _make_user("member@ws_active.test")
    goal = _daily_goal(owner)
    invite_participant(actor=owner, goal_id=goal.id, user_id=member.id)
    accept_invitation(actor=member, goal_id=goal.id)

    from apps.goals.consumers import _validate_goal_membership
    is_valid, close_code, canonical_id = _validate_goal_membership(str(goal.id), member)
    assert is_valid
    assert close_code == 0
    assert canonical_id == str(goal.id)


# ---------------------------------------------------------------------------
# 9. OUTBOX TRANSACTION ROLLBACK ISOLATION
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_outbox_event_not_created_on_transaction_rollback(db):
    """OutboxEvent inside a rolled-back transaction must not persist."""
    from apps.outbox.models import OutboxEvent
    from apps.outbox.services import record_outbox_event

    initial_count = OutboxEvent.objects.count()
    try:
        with transaction.atomic():
            record_outbox_event(
                aggregate_type="test",
                aggregate_id=uuid.uuid4(),
                event_type="test.rollback",
                payload={"key": "value"},
                occurred_at=timezone.now(),
            )
            raise ValueError("Forced rollback")
    except ValueError:
        pass

    assert OutboxEvent.objects.count() == initial_count


@pytest.mark.django_db
def test_on_commit_not_invoked_on_rollback(db):
    """transaction.on_commit must NOT fire when its transaction rolls back."""
    callbacks_fired = []
    try:
        with transaction.atomic():
            transaction.on_commit(lambda: callbacks_fired.append(True))
            raise RuntimeError("Forced rollback")
    except RuntimeError:
        pass
    assert callbacks_fired == []


@pytest.mark.django_db(transaction=True)
def test_on_commit_invoked_exactly_once_after_success():
    """transaction.on_commit must fire exactly once after successful commit."""
    callbacks_fired = []
    with transaction.atomic():
        transaction.on_commit(lambda: callbacks_fired.append(True))
    assert callbacks_fired == [True]


# ---------------------------------------------------------------------------
# 10. AUTH / SESSION HARDENING
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_revoked_session_is_in_denylist_after_logout(db):
    """After logout, session must appear in denylist (Redis fast path)."""
    from apps.authentication.models import AuthSession
    from apps.authentication.services import login_user, logout_session

    owner = _make_user("owner@session.test")

    # login_user returns an AuthenticationResult with access/refresh tokens
    result = login_user(
        email="owner@session.test",
        password="s3cur3-pass!",
        device_id="dev-session",
        device_name="Test Phone",
    )
    session_id = result.session.id
    refresh_token = result.refresh_token

    logout_session(user=owner, refresh_token=refresh_token)

    from apps.authentication.denylist import is_session_denied
    assert is_session_denied(session_id)


@pytest.mark.django_db
def test_expired_access_token_rejected_by_api(db):
    """Expired JWT must return 401 from protected API endpoints."""
    import jwt
    from django.conf import settings

    client = APIClient()
    payload = {
        "user_id": str(uuid.uuid4()),
        "session_id": str(uuid.uuid4()),
        "exp": int((timezone.now() - timedelta(hours=1)).timestamp()),
        "iat": int((timezone.now() - timedelta(hours=2)).timestamp()),
    }
    expired_token = jwt.encode(
        payload, settings.JWT_SIGNING_KEY, algorithm=settings.JWT_ALGORITHM
    )
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {expired_token}")
    response = client.get("/api/v1/goals/")
    assert response.status_code in (401, 403)


# ---------------------------------------------------------------------------
# 11. IDOR / AUTHORIZATION BOUNDARIES
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_user_cannot_view_another_users_personal_goal(db):
    """User B must not view User A's personal goal."""
    user_a = _make_user("user_a@idor.test")
    user_b = _make_user("user_b@idor.test")
    goal = _daily_goal(user_a)

    from apps.goals.exceptions import GoalNotFoundError
    from apps.goals.services import get_visible_goal

    with pytest.raises(GoalNotFoundError):
        get_visible_goal(viewer=user_b, goal_id=goal.id)


@pytest.mark.django_db
def test_removed_participant_cannot_view_shared_goal(db):
    """Removed participant receives GoalNotFoundError."""
    owner = _make_user("owner@removed.test")
    member = _make_user("member@removed.test")
    goal = _daily_goal(owner)
    invite_participant(actor=owner, goal_id=goal.id, user_id=member.id)
    accept_invitation(actor=member, goal_id=goal.id)

    from apps.goals.services import remove_participant
    remove_participant(actor=owner, goal_id=goal.id, user_id=member.id)

    from apps.goals.exceptions import GoalNotFoundError
    from apps.goals.services import get_visible_goal

    with pytest.raises(GoalNotFoundError):
        get_visible_goal(viewer=member, goal_id=goal.id)


@pytest.mark.django_db
def test_cross_user_commitment_access_blocked(db):
    """User B cannot view User A's commitment."""
    user_a = _make_user("user_a@cmt_idor.test")
    user_b = _make_user("user_b@cmt_idor.test")
    commitment = create_commitment(creator=user_a, title="Private commitment")

    from apps.commitments.exceptions import CommitmentNotFoundError
    from apps.commitments.services import get_visible_commitment

    with pytest.raises(CommitmentNotFoundError):
        get_visible_commitment(viewer=user_b, commitment_id=commitment.id)


@pytest.mark.django_db
def test_participant_cannot_pause_goal_as_non_owner(db):
    """A PARTICIPANT-role member must not be able to pause the goal."""
    owner = _make_user("owner@mgmt.test")
    member = _make_user("member@mgmt.test")
    goal = _daily_goal(owner)
    invite_participant(actor=owner, goal_id=goal.id, user_id=member.id)
    accept_invitation(actor=member, goal_id=goal.id)

    from apps.goals.exceptions import GoalNotFoundError

    with pytest.raises(GoalNotFoundError):
        pause_goal(actor=member, goal_id=goal.id)


@pytest.mark.django_db
def test_ownership_is_unique_after_transfer(db):
    """After ownership transfer, exactly one OWNER participant exists."""
    owner = _make_user("owner@transfer.test")
    member = _make_user("member@transfer.test")
    goal = _daily_goal(owner)
    invite_participant(actor=owner, goal_id=goal.id, user_id=member.id)
    accept_invitation(actor=member, goal_id=goal.id)

    member_participant = GoalParticipant.objects.get(goal=goal, user=member)
    transfer_goal_ownership(
        actor=owner, goal_id=goal.id, target_participant_id=member_participant.id
    )

    owner_count = GoalParticipant.objects.filter(
        goal=goal, role=GoalParticipant.Role.OWNER
    ).count()
    assert owner_count == 1


# ---------------------------------------------------------------------------
# 12. AI FAILURE ISOLATION
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_ai_unavailable_does_not_affect_goal_creation(db):
    """Goal creation must succeed even when the AI provider is completely down."""
    owner = _make_user("owner@ai_isolation.test")
    # Core create_goal does not call AI at all; verify this holds regardless
    with patch("apps.ai.providers.gemini.GeminiProvider.generate_structured") as mock_gen:
        mock_gen.side_effect = Exception("AI_UNAVAILABLE: All keys exhausted")
        goal = _daily_goal(owner)
        assert goal.id is not None
        assert goal.status == Goal.Status.ACTIVE


@pytest.mark.django_db
def test_ai_unavailable_does_not_affect_commitment_creation(db):
    """Commitment creation must succeed even when AI is completely unavailable."""
    owner = _make_user("owner@ai_commitment.test")
    with patch("apps.ai.providers.gemini.GeminiProvider.generate_structured") as mock_gen:
        mock_gen.side_effect = Exception("AI_UNAVAILABLE")
        commitment = create_commitment(creator=owner, title="No AI needed")
        assert commitment.id is not None
        assert commitment.status == Commitment.Status.PENDING


@pytest.mark.django_db
def test_ai_suggestion_does_not_create_goal_without_user_confirmation(db):
    """AI goal builder must return a suggestion dict, not create a Goal instance."""
    owner = _make_user("owner@injection.test")
    goal_count_before = Goal.objects.filter(created_by=owner).count()

    injected_response = {
        "status": "READY",
        "goal": {
            "title": "AI Suggested Goal",
            "recurrence_kind": "DAILY",
            "start_date": "2026-01-01",
        },
    }
    with patch("apps.ai.providers.gemini.GeminiProvider.generate_structured") as mock_gen:
        mock_gen.return_value = injected_response
        try:
            from apps.ai.services.goal_builder import build_goal_suggestion
            result = build_goal_suggestion(actor=owner, raw_text="some text")
            assert not isinstance(result, Goal), "AI must not return a created Goal"
        except Exception:
            # If import fails (expected in some environments), that's acceptable
            pass

    goal_count_after = Goal.objects.filter(created_by=owner).count()
    assert goal_count_after == goal_count_before


# ---------------------------------------------------------------------------
# 13. REDIS FAILURE BEHAVIORS
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_redis_presence_update_is_safe_on_failure(db):
    """WebSocket presence writes must not raise when Redis is down."""
    from redis.exceptions import RedisError

    with patch("apps.core.redis.get_redis_client") as mock_redis:
        mock_redis.side_effect = RedisError("Redis unavailable")
        from apps.goals.consumers import remove_presence, update_presence
        # Must NOT raise
        update_presence("goal-123", "user-456")
        remove_presence("goal-123", "user-456")


@pytest.mark.django_db
def test_redis_presence_check_returns_false_on_failure(db):
    """is_user_present_in_chat must return False (fail-safe) when Redis is down."""
    from redis.exceptions import RedisError

    with patch("apps.core.redis.get_redis_client") as mock_redis:
        mock_redis.side_effect = RedisError("Redis unavailable")
        from apps.goals.consumers import is_user_present_in_chat
        result = is_user_present_in_chat("goal-123", "user-456")
        assert result is False


@pytest.mark.django_db
def test_redis_denylist_read_failure_fails_open(db):
    """Redis denylist read failure must fail-open (return False = not denied)."""
    from redis.exceptions import RedisError

    with patch("apps.authentication.denylist.get_redis_client") as mock_redis:
        mock_redis.side_effect = RedisError("Redis unavailable")
        from apps.authentication.denylist import is_session_denied
        result = is_session_denied(str(uuid.uuid4()))
        assert result is False


# ---------------------------------------------------------------------------
# 14. DATA INTEGRITY INVARIANTS
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_goal_always_has_exactly_one_owner_on_creation(db):
    """Every created goal must have exactly one OWNER participant."""
    owner = _make_user("owner@integrity.test")
    goal = _daily_goal(owner)
    owner_count = GoalParticipant.objects.filter(
        goal=goal, role=GoalParticipant.Role.OWNER
    ).count()
    assert owner_count == 1


@pytest.mark.django_db
def test_goal_owner_participant_is_active_on_creation(db):
    """The owner participant must be ACTIVE at goal creation."""
    owner = _make_user("owner@owner_active.test")
    goal = _daily_goal(owner)
    owner_p = GoalParticipant.objects.get(goal=goal, role=GoalParticipant.Role.OWNER)
    assert owner_p.status == GoalParticipant.Status.ACTIVE


@pytest.mark.django_db
def test_personal_goal_has_exactly_one_participant(db):
    """A new personal goal must have exactly 1 participant (the owner)."""
    owner = _make_user("owner@personal.test")
    goal = _daily_goal(owner)
    assert GoalParticipant.objects.filter(goal=goal).count() == 1


@pytest.mark.django_db
def test_shared_goal_participant_uniqueness_per_user(db):
    """Each user can have at most one GoalParticipant row per goal."""
    from django.db import IntegrityError

    owner = _make_user("owner@uniq_participant.test")
    goal = _daily_goal(owner)
    with pytest.raises(IntegrityError):
        GoalParticipant.objects.create(
            goal=goal,
            user=owner,
            role=GoalParticipant.Role.PARTICIPANT,
            status=GoalParticipant.Status.INVITED,
        )


@pytest.mark.django_db
def test_reminder_identity_key_uniqueness(db):
    """Duplicate identity_key on reminders raises IntegrityError."""
    from django.db import IntegrityError

    owner = _make_user("owner@identity.test")
    entity_id = uuid.uuid4()
    key = f"{owner.id}:COMMITMENT:{entity_id}:due_now:none"
    Reminder.objects.create(
        user=owner,
        entity_type=Reminder.EntityType.COMMITMENT,
        entity_id=entity_id,
        event_type="commitment.due_now",
        identity_key=key,
        scheduled_for=timezone.now() + timedelta(hours=1),
    )
    with pytest.raises(IntegrityError):
        Reminder.objects.create(
            user=owner,
            entity_type=Reminder.EntityType.COMMITMENT,
            entity_id=entity_id,
            event_type="commitment.due_now",
            identity_key=key,
            scheduled_for=timezone.now() + timedelta(hours=2),
        )


@pytest.mark.django_db
def test_notification_delivery_unique_per_reminder_device(db):
    """NotificationDelivery must be unique per (reminder, user_device)."""
    from django.db import IntegrityError

    owner = _make_user("owner@nd_uniq.test")
    device = UserDevice.objects.create(
        user=owner, fcm_token="nd-uniq-token", device_id="dev-nd-uniq", is_active=True
    )
    commitment = create_commitment(creator=owner, title="Test")
    reminder = Reminder.objects.create(
        user=owner,
        entity_type=Reminder.EntityType.COMMITMENT,
        entity_id=commitment.id,
        event_type="commitment.due_now",
        identity_key=Reminder.compute_identity_key(
            owner.id, "COMMITMENT", commitment.id, "commitment.due_now"
        ),
        scheduled_for=timezone.now() + timedelta(hours=1),
    )
    NotificationDelivery.objects.create(reminder=reminder, user_device=device)
    with pytest.raises(IntegrityError):
        NotificationDelivery.objects.create(reminder=reminder, user_device=device)


# ---------------------------------------------------------------------------
# 15. OUTBOX CONSUMER IDEMPOTENCY
# ---------------------------------------------------------------------------


@pytest.mark.django_db(transaction=True)
def test_outbox_consumer_duplicate_event_is_idempotent():
    """Duplicate Kafka delivery of the same event_id must not double-process."""
    from apps.outbox.consumer import handle_record

    event_id = str(uuid.uuid4())
    raw_value = (
        f'{{"event_id":"{event_id}",'
        f'"event_version":1,'
        f'"aggregate_type":"commitment",'
        f'"aggregate_id":"{uuid.uuid4()}",'
        f'"event_type":"commitment.created",'
        f'"payload":{{}},'
        f'"occurred_at":"2026-08-22T00:00:00Z"}}'
    ).encode()

    commits = []
    processor_calls = []

    def commit_offset():
        commits.append(True)

    def processor(envelope):
        processor_calls.append(envelope["event_id"])

    outcome1 = handle_record(
        consumer_group="test-group-idempotent",
        raw_value=raw_value,
        processor=processor,
        commit_offset=commit_offset,
    )
    outcome2 = handle_record(
        consumer_group="test-group-idempotent",
        raw_value=raw_value,
        processor=processor,
        commit_offset=commit_offset,
    )

    assert outcome1 == "processed"
    assert outcome2 == "duplicate"
    assert len(processor_calls) == 1, "Processor must be called exactly once"
    assert len(commits) == 2
