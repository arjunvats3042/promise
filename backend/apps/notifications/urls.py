from django.urls import path

from apps.notifications.views import (
    DeviceDetailView,
    DeviceRegistrationView,
    NotificationHistoryView,
    NotificationPreferencesView,
)

urlpatterns = [
    path("devices/", DeviceRegistrationView.as_view(), name="notification-devices"),
    path("devices/<str:device_id>/", DeviceDetailView.as_view(), name="notification-device-detail"),
    path("preferences/", NotificationPreferencesView.as_view(), name="notification-preferences"),
    path("history/", NotificationHistoryView.as_view(), name="notification-history"),
]
