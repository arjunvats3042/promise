import threading
from datetime import timedelta
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model
from django.db import connection
from django.utils import timezone

from apps.commitments.exceptions import (
    CommitmentForbiddenError,
    CommitmentInvalidSnoozeError,
    CommitmentInvalidTransitionError,
    CommitmentNotFoundError,
    CommitmentValidationError,
)
from apps.commitments.models import Commitment, CommitmentEvent, CommitmentParticipant
from apps.commitments.services import (
    SNOOZE_MAX_LIFETIME,
    can_cancel,
    can_complete,
    can_snooze,
    can_unsnooze,
    can_view,
    can_wait,
    cancel_commitment,
    clear_waiting,
    complete_commitment,
    create_commitment,
    set_waiting,
    snooze_commitment,
    unsnooze_commitment,
)

User = get_user_model()


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun@example.com",
        name="Arjun",
        password="a-secure-password",
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul@example.com",
        name="Rahul",
        password="a-secure-password",
    )


@pytest.fixture
def observer(db):
    return User.objects.create_user(
        email="observer@example.com",
        name="Observer",
        password="a-secure-password",
    )


@pytest.fixture
def stranger(db):
    return User.objects.create_user(
        email="stranger@example.com",
        name="Stranger",
        password="a-secure-password",
    )


def _future(hours=2):
    return timezone.now() + timedelta(hours=hours)


def _personal(creator, title="Study DSA tonight", **overrides):
    return create_commitment(creator=creator, title=title, **overrides)


def _attach(commitment, user, role):
    return CommitmentParticipant.objects.create(
        commitment=commitment,
        user=user,
        role=role,
        joined_at=timezone.now(),
    )


def _shared_rahul_responsible(arjun, rahul):
    """Arjun created it; Rahul is responsible; Arjun is recipient.

    create_commitment always inserts the creator as RESPONSIBLE. Shared
    invite flows are deferred, so this helper rearranges rows for auth tests.
    """
    commitment = _personal(arjun, title="Rahul will send credentials to Arjun.")
    CommitmentParticipant.objects.filter(
        commitment=commitment, user=arjun
    ).update(role=CommitmentParticipant.Role.RECIPIENT)
    _attach(commitment, rahul, CommitmentParticipant.Role.RESPONSIBLE)
    return commitment


def _event_types(commitment):
    return list(
        commitment.events.order_by("created_at").values_list("event_type", flat=True)
    )


@pytest.mark.django_db
def test_create_commitment_inserts_responsible_participant_and_created_event(arjun):
    due_at = _future()
    commitment = create_commitment(
        creator=arjun,
        title="Study DSA tonight",
        description="Arrays",
        due_at=due_at,
        due_precision=Commitment.DuePrecision.DATETIME,
        source=Commitment.Source.SHARED,
    )

    participant = commitment.participants.get()
    event = commitment.events.get()

    assert commitment.created_by == arjun
    assert commitment.status == Commitment.Status.PENDING
    assert commitment.title == "Study DSA tonight"
    assert commitment.description == "Arrays"
    assert commitment.source == Commitment.Source.SHARED
    assert commitment.due_at == due_at
    assert participant.user == arjun
    assert participant.role == CommitmentParticipant.Role.RESPONSIBLE
    assert participant.status == CommitmentParticipant.Status.ACTIVE
    assert event.event_type == CommitmentEvent.EventType.CREATED
    assert event.actor == arjun
    assert event.metadata == {}
    assert arjun.password not in str(event.metadata)


@pytest.mark.django_db
def test_create_rejects_blank_title(arjun):
    with pytest.raises(CommitmentValidationError):
        create_commitment(creator=arjun, title="   ")

    assert Commitment.objects.count() == 0


@pytest.mark.django_db
def test_create_rolls_back_if_event_fails(arjun):
    with patch(
        "apps.commitments.services.CommitmentEvent.objects.create",
        side_effect=RuntimeError("event failed"),
    ):
        with pytest.raises(RuntimeError):
            create_commitment(creator=arjun, title="Study DSA tonight")

    assert Commitment.objects.count() == 0
    assert CommitmentParticipant.objects.count() == 0


@pytest.mark.django_db
def test_complete_from_pending_waiting_and_snoozed(arjun):
    pending = _personal(arjun, title="pending")
    waiting = _personal(arjun, title="waiting")
    snoozed = _personal(arjun, title="snoozed")
    set_waiting(actor=arjun, commitment_id=waiting.id)
    snooze_commitment(
        actor=arjun, commitment_id=snoozed.id, snoozed_until=_future(hours=3)
    )

    for commitment in (pending, waiting, snoozed):
        result = complete_commitment(actor=arjun, commitment_id=commitment.id)
        result.refresh_from_db()
        assert result.status == Commitment.Status.COMPLETED
        assert result.completed_at is not None
        assert timezone.is_aware(result.completed_at)
        assert result.snoozed_until is None
        assert result.cancelled_at is None
        assert result.events.filter(event_type=CommitmentEvent.EventType.COMPLETED).count() == 1
        assert result.events.get(
            event_type=CommitmentEvent.EventType.COMPLETED
        ).actor == arjun


