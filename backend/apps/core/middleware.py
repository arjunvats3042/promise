import logging
import re
import time
import uuid

logger = logging.getLogger("promise")
_REQUEST_ID_REGEX = re.compile(r"^[a-zA-Z0-9_-]{1,128}$")


class RequestObservabilityMiddleware:
    """Attaches correlation request IDs, measures latency, and produces structured logs.

    Strict privacy: Never logs authorization tokens, credentials, or private message bodies.
    """

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        incoming_id = request.META.get("HTTP_X_REQUEST_ID", "").strip()
        if incoming_id and _REQUEST_ID_REGEX.match(incoming_id):
            request_id = incoming_id
        else:
            request_id = uuid.uuid4().hex

        request.request_id = request_id
        t0 = time.monotonic()

        response = self.get_response(request)

        duration_ms = round((time.monotonic() - t0) * 1000, 2)
        response["X-Request-ID"] = request_id

        user_id = "-"
        if hasattr(request, "user") and getattr(request.user, "is_authenticated", False):
            user_id = str(getattr(request.user, "id", "-"))

        logger.info(
            "HTTP request_id=%s method=%s path=%s status=%s duration_ms=%.2f user_id=%s",
            request_id,
            request.method,
            request.path,
            response.status_code,
            duration_ms,
            user_id,
        )

        return response
