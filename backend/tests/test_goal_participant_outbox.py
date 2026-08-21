"""Phase 10.5 — Shared Goal participant event / outbox / Kafka verification."""

import json
import threading
from datetime import date
from unittest.mock import patch
from uuid import uuid4

import pytest
from django.contrib.auth import get_user_model
from django.db import connection

from apps.goals.models import Goal, GoalCheckIn, GoalEvent, GoalParticipant
from apps.goals.services import (
    accept_invitation,
    create_goal,
    decline_invitation,
    invite_participant,
    leave_goal,
    record_check_in,
    remove_participant,
)
from apps.outbox.consumer import handle_record
from apps.outbox.models import OutboxEvent, ProcessedEvent
from apps.outbox.publisher import GOAL_TOPIC, publish_due_outbox_events
from apps.outbox.services import EVENT_VERSION

User = get_user_model()

PARTICIPANT_EVENT_MAP = {
    GoalEvent.EventType.PARTICIPANT_INVITED: "goal.participant.invited",
    GoalEvent.EventType.PARTICIPANT_JOINED: "goal.participant.joined",
    GoalEvent.EventType.PARTICIPANT_DECLINED: "goal.participant.declined",
    GoalEvent.EventType.PARTICIPANT_LEFT: "goal.participant.left",
    GoalEvent.EventType.PARTICIPANT_REMOVED: "goal.participant.removed",
}


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun-outbox-shared@example.com",
        name="Arjun",
        password="a-secure-password",
        timezone="Asia/Kolkata",
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul-outbox-shared@example.com",
        name="Rahul",
        password="a-secure-password",
        timezone="UTC",
    )


def _daily(creator, **overrides):
    fields = {
        "creator": creator,
        "title": "Shared practice",
        "description": "secret study notes",
        "start_date": date(2026, 8, 10),
        "recurrence_kind": Goal.RecurrenceKind.DAILY,
    }
    fields.update(overrides)
    return create_goal(**fields)


def _assert_participant_outbox(goal, domain_event, participant, *, actor):
    outbox_type = PARTICIPANT_EVENT_MAP[domain_event.event_type]
    outbox = OutboxEvent.objects.get(
        aggregate_id=goal.id,
        event_type=outbox_type,
    )
    payload = outbox.payload
    assert outbox.aggregate_type == "goal"
    assert outbox.event_version == EVENT_VERSION == 1
    assert outbox.published_at is None
    assert payload["goal_event_id"] == str(domain_event.id)
    assert payload["goal_id"] == str(goal.id)
    assert payload["actor_user_id"] == str(actor.id)
    assert payload["participant_id"] == str(participant.id)
    assert payload["target_user_id"] == str(participant.user_id)
    assert payload["participant_role"] == participant.role
    assert payload["participant_status"] == participant.status
    assert payload["status"] == goal.status
    assert "title" not in payload
    assert "description" not in payload
    assert "note" not in payload
    assert "email" not in payload
    dumped = json.dumps(payload)
    lowered = dumped.lower()
    for marker in ("password", "jwt", "refresh_token", "access_token", "signing_key"):
        assert marker not in lowered
    assert "secret study notes" not in dumped
    assert "a-secure-password" not in dumped
    assert domain_event.actor_id == actor.id
    assert domain_event.metadata["participant_id"] == str(participant.id)
    assert domain_event.metadata["target_user_id"] == str(participant.user_id)
    return outbox


@pytest.mark.django_db
def test_each_participant_mutation_writes_one_event_and_outbox(arjun, rahul):
    goal = _daily(arjun)
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()

    invited = invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    invited_event = goal.events.get(event_type=GoalEvent.EventType.PARTICIPANT_INVITED)
    _assert_participant_outbox(goal, invited_event, invited, actor=arjun)
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    # duplicate invite no-op
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    joined = accept_invitation(actor=rahul, goal_id=goal.id)
    joined_event = goal.events.get(event_type=GoalEvent.EventType.PARTICIPANT_JOINED)
    _assert_participant_outbox(goal, joined_event, joined, actor=rahul)

    # already ACTIVE no-op
    accept_invitation(actor=rahul, goal_id=goal.id)
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    left = leave_goal(actor=rahul, goal_id=goal.id)
    left_event = goal.events.get(event_type=GoalEvent.EventType.PARTICIPANT_LEFT)
    _assert_participant_outbox(goal, left_event, left, actor=rahul)
    leave_goal(actor=rahul, goal_id=goal.id)
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    declined = decline_invitation(actor=rahul, goal_id=goal.id)
    declined_event = goal.events.get(
        event_type=GoalEvent.EventType.PARTICIPANT_DECLINED
    )
    _assert_participant_outbox(goal, declined_event, declined, actor=rahul)
    decline_invitation(actor=rahul, goal_id=goal.id)
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    accept_invitation(actor=rahul, goal_id=goal.id)
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    removed = remove_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    removed_event = goal.events.get(
        event_type=GoalEvent.EventType.PARTICIPANT_REMOVED
    )
    _assert_participant_outbox(goal, removed_event, removed, actor=arjun)
    remove_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1


