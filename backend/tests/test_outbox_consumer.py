import json
import logging
import threading
from io import StringIO
from uuid import UUID, uuid4

import pytest
from django.core.management import call_command
from django.db import IntegrityError, connection
from django.utils import timezone

from apps.outbox.consumer import (
    COMMITMENT_CONSUMER_GROUP,
    handle_record,
    process_and_record,
    process_commitment_event,
)
from apps.outbox.envelope import EnvelopeError, parse_envelope
from apps.outbox.models import ProcessedEvent
from apps.outbox.publisher import COMMITMENT_TOPIC, GOAL_TOPIC


def _valid_envelope(**overrides):
    data = {
        "event_id": str(uuid4()),
        "event_type": "commitment.created",
        "event_version": 1,
        "occurred_at": "2026-08-20T18:00:00Z",
        "aggregate_type": "commitment",
        "aggregate_id": str(uuid4()),
        "payload": {"status": "PENDING"},
    }
    data.update(overrides)
    return data


def _goal_envelope(**overrides):
    data = _valid_envelope(
        event_type="goal.created",
        aggregate_type="goal",
        payload={"status": "ACTIVE"},
    )
    data.update(overrides)
    return data


def test_parse_envelope_accepts_valid_event():
    raw = _valid_envelope()
    envelope = parse_envelope(json.dumps(raw).encode("utf-8"))

    assert envelope["event_id"] == UUID(raw["event_id"])
    assert envelope["event_type"] == "commitment.created"
    assert envelope["event_version"] == 1
    assert envelope["aggregate_type"] == "commitment"
    assert envelope["aggregate_id"] == UUID(raw["aggregate_id"])
    assert envelope["payload"] == {"status": "PENDING"}
    assert envelope["occurred_at"] is not None


def test_parse_envelope_rejects_missing_event_id():
    raw = _valid_envelope()
    del raw["event_id"]
    with pytest.raises(EnvelopeError):
        parse_envelope(raw)


def test_parse_envelope_rejects_missing_event_type():
    raw = _valid_envelope()
    del raw["event_type"]
    with pytest.raises(EnvelopeError):
        parse_envelope(raw)


def test_parse_envelope_rejects_invalid_event_version():
    with pytest.raises(EnvelopeError):
        parse_envelope(_valid_envelope(event_version="1"))
    with pytest.raises(EnvelopeError):
        parse_envelope(_valid_envelope(event_version=True))
    with pytest.raises(EnvelopeError):
        parse_envelope(_valid_envelope(event_version=0))


def test_parse_envelope_rejects_invalid_aggregate_id():
    with pytest.raises(EnvelopeError):
        parse_envelope(_valid_envelope(aggregate_id="not-a-uuid"))


def test_parse_envelope_rejects_invalid_payload():
    with pytest.raises(EnvelopeError):
        parse_envelope(_valid_envelope(payload=["not", "an", "object"]))
    with pytest.raises(EnvelopeError):
        parse_envelope(_valid_envelope(payload="secret"))
    with pytest.raises(EnvelopeError):
        parse_envelope(_valid_envelope(payload=None))


@pytest.mark.django_db
def test_first_delivery_processes_and_records_event():
    envelope = parse_envelope(_valid_envelope())
    calls = []

    outcome = process_and_record(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        envelope=envelope,
        processor=lambda event: calls.append(event["event_id"]),
    )

    assert outcome == "processed"
    assert calls == [envelope["event_id"]]
    row = ProcessedEvent.objects.get()
    assert row.consumer_group == COMMITMENT_CONSUMER_GROUP
    assert row.event_id == envelope["event_id"]
    assert row.processed_at is not None


@pytest.mark.django_db
def test_second_delivery_does_not_process_again():
    envelope = parse_envelope(_valid_envelope())
    calls = []

    def processor(event):
        calls.append(event["event_id"])

    first = process_and_record(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        envelope=envelope,
        processor=processor,
    )
    second = process_and_record(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        envelope=envelope,
        processor=processor,
    )

    assert first == "processed"
    assert second == "duplicate"
    assert calls == [envelope["event_id"]]
    assert ProcessedEvent.objects.count() == 1


