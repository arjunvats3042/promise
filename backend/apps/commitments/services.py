from datetime import timedelta

from django.db import transaction
from django.db.models import Exists, F, OuterRef, Q
from django.utils import timezone

from apps.commitments.exceptions import (
    CommitmentForbiddenError,
    CommitmentInvalidSnoozeError,
    CommitmentInvalidTransitionError,
    CommitmentNotFoundError,
    CommitmentValidationError,
)
from apps.commitments.models import Commitment, CommitmentEvent, CommitmentParticipant
from apps.outbox.services import record_outbox_event

SNOOZE_MAX_LIFETIME = timedelta(days=30)
_UNSET = object()

_RESPONSIBLE_STATUSES = (
    Commitment.Status.PENDING,
    Commitment.Status.WAITING,
    Commitment.Status.SNOOZED,
)
_OPEN_FOR_SNOOZE = (Commitment.Status.PENDING, Commitment.Status.SNOOZED)
_OPEN_FOR_WAIT = (Commitment.Status.PENDING, Commitment.Status.WAITING)
_TERMINAL_STATUSES = (Commitment.Status.COMPLETED, Commitment.Status.CANCELLED)


def can_view(user, commitment):
    if commitment.created_by_id == user.id:
        return True
    return commitment.participants.filter(
        user=user,
        status=CommitmentParticipant.Status.ACTIVE,
    ).exists()


def can_complete(user, commitment):
    return _is_active_responsible(user, commitment)


def can_snooze(user, commitment):
    return _is_active_responsible(user, commitment)


def can_unsnooze(user, commitment):
    return _is_active_responsible(user, commitment)


def can_wait(user, commitment):
    return _is_active_responsible(user, commitment)


def can_cancel(user, commitment):
    return _is_active_responsible(user, commitment)


def can_update(user, commitment):
    return _is_active_responsible(user, commitment)


def create_commitment(
    *,
    creator,
    title,
    description="",
    due_at=None,
    due_precision=Commitment.DuePrecision.NONE,
    source=Commitment.Source.MANUAL,
):
    title = title.strip() if title else title
    if not title:
        raise CommitmentValidationError()
    _validate_due_consistency(due_at, due_precision)

    now = timezone.now()
    with transaction.atomic():
        commitment = Commitment.objects.create(
            created_by=creator,
            title=title,
            description=description or "",
            status=Commitment.Status.PENDING,
            due_at=due_at,
            due_precision=due_precision,
            source=source,
        )
        CommitmentParticipant.objects.create(
            commitment=commitment,
            user=creator,
            role=CommitmentParticipant.Role.RESPONSIBLE,
            status=CommitmentParticipant.Status.ACTIVE,
            joined_at=now,
        )
        _add_event(
            commitment,
            actor=creator,
            event_type=CommitmentEvent.EventType.CREATED,
        )
        return commitment


import uuid


def _lookup_commitment(commitment_id):
    if isinstance(commitment_id, int) or (isinstance(commitment_id, str) and str(commitment_id).isdigit()):
        return Commitment.objects.filter(numeric_id=int(commitment_id)).first()
    try:
        val = uuid.UUID(str(commitment_id))
        return Commitment.objects.filter(id=val).first()
    except (ValueError, AttributeError):
        return None


def get_visible_commitment(*, viewer, commitment_id):
    commitment = _lookup_commitment(commitment_id)
    if commitment is None or not can_view(viewer, commitment):
        raise CommitmentNotFoundError()
    return commitment


def visible_commitments_queryset(viewer):
    is_active_participant = CommitmentParticipant.objects.filter(
        commitment_id=OuterRef("pk"),
        user=viewer,
        status=CommitmentParticipant.Status.ACTIVE,
    )
    return (
        Commitment.objects.filter(
            Q(created_by=viewer) | Exists(is_active_participant)
        ).order_by(F("due_at").asc(nulls_last=True), "-created_at")
    )


def list_visible_commitments(*, viewer, status=None, source=None, is_overdue=None):
    queryset = visible_commitments_queryset(viewer)
    if status is not None:
        queryset = queryset.filter(status=status)
    if source is not None:
        queryset = queryset.filter(source=source)
    if is_overdue is True:
        queryset = queryset.filter(_overdue_q(timezone.now()))
    elif is_overdue is False:
        queryset = queryset.exclude(_overdue_q(timezone.now()))
    return queryset


