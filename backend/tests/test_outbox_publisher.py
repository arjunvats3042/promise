import json
import threading
from datetime import date, timedelta
from io import StringIO
from uuid import uuid4
import pytest
from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.db import connection
from django.utils import timezone

from apps.commitments.services import create_commitment
from apps.outbox.models import OutboxEvent
from apps.outbox.publisher import (
    BATCH_SIZE,
    COMMITMENT_TOPIC,
    GOAL_TOPIC,
    MAX_ATTEMPTS,
    backoff_for_attempt,
    publish_due_outbox_events,
)

User = get_user_model()


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun@example.com",
        name="Arjun",
        password="a-secure-password",
    )


def _recording_publish(error=None):
    calls = []

    def publish(topic, key, value):
        calls.append((topic, key, value))
        if error is not None:
            raise error

    return calls, publish


def _due_outbox(**overrides):
    now = timezone.now()
    fields = {
        "aggregate_type": "commitment",
        "aggregate_id": uuid4(),
        "event_type": "commitment.created",
        "event_version": 1,
        "payload": {"status": "PENDING"},
        "occurred_at": now,
        "published_at": None,
        "attempts": 0,
        "next_attempt_at": now,
        "last_error": "",
    }
    fields.update(overrides)
    return OutboxEvent.objects.create(**fields)


def test_backoff_schedule_caps_at_thirty_minutes():
    assert backoff_for_attempt(1) == timedelta(seconds=5)
    assert backoff_for_attempt(2) == timedelta(seconds=15)
    assert backoff_for_attempt(3) == timedelta(seconds=45)
    assert backoff_for_attempt(4) == timedelta(minutes=2)
    assert backoff_for_attempt(5) == timedelta(minutes=5)
    assert backoff_for_attempt(6) == timedelta(minutes=15)
    assert backoff_for_attempt(7) == timedelta(minutes=30)
    assert backoff_for_attempt(8) == timedelta(minutes=30)
    assert backoff_for_attempt(99) == timedelta(minutes=30)
    assert MAX_ATTEMPTS == 8
    assert BATCH_SIZE == 50


@pytest.mark.django_db
def test_publish_due_events_sends_envelope_and_marks_published(arjun):
    commitment = create_commitment(creator=arjun, title="Study DSA tonight")
    outbox = OutboxEvent.objects.get()
    event_id = outbox.id
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    outbox.refresh_from_db()
    assert result.published == 1
    assert result.failed == 0
    assert len(calls) == 1
    topic, key, value = calls[0]
    assert topic == COMMITMENT_TOPIC == "promise.commitment.v1"
    assert key == str(commitment.id) == str(outbox.aggregate_id)
    assert value["event_id"] == str(event_id)
    assert value["event_type"] == "commitment.created"
    assert value["event_version"] == 1
    assert value["aggregate_type"] == "commitment"
    assert value["aggregate_id"] == str(commitment.id)
    assert value["payload"] == outbox.payload
    assert value["occurred_at"].endswith("Z")
    assert "+00:00" not in value["occurred_at"]
    assert "title" not in json.dumps(value)
    assert "a-secure-password" not in json.dumps(value)
    assert outbox.published_at is not None
    assert outbox.attempts == 1
    assert outbox.last_error == ""
    assert outbox.id == event_id
    assert OutboxEvent.objects.filter(id=event_id).count() == 1


@pytest.mark.django_db
def test_already_published_rows_are_skipped(arjun):
    create_commitment(creator=arjun, title="Done already")
    outbox = OutboxEvent.objects.get()
    outbox.published_at = timezone.now()
    outbox.attempts = 1
    outbox.save(update_fields=["published_at", "attempts", "updated_at"])
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    outbox.refresh_from_db()
    assert result.published == 0
    assert result.failed == 0
    assert calls == []
    assert outbox.published_at is not None
    assert outbox.attempts == 1


@pytest.mark.django_db
def test_no_due_rows_is_a_noop():
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    assert result.published == 0
    assert result.failed == 0
    assert calls == []
    assert OutboxEvent.objects.count() == 0


@pytest.mark.django_db
def test_future_and_parked_rows_are_skipped():
    now = timezone.now()
    _due_outbox(next_attempt_at=now + timedelta(hours=1), event_type="commitment.created")
    _due_outbox(next_attempt_at=None, event_type="commitment.completed")
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    assert result.published == 0
    assert calls == []
    assert OutboxEvent.objects.filter(published_at__isnull=True).count() == 2


