import json
from datetime import timedelta
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.commitments.models import Commitment, CommitmentEvent
from apps.commitments.services import (
    cancel_commitment,
    clear_waiting,
    complete_commitment,
    create_commitment,
    set_waiting,
    snooze_commitment,
    unsnooze_commitment,
    update_commitment,
)
from apps.outbox.models import OutboxEvent
from apps.outbox.services import EVENT_VERSION, MAX_PAYLOAD_BYTES, record_outbox_event

User = get_user_model()

EVENT_TYPE_MAP = {
    CommitmentEvent.EventType.CREATED: "commitment.created",
    CommitmentEvent.EventType.UPDATED: "commitment.updated",
    CommitmentEvent.EventType.COMPLETED: "commitment.completed",
    CommitmentEvent.EventType.SNOOZED: "commitment.snoozed",
    CommitmentEvent.EventType.UNSNOOZED: "commitment.unsnoozed",
    CommitmentEvent.EventType.WAITING: "commitment.waiting",
    CommitmentEvent.EventType.CANCELLED: "commitment.cancelled",
}


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun@example.com",
        name="Arjun",
        password="a-secure-password",
    )


def _assert_outbox_for(commitment, domain_event):
    outbox = OutboxEvent.objects.get()
    payload = outbox.payload
    assert outbox.aggregate_type == "commitment"
    assert outbox.aggregate_id == commitment.id
    assert outbox.event_version == EVENT_VERSION == 1
    assert outbox.event_type == EVENT_TYPE_MAP[domain_event.event_type]
    assert outbox.event_type.startswith("commitment.")
    assert outbox.published_at is None
    assert outbox.attempts == 0
    assert outbox.next_attempt_at is not None
    assert outbox.last_error == ""
    assert payload["commitment_event_id"] == str(domain_event.id)
    assert payload["commitment_id"] == str(commitment.id)
    assert payload["created_by_user_id"] == str(commitment.created_by_id)
    assert payload["actor_user_id"] == str(domain_event.actor_id)
    assert payload["status"] == commitment.status
    assert "title" not in payload
    assert "description" not in payload
    assert "password" not in payload
    assert "refresh_token" not in payload
    assert "access_token" not in payload
    dumped = json.dumps(payload)
    assert "password" not in dumped
    assert len(dumped.encode("utf-8")) <= MAX_PAYLOAD_BYTES
    return outbox


@pytest.mark.django_db
def test_create_writes_commitment_event_and_outbox_atomically(arjun):
    due_at = timezone.now() + timedelta(hours=2)
    commitment = create_commitment(
        creator=arjun,
        title="Send credentials",
        description="secret production credentials",
        due_at=due_at,
        due_precision=Commitment.DuePrecision.DATETIME,
        source=Commitment.Source.MANUAL,
    )
    domain_event = commitment.events.get()
    outbox = _assert_outbox_for(commitment, domain_event)

    assert OutboxEvent.objects.count() == 1
    assert CommitmentEvent.objects.count() == 1
    assert outbox.event_type == "commitment.created"
    assert outbox.payload["due_precision"] == Commitment.DuePrecision.DATETIME
    assert outbox.payload["source"] == Commitment.Source.MANUAL
    assert "secret production credentials" not in json.dumps(outbox.payload)
    assert arjun.password not in json.dumps(outbox.payload)


@pytest.mark.django_db
def test_create_rolls_back_if_outbox_fails(arjun):
    with patch(
        "apps.commitments.services.record_outbox_event",
        side_effect=RuntimeError("outbox failed"),
    ):
        with pytest.raises(RuntimeError):
            create_commitment(creator=arjun, title="Send credentials")

    assert Commitment.objects.count() == 0
    assert CommitmentEvent.objects.count() == 0
    assert OutboxEvent.objects.count() == 0


@pytest.mark.django_db
def test_complete_writes_one_event_and_one_outbox(arjun):
    commitment = create_commitment(creator=arjun, title="Do it")
    OutboxEvent.objects.all().delete()
    commitment.events.all().delete()

    complete_commitment(actor=arjun, commitment_id=commitment.id)
    commitment.refresh_from_db()
    domain_event = commitment.events.get()
    outbox = _assert_outbox_for(commitment, domain_event)

    assert domain_event.event_type == CommitmentEvent.EventType.COMPLETED
    assert outbox.event_type == "commitment.completed"
    assert outbox.payload["completed_at"] is not None
    assert CommitmentEvent.objects.count() == 1
    assert OutboxEvent.objects.count() == 1


