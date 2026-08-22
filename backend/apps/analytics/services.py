"""Analytics Services — Event Ingestion, Server-Side Authoritative Telemetry, Funnels & Retention.

Retention Policy:
- Raw `AnalyticsEvent` rows: Retained for 90 days by default. Purged via `purge_expired_raw_events()`.
- Aggregate `AnalyticsDailyMetric` snapshots: Retained indefinitely for long-term product trends.

Non-Blocking Guarantee:
- Server-side calls to `record_analytics_event()` execute inside `transaction.on_commit(...)`.
- Any unexpected database or network failure during analytics emission is silently caught and logged.
- Domain operations NEVER fail because of an analytics issue.
"""

from datetime import timedelta
import logging
from typing import Any, Dict, List, Optional
import uuid

from django.db import IntegrityError, transaction
from django.utils import timezone

from apps.analytics.events import is_event_whitelisted, sanitize_analytics_properties
from apps.analytics.models import (
    AnalyticsDailyMetric,
    AnalyticsEvent,
    compute_pseudonymous_analytics_id,
)

logger = logging.getLogger("promise")


def record_analytics_event(
    *,
    event_name: str,
    user=None,
    properties: Optional[Dict[str, Any]] = None,
    event_id: Optional[uuid.UUID] = None,
    platform: str = "server",
    app_version: str = "1.0.0",
    occurred_at=None,
) -> None:
    """Emits an authoritative server-side analytics event.

    - Whitelist verified.
    - Sanitizes properties (no PII/content).
    - Defers write until transaction commit via `transaction.on_commit`.
    - Fail-safe: Swallows exceptions to guarantee domain action success.
    """
    if not is_event_whitelisted(event_name):
        logger.warning("Attempted to record unapproved analytics event: %s", event_name)
        return

    clean_props = sanitize_analytics_properties(properties or {})
    event_uuid = event_id or uuid.uuid4()
    event_time = occurred_at or timezone.now()
    anon_user_id = compute_pseudonymous_analytics_id(user) if user else "server"

    def _save():
        try:
            # Idempotent insert: skip if event_id already exists
            if AnalyticsEvent.objects.filter(event_id=event_uuid).exists():
                return
            AnalyticsEvent.objects.create(
                event_id=event_uuid,
                event_name=event_name,
                event_version=1,
                user=user if getattr(user, "is_authenticated", False) else None,
                analytics_user_id=anon_user_id,
                platform=platform,
                app_version=app_version,
                occurred_at=event_time,
                properties=clean_props,
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning("Non-fatal error recording server analytics event %s: %s", event_name, exc)

    try:
        # Schedule write after successful DB transaction commit
        transaction.on_commit(_save)
    except Exception:  # noqa: BLE001
        # If outside active transaction, execute directly
        _save()


def ingest_event_batch(
    *,
    user,
    events_data: List[Dict[str, Any]],
) -> Dict[str, int]:
    """Ingests a batch of client analytics events idempotently."""
    ingested = 0
    duplicates = 0
    anon_id = compute_pseudonymous_analytics_id(user) if user else "anonymous"
    authenticated_user = user if getattr(user, "is_authenticated", False) else None

    to_create = []
    for data in events_data:
        event_uuid = data.get("event_id") or uuid.uuid4()
        event_name = data["event_name"]
        clean_props = sanitize_analytics_properties(data.get("properties", {}))

        if AnalyticsEvent.objects.filter(event_id=event_uuid).exists():
            duplicates += 1
            continue

        to_create.append(
            AnalyticsEvent(
                event_id=event_uuid,
                event_name=event_name,
                event_version=data.get("event_version", 1),
                user=authenticated_user,
                analytics_user_id=anon_id,
                platform=data.get("platform", "android"),
                app_version=data.get("app_version", "1.0.0"),
                occurred_at=data.get("occurred_at") or timezone.now(),
                properties=clean_props,
            )
        )

    if to_create:
        try:
            created = AnalyticsEvent.objects.bulk_create(to_create, ignore_conflicts=True)
            ingested = len(created)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Failed bulk ingestion of analytics batch: %s", exc)

    return {"ingested": ingested, "duplicates": duplicates}


# ---------------------------------------------------------------------------
# Funnel & Aggregate Product Analytics Calculators
# ---------------------------------------------------------------------------


def calculate_onboarding_funnel(start_date=None, end_date=None) -> Dict[str, Any]:
    """Calculates Onboarding Conversion Funnel."""
    qs = AnalyticsEvent.objects.all()
    if start_date:
        qs = qs.filter(occurred_at__date__gte=start_date)
    if end_date:
        qs = qs.filter(occurred_at__date__lte=end_date)

    opened = qs.filter(event_name="app_opened").count()
    registered = qs.filter(event_name__in=["register_success", "google_login_success"]).count()
    first_commitment = qs.filter(event_name="first_commitment_created").count()
    first_goal = qs.filter(event_name="first_goal_created").count()
    first_checkin = qs.filter(event_name="first_checkin_completed").count()

    conv_register = (registered / opened * 100.0) if opened > 0 else 0.0
    conv_checkin = (first_checkin / registered * 100.0) if registered > 0 else 0.0

    return {
        "funnel_name": "onboarding",
        "stages": {
            "app_opened": opened,
            "registered_or_logged_in": registered,
            "first_commitment_created": first_commitment,
            "first_goal_created": first_goal,
            "first_checkin_completed": first_checkin,
        },
        "conversion_rates": {
            "registration_rate_percent": round(conv_register, 2),
            "activation_rate_percent": round(conv_checkin, 2),
        },
    }


def calculate_shared_goal_funnel(start_date=None, end_date=None) -> Dict[str, Any]:
    """Calculates Shared Goal Adoption & Interaction Funnel."""
    qs = AnalyticsEvent.objects.all()
    if start_date:
        qs = qs.filter(occurred_at__date__gte=start_date)
    if end_date:
        qs = qs.filter(occurred_at__date__lte=end_date)

    created = qs.filter(event_name="shared_goal_created").count()
    invited = qs.filter(event_name="participant_invited").count()
    joined = qs.filter(event_name="participant_joined").count()
    chat_sent = qs.filter(event_name="shared_goal_message_sent").count()

    acceptance_rate = (joined / invited * 100.0) if invited > 0 else 0.0

    return {
        "funnel_name": "shared_goals",
        "stages": {
            "shared_goals_created": created,
            "invitations_sent": invited,
            "invitations_accepted": joined,
            "chat_messages_sent": chat_sent,
        },
        "conversion_rates": {
            "invitation_acceptance_rate_percent": round(acceptance_rate, 2),
        },
    }


def calculate_ai_funnel(start_date=None, end_date=None) -> Dict[str, Any]:
    """Calculates AI Feature Adoption & Confirmation Funnel."""
    qs = AnalyticsEvent.objects.all()
    if start_date:
        qs = qs.filter(occurred_at__date__gte=start_date)
    if end_date:
        qs = qs.filter(occurred_at__date__lte=end_date)

    used = qs.filter(
        event_name__in=[
            "ai_goal_builder_used",
            "ai_commitment_refiner_used",
            "ai_thought_parser_used",
        ]
    ).count()
    confirmed = qs.filter(
        event_name__in=[
            "ai_goal_builder_confirmed",
            "ai_commitment_refiner_confirmed",
            "ai_thought_parser_confirmed",
        ]
    ).count()
    clarification = qs.filter(event_name="ai_clarification_requested").count()
    unavailable = qs.filter(event_name="ai_unavailable").count()

    confirmation_rate = (confirmed / used * 100.0) if used > 0 else 0.0

    return {
        "funnel_name": "ai_features",
        "stages": {
            "ai_features_invoked": used,
            "clarifications_requested": clarification,
            "suggestions_confirmed": confirmed,
            "ai_unavailable_count": unavailable,
        },
        "conversion_rates": {
            "confirmation_rate_percent": round(confirmation_rate, 2),
        },
    }


def calculate_notification_funnel(start_date=None, end_date=None) -> Dict[str, Any]:
    """Calculates Notification Performance Funnel."""
    qs = AnalyticsEvent.objects.all()
    if start_date:
        qs = qs.filter(occurred_at__date__gte=start_date)
    if end_date:
        qs = qs.filter(occurred_at__date__lte=end_date)

    scheduled = qs.filter(event_name="notification_scheduled").count()
    delivered = qs.filter(event_name="notification_delivered").count()
    opened = qs.filter(event_name="notification_opened").count()
    action_clicked = qs.filter(event_name="notification_action_clicked").count()
    suppressed = qs.filter(event_name="notification_suppressed").count()
    failed = qs.filter(event_name="notification_failed").count()

    open_rate = (opened / delivered * 100.0) if delivered > 0 else 0.0
    action_rate = (action_clicked / delivered * 100.0) if delivered > 0 else 0.0

    return {
        "funnel_name": "notifications",
        "stages": {
            "scheduled": scheduled,
            "delivered": delivered,
            "opened": opened,
            "action_clicked": action_clicked,
            "suppressed": suppressed,
            "failed": failed,
        },
        "conversion_rates": {
            "open_rate_percent": round(open_rate, 2),
            "action_rate_percent": round(action_rate, 2),
        },
    }


def calculate_search_funnel(start_date=None, end_date=None) -> Dict[str, Any]:
    """Calculates Search Effectiveness Funnel."""
    qs = AnalyticsEvent.objects.all()
    if start_date:
        qs = qs.filter(occurred_at__date__gte=start_date)
    if end_date:
        qs = qs.filter(occurred_at__date__lte=end_date)

    started = qs.filter(event_name="search_started").count()
    selected = qs.filter(event_name="search_result_selected").count()
    zero_results = qs.filter(event_name="search_zero_results").count()

    selection_rate = (selected / started * 100.0) if started > 0 else 0.0
    zero_rate = (zero_results / started * 100.0) if started > 0 else 0.0

    return {
        "funnel_name": "search",
        "stages": {
            "search_started": started,
            "result_selected": selected,
            "zero_results": zero_results,
        },
        "conversion_rates": {
            "selection_rate_percent": round(selection_rate, 2),
            "zero_result_rate_percent": round(zero_rate, 2),
        },
    }


def calculate_product_metrics(start_date=None, end_date=None) -> Dict[str, Any]:
    """Returns overall product insights and usage metrics."""
    today = timezone.now().date()
    start_7d = today - timedelta(days=7)

    qs = AnalyticsEvent.objects.all()

    active_users_7d = (
        qs.filter(occurred_at__date__gte=start_7d)
        .values("analytics_user_id")
        .distinct()
        .count()
    )
    goals_created = qs.filter(event_name="goal_created").count()
    checkins_completed = qs.filter(event_name="goal_checked_in").count()
    commitments_completed = qs.filter(event_name="commitment_completed").count()

    return {
        "active_users_7d": active_users_7d,
        "total_goals_created": goals_created,
        "total_checkins_completed": checkins_completed,
        "total_commitments_completed": commitments_completed,
        "funnels": {
            "onboarding": calculate_onboarding_funnel(start_date, end_date),
            "shared_goals": calculate_shared_goal_funnel(start_date, end_date),
            "ai": calculate_ai_funnel(start_date, end_date),
            "notifications": calculate_notification_funnel(start_date, end_date),
            "search": calculate_search_funnel(start_date, end_date),
        },
    }


# ---------------------------------------------------------------------------
# Data Retention Policy Enforcement
# ---------------------------------------------------------------------------


def purge_expired_raw_events(retention_days: int = 90) -> int:
    """Enforces raw analytics retention policy.

    Deletes raw `AnalyticsEvent` records older than `retention_days` (default: 90 days).
    Long-term aggregate `AnalyticsDailyMetric` rows are preserved.
    """
    cutoff = timezone.now() - timedelta(days=retention_days)
    deleted_count, _ = AnalyticsEvent.objects.filter(occurred_at__lt=cutoff).delete()
    logger.info("Purged %d raw AnalyticsEvent records older than %d days.", deleted_count, retention_days)
    return deleted_count