@pytest.mark.django_db
def test_complete_is_idempotent_without_a_second_event(arjun):
    commitment = _personal(arjun)
    first = complete_commitment(actor=arjun, commitment_id=commitment.id)
    completed_at = first.completed_at
    second = complete_commitment(actor=arjun, commitment_id=commitment.id)

    assert second.status == Commitment.Status.COMPLETED
    assert second.completed_at == completed_at
    assert commitment.events.filter(event_type=CommitmentEvent.EventType.COMPLETED).count() == 1


@pytest.mark.django_db
def test_complete_from_cancelled_is_invalid(arjun):
    commitment = _personal(arjun)
    cancel_commitment(actor=arjun, commitment_id=commitment.id)

    with pytest.raises(CommitmentInvalidTransitionError):
        complete_commitment(actor=arjun, commitment_id=commitment.id)


@pytest.mark.django_db
def test_snooze_sets_status_preserves_due_at_and_records_event(arjun):
    due_at = timezone.now() - timedelta(minutes=1)
    commitment = _personal(
        arjun,
        due_at=due_at,
        due_precision=Commitment.DuePrecision.DATETIME,
    )
    snoozed_until = _future(hours=4)

    assert commitment.is_overdue() is True
    result = snooze_commitment(
        actor=arjun, commitment_id=commitment.id, snoozed_until=snoozed_until
    )
    result.refresh_from_db()

    assert result.status == Commitment.Status.SNOOZED
    assert result.due_at == due_at
    assert result.snoozed_until == snoozed_until
    assert result.completed_at is None
    assert result.is_overdue() is False
    event = result.events.get(event_type=CommitmentEvent.EventType.SNOOZED)
    assert event.actor == arjun
    assert event.metadata == {"snoozed_until": snoozed_until.isoformat()}


@pytest.mark.django_db
def test_snooze_rejects_past_and_too_far_and_waiting(arjun):
    commitment = _personal(arjun)
    waiting = _personal(arjun, title="waiting")
    set_waiting(actor=arjun, commitment_id=waiting.id)

    with pytest.raises(CommitmentInvalidSnoozeError):
        snooze_commitment(
            actor=arjun,
            commitment_id=commitment.id,
            snoozed_until=timezone.now() - timedelta(seconds=1),
        )
    with pytest.raises(CommitmentInvalidSnoozeError):
        snooze_commitment(
            actor=arjun,
            commitment_id=commitment.id,
            snoozed_until=timezone.now() + SNOOZE_MAX_LIFETIME + timedelta(seconds=1),
        )
    with pytest.raises(CommitmentInvalidTransitionError):
        snooze_commitment(
            actor=arjun, commitment_id=waiting.id, snoozed_until=_future()
        )
    commitment.refresh_from_db()
    assert commitment.status == Commitment.Status.PENDING
    assert commitment.snoozed_until is None


@pytest.mark.django_db
def test_resnooze_same_time_is_idempotent_new_time_writes_event(arjun):
    commitment = _personal(arjun)
    first_until = _future(hours=2)
    second_until = _future(hours=5)
    snooze_commitment(
        actor=arjun, commitment_id=commitment.id, snoozed_until=first_until
    )
    snooze_commitment(
        actor=arjun, commitment_id=commitment.id, snoozed_until=first_until
    )
    assert commitment.events.filter(event_type=CommitmentEvent.EventType.SNOOZED).count() == 1

    snooze_commitment(
        actor=arjun, commitment_id=commitment.id, snoozed_until=second_until
    )
    commitment.refresh_from_db()
    assert commitment.snoozed_until == second_until
    assert commitment.events.filter(event_type=CommitmentEvent.EventType.SNOOZED).count() == 2


@pytest.mark.django_db
def test_unsnooze_from_snoozed_and_rejects_waiting_and_terminal(arjun):
    snoozed = _personal(arjun, title="snoozed")
    waiting = _personal(arjun, title="waiting")
    done = _personal(arjun, title="done")
    snooze_commitment(
        actor=arjun, commitment_id=snoozed.id, snoozed_until=_future()
    )
    set_waiting(actor=arjun, commitment_id=waiting.id)
    complete_commitment(actor=arjun, commitment_id=done.id)

    result = unsnooze_commitment(actor=arjun, commitment_id=snoozed.id)
    result.refresh_from_db()
    assert result.status == Commitment.Status.PENDING
    assert result.snoozed_until is None
    assert result.events.filter(event_type=CommitmentEvent.EventType.UNSNOOZED).count() == 1

    with pytest.raises(CommitmentInvalidTransitionError):
        unsnooze_commitment(actor=arjun, commitment_id=waiting.id)
    with pytest.raises(CommitmentInvalidTransitionError):
        unsnooze_commitment(actor=arjun, commitment_id=done.id)


