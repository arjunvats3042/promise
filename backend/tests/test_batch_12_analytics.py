"""Batch 12 — Analytics & Product Insights Test Suite.

Comprehensive test suite verifying:
- Central Event Taxonomy whitelist enforcement
- Property sanitization (stripping title, text, notes, prompt, query, password, PII)
- Pseudonymous user identity calculation
- Idempotent deduplication by event_id
- Server-side authoritative event emission
- Non-blocking error isolation
- Funnel and metric aggregate calculations
- Data retention policy enforcement (90-day purging of raw events)
- Security & authorization boundaries (staff-only report views)
"""

from datetime import timedelta
import uuid

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient

from apps.analytics.events import (
    APPROVED_TAXONOMY_EVENTS,
    EVENT_COMMITMENT_COMPLETED,
    EVENT_COMMITMENT_CREATED,
    EVENT_GOAL_CHECKED_IN,
    EVENT_GOAL_CREATED,
    EVENT_ONBOARDING_STARTED,
    EVENT_REGISTER_SUCCESS,
    EVENT_SEARCH_STARTED,
    is_event_whitelisted,
    sanitize_analytics_properties,
)
from apps.analytics.models import (
    AnalyticsDailyMetric,
    AnalyticsEvent,
    compute_pseudonymous_analytics_id,
)
from apps.analytics.serializers import AnalyticsEventSerializer
from apps.analytics.services import (
    calculate_ai_funnel,
    calculate_notification_funnel,
    calculate_onboarding_funnel,
    calculate_product_metrics,
    calculate_search_funnel,
    calculate_shared_goal_funnel,
    ingest_event_batch,
    purge_expired_raw_events,
    record_analytics_event,
)
from apps.commitments.models import Commitment
from apps.commitments.services import complete_commitment, create_commitment
from apps.goals.models import Goal, GoalCheckIn
from apps.goals.services import (
    accept_invitation,
    create_goal,
    invite_participant,
    record_check_in,
)

User = get_user_model()


@pytest.fixture
def user_a(db):
    return User.objects.create_user(
        email="analytics_user_a@test.com",
        name="User A",
        password="s3cur3-pass!",
    )


@pytest.fixture
def staff_user(db):
    return User.objects.create_user(
        email="analytics_staff@test.com",
        name="Staff User",
        password="s3cur3-pass!",
        is_staff=True,
    )


# ---------------------------------------------------------------------------
# 1. Event Taxonomy & Whitelist Tests
# ---------------------------------------------------------------------------


def test_approved_taxonomy_events_whitelisted():
    assert is_event_whitelisted(EVENT_GOAL_CREATED)
    assert is_event_whitelisted(EVENT_COMMITMENT_COMPLETED)
    assert is_event_whitelisted("ai_goal_builder_used")
    assert not is_event_whitelisted("unapproved_micro_event_xyz")


def test_unwhitelisted_event_rejected_by_serializer():
    serializer = AnalyticsEventSerializer(
        data={
            "event_name": "arbitrary_custom_injection_event",
            "properties": {},
        }
    )
    assert not serializer.is_valid()
    assert "event_name" in serializer.errors


# ---------------------------------------------------------------------------
# 2. Privacy & Data Minimization Tests
# ---------------------------------------------------------------------------


def test_forbidden_keywords_stripped_from_properties():
    raw_props = {
        "title": "Private goal title",
        "description": "Private description text",
        "note": "Private check-in note",
        "body": "Chat message body text",
        "prompt": "Raw AI prompt with sensitive data",
        "response": "Raw AI response text",
        "query": "Sensitive search query string",
        "q": "search term",
        "password": "secret-pass",
        "token": "bearer-token-value",
        "is_shared": True,
        "recurrence_kind": "DAILY",
        "latency_ms": 120,
    }
    sanitized = sanitize_analytics_properties(raw_props)

    # Privacy invariants: No PII or text contents allowed
    assert "title" not in sanitized
    assert "description" not in sanitized
    assert "note" not in sanitized
    assert "body" not in sanitized
    assert "prompt" not in sanitized
    assert "response" not in sanitized
    assert "query" not in sanitized
    assert "q" not in sanitized
    assert "password" not in sanitized
    assert "token" not in sanitized

    # Non-sensitive coarse properties preserved
    assert sanitized["is_shared"] is True
    assert sanitized["recurrence_kind"] == "DAILY"
    assert sanitized["latency_ms"] == 120


def test_analytics_user_id_is_pseudonymous_and_no_pii_exposed(user_a):
    anon_id = compute_pseudonymous_analytics_id(user_a)
    assert user_a.email not in anon_id
    assert user_a.name not in anon_id
    assert len(anon_id) == 32  # SHA-256 slice
    assert anon_id != str(user_a.id)


# ---------------------------------------------------------------------------
# 3. Idempotency & Duplicate Safety Tests
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_duplicate_event_id_ingested_once(user_a):
    event_id = uuid.uuid4()
    events_data = [
        {
            "event_id": str(event_id),
            "event_name": EVENT_GOAL_CREATED,
            "event_version": 1,
            "occurred_at": timezone.now().isoformat(),
            "platform": "android",
            "properties": {"is_shared": False},
        }
    ]

    res1 = ingest_event_batch(user=user_a, events_data=events_data)
    res2 = ingest_event_batch(user=user_a, events_data=events_data)

    assert res1["ingested"] == 1
    assert res1["duplicates"] == 0

    assert res2["ingested"] == 0
    assert res2["duplicates"] == 1

    assert AnalyticsEvent.objects.filter(event_id=event_id).count() == 1


