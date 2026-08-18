import uuid

from django.db import models


class BaseModel(models.Model):
    """Shared abstract base for domain models.

    New domain models should inherit from this class. Auth User is an
    exception: it already defines UUID id, created_at, and updated_at.

    Primary keys are UUID v4 so public API identifiers are stable across
    services and are not trivially enumerable. UUIDs are not a security
    control by themselves.

    DateTime fields are timezone-aware. Django stores and compares them in
    UTC (TIME_ZONE = UTC, USE_TZ = True). A user's timezone preference is
    separate and does not change stored values. APIs should serialize
    timezone-aware timestamps.
    """

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True