@pytest.mark.django_db
def test_same_event_id_in_different_groups_processes_independently():
    envelope = parse_envelope(_valid_envelope())
    calls = []

    def processor(event):
        calls.append(event["event_id"])

    first = process_and_record(
        consumer_group="promise-commitment-events",
        envelope=envelope,
        processor=processor,
    )
    second = process_and_record(
        consumer_group="promise-notifications",
        envelope=envelope,
        processor=processor,
    )

    assert first == "processed"
    assert second == "processed"
    assert calls == [envelope["event_id"], envelope["event_id"]]
    assert ProcessedEvent.objects.count() == 2
    groups = set(ProcessedEvent.objects.values_list("consumer_group", flat=True))
    assert groups == {"promise-commitment-events", "promise-notifications"}


@pytest.mark.django_db
def test_processed_events_unique_constraint():
    event_id = uuid4()
    ProcessedEvent.objects.create(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        event_id=event_id,
        processed_at=timezone.now(),
    )
    with pytest.raises(IntegrityError):
        ProcessedEvent.objects.create(
            consumer_group=COMMITMENT_CONSUMER_GROUP,
            event_id=event_id,
            processed_at=timezone.now(),
        )


@pytest.mark.django_db
def test_processor_failure_rolls_back_processed_events():
    envelope = parse_envelope(_valid_envelope())

    def processor(event):
        raise RuntimeError("processor failed")

    with pytest.raises(RuntimeError):
        process_and_record(
            consumer_group=COMMITMENT_CONSUMER_GROUP,
            envelope=envelope,
            processor=processor,
        )

    assert ProcessedEvent.objects.count() == 0


@pytest.mark.django_db
def test_handle_record_commits_offset_after_successful_processing():
    envelope = _valid_envelope()
    commits = []
    calls = []

    outcome = handle_record(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        raw_value=json.dumps(envelope).encode("utf-8"),
        processor=lambda event: calls.append(event["event_id"]),
        commit_offset=lambda: commits.append("commit"),
    )

    assert outcome == "processed"
    assert commits == ["commit"]
    assert len(calls) == 1
    assert ProcessedEvent.objects.filter(event_id=envelope["event_id"]).count() == 1


@pytest.mark.django_db
def test_handle_record_does_not_commit_offset_on_processor_failure():
    envelope = _valid_envelope()
    commits = []

    def processor(event):
        raise RuntimeError("processor failed")

    outcome = handle_record(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        raw_value=json.dumps(envelope).encode("utf-8"),
        processor=processor,
        commit_offset=lambda: commits.append("commit"),
    )

    assert outcome == "retry"
    assert commits == []
    assert ProcessedEvent.objects.count() == 0


@pytest.mark.django_db
def test_unknown_event_type_is_skipped_without_processing():
    envelope = _valid_envelope(event_type="commitment.overdue")
    commits = []
    calls = []

    outcome = handle_record(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        raw_value=json.dumps(envelope).encode("utf-8"),
        processor=lambda event: calls.append(event["event_id"]),
        commit_offset=lambda: commits.append("commit"),
    )

    assert outcome == "skipped"
    assert commits == ["commit"]
    assert calls == []
    assert ProcessedEvent.objects.count() == 0


@pytest.mark.django_db
def test_goal_event_first_delivery_processes_and_records():
    envelope = _goal_envelope()
    commits = []
    calls = []

    outcome = handle_record(
        consumer_group="promise-goal-events-verify",
        raw_value=json.dumps(envelope).encode("utf-8"),
        processor=lambda event: calls.append(event["event_id"]),
        commit_offset=lambda: commits.append("commit"),
    )

    assert outcome == "processed"
    assert commits == ["commit"]
    assert len(calls) == 1
    row = ProcessedEvent.objects.get()
    assert row.consumer_group == "promise-goal-events-verify"
    assert str(row.event_id) == envelope["event_id"]


@pytest.mark.django_db
def test_goal_event_replay_is_duplicate_without_second_row():
    envelope = _goal_envelope()
    commits = []
    calls = []

    first = handle_record(
        consumer_group="promise-goal-events-verify",
        raw_value=json.dumps(envelope).encode("utf-8"),
        processor=lambda event: calls.append(event["event_id"]),
        commit_offset=lambda: commits.append("commit"),
    )
    second = handle_record(
        consumer_group="promise-goal-events-verify",
        raw_value=json.dumps(envelope).encode("utf-8"),
        processor=lambda event: calls.append(event["event_id"]),
        commit_offset=lambda: commits.append("commit"),
    )

    assert first == "processed"
    assert second == "duplicate"
    assert len(calls) == 1
    assert commits == ["commit", "commit"]
    assert ProcessedEvent.objects.filter(event_id=envelope["event_id"]).count() == 1


