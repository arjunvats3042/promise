import datetime
from datetime import timedelta
import uuid
import pytest
from django.utils import timezone as django_timezone

from apps.commitments.models import Commitment
from apps.goals.models import Goal, GoalCheckIn
from apps.notifications.dispatcher import dispatch_due_reminders, is_in_quiet_hours
from apps.notifications.fcm import MockFcmClient
from apps.notifications.models import (
    NotificationDelivery,
    Reminder,
    UserDevice,
    UserNotificationPreferences,
)
from apps.notifications.services import (
    get_or_create_preferences,
    schedule_reminder,
)
from apps.users.models import User


@pytest.fixture
def test_user(db):
    user = User.objects.create_user(
        email="push_user@example.com",
        name="Push User",
        timezone="UTC",
        password="correct-horse-battery-staple",
    )
    UserNotificationPreferences.objects.create(
        user=user,
        enabled=True,
        quiet_hours_enabled=False,
    )
    return user


@pytest.fixture
def mock_fcm():
    client = MockFcmClient()
    client.reset()
    return client


@pytest.mark.django_db
def test_dispatch_due_reminder_success_single_device(test_user, mock_fcm):
    UserDevice.objects.create(
        user=test_user,
        device_id="pixel_9",
        fcm_token="fcm_valid_tok",
        is_active=True,
    )

    commitment = Commitment.objects.create(
        created_by=test_user,
        title="Draft annual report",
        status=Commitment.Status.PENDING,
    )

    now = django_timezone.now()
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=commitment.id,
        event_type="commitment.due_now",
        scheduled_for=now - timedelta(minutes=1),
    )

    result = dispatch_due_reminders(fcm_client=mock_fcm)
    assert result.dispatched == 1
    assert len(mock_fcm.sent_messages) == 1
    assert mock_fcm.sent_messages[0]["token"] == "fcm_valid_tok"
    assert mock_fcm.sent_messages[0]["priority"] == "high"

    reminder.refresh_from_db()
    assert reminder.status == Reminder.ReminderStatus.DISPATCHED
    assert reminder.dispatched_at is not None

    delivery = NotificationDelivery.objects.get(reminder=reminder)
    assert delivery.status == NotificationDelivery.DeliveryStatus.SENT
    assert delivery.sent_at is not None


@pytest.mark.django_db
def test_dispatch_due_reminder_multi_device_success(test_user, mock_fcm):
    UserDevice.objects.create(
        user=test_user, device_id="phone", fcm_token="tok_phone", is_active=True
    )
    UserDevice.objects.create(
        user=test_user, device_id="tablet", fcm_token="tok_tablet", is_active=True
    )

    commitment = Commitment.objects.create(
        created_by=test_user, title="Team sync", status=Commitment.Status.PENDING
    )

    now = django_timezone.now()
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=commitment.id,
        event_type="commitment.due_soon",
        scheduled_for=now - timedelta(minutes=1),
    )

    result = dispatch_due_reminders(fcm_client=mock_fcm)
    assert result.dispatched == 1
    assert len(mock_fcm.sent_messages) == 2

    reminder.refresh_from_db()
    assert reminder.status == Reminder.ReminderStatus.DISPATCHED

    deliveries = NotificationDelivery.objects.filter(reminder=reminder)
    assert deliveries.count() == 2
    for d in deliveries:
        assert d.status == NotificationDelivery.DeliveryStatus.SENT


@pytest.mark.django_db
def test_partial_multi_device_failure_retries_only_failing_device(test_user, mock_fcm):
    UserDevice.objects.create(
        user=test_user, device_id="phone", fcm_token="tok_phone", is_active=True
    )
    UserDevice.objects.create(
        user=test_user, device_id="tablet", fcm_token="tok_failing_tablet", is_active=True
    )
    mock_fcm.failing_tokens.add("tok_failing_tablet")

    commitment = Commitment.objects.create(
        created_by=test_user, title="Team sync", status=Commitment.Status.PENDING
    )

    now = django_timezone.now()
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=commitment.id,
        event_type="commitment.due_soon",
        scheduled_for=now - timedelta(minutes=1),
    )

    # First dispatch run: phone succeeds, tablet fails transiently
    result = dispatch_due_reminders(fcm_client=mock_fcm)
    assert result.retried == 1
    assert len(mock_fcm.sent_messages) == 1
    assert mock_fcm.sent_messages[0]["token"] == "tok_phone"

    reminder.refresh_from_db()
    assert reminder.status == Reminder.ReminderStatus.SCHEDULED

    phone_del = NotificationDelivery.objects.get(reminder=reminder, user_device__device_id="phone")
    tablet_del = NotificationDelivery.objects.get(reminder=reminder, user_device__device_id="tablet")
    assert phone_del.status == NotificationDelivery.DeliveryStatus.SENT
    assert tablet_del.status == NotificationDelivery.DeliveryStatus.PENDING
    assert tablet_del.attempts == 1

    # Tablet now recovers
    mock_fcm.failing_tokens.clear()
    # Fast forward scheduled_for
    reminder.scheduled_for = django_timezone.now() - timedelta(seconds=1)
    reminder.save()
    tablet_del.next_attempt_at = django_timezone.now() - timedelta(seconds=1)
    tablet_del.save()

    # Second dispatch run: only tablet is re-sent
    mock_fcm.sent_messages.clear()
    result2 = dispatch_due_reminders(fcm_client=mock_fcm)
    assert result2.dispatched == 1
    assert len(mock_fcm.sent_messages) == 1
    assert mock_fcm.sent_messages[0]["token"] == "tok_failing_tablet"

    reminder.refresh_from_db()
    assert reminder.status == Reminder.ReminderStatus.DISPATCHED


