import json
from datetime import date
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model

from apps.goals.models import Goal, GoalCheckIn, GoalEvent
from apps.goals.services import (
    cancel_goal,
    complete_goal,
    create_goal,
    pause_goal,
    record_check_in,
    resume_goal,
    update_goal,
)
from apps.outbox.models import OutboxEvent
from apps.outbox.publisher import publish_due_outbox_events
from apps.outbox.services import EVENT_VERSION, MAX_PAYLOAD_BYTES

User = get_user_model()

EVENT_TYPE_MAP = {
    GoalEvent.EventType.CREATED: "goal.created",
    GoalEvent.EventType.UPDATED: "goal.updated",
    GoalEvent.EventType.PAUSED: "goal.paused",
    GoalEvent.EventType.RESUMED: "goal.resumed",
    GoalEvent.EventType.COMPLETED: "goal.completed",
    GoalEvent.EventType.CANCELLED: "goal.cancelled",
    GoalEvent.EventType.CHECKIN_RECORDED: "goal.checkin.created",
    GoalEvent.EventType.CHECKIN_UPDATED: "goal.checkin.updated",
    GoalEvent.EventType.PARTICIPANT_INVITED: "goal.participant.invited",
    GoalEvent.EventType.PARTICIPANT_JOINED: "goal.participant.joined",
    GoalEvent.EventType.PARTICIPANT_DECLINED: "goal.participant.declined",
    GoalEvent.EventType.PARTICIPANT_LEFT: "goal.participant.left",
    GoalEvent.EventType.PARTICIPANT_REMOVED: "goal.participant.removed",
}
FORBIDDEN_PAYLOAD_MARKERS = (
    "password",
    "jwt",
    "refresh_token",
    "access_token",
    "signing_key",
    "encryption_key",
    "hmac",
)


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun@example.com",
        name="Arjun",
        password="a-secure-password",
        timezone="Asia/Kolkata",
    )


def _daily(creator, **overrides):
    fields = {
        "creator": creator,
        "title": "Read every day",
        "description": "secret study notes",
        "start_date": date(2026, 8, 10),
        "recurrence_kind": Goal.RecurrenceKind.DAILY,
    }
    fields.update(overrides)
    return create_goal(**fields)


def _assert_outbox_for(goal, domain_event):
    outbox = OutboxEvent.objects.get(
        aggregate_id=goal.id,
        event_type=EVENT_TYPE_MAP[domain_event.event_type],
    )
    payload = outbox.payload
    dumped = json.dumps(payload)
    assert outbox.id is not None
    assert outbox.event_type == EVENT_TYPE_MAP[domain_event.event_type]
    assert outbox.event_version == EVENT_VERSION == 1
    assert outbox.aggregate_type == "goal"
    assert outbox.aggregate_id == goal.id
    assert isinstance(payload, dict)
    assert payload["goal_event_id"] == str(domain_event.id)
    assert payload["goal_id"] == str(goal.id)
    assert payload["created_by_user_id"] == str(goal.created_by_id)
    assert payload["status"] == goal.status
    assert outbox.published_at is None
    assert "title" not in payload
    assert "description" not in payload
    assert "note" not in payload
    lowered = dumped.lower()
    for marker in FORBIDDEN_PAYLOAD_MARKERS:
        assert marker not in lowered
    assert "secret study notes" not in dumped
    assert "a-secure-password" not in dumped
    assert len(dumped.encode("utf-8")) <= MAX_PAYLOAD_BYTES
    return outbox


def _latest_event(goal):
    return goal.events.order_by("created_at").last()


@pytest.mark.django_db
def test_create_writes_one_goal_event_and_one_outbox(arjun):
    goal = _daily(arjun)
    domain_event = goal.events.get()
    outbox = _assert_outbox_for(goal, domain_event)

    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1
    assert domain_event.event_type == GoalEvent.EventType.CREATED
    assert outbox.event_type == "goal.created"
    assert outbox.payload["recurrence_kind"] == Goal.RecurrenceKind.DAILY
    assert outbox.payload["source"] == Goal.Source.MANUAL


@pytest.mark.django_db
def test_create_rolls_back_if_outbox_fails(arjun):
    with patch(
        "apps.goals.services.record_outbox_event",
        side_effect=RuntimeError("outbox failed"),
    ):
        with pytest.raises(RuntimeError):
            _daily(arjun)

    assert Goal.objects.count() == 0
    assert GoalEvent.objects.count() == 0
    assert OutboxEvent.objects.count() == 0