@pytest.mark.django_db
def test_failed_publish_increments_attempts_and_schedules_backoff(monkeypatch):
    now = timezone.now()
    monkeypatch.setattr("apps.outbox.publisher.timezone.now", lambda: now)
    outbox = _due_outbox(next_attempt_at=now, occurred_at=now)
    event_id = outbox.id
    error = RuntimeError("password=supersecret token=abc payload=should-not-leak")
    calls, publish = _recording_publish(error=error)

    result = publish_due_outbox_events(publish=publish)

    outbox.refresh_from_db()
    assert result.published == 0
    assert result.failed == 1
    assert len(calls) == 1
    assert calls[0][2]["event_id"] == str(event_id)
    assert outbox.published_at is None
    assert outbox.attempts == 1
    assert outbox.next_attempt_at == now + timedelta(seconds=5)
    assert outbox.id == event_id
    assert len(outbox.last_error) <= 2048
    assert outbox.last_error != ""
    assert "supersecret" not in outbox.last_error
    assert "password=" not in outbox.last_error
    assert "token=abc" not in outbox.last_error
    assert "should-not-leak" not in outbox.last_error


@pytest.mark.django_db
def test_retry_preserves_event_id_then_publishes(monkeypatch):
    now = timezone.now()
    monkeypatch.setattr("apps.outbox.publisher.timezone.now", lambda: now)
    outbox = _due_outbox(next_attempt_at=now, occurred_at=now)
    event_id = outbox.id
    calls = []
    state = {"fails_left": 1}

    def publish(topic, key, value):
        calls.append((topic, key, value))
        if state["fails_left"]:
            state["fails_left"] -= 1
            raise RuntimeError("broker down")

    first = publish_due_outbox_events(publish=publish)
    outbox.refresh_from_db()
    assert first.failed == 1
    assert outbox.published_at is None
    assert outbox.attempts == 1

    outbox.next_attempt_at = now
    outbox.save(update_fields=["next_attempt_at", "updated_at"])

    second = publish_due_outbox_events(publish=publish)
    outbox.refresh_from_db()
    assert second.published == 1
    assert outbox.published_at == now
    assert outbox.attempts == 2
    assert outbox.last_error == ""
    assert outbox.id == event_id
    assert [value["event_id"] for _, _, value in calls] == [str(event_id), str(event_id)]


@pytest.mark.django_db
def test_seventh_failure_uses_thirty_minute_cap(monkeypatch):
    now = timezone.now()
    monkeypatch.setattr("apps.outbox.publisher.timezone.now", lambda: now)
    outbox = _due_outbox(attempts=6, next_attempt_at=now, occurred_at=now)
    calls, publish = _recording_publish(error=RuntimeError("broker down"))

    publish_due_outbox_events(publish=publish)

    outbox.refresh_from_db()
    assert outbox.published_at is None
    assert outbox.attempts == 7
    assert outbox.next_attempt_at == now + timedelta(minutes=30)


@pytest.mark.django_db
def test_eighth_failure_parks_the_event(monkeypatch):
    now = timezone.now()
    monkeypatch.setattr("apps.outbox.publisher.timezone.now", lambda: now)
    outbox = _due_outbox(
        attempts=7,
        last_error="prior",
        next_attempt_at=now,
        occurred_at=now,
    )
    calls, publish = _recording_publish(error=RuntimeError("broker down"))

    result = publish_due_outbox_events(publish=publish)

    outbox.refresh_from_db()
    assert result.failed == 1
    assert outbox.published_at is None
    assert outbox.attempts == 8
    assert outbox.next_attempt_at is None
    assert outbox.last_error != ""
    assert "broker down" not in outbox.last_error

    calls.clear()
    skipped = publish_due_outbox_events(publish=publish)
    assert skipped.published == 0
    assert skipped.failed == 0
    assert calls == []


@pytest.mark.django_db
def test_duplicate_invocation_does_not_republish(arjun):
    create_commitment(creator=arjun, title="Once only")
    calls, publish = _recording_publish()

    first = publish_due_outbox_events(publish=publish)
    second = publish_due_outbox_events(publish=publish)

    assert first.published == 1
    assert second.published == 0
    assert len(calls) == 1
    assert OutboxEvent.objects.get().attempts == 1