@pytest.mark.django_db
def test_unregistered_token_deactivates_device(test_user, mock_fcm):
    UserDevice.objects.create(
        user=test_user, device_id="old_phone", fcm_token="tok_unregistered", is_active=True
    )
    mock_fcm.unregistered_tokens.add("tok_unregistered")

    commitment = Commitment.objects.create(
        created_by=test_user, title="Team sync", status=Commitment.Status.PENDING
    )

    now = django_timezone.now()
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=commitment.id,
        event_type="commitment.due_soon",
        scheduled_for=now - timedelta(minutes=1),
    )

    result = dispatch_due_reminders(fcm_client=mock_fcm)
    assert result.suppressed == 1

    device = UserDevice.objects.get(user=test_user, device_id="old_phone")
    assert device.is_active is False

    delivery = NotificationDelivery.objects.get(reminder=reminder)
    assert delivery.status == NotificationDelivery.DeliveryStatus.CANCELLED


@pytest.mark.django_db
def test_completed_commitment_suppresses_reminder(test_user, mock_fcm):
    UserDevice.objects.create(
        user=test_user, device_id="phone", fcm_token="tok_1", is_active=True
    )

    commitment = Commitment.objects.create(
        created_by=test_user,
        title="Completed promise",
        status=Commitment.Status.COMPLETED,
    )

    now = django_timezone.now()
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=commitment.id,
        event_type="commitment.due_soon",
        scheduled_for=now - timedelta(minutes=1),
    )

    result = dispatch_due_reminders(fcm_client=mock_fcm)
    assert result.cancelled == 1
    assert len(mock_fcm.sent_messages) == 0

    reminder.refresh_from_db()
    assert reminder.status == Reminder.ReminderStatus.CANCELLED


@pytest.mark.django_db
def test_checked_in_goal_suppresses_evening_reminder(test_user, mock_fcm):
    UserDevice.objects.create(
        user=test_user, device_id="phone", fcm_token="tok_1", is_active=True
    )

    goal = Goal.objects.create(
        created_by=test_user,
        title="Daily Meditation",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.BINARY,
        start_date=datetime.date(2026, 1, 1),
        status=Goal.Status.ACTIVE,
    )

    from apps.goals.models import GoalParticipant
    participant = GoalParticipant.objects.create(
        goal=goal,
        user=test_user,
        role=GoalParticipant.Role.OWNER,
    )
    today_str = "2026-08-22"
    GoalCheckIn.objects.create(
        goal=goal,
        participant=participant,
        created_by=test_user,
        period_date=datetime.date(2026, 8, 22),
        checked_at=django_timezone.now(),
        status=GoalCheckIn.Status.COMPLETED,
    )

    now = django_timezone.now()
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="GOAL",
        entity_id=goal.id,
        event_type="goal.streak_protection",
        scheduled_for=now - timedelta(minutes=1),
        target_period=today_str,
    )

    result = dispatch_due_reminders(fcm_client=mock_fcm)
    assert result.cancelled == 1
    assert len(mock_fcm.sent_messages) == 0

    reminder.refresh_from_db()
    assert reminder.status == Reminder.ReminderStatus.CANCELLED


@pytest.mark.django_db
def test_disabled_preferences_suppresses_reminder(test_user, mock_fcm):
    UserDevice.objects.create(
        user=test_user, device_id="phone", fcm_token="tok_1", is_active=True
    )
    prefs = get_or_create_preferences(test_user)
    prefs.enabled = False
    prefs.save()

    commitment = Commitment.objects.create(
        created_by=test_user, title="Task", status=Commitment.Status.PENDING
    )

    now = django_timezone.now()
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=commitment.id,
        event_type="commitment.due_soon",
        scheduled_for=now - timedelta(minutes=1),
    )

    result = dispatch_due_reminders(fcm_client=mock_fcm)
    assert result.suppressed == 1
    assert len(mock_fcm.sent_messages) == 0

    reminder.refresh_from_db()
    assert reminder.status == Reminder.ReminderStatus.SUPPRESSED


@pytest.mark.django_db
def test_quiet_hours_defers_non_critical_reminder(test_user, mock_fcm):
    UserDevice.objects.create(
        user=test_user, device_id="phone", fcm_token="tok_1", is_active=True
    )
    prefs = get_or_create_preferences(test_user)
    prefs.quiet_hours_enabled = True
    prefs.quiet_hours_start = datetime.time(22, 0)
    prefs.quiet_hours_end = datetime.time(8, 0)
    prefs.save()

    commitment = Commitment.objects.create(
        created_by=test_user, title="Task", status=Commitment.Status.PENDING
    )

    # Midnight UTC
    now = datetime.datetime(2026, 8, 22, 0, 30, tzinfo=datetime.timezone.utc)
    reminder, _ = schedule_reminder(
        user=test_user,
        entity_type="COMMITMENT",
        entity_id=commitment.id,
        event_type="commitment.due_soon",
        scheduled_for=now - timedelta(minutes=5),
    )

    # Monkeypatch django_timezone.now
    import apps.notifications.dispatcher as disp
    original_now = disp.django_timezone.now
    disp.django_timezone.now = lambda: now

    try:
        result = dispatch_due_reminders(fcm_client=mock_fcm)
        assert result.retried == 1
        assert len(mock_fcm.sent_messages) == 0

        reminder.refresh_from_db()
        assert reminder.status == Reminder.ReminderStatus.SCHEDULED
        assert reminder.scheduled_for == datetime.datetime(2026, 8, 22, 8, 0, tzinfo=datetime.timezone.utc)
    finally:
        disp.django_timezone.now = original_now
