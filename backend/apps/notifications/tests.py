import datetime
from zoneinfo import ZoneInfo
from django.test import TestCase
from django.utils import timezone

from apps.commitments.models import Commitment
from apps.commitments.services import create_commitment, complete_commitment, snooze_commitment
from apps.goals.models import Goal, GoalCheckIn
from apps.goals.services import create_goal, record_check_in
from apps.notifications.dispatcher import dispatch_due_reminders
from apps.notifications.models import NotificationDelivery, Reminder, UserDevice, UserNotificationPreferences
from apps.notifications.services import (
    get_or_create_preferences,
    send_test_notification_to_user,
    sync_all_active_reminders,
    sync_commitment_reminders,
    sync_goal_reminders,
)
from apps.users.models import User


class NotificationSchedulingTests(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            email="testuser@example.com",
            name="Test User",
            timezone="Asia/Kolkata",
        )
        self.device = UserDevice.objects.create(
            user=self.user,
            device_id="test_android_device_1",
            fcm_token="test_fcm_token_12345",
            platform="ANDROID",
            is_active=True,
        )

    def test_user_default_timezone_is_ist(self):
        self.assertEqual(self.user.timezone, "Asia/Kolkata")
        prefs = get_or_create_preferences(self.user)
        self.assertTrue(prefs.enabled)
        self.assertTrue(prefs.commitments_due_soon)
        self.assertTrue(prefs.commitments_due_now)

    def test_commitment_reminder_sync_future_datetime(self):
        tz = ZoneInfo("Asia/Kolkata")
        now = timezone.now()
        due_at = now + datetime.timedelta(hours=3)

        commitment = create_commitment(
            creator=self.user,
            title="Submit Project Report",
            due_at=due_at,
            due_precision=Commitment.DuePrecision.DATETIME,
        )

        reminders = Reminder.objects.filter(entity_type="COMMITMENT", entity_id=commitment.id)
        event_types = set(reminders.values_list("event_type", flat=True))
        self.assertIn("commitment.due_soon", event_types)
        self.assertIn("commitment.due_now", event_type := "commitment.due_now")

        due_soon_reminder = reminders.get(event_type="commitment.due_soon")
        expected_due_soon = due_at - datetime.timedelta(hours=1)
        self.assertEqual(due_soon_reminder.scheduled_for, expected_due_soon)

        # Complete commitment -> reminders should be cancelled
        complete_commitment(actor=self.user, commitment_id=commitment.id)
        reminders = Reminder.objects.filter(entity_type="COMMITMENT", entity_id=commitment.id)
        for r in reminders:
            self.assertEqual(r.status, Reminder.ReminderStatus.CANCELLED)

    def test_goal_reminder_sync_and_checkin_cancel(self):
        goal = create_goal(
            creator=self.user,
            title="Morning Meditation",
            recurrence_kind=Goal.RecurrenceKind.DAILY,
            timezone="Asia/Kolkata",
        )

        reminders = Reminder.objects.filter(entity_type="GOAL", entity_id=goal.id)
        self.assertTrue(reminders.exists())

        # Check in today -> reminders cancelled
        tz = ZoneInfo("Asia/Kolkata")
        today_ist = timezone.now().astimezone(tz).date()
        record_check_in(
            actor=self.user,
            goal_id=goal.id,
            period_date=today_ist,
            status=GoalCheckIn.Status.COMPLETED,
        )

        reminders = Reminder.objects.filter(entity_type="GOAL", entity_id=goal.id)
        for r in reminders:
            self.assertEqual(r.status, Reminder.ReminderStatus.CANCELLED)

    def test_test_push_notification_service(self):
        res = send_test_notification_to_user(self.user, title="Test Alert", body="Working!")
        self.assertEqual(res["status"], "SENT")
        self.assertEqual(res["active_devices_count"], 1)
        self.assertEqual(res["dispatched_count"], 1)

        deliveries = NotificationDelivery.objects.filter(user_device=self.device)
        self.assertTrue(deliveries.exists())
        self.assertEqual(deliveries.first().status, NotificationDelivery.DeliveryStatus.SENT)

    def test_dispatcher_claims_and_sends_due_reminders(self):
        now = timezone.now()
        # Schedule reminder in the past
        past_time = now - datetime.timedelta(minutes=5)
        commitment = create_commitment(
            creator=self.user,
            title="Instant Action",
            due_at=now + datetime.timedelta(hours=2),
            due_precision=Commitment.DuePrecision.DATETIME,
        )
        reminder = Reminder.objects.filter(entity_type="COMMITMENT", entity_id=commitment.id).first()
        reminder.scheduled_for = past_time
        reminder.save()

        result = dispatch_due_reminders()
        self.assertGreaterEqual(result.dispatched, 1)

        reminder.refresh_from_db()
        self.assertEqual(reminder.status, Reminder.ReminderStatus.DISPATCHED)
        self.assertIsNotNone(reminder.dispatched_at)