@pytest.mark.django_db
def test_invalid_payload_is_not_published_and_is_retried():
    outbox = _due_outbox(payload=["not-an-object", "supersecret-token"])
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    outbox.refresh_from_db()
    assert result.published == 0
    assert result.failed == 1
    assert calls == []
    assert outbox.published_at is None
    assert outbox.attempts == 1
    assert outbox.next_attempt_at is not None
    assert "supersecret-token" not in outbox.last_error
    assert "not-an-object" not in outbox.last_error


@pytest.mark.django_db
def test_goal_events_route_to_goal_topic_with_aggregate_id_key(arjun):
    from apps.goals.models import Goal
    from apps.goals.services import create_goal

    goal = create_goal(
        creator=arjun,
        title="Study DSA 5 days every week",
        start_date=date(2026, 8, 10),
        recurrence_kind=Goal.RecurrenceKind.N_PER_PERIOD,
        period_unit=Goal.PeriodUnit.WEEK,
        times_per_period=5,
    )
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    outbox = OutboxEvent.objects.get()
    topic, key, value = calls[0]
    assert result.published == 1
    assert result.failed == 0
    assert topic == GOAL_TOPIC == "promise.goal.v1"
    assert key == str(goal.id) == str(outbox.aggregate_id)
    assert value["aggregate_type"] == "goal"
    assert value["aggregate_id"] == str(goal.id)
    assert value["event_type"] == "goal.created"
    assert value["event_id"] == str(outbox.id)
    assert "title" not in json.dumps(value)
    assert outbox.published_at is not None
    assert outbox.attempts == 1


