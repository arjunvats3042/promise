import logging
from collections import namedtuple
from datetime import timedelta
from datetime import timezone as dt_timezone

from django.db import transaction
from django.utils import timezone

from apps.outbox.models import OutboxEvent

logger = logging.getLogger("promise")

COMMITMENT_TOPIC = "promise.commitment.v1"
GOAL_TOPIC = "promise.goal.v1"
_TOPICS = {
    "commitment": COMMITMENT_TOPIC,
    "goal": GOAL_TOPIC,
}
BATCH_SIZE = 50
MAX_ATTEMPTS = 8
MAX_LAST_ERROR_LENGTH = 2048

_BACKOFF = (
    timedelta(seconds=5),
    timedelta(seconds=15),
    timedelta(seconds=45),
    timedelta(minutes=2),
    timedelta(minutes=5),
    timedelta(minutes=15),
    timedelta(minutes=30),
)

PublishResult = namedtuple("PublishResult", ["published", "failed"])


def backoff_for_attempt(attempts):
    if attempts < 1:
        attempts = 1
    index = min(attempts, len(_BACKOFF)) - 1
    return _BACKOFF[index]


def publish_due_outbox_events(*, publish=None):
    if publish is None:
        from apps.outbox.kafka import publish_record

        publish = publish_record

    published = 0
    failed = 0
    while True:
        batch_published, batch_failed, claimed = _publish_batch(publish)
        published += batch_published
        failed += batch_failed
        if claimed == 0:
            break
    return PublishResult(published=published, failed=failed)


def _publish_batch(publish):
    published = 0
    failed = 0
    with transaction.atomic():
        events = list(
            OutboxEvent.objects.select_for_update(skip_locked=True)
            .filter(
                published_at__isnull=True,
                next_attempt_at__isnull=False,
                next_attempt_at__lte=timezone.now(),
            )
            .order_by("next_attempt_at", "created_at")[:BATCH_SIZE]
        )
        for event in events:
            event.attempts += 1
            try:
                _produce_event(event, publish)
                event.published_at = timezone.now()
                event.last_error = ""
                published += 1
                logger.info(
                    "Outbox published event_id=%s event_type=%s aggregate_id=%s attempts=%s",
                    event.id,
                    event.event_type,
                    event.aggregate_id,
                    event.attempts,
                )
            except Exception as exc:
                event.last_error = _safe_last_error(exc)
                if event.attempts >= MAX_ATTEMPTS:
                    event.next_attempt_at = None
                    logger.error(
                        "Outbox parked event_id=%s event_type=%s aggregate_id=%s attempts=%s",
                        event.id,
                        event.event_type,
                        event.aggregate_id,
                        event.attempts,
                    )
                else:
                    event.next_attempt_at = timezone.now() + backoff_for_attempt(
                        event.attempts
                    )
                    logger.warning(
                        "Outbox publish failed event_id=%s event_type=%s aggregate_id=%s attempts=%s",
                        event.id,
                        event.event_type,
                        event.aggregate_id,
                        event.attempts,
                    )
                failed += 1
            event.save(
                update_fields=[
                    "attempts",
                    "published_at",
                    "next_attempt_at",
                    "last_error",
                    "updated_at",
                ]
            )
    return published, failed, len(events)


def _produce_event(event, publish):
    if not isinstance(event.payload, dict):
        raise ValueError("Invalid outbox payload")
    publish(
        _topic_for(event.aggregate_type),
        str(event.aggregate_id),
        _envelope(event),
    )


def _topic_for(aggregate_type):
    try:
        return _TOPICS[aggregate_type]
    except KeyError:
        raise ValueError("Unknown aggregate type")


def _envelope(event):
    return {
        "event_id": str(event.id),
        "event_type": event.event_type,
        "event_version": event.event_version,
        "occurred_at": _isoformat_z(event.occurred_at),
        "aggregate_type": event.aggregate_type,
        "aggregate_id": str(event.aggregate_id),
        "payload": event.payload,
    }


def _isoformat_z(value):
    if timezone.is_aware(value):
        value = value.astimezone(dt_timezone.utc)
    iso = value.isoformat()
    if iso.endswith("+00:00"):
        return iso[:-6] + "Z"
    return iso


def _safe_last_error(exc):
    name = type(exc).__name__
    if not name.isidentifier():
        name = "PublishError"
    return f"{name}: Kafka publish failed."[:MAX_LAST_ERROR_LENGTH]