def update_commitment(
    *,
    actor,
    commitment_id,
    title=_UNSET,
    description=_UNSET,
    due_at=_UNSET,
    due_precision=_UNSET,
):
    with transaction.atomic():
        commitment = _lock_for_responsible(actor, commitment_id)
        if commitment.status in _TERMINAL_STATUSES:
            raise CommitmentInvalidTransitionError()

        next_title = commitment.title
        next_description = commitment.description
        next_due_at = commitment.due_at
        next_due_precision = commitment.due_precision
        if title is not _UNSET:
            next_title = title.strip() if title else title
            if not next_title:
                raise CommitmentValidationError()
        if description is not _UNSET:
            next_description = description or ""
        if due_at is not _UNSET:
            next_due_at = due_at
        if due_precision is not _UNSET:
            next_due_precision = due_precision
        _validate_due_consistency(next_due_at, next_due_precision)

        changed = []
        if next_title != commitment.title:
            commitment.title = next_title
            changed.append("title")
        if next_description != commitment.description:
            commitment.description = next_description
            changed.append("description")
        if next_due_at != commitment.due_at:
            commitment.due_at = next_due_at
            changed.append("due_at")
        if next_due_precision != commitment.due_precision:
            commitment.due_precision = next_due_precision
            changed.append("due_precision")
        if not changed:
            return commitment

        commitment.save(update_fields=[*changed, "updated_at"])
        _add_event(
            commitment,
            actor=actor,
            event_type=CommitmentEvent.EventType.UPDATED,
            metadata={"fields": changed},
        )
        return commitment


def complete_commitment(*, actor, commitment_id):
    with transaction.atomic():
        commitment = _lock_for_responsible(actor, commitment_id)
        if commitment.status == Commitment.Status.COMPLETED:
            return commitment
        if commitment.status not in _RESPONSIBLE_STATUSES:
            raise CommitmentInvalidTransitionError()
        now = timezone.now()
        commitment.status = Commitment.Status.COMPLETED
        commitment.completed_at = now
        commitment.snoozed_until = None
        commitment.save(
            update_fields=["status", "completed_at", "snoozed_until", "updated_at"]
        )
        _add_event(
            commitment,
            actor=actor,
            event_type=CommitmentEvent.EventType.COMPLETED,
        )
        return commitment


def snooze_commitment(*, actor, commitment_id, snoozed_until):
    _validate_snoozed_until(snoozed_until)
    with transaction.atomic():
        commitment = _lock_for_responsible(actor, commitment_id)
        if (
            commitment.status == Commitment.Status.SNOOZED
            and commitment.snoozed_until == snoozed_until
        ):
            return commitment
        if commitment.status not in _OPEN_FOR_SNOOZE:
            raise CommitmentInvalidTransitionError()
        commitment.status = Commitment.Status.SNOOZED
        commitment.snoozed_until = snoozed_until
        commitment.save(update_fields=["status", "snoozed_until", "updated_at"])
        _add_event(
            commitment,
            actor=actor,
            event_type=CommitmentEvent.EventType.SNOOZED,
            metadata={"snoozed_until": snoozed_until.isoformat()},
        )
        return commitment


def unsnooze_commitment(*, actor, commitment_id):
    with transaction.atomic():
        commitment = _lock_for_responsible(actor, commitment_id)
        if commitment.status == Commitment.Status.PENDING:
            return commitment
        if commitment.status != Commitment.Status.SNOOZED:
            raise CommitmentInvalidTransitionError()
        commitment.status = Commitment.Status.PENDING
        commitment.snoozed_until = None
        commitment.save(update_fields=["status", "snoozed_until", "updated_at"])
        _add_event(
            commitment,
            actor=actor,
            event_type=CommitmentEvent.EventType.UNSNOOZED,
        )
        return commitment


def set_waiting(*, actor, commitment_id):
    with transaction.atomic():
        commitment = _lock_for_responsible(actor, commitment_id)
        if commitment.status == Commitment.Status.WAITING:
            return commitment
        if commitment.status != Commitment.Status.PENDING:
            raise CommitmentInvalidTransitionError()
        commitment.status = Commitment.Status.WAITING
        commitment.save(update_fields=["status", "updated_at"])
        _add_event(
            commitment,
            actor=actor,
            event_type=CommitmentEvent.EventType.WAITING,
        )
        return commitment


def clear_waiting(*, actor, commitment_id):
    with transaction.atomic():
        commitment = _lock_for_responsible(actor, commitment_id)
        if commitment.status == Commitment.Status.PENDING:
            return commitment
        if commitment.status != Commitment.Status.WAITING:
            raise CommitmentInvalidTransitionError()
        commitment.status = Commitment.Status.PENDING
        commitment.save(update_fields=["status", "updated_at"])
        _add_event(
            commitment,
            actor=actor,
            event_type=CommitmentEvent.EventType.UPDATED,
            metadata={"from": Commitment.Status.WAITING, "to": Commitment.Status.PENDING},
        )
        return commitment