@pytest.mark.django_db
def test_run_consumer_goal_topic_counts_processed_then_duplicate():
    from apps.outbox.consumer import run_consumer

    raw = json.dumps(_goal_envelope()).encode("utf-8")

    class FakeMessage:
        def error(self):
            return None

        def value(self):
            return raw

    class FakeConsumer:
        def __init__(self):
            self.remaining = [FakeMessage(), FakeMessage()]
            self.committed = 0
            self.closed = False

        def subscribe(self, topics):
            self.topics = topics

        def poll(self, timeout):
            if self.remaining:
                return self.remaining.pop(0)
            return None

        def commit(self, message=None, asynchronous=True):
            self.committed += 1

        def close(self):
            self.closed = True

    fake = FakeConsumer()
    calls = []
    result = run_consumer(
        topic=GOAL_TOPIC,
        group_id="promise-goal-events-verify",
        processor=lambda event: calls.append(event["event_id"]),
        max_messages=2,
        consumer=fake,
    )

    assert fake.topics == [GOAL_TOPIC]
    assert result["processed"] == 1
    assert result["duplicates"] == 1
    assert result["skipped"] == 0
    assert result["retried"] == 0
    assert len(calls) == 1
    assert fake.committed == 2
    assert fake.closed is True
    assert ProcessedEvent.objects.count() == 1


@pytest.mark.django_db
def test_unsupported_event_version_is_skipped_without_processing():
    envelope = _valid_envelope(event_version=2)
    commits = []
    calls = []

    outcome = handle_record(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        raw_value=json.dumps(envelope).encode("utf-8"),
        processor=lambda event: calls.append(event["event_id"]),
        commit_offset=lambda: commits.append("commit"),
    )

    assert outcome == "skipped"
    assert commits == ["commit"]
    assert calls == []
    assert ProcessedEvent.objects.count() == 0


@pytest.mark.django_db
def test_malformed_event_is_skipped_and_offset_committed():
    commits = []
    calls = []
    raw = _valid_envelope()
    del raw["event_id"]

    outcome = handle_record(
        consumer_group=COMMITMENT_CONSUMER_GROUP,
        raw_value=json.dumps(raw).encode("utf-8"),
        processor=lambda event: calls.append("processed"),
        commit_offset=lambda: commits.append("commit"),
    )

    assert outcome == "skipped"
    assert commits == ["commit"]
    assert calls == []
    assert ProcessedEvent.objects.count() == 0


def test_process_commitment_event_logs_identifiers_not_payload(caplog):
    envelope = parse_envelope(
        _valid_envelope(
            payload={
                "status": "PENDING",
                "password": "supersecret",
                "title": "should-not-be-logged",
            }
        )
    )

    with caplog.at_level(logging.INFO, logger="promise"):
        process_commitment_event(envelope)

    text = caplog.text
    assert str(envelope["event_id"]) in text
    assert "commitment.created" in text
    assert str(envelope["aggregate_id"]) in text
    assert "supersecret" not in text
    assert "should-not-be-logged" not in text
    assert "payload" not in text.lower()


@pytest.mark.django_db
def test_handle_record_does_not_log_payload_or_secrets(caplog):
    raw = _valid_envelope(
        payload={"password": "supersecret", "refresh_token": "tok_live"}
    )

    with caplog.at_level(logging.INFO, logger="promise"):
        handle_record(
            consumer_group=COMMITMENT_CONSUMER_GROUP,
            raw_value=json.dumps(raw).encode("utf-8"),
            processor=process_commitment_event,
            commit_offset=lambda: None,
        )

    text = caplog.text
    assert raw["event_id"] in text
    assert "commitment.created" in text
    assert raw["aggregate_id"] in text
    assert "supersecret" not in text
    assert "tok_live" not in text
    assert "password" not in text.lower()


@pytest.mark.django_db(transaction=True)
def test_concurrent_duplicate_delivery_inserts_one_processed_row():
    envelope = parse_envelope(_valid_envelope())
    calls = []
    errors = []
    lock = threading.Lock()

    def processor(event):
        with lock:
            calls.append(event["event_id"])

    def worker():
        try:
            process_and_record(
                consumer_group=COMMITMENT_CONSUMER_GROUP,
                envelope=envelope,
                processor=processor,
            )
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
    assert calls == [envelope["event_id"]]
    assert ProcessedEvent.objects.filter(event_id=envelope["event_id"]).count() == 1


