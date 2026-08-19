import json
from datetime import datetime
from uuid import UUID

from django.utils import timezone


class EnvelopeError(ValueError):
    pass


_REQUIRED_FIELDS = (
    "event_id",
    "event_type",
    "event_version",
    "occurred_at",
    "aggregate_type",
    "aggregate_id",
    "payload",
)


def parse_envelope(value):
    data = _as_object(value)
    missing = [field for field in _REQUIRED_FIELDS if field not in data]
    if missing:
        raise EnvelopeError("Event envelope is missing required fields")

    event_id = _as_uuid(data["event_id"], "event_id")
    aggregate_id = _as_uuid(data["aggregate_id"], "aggregate_id")
    event_type = _as_nonempty_string(data["event_type"], "event_type")
    aggregate_type = _as_nonempty_string(data["aggregate_type"], "aggregate_type")
    event_version = _as_version(data["event_version"])
    occurred_at = _as_occurred_at(data["occurred_at"])
    payload = data["payload"]
    if not isinstance(payload, dict):
        raise EnvelopeError("Event payload must be a JSON object")

    return {
        "event_id": event_id,
        "event_type": event_type,
        "event_version": event_version,
        "occurred_at": occurred_at,
        "aggregate_type": aggregate_type,
        "aggregate_id": aggregate_id,
        "payload": payload,
    }


def _as_object(value):
    if isinstance(value, (bytes, bytearray)):
        try:
            value = json.loads(value.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise EnvelopeError("Event JSON is invalid") from None
    if not isinstance(value, dict):
        raise EnvelopeError("Event must be a JSON object")
    return value


def _as_uuid(value, field):
    try:
        return UUID(str(value))
    except (TypeError, ValueError, AttributeError):
        raise EnvelopeError(f"Event {field} must be a UUID") from None


def _as_nonempty_string(value, field):
    if not isinstance(value, str) or not value.strip():
        raise EnvelopeError(f"Event {field} must be a non-empty string")
    return value


def _as_version(value):
    if type(value) is not int or value < 1:
        raise EnvelopeError("Event event_version must be a positive integer")
    return value


def _as_occurred_at(value):
    if not isinstance(value, str) or not value:
        raise EnvelopeError("Event occurred_at must be an ISO-8601 timestamp")
    normalized = value.replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        raise EnvelopeError("Event occurred_at must be an ISO-8601 timestamp") from None
    if timezone.is_naive(parsed):
        parsed = timezone.make_aware(parsed)
    return parsed