@pytest.mark.django_db
def test_goal_checkin_events_use_goal_topic_and_goal_id_key(arjun):
    from apps.goals.models import Goal, GoalCheckIn
    from apps.goals.services import create_goal, record_check_in

    goal = create_goal(
        creator=arjun,
        title="Read every day",
        start_date=date(2026, 8, 10),
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    OutboxEvent.objects.filter(aggregate_id=goal.id).update(
        published_at=timezone.now(),
        next_attempt_at=None,
    )
    record_check_in(
        actor=arjun,
        goal_id=goal.id,
        period_date=date(2026, 8, 10),
        status=GoalCheckIn.Status.COMPLETED,
    )
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    topic, key, value = calls[0]
    assert result.published == 1
    assert topic == GOAL_TOPIC == "promise.goal.v1"
    assert key == str(goal.id)
    assert value["aggregate_type"] == "goal"
    assert value["aggregate_id"] == str(goal.id)
    assert value["event_type"] == "goal.checkin.created"


@pytest.mark.django_db
def test_commitment_and_goal_events_route_to_separate_topics(arjun):
    from apps.goals.models import Goal
    from apps.goals.services import create_goal

    commitment = create_commitment(creator=arjun, title="Finite promise")
    goal = create_goal(
        creator=arjun,
        title="Recurring practice",
        start_date=date(2026, 8, 10),
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    by_type = {value["aggregate_type"]: (topic, key) for topic, key, value in calls}
    assert result.published == 2
    assert result.failed == 0
    assert by_type["commitment"] == (COMMITMENT_TOPIC, str(commitment.id))
    assert by_type["goal"] == (GOAL_TOPIC, str(goal.id))


@pytest.mark.django_db
def test_unknown_aggregate_type_is_not_published_and_is_retried():
    outbox = _due_outbox(
        aggregate_type="user",
        event_type="user.created",
        payload={"password": "supersecret-token"},
    )
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    outbox.refresh_from_db()
    assert result.published == 0
    assert result.failed == 1
    assert calls == []
    assert outbox.published_at is None
    assert outbox.attempts == 1
    assert outbox.next_attempt_at is not None
    assert outbox.last_error != ""
    assert "supersecret-token" not in outbox.last_error
    assert "user" not in outbox.last_error


@pytest.mark.django_db
def test_unknown_aggregate_type_retry_then_park_matches_existing_backoff(monkeypatch):
    now = timezone.now()
    monkeypatch.setattr("apps.outbox.publisher.timezone.now", lambda: now)
    outbox = _due_outbox(
        aggregate_type="challenge",
        event_type="challenge.created",
        attempts=7,
        next_attempt_at=now,
        occurred_at=now,
    )
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    outbox.refresh_from_db()
    assert result.failed == 1
    assert calls == []
    assert outbox.published_at is None
    assert outbox.attempts == 8
    assert outbox.next_attempt_at is None


@pytest.mark.django_db
def test_publisher_processes_bounded_batches(monkeypatch):
    monkeypatch.setattr("apps.outbox.publisher.BATCH_SIZE", 1)
    first = _due_outbox(event_type="commitment.created")
    second = _due_outbox(event_type="commitment.completed")
    calls, publish = _recording_publish()

    result = publish_due_outbox_events(publish=publish)

    published_ids = {value["event_id"] for _, _, value in calls}
    assert result.published == 2
    assert published_ids == {str(first.id), str(second.id)}
    assert OutboxEvent.objects.filter(published_at__isnull=False).count() == 2


@pytest.mark.django_db(transaction=True)
def test_concurrent_publishers_do_not_double_publish(arjun, monkeypatch):
    monkeypatch.setattr("apps.outbox.publisher.BATCH_SIZE", 1)
    create_commitment(creator=arjun, title="First")
    create_commitment(creator=arjun, title="Second")
    barrier = threading.Barrier(2)
    lock = threading.Lock()
    calls = []
    errors = []

    def publish(topic, key, value):
        barrier.wait(timeout=5)
        with lock:
            calls.append((topic, key, value))

    def worker():
        try:
            publish_due_outbox_events(publish=publish)
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
    event_ids = [value["event_id"] for _, _, value in calls]
    assert len(event_ids) == 2
    assert set(event_ids) == {str(event.id) for event in OutboxEvent.objects.all()}
    assert OutboxEvent.objects.filter(published_at__isnull=False).count() == 2
    for event in OutboxEvent.objects.all():
        assert event.attempts == 1


@pytest.mark.django_db
def test_publish_outbox_command_publishes_due_events(arjun, monkeypatch):
    commitment = create_commitment(creator=arjun, title="From the command")
    outbox = OutboxEvent.objects.get()
    calls = []

    def publish(topic, key, value):
        calls.append((topic, key, value))

    monkeypatch.setattr(
        "apps.outbox.management.commands.publish_outbox.publish_due_outbox_events",
        lambda: publish_due_outbox_events(publish=publish),
    )
    stdout = StringIO()
    call_command("publish_outbox", stdout=stdout)

    outbox.refresh_from_db()
    assert "published=1" in stdout.getvalue()
    assert len(calls) == 1
    assert calls[0][0] == "promise.commitment.v1"
    assert calls[0][1] == str(commitment.id)
    assert calls[0][2]["event_id"] == str(outbox.id)
    assert outbox.published_at is not None
    assert outbox.attempts == 1


def test_publish_record_sends_json_and_waits_for_ack():
    from apps.outbox.kafka import publish_record

    produced = []

    class RecordingProducer:
        def produce(self, topic, key=None, value=None, on_delivery=None):
            produced.append((topic, key, value))
            if on_delivery is not None:
                on_delivery(None, None)

        def flush(self, timeout=None):
            return 0

    envelope = {"event_id": "abc", "payload": {"status": "PENDING"}}
    publish_record(
        "promise.commitment.v1",
        "aggregate-key",
        envelope,
        producer=RecordingProducer(),
    )

    assert len(produced) == 1
    topic, key, value = produced[0]
    assert topic == "promise.commitment.v1"
    assert key == b"aggregate-key"
    assert json.loads(value.decode("utf-8")) == envelope


def test_publish_record_raises_on_delivery_error_without_leaking_details():
    from apps.outbox.kafka import KafkaPublishError, publish_record

    class FailingProducer:
        def produce(self, topic, key=None, value=None, on_delivery=None):
            if on_delivery is not None:
                on_delivery("password=supersecret broker failure", None)

        def flush(self, timeout=None):
            return 0

    with pytest.raises(KafkaPublishError, match="Kafka publish failed"):
        publish_record(
            "promise.commitment.v1",
            "k",
            {"event_id": "x"},
            producer=FailingProducer(),
        )


@pytest.mark.django_db
def test_unavailable_kafka_broker_keeps_event_unpublished():
    from confluent_kafka import Producer

    from apps.outbox.kafka import publish_record

    outbox = _due_outbox()
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
    assert outbox.published_at is None
    assert outbox.attempts == 1
    assert outbox.next_attempt_at is not None
    assert outbox.last_error != ""
    assert len(outbox.last_error) <= 2048
    assert "password" not in outbox.last_error.lower()
    assert "supersecret" not in outbox.last_error