@pytest.mark.django_db
def test_participant_invite_rolls_back_if_outbox_fails(arjun, rahul):
    goal = _daily(arjun)
    with patch(
        "apps.goals.services.record_outbox_event",
        side_effect=RuntimeError("outbox failed"),
    ):
        with pytest.raises(RuntimeError):
            invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)

    assert GoalParticipant.objects.filter(goal=goal, user=rahul).count() == 0
    assert (
        GoalEvent.objects.filter(
            goal=goal, event_type=GoalEvent.EventType.PARTICIPANT_INVITED
        ).count()
        == 0
    )
    assert (
        OutboxEvent.objects.filter(
            aggregate_id=goal.id, event_type="goal.participant.invited"
        ).count()
        == 0
    )


@pytest.mark.django_db
def test_checkin_outbox_includes_participant_id(arjun):
    goal = _daily(arjun)
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    check_in = record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        note="secret note",
    )
    domain_event = goal.events.get()
    outbox = OutboxEvent.objects.get(event_type="goal.checkin.created")
    assert domain_event.metadata["participant_id"] == str(check_in.participant_id)
    assert outbox.payload["participant_id"] == str(check_in.participant_id)
    assert "secret note" not in json.dumps(outbox.payload)


@pytest.mark.django_db
def test_participant_events_publish_to_goal_topic(arjun, rahul):
    goal = _daily(arjun)
    OutboxEvent.objects.filter(aggregate_id=goal.id).update(
        published_at=timezone_now_proxy(),
        next_attempt_at=None,
    )
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    calls = []

    def publish(topic, key, value):
        calls.append((topic, key, value))

    result = publish_due_outbox_events(publish=publish)
    outbox = OutboxEvent.objects.get(event_type="goal.participant.invited")
    topic, key, value = calls[0]
    assert result.published == 1
    assert topic == GOAL_TOPIC == "promise.goal.v1"
    assert key == str(goal.id) == str(outbox.aggregate_id)
    assert value["event_id"] == str(outbox.id)
    assert value["event_type"] == "goal.participant.invited"
    assert value["event_version"] == 1
    assert value["aggregate_type"] == "goal"
    assert "title" not in json.dumps(value)
    assert "email" not in json.dumps(value).lower()
    outbox.refresh_from_db()
    assert outbox.published_at is not None
    assert OutboxEvent.objects.filter(id=outbox.id).exists()


def timezone_now_proxy():
    from django.utils import timezone

    return timezone.now()


@pytest.mark.django_db
def test_participant_event_consumer_accepts_and_dedupes(arjun, rahul):
    goal = _daily(arjun)
    participant = invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    outbox = OutboxEvent.objects.get(event_type="goal.participant.invited")
    envelope = {
        "event_id": str(outbox.id),
        "event_type": "goal.participant.invited",
        "event_version": 1,
        "occurred_at": "2026-08-20T18:00:00Z",
        "aggregate_type": "goal",
        "aggregate_id": str(goal.id),
        "payload": {
            "participant_id": str(participant.id),
            "target_user_id": str(rahul.id),
            "status": "ACTIVE",
        },
    }
    raw = json.dumps(envelope).encode("utf-8")
    commits = []
    calls = []
    group = "promise-goal-events-verify"

    first = handle_record(
        consumer_group=group,
        raw_value=raw,
        processor=lambda event: calls.append(event["event_type"]),
        commit_offset=lambda: commits.append("commit"),
    )
    second = handle_record(
        consumer_group=group,
        raw_value=raw,
        processor=lambda event: calls.append(event["event_type"]),
        commit_offset=lambda: commits.append("commit"),
    )

    assert first == "processed"
    assert second == "duplicate"
    assert calls == ["goal.participant.invited"]
    assert commits == ["commit", "commit"]
    assert ProcessedEvent.objects.filter(
        consumer_group=group, event_id=outbox.id
    ).count() == 1