# ---------------------------------------------------------------------------
# 4. Server-Side Event Generation & Non-Blocking Isolation Tests
# ---------------------------------------------------------------------------


@pytest.mark.django_db(transaction=True)
def test_create_goal_emits_goal_created_server_event(user_a):
    goal = create_goal(
        creator=user_a,
        title="Test Goal for Analytics",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
    )
    assert goal.id is not None

    event = AnalyticsEvent.objects.filter(
        event_name=EVENT_GOAL_CREATED,
        user=user_a,
    ).first()
    assert event is not None
    assert event.properties.get("is_shared") is False


@pytest.mark.django_db(transaction=True)
def test_complete_commitment_emits_commitment_completed_server_event(user_a):
    commitment = create_commitment(creator=user_a, title="Test Commitment")
    complete_commitment(actor=user_a, commitment_id=commitment.id)

    event = AnalyticsEvent.objects.filter(
        event_name=EVENT_COMMITMENT_COMPLETED,
        user=user_a,
    ).first()
    assert event is not None


@pytest.mark.django_db(transaction=True)
def test_analytics_failure_does_not_break_domain_action(user_a, monkeypatch):
    """Failure inside analytics service must never break domain execution."""
    def broken_save(*args, **kwargs):
        raise Exception("Database failure in analytics")

    monkeypatch.setattr("apps.analytics.models.AnalyticsEvent.objects.create", broken_save)

    # Goal creation must complete smoothly
    goal = create_goal(
        creator=user_a,
        title="Goal despite analytics failure",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
    )
    assert goal.id is not None


# ---------------------------------------------------------------------------
# 5. Funnel & Product Metric Calculation Tests
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_calculate_onboarding_funnel_stages(user_a):
    AnalyticsEvent.objects.create(
        event_name="app_opened",
        user=user_a,
        occurred_at=timezone.now(),
    )
    AnalyticsEvent.objects.create(
        event_name="register_success",
        user=user_a,
        occurred_at=timezone.now(),
    )
    AnalyticsEvent.objects.create(
        event_name="first_goal_created",
        user=user_a,
        occurred_at=timezone.now(),
    )

    funnel = calculate_onboarding_funnel()
    assert funnel["stages"]["app_opened"] >= 1
    assert funnel["stages"]["registered_or_logged_in"] >= 1
    assert funnel["stages"]["first_goal_created"] >= 1
    assert "registration_rate_percent" in funnel["conversion_rates"]


@pytest.mark.django_db
def test_calculate_product_metrics_returns_aggregate_dict(user_a):
    metrics = calculate_product_metrics()
    assert "active_users_7d" in metrics
    assert "funnels" in metrics
    assert "onboarding" in metrics["funnels"]
    assert "shared_goals" in metrics["funnels"]
    assert "ai" in metrics["funnels"]


# ---------------------------------------------------------------------------
# 6. Data Retention Policy Tests
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_purge_expired_raw_events_purges_older_than_90_days(user_a):
    now = timezone.now()
    old_event = AnalyticsEvent.objects.create(
        event_name=EVENT_GOAL_CREATED,
        user=user_a,
        occurred_at=now - timedelta(days=95),
    )
    recent_event = AnalyticsEvent.objects.create(
        event_name=EVENT_GOAL_CREATED,
        user=user_a,
        occurred_at=now - timedelta(days=10),
    )

    # Preserve aggregate metrics
    metric = AnalyticsDailyMetric.objects.create(
        metric_name="daily_active_users",
        date=(now - timedelta(days=95)).date(),
        value=10.0,
    )

    purged_count = purge_expired_raw_events(retention_days=90)
    assert purged_count >= 1

    assert not AnalyticsEvent.objects.filter(event_id=old_event.event_id).exists()
    assert AnalyticsEvent.objects.filter(event_id=recent_event.event_id).exists()
    assert AnalyticsDailyMetric.objects.filter(id=metric.id).exists()


# ---------------------------------------------------------------------------
# 7. Security & API View Tests
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_ingest_analytics_events_api(user_a):
    client = APIClient()
    client.force_authenticate(user=user_a)

    payload = {
        "events": [
            {
                "event_id": str(uuid.uuid4()),
                "event_name": EVENT_GOAL_CREATED,
                "event_version": 1,
                "occurred_at": timezone.now().isoformat(),
                "platform": "android",
                "properties": {"is_shared": False},
            }
        ]
    }
    response = client.post("/api/v1/analytics/events/", data=payload, format="json")
    assert response.status_code == 200
    assert response.data["ingested"] == 1


@pytest.mark.django_db
def test_admin_reports_endpoint_requires_staff(user_a, staff_user):
    client = APIClient()

    # Regular user rejected with 403
    client.force_authenticate(user=user_a)
    resp_user = client.get("/api/v1/analytics/admin/reports/")
    assert resp_user.status_code == 403

    # Staff user authorized with 200
    client.force_authenticate(user=staff_user)
    resp_staff = client.get("/api/v1/analytics/admin/reports/")
    assert resp_staff.status_code == 200
    assert "active_users_7d" in resp_staff.data
