import pytest
from rest_framework.test import APIClient

from apps.authentication.services import login_user, logout_all_sessions, logout_session
from apps.notifications.models import UserDevice
from apps.users.models import User


@pytest.fixture
def test_user(db):
    return User.objects.create_user(
        email="device_user@example.com",
        name="Device User",
        password="correct-horse-battery-staple",
    )


@pytest.fixture
def other_user(db):
    return User.objects.create_user(
        email="other_device_user@example.com",
        name="Other Device User",
        password="correct-horse-battery-staple",
    )


@pytest.mark.django_db
def test_register_device_creates_active_record(test_user):
    client = APIClient()
    client.force_authenticate(user=test_user)

    response = client.post(
        "/api/v1/notifications/devices/",
        {
            "fcm_token": "fcm_token_123",
            "device_id": "pixel_9_dev_1",
            "device_name": "Pixel 9",
            "platform": "ANDROID",
            "app_version": "0.1.0",
        },
        format="json",
    )

    assert response.status_code == 201
    assert response.data["device_id"] == "pixel_9_dev_1"
    assert response.data["is_active"] is True

    device = UserDevice.objects.get(user=test_user, device_id="pixel_9_dev_1")
    assert device.fcm_token == "fcm_token_123"
    assert device.is_active is True


@pytest.mark.django_db
def test_register_device_rotates_token_for_same_device_id(test_user):
    client = APIClient()
    client.force_authenticate(user=test_user)

    client.post(
        "/api/v1/notifications/devices/",
        {
            "fcm_token": "fcm_token_old",
            "device_id": "pixel_9_dev_1",
        },
        format="json",
    )

    response = client.post(
        "/api/v1/notifications/devices/",
        {
            "fcm_token": "fcm_token_new",
            "device_id": "pixel_9_dev_1",
        },
        format="json",
    )

    assert response.status_code == 200
    assert UserDevice.objects.filter(user=test_user).count() == 1
    device = UserDevice.objects.get(user=test_user, device_id="pixel_9_dev_1")
    assert device.fcm_token == "fcm_token_new"


@pytest.mark.django_db
def test_register_device_reassigns_token_from_other_user(test_user, other_user):
    UserDevice.objects.create(
        user=other_user,
        device_id="old_device_id",
        fcm_token="shared_fcm_token",
        is_active=True,
    )

    client = APIClient()
    client.force_authenticate(user=test_user)

    response = client.post(
        "/api/v1/notifications/devices/",
        {
            "fcm_token": "shared_fcm_token",
            "device_id": "new_device_id",
        },
        format="json",
    )

    assert response.status_code == 201
    assert UserDevice.objects.filter(user=other_user, device_id="old_device_id").exists() is False

    new_device = UserDevice.objects.get(user=test_user, device_id="new_device_id")
    assert new_device.is_active is True
    assert new_device.fcm_token == "shared_fcm_token"


@pytest.mark.django_db
def test_delete_device_deactivates_record(test_user):
    UserDevice.objects.create(
        user=test_user,
        device_id="pixel_9_dev_1",
        fcm_token="fcm_token_123",
        is_active=True,
    )

    client = APIClient()
    client.force_authenticate(user=test_user)

    response = client.delete("/api/v1/notifications/devices/pixel_9_dev_1/")
    assert response.status_code == 204

    device = UserDevice.objects.get(user=test_user, device_id="pixel_9_dev_1")
    assert device.is_active is False


@pytest.mark.django_db
def test_single_logout_deactivates_only_current_device(test_user):
    dev1 = UserDevice.objects.create(
        user=test_user, device_id="dev_1", fcm_token="tok_1", is_active=True
    )
    dev2 = UserDevice.objects.create(
        user=test_user, device_id="dev_2", fcm_token="tok_2", is_active=True
    )

    auth_res = login_user(email=test_user.email, password="correct-horse-battery-staple")
    logout_session(user=test_user, refresh_token=auth_res.refresh_token, device_id="dev_1")

    dev1.refresh_from_db()
    dev2.refresh_from_db()
    assert dev1.is_active is False
    assert dev2.is_active is True


@pytest.mark.django_db
def test_logout_all_deactivates_all_user_devices(test_user):
    dev1 = UserDevice.objects.create(
        user=test_user, device_id="dev_1", fcm_token="tok_1", is_active=True
    )
    dev2 = UserDevice.objects.create(
        user=test_user, device_id="dev_2", fcm_token="tok_2", is_active=True
    )

    logout_all_sessions(user=test_user)

    dev1.refresh_from_db()
    dev2.refresh_from_db()
    assert dev1.is_active is False
    assert dev2.is_active is False