@pytest.mark.django_db
def test_all_participant_outbox_types_are_consumer_supported():
    from apps.outbox.consumer import SUPPORTED_EVENT_TYPES

    for event_type in PARTICIPANT_EVENT_MAP.values():
        assert event_type in SUPPORTED_EVENT_TYPES


@pytest.mark.django_db(transaction=True)
def test_concurrent_duplicate_consumer_delivery_one_processed_row(arjun, rahul):
    goal = _daily(arjun)
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    outbox = OutboxEvent.objects.get(event_type="goal.participant.invited")
    envelope = {
        "event_id": str(outbox.id),
        "event_type": "goal.participant.invited",
        "event_version": 1,
        "occurred_at": "2026-08-20T18:00:00Z",
        "aggregate_type": "goal",
        "aggregate_id": str(goal.id),
        "payload": {"participant_id": str(uuid4())},
    }
    raw = json.dumps(envelope).encode("utf-8")
    group = f"promise-goal-events-concurrent-{uuid4()}"
    outcomes = []

    def worker():
        try:
            outcomes.append(
                handle_record(
                    consumer_group=group,
                    raw_value=raw,
                    processor=lambda event: None,
                    commit_offset=lambda: None,
                )
            )
        finally:
            connection.close()

    threads = [threading.Thread(target=worker) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert sorted(outcomes) == ["duplicate", "processed"]
    assert ProcessedEvent.objects.filter(
        consumer_group=group, event_id=outbox.id
    ).count() == 1


@pytest.mark.django_db
def test_kafka_unavailable_keeps_participant_mutation_and_retries_outbox(arjun, rahul):
    from confluent_kafka import Producer

    from apps.outbox.kafka import publish_record

    goal = _daily(arjun)
    OutboxEvent.objects.filter(aggregate_id=goal.id).update(
        published_at=timezone_now_proxy(),
        next_attempt_at=None,
    )
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    outbox = OutboxEvent.objects.get(event_type="goal.participant.invited")
    event_id = outbox.id
    producer = Producer(
        {
            "bootstrap.servers": "127.0.0.1:1",
            "acks": "all",
            "socket.timeout.ms": 1000,
            "socket.connection.setup.timeout.ms": 1000,
            "message.timeout.ms": 1000,
        }
    )

    def publish(topic, key, value):
        publish_record(topic, key, value, producer=producer)

    result = publish_due_outbox_events(publish=publish)
    outbox.refresh_from_db()

    assert result.published == 0
    assert result.failed == 1
    assert GoalParticipant.objects.filter(goal=goal, user=rahul).exists()
    assert GoalEvent.objects.filter(
        goal=goal, event_type=GoalEvent.EventType.PARTICIPANT_INVITED
    ).exists()
    assert outbox.id == event_id
    assert outbox.published_at is None
    assert outbox.attempts == 1
    assert outbox.next_attempt_at is not None
    assert outbox.last_error != ""
    assert "password" not in outbox.last_error.lower()


@pytest.mark.django_db
def test_full_lifecycle_outbox_types_then_publish_and_consume(arjun, rahul):
    goal = _daily(arjun)
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()

    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    accept_invitation(actor=rahul, goal_id=goal.id)
    leave_goal(actor=rahul, goal_id=goal.id)
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    decline_invitation(actor=rahul, goal_id=goal.id)
    invite_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)
    accept_invitation(actor=rahul, goal_id=goal.id)
    remove_participant(actor=arjun, goal_id=goal.id, user_id=rahul.id)

    types = list(
        OutboxEvent.objects.filter(aggregate_id=goal.id)
        .order_by("created_at")
        .values_list("event_type", flat=True)
    )
    assert types == [
        "goal.participant.invited",
        "goal.participant.joined",
        "goal.participant.left",
        "goal.participant.invited",
        "goal.participant.declined",
        "goal.participant.invited",
        "goal.participant.joined",
        "goal.participant.removed",
    ]

    published = []

    def publish(topic, key, value):
        published.append(value)
        assert topic == GOAL_TOPIC
        assert key == str(goal.id)

    result = publish_due_outbox_events(publish=publish)
    assert result.published == len(types)
    assert result.failed == 0
    assert OutboxEvent.objects.filter(
        aggregate_id=goal.id, published_at__isnull=True
    ).count() == 0

    group = f"promise-goal-lifecycle-{uuid4()}"
    for value in published:
        outcome = handle_record(
            consumer_group=group,
            raw_value=json.dumps(value).encode("utf-8"),
            processor=lambda event: None,
            commit_offset=lambda: None,
        )
        assert outcome == "processed"
    assert ProcessedEvent.objects.filter(consumer_group=group).count() == len(types)