@pytest.mark.django_db
def test_consume_commitment_events_command_invokes_consumer(monkeypatch):
    calls = []

    def fake_run(**kwargs):
        calls.append(kwargs)
        return {"processed": 1, "duplicates": 0, "skipped": 0, "retried": 0}

    monkeypatch.setattr(
        "apps.outbox.management.commands.consume_commitment_events.run_commitment_consumer",
        fake_run,
    )
    stdout = StringIO()
    call_command(
        "consume_commitment_events",
        "--max-messages",
        "1",
        "--timeout",
        "5",
        stdout=stdout,
    )

    assert calls[0]["max_messages"] == 1
    assert calls[0]["timeout_seconds"] == 5.0
    assert COMMITMENT_CONSUMER_GROUP == "promise-commitment-events"
    assert COMMITMENT_TOPIC == "promise.commitment.v1"
    assert "processed=1" in stdout.getvalue()


@pytest.mark.django_db
def test_run_consumer_counts_duplicates_and_does_not_autocommit_on_close():
    from apps.outbox.consumer import run_consumer

    raw = json.dumps(_valid_envelope()).encode("utf-8")

    class FakeMessage:
        def error(self):
            return None

        def value(self):
            return raw

    class FakeConsumer:
        def __init__(self):
            self.remaining = [FakeMessage(), FakeMessage()]
            self.committed = 0
            self.closed = False

        def subscribe(self, topics):
            self.topics = topics

        def poll(self, timeout):
            if self.remaining:
                return self.remaining.pop(0)
            return None

        def commit(self, message=None, asynchronous=True):
            self.committed += 1

        def close(self):
            self.closed = True

    fake = FakeConsumer()
    calls = []
    result = run_consumer(
        topic=COMMITMENT_TOPIC,
        group_id=COMMITMENT_CONSUMER_GROUP,
        processor=lambda event: calls.append(event["event_id"]),
        max_messages=2,
        consumer=fake,
    )

    assert result["processed"] == 1
    assert result["duplicates"] == 1
    assert result["skipped"] == 0
    assert result["retried"] == 0
    assert len(calls) == 1
    assert fake.committed == 2
    assert fake.closed is True


@pytest.mark.django_db
def test_run_consumer_retries_failed_record_before_later_success():
    from apps.outbox.consumer import run_consumer

    first = _valid_envelope()
    second = _valid_envelope()
    messages = [
        _FakeOffsetMessage(json.dumps(first).encode("utf-8"), 0),
        _FakeOffsetMessage(json.dumps(second).encode("utf-8"), 1),
    ]
    fake = _SeekableFakeConsumer(messages)
    calls = []
    attempts = {"first_failures": 0}

    def processor(event):
        if str(event["event_id"]) == first["event_id"] and attempts["first_failures"] == 0:
            attempts["first_failures"] += 1
            raise RuntimeError("transient")
        calls.append(str(event["event_id"]))

    result = run_consumer(
        topic=COMMITMENT_TOPIC,
        group_id=COMMITMENT_CONSUMER_GROUP,
        processor=processor,
        max_messages=3,
        consumer=fake,
    )

    assert attempts["first_failures"] == 1
    assert result["retried"] == 1
    assert result["processed"] == 2
    assert calls == [first["event_id"], second["event_id"]]
    assert set(
        ProcessedEvent.objects.values_list("event_id", flat=True)
    ) == {UUID(first["event_id"]), UUID(second["event_id"])}
    assert fake.committed_offsets == [0, 1]


class _FakeOffsetMessage:
    def __init__(self, value, offset):
        self._value = value
        self._offset = offset

    def error(self):
        return None

    def value(self):
        return self._value

    def topic(self):
        return COMMITMENT_TOPIC

    def partition(self):
        return 0

    def offset(self):
        return self._offset


class _SeekableFakeConsumer:
    def __init__(self, messages):
        self.messages = messages
        self.position = 0
        self.committed_offsets = []
        self.closed = False

    def subscribe(self, topics):
        self.topics = topics

    def poll(self, timeout):
        if 0 <= self.position < len(self.messages):
            message = self.messages[self.position]
            self.position += 1
            return message
        return None

    def seek(self, tp):
        self.position = tp.offset

    def commit(self, message=None, asynchronous=True):
        self.committed_offsets.append(message.offset())

    def close(self):
        self.closed = True


