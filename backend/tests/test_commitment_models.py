import uuid
from datetime import timedelta

import pytest
from django.contrib.auth import get_user_model
from django.db import IntegrityError
from django.db.models.deletion import ProtectedError
from django.utils import timezone

from apps.commitments.models import (
    Commitment,
    CommitmentEvent,
    CommitmentParticipant,
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


def _create_commitment(created_by, **overrides):
    fields = {
        "created_by": created_by,
        "title": "Study DSA tonight",
    }
    fields.update(overrides)
    return Commitment.objects.create(**fields)


@pytest.mark.django_db
def test_commitment_id_is_uuid_v4(arjun):
    commitment = _create_commitment(arjun)

    assert isinstance(commitment.id, uuid.UUID)
    assert commitment.id.version == 4


@pytest.mark.django_db
def test_commitment_requires_title_and_created_by(arjun):
    commitment = _create_commitment(arjun, title="Send credentials")

    assert commitment.created_by == arjun
    assert commitment.title == "Send credentials"
    assert commitment.description == ""


@pytest.mark.django_db
def test_commitment_default_status_is_pending(arjun):
    commitment = _create_commitment(arjun)

    assert commitment.status == Commitment.Status.PENDING
    assert "OVERDUE" not in Commitment.Status.values


@pytest.mark.django_db
def test_commitment_due_at_is_nullable_and_timezone_aware(arjun):
    without_due = _create_commitment(arjun)
    due_at = timezone.now() + timedelta(hours=2)
    with_due = _create_commitment(
        arjun,
        title="Send credentials by 6 PM",
        due_at=due_at,
        due_precision=Commitment.DuePrecision.DATETIME,
    )

    without_due.refresh_from_db()
    with_due.refresh_from_db()

    assert without_due.due_at is None
    assert without_due.due_precision == Commitment.DuePrecision.NONE
    assert with_due.due_at == due_at
    assert timezone.is_aware(with_due.due_at)
    assert with_due.due_precision == Commitment.DuePrecision.DATETIME


@pytest.mark.django_db
def test_commitment_source_defaults_to_manual(arjun):
    commitment = _create_commitment(arjun)
    imported = _create_commitment(
        arjun,
        title="Imported promise",
        source=Commitment.Source.IMPORT,
    )

    assert commitment.source == Commitment.Source.MANUAL
    assert imported.source == Commitment.Source.IMPORT
    assert Commitment.Source.SHARED in Commitment.Source.values


@pytest.mark.django_db
def test_commitment_basemodel_timestamps_are_timezone_aware(arjun):
    commitment = _create_commitment(arjun)

    assert timezone.is_aware(commitment.created_at)
    assert timezone.is_aware(commitment.updated_at)
    assert commitment.completed_at is None
    assert commitment.cancelled_at is None
    assert commitment.snoozed_until is None


@pytest.mark.django_db
def test_overdue_is_derived_and_save_does_not_change_status(arjun):
    commitment = _create_commitment(
        arjun,
        due_at=timezone.now() - timedelta(minutes=1),
        due_precision=Commitment.DuePrecision.DATETIME,
    )

    commitment.refresh_from_db()

    assert commitment.status == Commitment.Status.PENDING
    assert commitment.is_overdue() is True
    snoozed = _create_commitment(
        arjun,
        title="Snoozed past due",
        status=Commitment.Status.SNOOZED,
        due_at=timezone.now() - timedelta(minutes=1),
        due_precision=Commitment.DuePrecision.DATETIME,
        snoozed_until=timezone.now() + timedelta(hours=1),
    )
    assert snoozed.is_overdue() is False


@pytest.mark.django_db
def test_personal_commitment_uses_same_models(arjun):
    commitment = _create_commitment(arjun)
    participant = CommitmentParticipant.objects.create(
        commitment=commitment,
        user=arjun,
        role=CommitmentParticipant.Role.RESPONSIBLE,
        joined_at=timezone.now(),
    )

    assert commitment.created_by == participant.user
    assert participant.role == CommitmentParticipant.Role.RESPONSIBLE
    assert participant.status == CommitmentParticipant.Status.ACTIVE


@pytest.mark.django_db
def test_shared_commitment_uses_participants_not_a_separate_model(arjun, rahul):
    commitment = _create_commitment(
        arjun,
        title="Rahul will send credentials to Arjun.",
    )
    responsible = CommitmentParticipant.objects.create(
        commitment=commitment,
        user=rahul,
        role=CommitmentParticipant.Role.RESPONSIBLE,
        joined_at=timezone.now(),
    )
    recipient = CommitmentParticipant.objects.create(
        commitment=commitment,
        user=arjun,
        role=CommitmentParticipant.Role.RECIPIENT,
        joined_at=timezone.now(),
    )

    assert commitment.created_by == arjun
    assert responsible.user == rahul
    assert recipient.user == arjun
    assert set(commitment.participants.values_list("role", flat=True)) == {
        CommitmentParticipant.Role.RESPONSIBLE,
        CommitmentParticipant.Role.RECIPIENT,
    }


@pytest.mark.django_db
def test_participant_relationships_role_and_joined_at(arjun, rahul):
    commitment = _create_commitment(arjun)
    joined_at = timezone.now()
    participant = CommitmentParticipant.objects.create(
        commitment=commitment,
        user=rahul,
        role=CommitmentParticipant.Role.OBSERVER,
        joined_at=joined_at,
    )

    assert participant.commitment == commitment
    assert participant.user == rahul
    assert participant.role == CommitmentParticipant.Role.OBSERVER
    assert timezone.is_aware(participant.joined_at)
    assert list(commitment.participants.all()) == [participant]
    assert list(rahul.commitment_participations.all()) == [participant]


@pytest.mark.django_db
def test_duplicate_participant_for_same_user_is_rejected(arjun, rahul):
    commitment = _create_commitment(arjun)
    CommitmentParticipant.objects.create(
        commitment=commitment,
        user=rahul,
        role=CommitmentParticipant.Role.RESPONSIBLE,
        joined_at=timezone.now(),
    )

    with pytest.raises(IntegrityError):
        CommitmentParticipant.objects.create(
            commitment=commitment,
            user=rahul,
            role=CommitmentParticipant.Role.RECIPIENT,
            joined_at=timezone.now(),
        )


@pytest.mark.django_db
def test_event_relationship_nullable_actor_type_and_metadata(arjun, rahul):
    commitment = _create_commitment(arjun)
    event = CommitmentEvent.objects.create(
        commitment=commitment,
        actor=rahul,
        event_type=CommitmentEvent.EventType.CREATED,
        metadata={"fields": ["title"]},
    )
    system_event = CommitmentEvent.objects.create(
        commitment=commitment,
        actor=None,
        event_type=CommitmentEvent.EventType.WAITING,
    )

    event.refresh_from_db()
    system_event.refresh_from_db()

    assert event.commitment == commitment
    assert event.actor == rahul
    assert event.event_type == CommitmentEvent.EventType.CREATED
    assert event.metadata == {"fields": ["title"]}
    assert system_event.actor is None
    assert system_event.metadata == {}
    assert timezone.is_aware(event.created_at)
    assert timezone.is_aware(event.updated_at)


@pytest.mark.django_db
def test_events_are_append_only_by_convention(arjun):
    commitment = _create_commitment(arjun)
    first = CommitmentEvent.objects.create(
        commitment=commitment,
        actor=arjun,
        event_type=CommitmentEvent.EventType.CREATED,
        metadata={"n": 1},
    )
    CommitmentEvent.objects.create(
        commitment=commitment,
        actor=arjun,
        event_type=CommitmentEvent.EventType.UPDATED,
        metadata={"n": 2},
    )
    first.refresh_from_db()

    assert list(
        commitment.events.order_by("created_at").values_list("event_type", flat=True)
    ) == [
        CommitmentEvent.EventType.CREATED,
        CommitmentEvent.EventType.UPDATED,
    ]
    assert first.metadata == {"n": 1}
    assert CommitmentEvent.objects.filter(commitment=commitment).count() == 2


@pytest.mark.django_db
def test_deleting_commitment_cascades_participants_and_events(arjun, rahul):
    commitment = _create_commitment(arjun)
    CommitmentParticipant.objects.create(
        commitment=commitment,
        user=rahul,
        role=CommitmentParticipant.Role.RESPONSIBLE,
        joined_at=timezone.now(),
    )
    CommitmentEvent.objects.create(
        commitment=commitment,
        actor=arjun,
        event_type=CommitmentEvent.EventType.CREATED,
    )
    commitment_id = commitment.id
    commitment.delete()

    assert Commitment.objects.filter(id=commitment_id).exists() is False
    assert CommitmentParticipant.objects.filter(commitment_id=commitment_id).exists() is False
    assert CommitmentEvent.objects.filter(commitment_id=commitment_id).exists() is False


@pytest.mark.django_db
def test_deleting_actor_sets_event_actor_null(arjun, rahul):
    commitment = _create_commitment(arjun)
    event = CommitmentEvent.objects.create(
        commitment=commitment,
        actor=rahul,
        event_type=CommitmentEvent.EventType.COMPLETED,
    )
    rahul.delete()
    event.refresh_from_db()

    assert event.actor_id is None
    assert Commitment.objects.filter(id=commitment.id).exists() is True


@pytest.mark.django_db
def test_deleting_participant_user_is_protected(arjun, rahul):
    commitment = _create_commitment(arjun)
    participant = CommitmentParticipant.objects.create(
        commitment=commitment,
        user=rahul,
        role=CommitmentParticipant.Role.RESPONSIBLE,
        joined_at=timezone.now(),
    )

    with pytest.raises(ProtectedError):
        rahul.delete()

    assert CommitmentParticipant.objects.filter(id=participant.id).exists() is True
    assert Commitment.objects.filter(id=commitment.id).exists() is True


@pytest.mark.django_db
def test_deleting_created_by_user_is_protected(arjun):
    commitment = _create_commitment(arjun)

    with pytest.raises(ProtectedError):
        arjun.delete()

    assert Commitment.objects.filter(id=commitment.id).exists() is True