def cancel_commitment(*, actor, commitment_id):
    with transaction.atomic():
        commitment = _lock_for_responsible(actor, commitment_id)
        if commitment.status == Commitment.Status.CANCELLED:
            return commitment
        if commitment.status not in _RESPONSIBLE_STATUSES:
            raise CommitmentInvalidTransitionError()
        now = timezone.now()
        commitment.status = Commitment.Status.CANCELLED
        commitment.cancelled_at = now
        commitment.snoozed_until = None
        commitment.save(
            update_fields=["status", "cancelled_at", "snoozed_until", "updated_at"]
        )
        _add_event(
            commitment,
            actor=actor,
            event_type=CommitmentEvent.EventType.CANCELLED,
        )
        return commitment


def _overdue_q(now):
    return Q(
        status__in=(Commitment.Status.PENDING, Commitment.Status.WAITING),
        due_at__isnull=False,
        due_at__lte=now,
    )


def _validate_due_consistency(due_at, due_precision):
    if due_precision == Commitment.DuePrecision.NONE:
        if due_at is not None:
            raise CommitmentValidationError()
        return
    if due_at is None:
        raise CommitmentValidationError()


def _is_active_responsible(user, commitment):
    return commitment.participants.filter(
        user=user,
        role=CommitmentParticipant.Role.RESPONSIBLE,
        status=CommitmentParticipant.Status.ACTIVE,
    ).exists()


def _lookup_commitment_for_update(commitment_id):
    if isinstance(commitment_id, int) or (isinstance(commitment_id, str) and str(commitment_id).isdigit()):
        return Commitment.objects.select_for_update().filter(numeric_id=int(commitment_id)).first()
    try:
        val = uuid.UUID(str(commitment_id))
        return Commitment.objects.select_for_update().filter(id=val).first()
    except (ValueError, AttributeError):
        return None


def _lock_for_responsible(actor, commitment_id):
    commitment = _lookup_commitment_for_update(commitment_id)
    if commitment is None or not can_view(actor, commitment):
        raise CommitmentNotFoundError()
    if not _is_active_responsible(actor, commitment):
        raise CommitmentForbiddenError()
    return commitment


def _validate_snoozed_until(snoozed_until):
    if snoozed_until is None or timezone.is_naive(snoozed_until):
        raise CommitmentInvalidSnoozeError()
    now = timezone.now()
    if snoozed_until <= now:
        raise CommitmentInvalidSnoozeError()
    if snoozed_until > now + SNOOZE_MAX_LIFETIME:
        raise CommitmentInvalidSnoozeError()


_OUTBOX_EVENT_TYPES = {
    CommitmentEvent.EventType.CREATED: "commitment.created",
    CommitmentEvent.EventType.UPDATED: "commitment.updated",
    CommitmentEvent.EventType.COMPLETED: "commitment.completed",
    CommitmentEvent.EventType.SNOOZED: "commitment.snoozed",
    CommitmentEvent.EventType.UNSNOOZED: "commitment.unsnoozed",
    CommitmentEvent.EventType.WAITING: "commitment.waiting",
    CommitmentEvent.EventType.CANCELLED: "commitment.cancelled",
}


def _iso(value):
    if value is None:
        return None
    return value.isoformat()


def _commitment_outbox_payload(commitment, domain_event):
    payload = {
        "commitment_event_id": str(domain_event.id),
        "commitment_id": str(commitment.id),
        "created_by_user_id": str(commitment.created_by_id),
        "actor_user_id": (
            str(domain_event.actor_id) if domain_event.actor_id else None
        ),
        "status": commitment.status,
    }
    event_type = domain_event.event_type
    if event_type == CommitmentEvent.EventType.CREATED:
        payload["due_at"] = _iso(commitment.due_at)
        payload["due_precision"] = commitment.due_precision
        payload["source"] = commitment.source
    elif event_type == CommitmentEvent.EventType.UPDATED:
        if "fields" in domain_event.metadata:
            payload["fields"] = domain_event.metadata["fields"]
        if "from" in domain_event.metadata:
            payload["from"] = domain_event.metadata["from"]
            payload["to"] = domain_event.metadata["to"]
    elif event_type == CommitmentEvent.EventType.COMPLETED:
        payload["completed_at"] = _iso(commitment.completed_at)
    elif event_type == CommitmentEvent.EventType.SNOOZED:
        payload["snoozed_until"] = _iso(commitment.snoozed_until)
    elif event_type == CommitmentEvent.EventType.CANCELLED:
        payload["cancelled_at"] = _iso(commitment.cancelled_at)
    return payload


def _add_event(commitment, *, actor, event_type, metadata=None):
    domain_event = CommitmentEvent.objects.create(
        commitment=commitment,
        actor=actor,
        event_type=event_type,
        metadata=metadata or {},
    )
    record_outbox_event(
        aggregate_type="commitment",
        aggregate_id=commitment.id,
        event_type=_OUTBOX_EVENT_TYPES[event_type],
        payload=_commitment_outbox_payload(commitment, domain_event),
        occurred_at=domain_event.created_at,
    )
    return domain_event
