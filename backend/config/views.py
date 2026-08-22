import logging

from django.db import connection
from rest_framework import status
from rest_framework.decorators import api_view, authentication_classes, permission_classes
from rest_framework.exceptions import NotFound
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from apps.core.redis import get_redis_client

logger = logging.getLogger("promise")


@api_view(["GET"])
@authentication_classes([])
@permission_classes([AllowAny])
def health(_request):
    """Liveness probe: verifies process is alive and accepting HTTP traffic."""
    return Response({"status": "ok"})


@api_view(["GET"])
@authentication_classes([])
@permission_classes([AllowAny])
def ready(_request):
    """Readiness probe: verifies core downstream dependencies (DB and Redis)."""
    db_status = "healthy"
    redis_status = "healthy"
    is_ready = True

    try:
        connection.ensure_connection()
    except Exception as exc:
        logger.error("Readiness check DB failure: %s", type(exc).__name__)
        db_status = "unhealthy"
        is_ready = False

    try:
        get_redis_client().ping()
    except Exception as exc:
        logger.error("Readiness check Redis failure: %s", type(exc).__name__)
        redis_status = "unhealthy"
        is_ready = False

    payload = {
        "status": "ok" if is_ready else "degraded",
        "db": db_status,
        "redis": redis_status,
    }
    http_code = status.HTTP_200_OK if is_ready else status.HTTP_503_SERVICE_UNAVAILABLE
    return Response(payload, status=http_code)


@api_view(["GET", "POST", "PUT", "PATCH", "DELETE"])
@authentication_classes([])
@permission_classes([AllowAny])
def api_not_found(_request, resource=None):
    raise NotFound()
