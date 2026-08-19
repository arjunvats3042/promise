import json

from django.utils import timezone

from apps.outbox.models import OutboxEvent

EVENT_VERSION = 1
MAX_PAYLOAD_BYTES = 16 * 1024


def record_outbox_event(
    *,
    aggregate_type,
    aggregate_id,
    event_type,
    payload,
    occurred_at,
    event_version=EVENT_VERSION,
):
    if not isinstance(payload, dict):
        raise ValueError("Outbox payload must be a JSON object.")
    encoded = json.dumps(payload, default=str, separators=(",", ":")).encode("utf-8")
    if len(encoded) > MAX_PAYLOAD_BYTES:
        raise ValueError("Outbox payload exceeds 16 KiB.")

    return OutboxEvent.objects.create(
        aggregate_type=aggregate_type,
        aggregate_id=aggregate_id,
        event_type=event_type,
        event_version=event_version,
        payload=payload,
        occurred_at=occurred_at,
        published_at=None,
        attempts=0,
        next_attempt_at=timezone.now(),
        last_error="",
    )