@pytest.mark.django_db
def test_complete_noop_writes_neither_event_nor_outbox(arjun):
    commitment = create_commitment(creator=arjun, title="Do it")
    complete_commitment(actor=arjun, commitment_id=commitment.id)
    OutboxEvent.objects.all().delete()
    commitment.events.filter(event_type=CommitmentEvent.EventType.COMPLETED).delete()

    complete_commitment(actor=arjun, commitment_id=commitment.id)

    assert (
        commitment.events.filter(event_type=CommitmentEvent.EventType.COMPLETED).count()
        == 0
    )
    assert OutboxEvent.objects.count() == 0


@pytest.mark.django_db
def test_update_noop_writes_neither_event_nor_outbox(arjun):
    commitment = create_commitment(creator=arjun, title="Same")
    OutboxEvent.objects.all().delete()
    commitment.events.all().delete()

    update_commitment(actor=arjun, commitment_id=commitment.id, title="Same")

    assert CommitmentEvent.objects.count() == 0
    assert OutboxEvent.objects.count() == 0


@pytest.mark.django_db
def test_update_and_clear_waiting_map_to_commitment_updated(arjun):
    commitment = create_commitment(creator=arjun, title="Old")
    OutboxEvent.objects.all().delete()
    commitment.events.all().delete()

    update_commitment(actor=arjun, commitment_id=commitment.id, title="New")
    updated = OutboxEvent.objects.get()
    assert updated.event_type == "commitment.updated"
    assert updated.payload["fields"] == ["title"]

    set_waiting(actor=arjun, commitment_id=commitment.id)
    OutboxEvent.objects.all().delete()
    commitment.events.filter(event_type=CommitmentEvent.EventType.UPDATED).delete()

    clear_waiting(actor=arjun, commitment_id=commitment.id)
    cleared = OutboxEvent.objects.get()
    assert cleared.event_type == "commitment.updated"
    assert (
        commitment.events.filter(
            event_type=CommitmentEvent.EventType.UPDATED
        ).count()
        == 1
    )


@pytest.mark.django_db
def test_snooze_unsnooze_wait_cancel_outbox_mapping(arjun):
    commitment = create_commitment(
        creator=arjun,
        title="Timed",
        due_at=timezone.now() + timedelta(days=1),
        due_precision=Commitment.DuePrecision.DATETIME,
    )
    OutboxEvent.objects.all().delete()
    commitment.events.all().delete()
    until = timezone.now() + timedelta(hours=3)

    snooze_commitment(actor=arjun, commitment_id=commitment.id, snoozed_until=until)
    snoozed = OutboxEvent.objects.get()
    assert snoozed.event_type == "commitment.snoozed"
    assert "snoozed_until" in snoozed.payload

    OutboxEvent.objects.all().delete()
    unsnooze_commitment(actor=arjun, commitment_id=commitment.id)
    assert OutboxEvent.objects.get().event_type == "commitment.unsnoozed"

    OutboxEvent.objects.all().delete()
    unsnooze_commitment(actor=arjun, commitment_id=commitment.id)
    assert OutboxEvent.objects.count() == 0

    set_waiting(actor=arjun, commitment_id=commitment.id)
    assert OutboxEvent.objects.get().event_type == "commitment.waiting"

    OutboxEvent.objects.all().delete()
    cancel_commitment(actor=arjun, commitment_id=commitment.id)
    cancelled = OutboxEvent.objects.get()
    assert cancelled.event_type == "commitment.cancelled"
    assert cancelled.payload["cancelled_at"] is not None

    OutboxEvent.objects.all().delete()
    cancel_commitment(actor=arjun, commitment_id=commitment.id)
    assert OutboxEvent.objects.count() == 0


@pytest.mark.django_db
def test_record_outbox_event_rejects_oversized_payload(arjun):
    commitment = create_commitment(creator=arjun, title="Sized")
    huge = {"blob": "x" * (MAX_PAYLOAD_BYTES + 1)}
    with pytest.raises(ValueError):
        record_outbox_event(
            aggregate_type="commitment",
            aggregate_id=commitment.id,
            event_type="commitment.created",
            payload=huge,
            occurred_at=timezone.now(),
        )
