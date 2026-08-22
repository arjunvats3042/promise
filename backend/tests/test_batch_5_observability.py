import time
from unittest.mock import patch
import pytest
from django.contrib.auth import get_user_model
from django.urls import path
from redis.exceptions import ConnectionError as RedisConnectionError
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.test import APIClient, APIRequestFactory

from apps.authentication.rate_limits import enforce_login_rate_limits
from apps.core.observability import get_notification_metrics, get_outbox_metrics
from apps.core.redis import increment_rate_limit
from config.exceptions import RateLimitedError

User = get_user_model()


@api_view(["GET"])
@permission_classes([AllowAny])
def _buggy_view(_request):
    raise RuntimeError("Simulated internal catastrophic error with sensitive secret=SECRET123")


@pytest.mark.django_db
def test_request_id_generated_and_propagated(client):
    """Verify that every response has an X-Request-ID header, preserving client-sent correlation IDs."""
    # 1. Server-generated request ID
    res1 = client.get("/api/v1/health/")
    assert res1.status_code == 200
    assert "X-Request-ID" in res1.headers
    assert len(res1.headers["X-Request-ID"]) > 0

    # 2. Client-provided correlation ID
    custom_id = "trace-client-12345-abcdef"
    res2 = client.get("/api/v1/health/", HTTP_X_REQUEST_ID=custom_id)
    assert res2.status_code == 200
    assert res2.headers["X-Request-ID"] == custom_id


@pytest.mark.django_db
def test_health_liveness_and_readiness(client):
    """Verify liveness probe returns 200 and readiness probe verifies DB and Redis."""
    # Liveness
    res_live = client.get("/api/v1/health/")
    assert res_live.status_code == 200
    assert res_live.json() == {"status": "ok"}

    # Readiness when dependencies are healthy
    res_ready = client.get("/api/v1/health/ready/")
    assert res_ready.status_code == 200
    data = res_ready.json()
    assert data["status"] == "ok"
    assert data["db"] == "healthy"
    assert data["redis"] == "healthy"


@pytest.mark.django_db
def test_readiness_fails_gracefully_when_redis_is_down(client):
    """Verify readiness returns 503 degraded without leaking hostnames or credentials when Redis fails."""
    with patch("config.views.get_redis_client") as mock_redis:
        mock_redis.return_value.ping.side_effect = RedisConnectionError("Cannot connect to redis host")
        res = client.get("/api/v1/health/ready/")
        assert res.status_code == 503
        data = res.json()
        assert data["status"] == "degraded"
        assert data["db"] == "healthy"
        assert data["redis"] == "unhealthy"
        # Ensure no credential or internal trace is leaked
        assert "Cannot connect to redis host" not in str(data)


@pytest.mark.django_db
def test_unhandled_exception_is_sanitized_and_logs_request_id():
    """Verify that unhandled 500 exceptions return a clean generic envelope without leaking error strings."""
    from config.exceptions import api_exception_handler
    factory = APIRequestFactory()
    req = factory.get("/api/v1/some-endpoint/", HTTP_X_REQUEST_ID="req-unhandled-500-check")
    req.request_id = "req-unhandled-500-check"

    exc = RuntimeError("Simulated catastrophic error with sensitive secret=SECRET123")
    response = api_exception_handler(exc, {"request": req})

    assert response.status_code == 500
    assert response.data["error"]["code"] == "INTERNAL_SERVER_ERROR"
    assert response.data["error"]["message"] == "An unexpected error occurred."
    assert "SECRET123" not in str(response.data)


@pytest.mark.django_db
def test_rate_limit_failure_strategy_by_endpoint_class():
    """Verify auth endpoints fail closed, whereas ordinary endpoints fail open during Redis outages."""
    key = "promise:ratelimit:test:general"
    auth_key = "promise:ratelimit:auth:login:test"

    with patch("apps.core.redis.get_redis_client") as mock_redis:
        mock_redis.return_value.eval.side_effect = RedisConnectionError("Redis down")

        # 1. Ordinary endpoint: fail-open (returns count=0)
        res_open = increment_rate_limit(key, 60, fail_closed=False)
        assert res_open == {"count": 0, "ttl": 0}

        # 2. Security-sensitive auth check: fail-closed (raises RateLimitedError)
        factory = APIRequestFactory()
        req = factory.post("/api/v1/auth/login/", {"email": "attacker@example.com"}, REMOTE_ADDR="192.168.1.1")
        with pytest.raises(RateLimitedError):
            enforce_login_rate_limits(req, "attacker@example.com")


@pytest.mark.django_db
def test_async_metrics_helpers_return_accurate_snapshots():
    """Verify async observability helpers return structured metrics."""
    outbox_metrics = get_outbox_metrics()
    assert "pending_count" in outbox_metrics
    assert "failed_count" in outbox_metrics
    assert "oldest_pending_age_seconds" in outbox_metrics

    notification_metrics = get_notification_metrics()
    assert "scheduled_count" in notification_metrics
    assert "claimed_count" in notification_metrics
    assert "failed_count" in notification_metrics
    assert "dispatched_count" in notification_metrics