@pytest.mark.django_db
def test_unsnooze_when_already_pending_is_idempotent(arjun):
    commitment = _personal(arjun)
    result = unsnooze_commitment(actor=arjun, commitment_id=commitment.id)

    assert result.status == Commitment.Status.PENDING
    assert commitment.events.filter(event_type=CommitmentEvent.EventType.UNSNOOZED).count() == 0


@pytest.mark.django_db
def test_waiting_and_clear_waiting(arjun):
    commitment = _personal(arjun)
    waiting = set_waiting(actor=arjun, commitment_id=commitment.id)
    waiting.refresh_from_db()
    assert waiting.status == Commitment.Status.WAITING
    assert waiting.completed_at is None
    assert waiting.events.filter(event_type=CommitmentEvent.EventType.WAITING).count() == 1

    set_waiting(actor=arjun, commitment_id=commitment.id)
    assert waiting.events.filter(event_type=CommitmentEvent.EventType.WAITING).count() == 1

    pending = clear_waiting(actor=arjun, commitment_id=commitment.id)
    pending.refresh_from_db()
    assert pending.status == Commitment.Status.PENDING
    assert pending.events.filter(event_type=CommitmentEvent.EventType.UPDATED).count() == 1
    assert _event_types(pending) == [
        CommitmentEvent.EventType.CREATED,
        CommitmentEvent.EventType.WAITING,
        CommitmentEvent.EventType.UPDATED,
    ]

    snoozed = _personal(arjun, title="snoozed")
    snooze_commitment(actor=arjun, commitment_id=snoozed.id, snoozed_until=_future())
    with pytest.raises(CommitmentInvalidTransitionError):
        set_waiting(actor=arjun, commitment_id=snoozed.id)


@pytest.mark.django_db
def test_clear_waiting_when_pending_is_idempotent(arjun):
    commitment = _personal(arjun)
    result = clear_waiting(actor=arjun, commitment_id=commitment.id)

    assert result.status == Commitment.Status.PENDING
    assert _event_types(result) == [CommitmentEvent.EventType.CREATED]


@pytest.mark.django_db
def test_cancel_sets_timestamp_and_is_idempotent(arjun):
    commitment = _personal(arjun)
    first = cancel_commitment(actor=arjun, commitment_id=commitment.id)
    cancelled_at = first.cancelled_at
    second = cancel_commitment(actor=arjun, commitment_id=commitment.id)

    first.refresh_from_db()
    assert first.status == Commitment.Status.CANCELLED
    assert first.cancelled_at is not None
    assert timezone.is_aware(first.cancelled_at)
    assert first.completed_at is None
    assert first.snoozed_until is None
    assert second.cancelled_at == cancelled_at
    assert first.events.filter(event_type=CommitmentEvent.EventType.CANCELLED).count() == 1


@pytest.mark.django_db
def test_cancel_from_completed_is_invalid(arjun):
    commitment = _personal(arjun)
    complete_commitment(actor=arjun, commitment_id=commitment.id)

    with pytest.raises(CommitmentInvalidTransitionError):
        cancel_commitment(actor=arjun, commitment_id=commitment.id)


@pytest.mark.django_db
def test_cancel_from_snoozed_and_waiting(arjun):
    snoozed = _personal(arjun, title="snoozed")
    waiting = _personal(arjun, title="waiting")
    snooze_commitment(actor=arjun, commitment_id=snoozed.id, snoozed_until=_future())
    set_waiting(actor=arjun, commitment_id=waiting.id)

    cancel_commitment(actor=arjun, commitment_id=snoozed.id)
    cancel_commitment(actor=arjun, commitment_id=waiting.id)
    snoozed.refresh_from_db()
    waiting.refresh_from_db()
    assert snoozed.status == Commitment.Status.CANCELLED
    assert waiting.status == Commitment.Status.CANCELLED
    assert snoozed.snoozed_until is None


