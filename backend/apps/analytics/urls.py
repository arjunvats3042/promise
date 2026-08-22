from django.urls import path

from apps.analytics.views import get_analytics_reports, ingest_analytics_events

urlpatterns = [
    path("events/", ingest_analytics_events, name="analytics-events-ingest"),
    path("admin/reports/", get_analytics_reports, name="analytics-admin-reports"),
]
