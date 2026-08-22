from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.notifications.models import UserDevice, UserNotificationPreferences
from apps.notifications.serializers import (
    RegisterDeviceSerializer,
    UserDeviceSerializer,
    UserNotificationPreferencesSerializer,
)
from apps.notifications.services import get_or_create_preferences


class DeviceRegistrationView(APIView):
    """Register or update an FCM push device token for the authenticated user."""

    permission_classes = [IsAuthenticated]

    def get(self, request):
        devices = UserDevice.objects.filter(user=request.user, is_active=True)
        serializer = UserDeviceSerializer(devices, many=True)
        return Response({"devices": serializer.data})

    def post(self, request):
        serializer = RegisterDeviceSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        fcm_token = data["fcm_token"]
        device_id = data["device_id"]

        # If fcm_token belongs to another user/device, delete old row so unique fcm_token is freed
        UserDevice.objects.filter(fcm_token=fcm_token).exclude(
            user=request.user, device_id=device_id
        ).delete()

        device, created = UserDevice.objects.update_or_create(
            user=request.user,
            device_id=device_id,
            defaults={
                "fcm_token": fcm_token,
                "device_name": data.get("device_name", ""),
                "platform": data.get("platform", "ANDROID"),
                "app_version": data.get("app_version", ""),
                "is_active": True,
            },
        )

        resp_status = status.HTTP_201_CREATED if created else status.HTTP_200_OK
        return Response(UserDeviceSerializer(device).data, status=resp_status)


class DeviceDetailView(APIView):
    """Deactivate or retrieve a specific device."""

    permission_classes = [IsAuthenticated]

    def delete(self, request, device_id):
        updated = UserDevice.objects.filter(
            user=request.user, device_id=device_id, is_active=True
        ).update(is_active=False)

        if updated:
            return Response(status=status.HTTP_204_NO_CONTENT)
        return Response(
            {"detail": "Device not found or already inactive."},
            status=status.HTTP_404_NOT_FOUND,
        )


class NotificationPreferencesView(APIView):
    """Retrieve or update notification preferences for the authenticated user."""

    permission_classes = [IsAuthenticated]

    def get(self, request):
        prefs = get_or_create_preferences(request.user)
        return Response(UserNotificationPreferencesSerializer(prefs).data)

    def patch(self, request):
        prefs = get_or_create_preferences(request.user)
        serializer = UserNotificationPreferencesSerializer(
            prefs, data=request.data, partial=True
        )
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(serializer.data)