@pytest.mark.django_db
def test_overdue_stays_derived_through_mutations(arjun):
    due_at = timezone.now() - timedelta(minutes=5)
    commitment = _personal(
        arjun,
        due_at=due_at,
        due_precision=Commitment.DuePrecision.DATETIME,
    )
    assert commitment.status == Commitment.Status.PENDING
    assert commitment.is_overdue() is True
    assert "OVERDUE" not in Commitment.Status.values

    set_waiting(actor=arjun, commitment_id=commitment.id)
    commitment.refresh_from_db()
    assert commitment.status == Commitment.Status.WAITING
    assert commitment.is_overdue() is True
    assert commitment.due_at == due_at


@pytest.mark.django_db
def test_authorization_view_and_responsible_actions(
    arjun, rahul, observer, stranger
):
    commitment = _shared_rahul_responsible(arjun, rahul)
    _attach(commitment, observer, CommitmentParticipant.Role.OBSERVER)
    commitment.refresh_from_db()

    assert can_view(arjun, commitment) is True
    assert can_view(rahul, commitment) is True
    assert can_view(observer, commitment) is True
    assert can_view(stranger, commitment) is False

    assert can_complete(rahul, commitment) is True
    assert can_snooze(rahul, commitment) is True
    assert can_unsnooze(rahul, commitment) is True
    assert can_wait(rahul, commitment) is True
    assert can_cancel(rahul, commitment) is True

    for user in (arjun, observer, stranger):
        assert can_complete(user, commitment) is False
        assert can_snooze(user, commitment) is False
        assert can_cancel(user, commitment) is False

    complete_commitment(actor=rahul, commitment_id=commitment.id)
    with pytest.raises(CommitmentForbiddenError):
        complete_commitment(actor=arjun, commitment_id=commitment.id)
    with pytest.raises(CommitmentForbiddenError):
        cancel_commitment(actor=observer, commitment_id=commitment.id)
    with pytest.raises(CommitmentNotFoundError):
        complete_commitment(actor=stranger, commitment_id=commitment.id)


@pytest.mark.django_db
def test_personal_creator_is_responsible_and_can_mutate(arjun, stranger):
    commitment = _personal(arjun)
    assert can_complete(arjun, commitment) is True
    complete_commitment(actor=arjun, commitment_id=commitment.id)
    with pytest.raises(CommitmentNotFoundError):
        cancel_commitment(actor=stranger, commitment_id=commitment.id)


@pytest.mark.django_db
def test_complete_rolls_back_if_event_fails(arjun):
    commitment = _personal(arjun)
    with patch(
        "apps.commitments.services.CommitmentEvent.objects.create",
        side_effect=RuntimeError("event failed"),
    ):
        with pytest.raises(RuntimeError):
            complete_commitment(actor=arjun, commitment_id=commitment.id)

    commitment.refresh_from_db()
    assert commitment.status == Commitment.Status.PENDING
    assert commitment.completed_at is None
    assert commitment.events.filter(event_type=CommitmentEvent.EventType.COMPLETED).count() == 0


@pytest.mark.django_db(transaction=True)
def test_concurrent_complete_writes_one_completed_event(arjun):
    commitment = _personal(arjun)
    barrier = threading.Barrier(2)
    errors = []

    def worker():
        barrier.wait()
        try:
            complete_commitment(actor=arjun, commitment_id=commitment.id)
        except Exception as exc:
            errors.append(exc)
        finally:
            connection.close()

    threads = [threading.Thread(target=worker) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    commitment.refresh_from_db()
    assert errors == []
    assert commitment.status == Commitment.Status.COMPLETED
    assert commitment.events.filter(event_type=CommitmentEvent.EventType.COMPLETED).count() == 1


@pytest.mark.django_db(transaction=True)
def test_concurrent_complete_and_cancel_serialize(arjun):
    commitment = _personal(arjun)
    barrier = threading.Barrier(2)
    errors = []

    def complete_worker():
        barrier.wait()
        try:
            complete_commitment(actor=arjun, commitment_id=commitment.id)
        except Exception as exc:
            errors.append(exc)
        finally:
            connection.close()

    def cancel_worker():
        barrier.wait()
        try:
            cancel_commitment(actor=arjun, commitment_id=commitment.id)
        except Exception as exc:
            errors.append(exc)
        finally:
            connection.close()

    threads = [
        threading.Thread(target=complete_worker),
        threading.Thread(target=cancel_worker),
    ]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    commitment.refresh_from_db()
    terminal = {Commitment.Status.COMPLETED, Commitment.Status.CANCELLED}
    assert commitment.status in terminal
    assert len(errors) == 1
    assert isinstance(errors[0], CommitmentInvalidTransitionError)
    completed_events = commitment.events.filter(
        event_type=CommitmentEvent.EventType.COMPLETED
    ).count()
    cancelled_events = commitment.events.filter(
        event_type=CommitmentEvent.EventType.CANCELLED
    ).count()
    assert completed_events + cancelled_events == 1
