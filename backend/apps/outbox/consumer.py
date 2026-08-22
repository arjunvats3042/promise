"""Kafka consumer foundation for commitment and goal integration events.

At-least-once delivery. PostgreSQL `processed_events` is the idempotency
source of truth, keyed by `(consumer_group, event_id)`.

Offset policy:
- processed / duplicate: commit Kafka offset after the DB transaction
- malformed, unknown event_type, unsupported event_version: permanent skip
  (cannot succeed); log and commit offset so the partition is not blocked
- processor/DB failure: do not commit offset; seek back to the
  failed record so a later success cannot commit past it; Kafka redelivers

Crash A: before DB commit → Kafka redelivers, processor runs.
Crash B: after DB commit, before offset commit → Kafka redelivers,
          unique constraint treats it as a duplicate.
Crash C: DB + offset committed → done.

Notification consumers may later add retry limits, parking, and a DLT.
This foundation uses Kafka redelivery for transient failures only.
"""

import logging
import time

from django.db import IntegrityError, transaction
from django.utils import timezone

from apps.outbox.envelope import EnvelopeError, parse_envelope
from apps.outbox.models import ProcessedEvent
from apps.outbox.publisher import COMMITMENT_TOPIC, GOAL_TOPIC

logger = logging.getLogger("promise")

COMMITMENT_CONSUMER_GROUP = "promise-commitment-events"
GOAL_CONSUMER_GROUP = "promise-goal-events"
SUPPORTED_EVENT_VERSION = 1
SUPPORTED_EVENT_TYPES = (
    "commitment.created",
    "commitment.updated",
    "commitment.completed",
    "commitment.snoozed",
    "commitment.unsnoozed",
    "commitment.waiting",
    "commitment.cancelled",
    "goal.created",
    "goal.updated",
    "goal.paused",
    "goal.resumed",
    "goal.completed",
    "goal.cancelled",
    "goal.checkin.created",
    "goal.checkin.updated",
    "goal.participant.invited",
    "goal.participant.joined",
    "goal.participant.declined",
    "goal.participant.left",
    "goal.participant.removed",
    "goal.chat.message_created",
)


def process_commitment_event(envelope):
    logger.info(
        "Consumed commitment event_id=%s event_type=%s aggregate_id=%s",
        envelope["event_id"],
        envelope["event_type"],
        envelope["aggregate_id"],
    )


def process_goal_event(envelope):
    logger.info(
        "Consumed goal event_id=%s event_type=%s aggregate_id=%s",
        envelope["event_id"],
        envelope["event_type"],
        envelope["aggregate_id"],
    )
    from apps.notifications.services import handle_goal_event

    handle_goal_event(envelope)


def process_and_record(*, consumer_group, envelope, processor):
    with transaction.atomic():
        try:
            with transaction.atomic():
                ProcessedEvent.objects.create(
                    consumer_group=consumer_group,
                    event_id=envelope["event_id"],
                    processed_at=timezone.now(),
                )
        except IntegrityError:
            return "duplicate"
        processor(envelope)
    return "processed"


def handle_record(*, consumer_group, raw_value, processor, commit_offset):
    try:
        envelope = parse_envelope(raw_value)
    except EnvelopeError:
        logger.warning("Skipped malformed commitment event")
        commit_offset()
        return "skipped"

    if envelope["event_type"] not in SUPPORTED_EVENT_TYPES:
        logger.info(
            "Skipped unknown event_type event_id=%s event_type=%s aggregate_id=%s",
            envelope["event_id"],
            envelope["event_type"],
            envelope["aggregate_id"],
        )
        commit_offset()
        return "skipped"

    if envelope["event_version"] != SUPPORTED_EVENT_VERSION:
        logger.info(
            "Skipped unsupported event_version event_id=%s event_type=%s aggregate_id=%s",
            envelope["event_id"],
            envelope["event_type"],
            envelope["aggregate_id"],
        )
        commit_offset()
        return "skipped"

    try:
        outcome = process_and_record(
            consumer_group=consumer_group,
            envelope=envelope,
            processor=processor,
        )
    except Exception:
        logger.warning(
            "Commitment event processing failed event_id=%s event_type=%s aggregate_id=%s",
            envelope["event_id"],
            envelope["event_type"],
            envelope["aggregate_id"],
        )
        return "retry"

    commit_offset()
    return outcome


def run_commitment_consumer(*, max_messages=None, timeout_seconds=None, consumer=None):
    return run_consumer(
        topic=COMMITMENT_TOPIC,
        group_id=COMMITMENT_CONSUMER_GROUP,
        processor=process_commitment_event,
        max_messages=max_messages,
        timeout_seconds=timeout_seconds,
        consumer=consumer,
    )


def run_goal_consumer(*, max_messages=None, timeout_seconds=None, consumer=None):
    return run_consumer(
        topic=GOAL_TOPIC,
        group_id=GOAL_CONSUMER_GROUP,
        processor=process_goal_event,
        max_messages=max_messages,
        timeout_seconds=timeout_seconds,
        consumer=consumer,
    )


def run_consumer(
    *,
    topic,
    group_id,
    processor,
    max_messages=None,
    timeout_seconds=None,
    consumer=None,
):
    from confluent_kafka import TopicPartition

    from apps.outbox.kafka import get_kafka_consumer

    import signal

    owned = consumer is None
    if owned:
        consumer = get_kafka_consumer(group_id)

    counts = {
        "processed": 0,
        "duplicates": 0,
        "skipped": 0,
        "retried": 0,
    }
    handled = 0
    started = time.monotonic()
    consumer.subscribe([topic])

    stop_requested = False

    def sig_handler(signum, frame):
        nonlocal stop_requested
        logger.info("Kafka consumer %s received signal %s, shutting down...", group_id, signum)
        stop_requested = True

    try:
        signal.signal(signal.SIGINT, sig_handler)
        signal.signal(signal.SIGTERM, sig_handler)
    except (ValueError, AttributeError):
        pass  # if not in main thread

    try:
        while not stop_requested:
            if max_messages is not None and handled >= max_messages:
                break
            if (
                timeout_seconds is not None
                and time.monotonic() - started >= timeout_seconds
            ):
                break
            message = consumer.poll(1.0)
            if message is None:
                continue
            if message.error():
                logger.warning("Kafka consume error")
                continue
            handled += 1

            def commit_offset(msg=message):
                consumer.commit(message=msg, asynchronous=False)

            outcome = handle_record(
                consumer_group=group_id,
                raw_value=message.value(),
                processor=processor,
                commit_offset=commit_offset,
            )
            if outcome == "processed":
                counts["processed"] += 1
            elif outcome == "duplicate":
                counts["duplicates"] += 1
            elif outcome == "skipped":
                counts["skipped"] += 1
            elif outcome == "retry":
                counts["retried"] += 1
                consumer.seek(
                    TopicPartition(
                        message.topic(),
                        message.partition(),
                        message.offset(),
                    )
                )
    except KeyboardInterrupt:
        logger.info("Kafka consumer shutting down")
    finally:
        consumer.close()
    return counts
