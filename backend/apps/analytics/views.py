from rest_framework import permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from apps.analytics.serializers import AnalyticsBatchSerializer, AnalyticsEventSerializer
from apps.analytics.services import (
    calculate_ai_funnel,
    calculate_notification_funnel,
    calculate_onboarding_funnel,
    calculate_product_metrics,
    calculate_search_funnel,
    calculate_shared_goal_funnel,
    ingest_event_batch,
)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def ingest_analytics_events(request):
    """Ingests client analytics events in batch (or single format).

    Rules:
    - Authenticated users only.
    - Whitelisted taxonomy events only.
    - Idempotent deduplication by event_id.
    - Non-blocking execution.
    """
    payload = request.data
    if isinstance(payload, dict) and "events" in payload:
        serializer = AnalyticsBatchSerializer(data=payload)
    elif isinstance(payload, dict):
        serializer = AnalyticsBatchSerializer(data={"events": [payload]})
    elif isinstance(payload, list):
        serializer = AnalyticsBatchSerializer(data={"events": payload})
    else:
        return Response(
            {"error": "Invalid format. Expected JSON object with 'events' array or single event."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if not serializer.is_valid():
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

    events_data = serializer.validated_data["events"]
    result = ingest_event_batch(user=request.user, events_data=events_data)

    return Response(
        {
            "status": "ok",
            "ingested": result["ingested"],
            "duplicates": result["duplicates"],
        },
        status=status.HTTP_200_OK,
    )


@api_view(["GET"])
@permission_classes([permissions.IsAdminUser])
def get_analytics_reports(request):
    """Internal admin reporting layer.

    Security & Privacy Guards:
    - Staff / Superuser access ONLY.
    - Returns aggregate funnel & metric statistics ONLY.
    - Raw user content or personal identifiers are NEVER included.
    """
    start_date = request.query_params.get("start_date")
    end_date = request.query_params.get("end_date")
    report_type = request.query_params.get("type", "all")

    if report_type == "onboarding":
        data = calculate_onboarding_funnel(start_date, end_date)
    elif report_type == "shared_goals":
        data = calculate_shared_goal_funnel(start_date, end_date)
    elif report_type == "ai":
        data = calculate_ai_funnel(start_date, end_date)
    elif report_type == "notifications":
        data = calculate_notification_funnel(start_date, end_date)
    elif report_type == "search":
        data = calculate_search_funnel(start_date, end_date)
    else:
        data = calculate_product_metrics(start_date, end_date)

    return Response(data, status=status.HTTP_200_OK)