@pytest.mark.django_db
def test_update_pause_resume_complete_cancel_write_one_event_and_outbox(arjun):
    goal = _daily(arjun)
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()

    update_goal(actor=arjun, goal_id=goal.id, title="Keep reading")
    goal.refresh_from_db()
    updated = _assert_outbox_for(goal, _latest_event(goal))
    assert updated.event_type == "goal.updated"
    assert updated.payload["fields"] == ["title"]
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    pause_goal(actor=arjun, goal_id=goal.id)
    goal.refresh_from_db()
    paused = _assert_outbox_for(goal, _latest_event(goal))
    assert paused.event_type == "goal.paused"
    assert paused.payload["paused_at"] is not None

    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    resume_goal(actor=arjun, goal_id=goal.id)
    goal.refresh_from_db()
    resumed = _assert_outbox_for(goal, _latest_event(goal))
    assert resumed.event_type == "goal.resumed"
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()
    complete_goal(actor=arjun, goal_id=goal.id)
    goal.refresh_from_db()
    completed = _assert_outbox_for(goal, _latest_event(goal))
    assert completed.event_type == "goal.completed"
    assert completed.payload["completed_at"] is not None

    other = _daily(arjun, title="Cancel me")
    OutboxEvent.objects.filter(aggregate_id=other.id).delete()
    other.events.all().delete()
    cancel_goal(actor=arjun, goal_id=other.id)
    other.refresh_from_db()
    cancelled = _assert_outbox_for(other, _latest_event(other))
    assert cancelled.event_type == "goal.cancelled"
    assert cancelled.payload["cancelled_at"] is not None


@pytest.mark.django_db
def test_noop_mutations_write_neither_event_nor_outbox(arjun):
    goal = _daily(arjun)
    update_goal(actor=arjun, goal_id=goal.id, title="Read every day")
    pause_goal(actor=arjun, goal_id=goal.id)
    pause_goal(actor=arjun, goal_id=goal.id)
    resume_goal(actor=arjun, goal_id=goal.id)
    resume_goal(actor=arjun, goal_id=goal.id)
    complete_goal(actor=arjun, goal_id=goal.id)
    complete_goal(actor=arjun, goal_id=goal.id)

    assert GoalEvent.objects.filter(goal=goal).count() == 4
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 4

    other = _daily(arjun, title="Cancel me")
    cancel_goal(actor=arjun, goal_id=other.id)
    cancel_goal(actor=arjun, goal_id=other.id)
    assert GoalEvent.objects.filter(goal=other).count() == 2
    assert OutboxEvent.objects.filter(aggregate_id=other.id).count() == 2


@pytest.mark.django_db
def test_check_in_create_update_and_identical_retry(arjun):
    goal = _daily(arjun)
    OutboxEvent.objects.filter(aggregate_id=goal.id).delete()
    goal.events.all().delete()

    created = record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        note="secret note",
    )
    recorded = _assert_outbox_for(goal, _latest_event(goal))
    assert recorded.event_type == "goal.checkin.created"
    assert recorded.payload["period_date"] == "2026-08-10"
    assert recorded.payload["checkin_id"] == str(created.id)
    assert recorded.payload["checkin_status"] == GoalCheckIn.Status.COMPLETED
    assert recorded.payload["participant_id"] == str(created.participant_id)
    assert "secret note" not in json.dumps(recorded.payload)
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
        note="secret note",
    )
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 1

    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.SKIPPED,
        note="changed",
    )
    updated = OutboxEvent.objects.get(event_type="goal.checkin.updated")
    domain_event = goal.events.get(event_type=GoalEvent.EventType.CHECKIN_UPDATED)
    assert updated.aggregate_type == "goal"
    assert updated.aggregate_id == goal.id
    assert updated.payload["checkin_status"] == GoalCheckIn.Status.SKIPPED
    assert updated.payload["goal_event_id"] == str(domain_event.id)
    assert "changed" not in json.dumps(updated.payload)
    assert GoalEvent.objects.filter(goal=goal).count() == 2
    assert OutboxEvent.objects.filter(aggregate_id=goal.id).count() == 2


@pytest.mark.django_db
def test_kafka_unavailable_leaves_goal_unchanged_and_outbox_unpublished(arjun):
    from confluent_kafka import Producer

    from apps.outbox.kafka import publish_record

    goal = _daily(arjun)
    outbox = OutboxEvent.objects.get(aggregate_id=goal.id)
    event_id = outbox.id
    status = goal.status
    title = goal.title
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

    goal.refresh_from_db()
    outbox.refresh_from_db()
    assert result.published == 0
    assert result.failed == 1
    assert goal.status == status
    assert goal.title == title
    assert Goal.objects.filter(id=goal.id).count() == 1
    assert GoalEvent.objects.filter(goal=goal).count() == 1
    assert outbox.id == event_id
    assert outbox.published_at is None
    assert outbox.attempts == 1
    assert outbox.next_attempt_at is not None
    assert outbox.last_error != ""
    assert "password" not in outbox.last_error.lower()
    assert "a-secure-password" not in outbox.last_error
