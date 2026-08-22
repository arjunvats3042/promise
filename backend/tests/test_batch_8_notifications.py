import datetime
from datetime import date, time, timedelta
from unittest.mock import MagicMock, patch
import uuid
from zoneinfo import ZoneInfo

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient

from apps.commitments.models import Commitment, CommitmentEvent
from apps.goals.models import ChatMessage, Goal, GoalCheckIn, GoalParticipant
from apps.notifications.dispatcher import (
    dispatch_due_reminders,
    is_in_quiet_hours,
    next_quiet_hours_exit,
)
from apps.notifications.fcm import FcmSendResult, MockFcmClient
from apps.notifications.models import (
    NotificationDelivery,
    Reminder,
    UserDevice,
    UserNotificationPreferences,
)
from apps.notifications.services import (
    calculate_weekly_digest_summary,
    cancel_reminders_for_entity,
    get_or_create_preferences,
    handle_goal_event,
    schedule_reminder,
    schedule_security_alert,
    schedule_weekly_digest,
)

User = get_user_model()


@pytest.fixture
def user_kolkata(db):
    return User.objects.create_user(
        email="kolkata@example.com",
        password="ValidPassword123!",
        name="Kolkata User",
        timezone="Asia/Kolkata",
    )


@pytest.fixture
def user_new_york(db):
    return User.objects.create_user(
        email="ny@example.com",
        password="ValidPassword123!",
        name="NY User",
        timezone="America/New_York",
    )


@pytest.fixture
def mock_fcm():
    client = MagicMock()
    client.send.return_value = FcmSendResult(token="test", success=True)
    return client


class TestNotificationDeduplication:
    @pytest.mark.django_db
    def test_schedule_reminder_is_idempotent(self, user_kolkata):
        entity_id = uuid.uuid4()
        now = timezone.now()

        r1, created1 = schedule_reminder(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=entity_id,
            event_type="commitment.due_now",
            scheduled_for=now,
            target_timestamp=now,
        )
        assert created1 is True

        r2, created2 = schedule_reminder(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=entity_id,
            event_type="commitment.due_now",
            scheduled_for=now + timedelta(minutes=5),
            target_timestamp=now,
        )
        assert created2 is False
        assert r1.id == r2.id
        assert Reminder.objects.filter(identity_key=r1.identity_key).count() == 1

    @pytest.mark.django_db
    def test_dispatch_per_device_delivery_tracking(self, user_kolkata, mock_fcm):
        UserDevice.objects.create(
            user=user_kolkata,
            fcm_token="fcm-token-device-1",
            device_id="dev-1",
            is_active=True,
        )
        prefs = get_or_create_preferences(user_kolkata)
        prefs.quiet_hours_enabled = False
        prefs.save()

        UserDevice.objects.create(
            user=user_kolkata,
            fcm_token="fcm-token-device-2",
            device_id="dev-2",
            is_active=True,
        )

        c = Commitment.objects.create(
            created_by=user_kolkata,
            title="Submit Report",
            due_at=timezone.now() + timedelta(hours=1),
            status=Commitment.Status.PENDING,
        )

        schedule_reminder(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=c.id,
            event_type="commitment.due_soon",
            scheduled_for=timezone.now() - timedelta(minutes=1),
        )

        # Dispatch
        res = dispatch_due_reminders(fcm_client=mock_fcm)
        assert res.dispatched == 1
        assert mock_fcm.send.call_count == 2

        # Verify deliveries created and marked SENT
        deliveries = NotificationDelivery.objects.filter(reminder__entity_id=c.id)
        assert deliveries.count() == 2
        for d in deliveries:
            assert d.status == NotificationDelivery.DeliveryStatus.SENT


class TestQuietHoursAndCriticalPolicy:
    @pytest.mark.django_db
    def test_quiet_hours_midnight_crossing_calculation(self):
        # 22:00 to 08:00
        start = time(22, 0)
        end = time(8, 0)

        # 23:30 Asia/Kolkata (in quiet hours)
        dt_night = datetime.datetime(2026, 8, 22, 23, 30, tzinfo=ZoneInfo("Asia/Kolkata"))
        assert is_in_quiet_hours("Asia/Kolkata", start, end, dt_night) is True

        # 05:00 Asia/Kolkata (in quiet hours)
        dt_early = datetime.datetime(2026, 8, 23, 5, 0, tzinfo=ZoneInfo("Asia/Kolkata"))
        assert is_in_quiet_hours("Asia/Kolkata", start, end, dt_early) is True

        # 14:00 Asia/Kolkata (outside quiet hours)
        dt_day = datetime.datetime(2026, 8, 22, 14, 0, tzinfo=ZoneInfo("Asia/Kolkata"))
        assert is_in_quiet_hours("Asia/Kolkata", start, end, dt_day) is False

    @pytest.mark.django_db
    def test_non_critical_reminder_deferred_to_quiet_hours_exit(self, user_kolkata, mock_fcm):
        UserDevice.objects.create(
            user=user_kolkata,
            fcm_token="token-quiet-1",
            device_id="dev-q1",
            is_active=True,
        )
        prefs = get_or_create_preferences(user_kolkata)
        prefs.quiet_hours_enabled = True
        prefs.quiet_hours_start = time(22, 0)
        prefs.quiet_hours_end = time(8, 0)
        prefs.save()

        g = Goal.objects.create(
            created_by=user_kolkata,
            title="Read 20 mins",
            timezone="Asia/Kolkata",
            start_date=timezone.now().date(),
            status=Goal.Status.ACTIVE,
        )

        # Mock current time to 23:00 IST
        dt_night_ist = datetime.datetime(2026, 8, 22, 23, 0, tzinfo=ZoneInfo("Asia/Kolkata"))
        dt_night_utc = dt_night_ist.astimezone(datetime.timezone.utc)

        reminder, _ = schedule_reminder(
            user=user_kolkata,
            entity_type="GOAL",
            entity_id=g.id,
            event_type="goal.today_practice",
            scheduled_for=dt_night_utc - timedelta(minutes=5),
        )

        with patch("django.utils.timezone.now", return_value=dt_night_utc):
            res = dispatch_due_reminders(fcm_client=mock_fcm)

        assert res.retried == 1
        assert mock_fcm.send.call_count == 0

        reminder.refresh_from_db()
        assert reminder.status == Reminder.ReminderStatus.SCHEDULED
        # Next exit should be 8:00 AM IST on August 23
        expected_exit_ist = datetime.datetime(2026, 8, 23, 8, 0, tzinfo=ZoneInfo("Asia/Kolkata"))
        expected_exit_utc = expected_exit_ist.astimezone(datetime.timezone.utc)
        assert reminder.scheduled_for == expected_exit_utc

    @pytest.mark.django_db
    def test_critical_due_now_and_security_bypass_quiet_hours(self, user_kolkata, mock_fcm):
        UserDevice.objects.create(
            user=user_kolkata,
            fcm_token="token-crit-1",
            device_id="dev-crit1",
            is_active=True,
        )
        prefs = get_or_create_preferences(user_kolkata)
        prefs.quiet_hours_enabled = True
        prefs.quiet_hours_start = time(22, 0)
        prefs.quiet_hours_end = time(8, 0)
        prefs.save()

        c = Commitment.objects.create(
            created_by=user_kolkata,
            title="Urgent Deadline",
            status=Commitment.Status.PENDING,
            due_at=timezone.now(),
        )

        dt_night_ist = datetime.datetime(2026, 8, 22, 23, 0, tzinfo=ZoneInfo("Asia/Kolkata"))
        dt_night_utc = dt_night_ist.astimezone(datetime.timezone.utc)

        schedule_reminder(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=c.id,
            event_type="commitment.due_now",
            scheduled_for=dt_night_utc - timedelta(minutes=1),
        )

        with patch("django.utils.timezone.now", return_value=dt_night_utc):
            res = dispatch_due_reminders(fcm_client=mock_fcm)

        # commitment.due_now must be dispatched immediately
        assert res.dispatched == 1
        assert mock_fcm.send.call_count == 1
        call_kwargs = mock_fcm.send.call_args.kwargs
        assert call_kwargs["priority"] == "high"


class TestPreferencesAndSuppression:
    @pytest.mark.django_db
    def test_master_switch_disables_all_reminders(self, user_kolkata, mock_fcm):
        UserDevice.objects.create(
            user=user_kolkata,
            fcm_token="token-master-off",
            device_id="dev-mo",
            is_active=True,
        )
        prefs = get_or_create_preferences(user_kolkata)
        prefs.enabled = False
        prefs.save()

        c = Commitment.objects.create(
            created_by=user_kolkata,
            title="Call Plumber",
            status=Commitment.Status.PENDING,
            due_at=timezone.now() + timedelta(hours=2),
        )

        schedule_reminder(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=c.id,
            event_type="commitment.due_soon",
            scheduled_for=timezone.now() - timedelta(minutes=1),
        )

        res = dispatch_due_reminders(fcm_client=mock_fcm)
        assert res.suppressed == 1
        assert mock_fcm.send.call_count == 0

        rem = Reminder.objects.get(entity_id=c.id)
        assert rem.status == Reminder.ReminderStatus.SUPPRESSED
        assert rem.cancellation_reason == "USER_DISABLED"

    @pytest.mark.django_db
    def test_category_switches_respected(self, user_kolkata, mock_fcm):
        UserDevice.objects.create(
            user=user_kolkata,
            fcm_token="token-cat-1",
            device_id="dev-cat1",
            is_active=True,
        )
        prefs = get_or_create_preferences(user_kolkata)
        prefs.commitments_due_soon = False
        prefs.shared_goals_activity = False
        prefs.save()

        c = Commitment.objects.create(
            created_by=user_kolkata,
            title="Submit Taxes",
            status=Commitment.Status.PENDING,
            due_at=timezone.now() + timedelta(hours=2),
        )

        schedule_reminder(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=c.id,
            event_type="commitment.due_soon",
            scheduled_for=timezone.now() - timedelta(minutes=1),
        )

        res = dispatch_due_reminders(fcm_client=mock_fcm)
        assert res.suppressed == 1
        rem = Reminder.objects.get(entity_id=c.id)
        assert rem.status == Reminder.ReminderStatus.SUPPRESSED
        assert rem.cancellation_reason == "CATEGORY_DISABLED"


class TestWeeklyDigest:
    @pytest.mark.django_db
    def test_weekly_digest_calculation_and_idempotency(self, user_kolkata):
        # Create some activity for the user
        c = Commitment.objects.create(
            created_by=user_kolkata,
            title="Finished Task",
            status=Commitment.Status.COMPLETED,
        )
        CommitmentEvent.objects.create(
            commitment=c,
            actor=user_kolkata,
            event_type="COMPLETED",
            created_at=timezone.now(),
        )

        g = Goal.objects.create(
            created_by=user_kolkata,
            title="Daily Jogging",
            start_date=timezone.now().date(),
            status=Goal.Status.ACTIVE,
        )
        participant = GoalParticipant.objects.create(
            goal=g,
            user=user_kolkata,
            role=GoalParticipant.Role.OWNER,
            status=GoalParticipant.Status.ACTIVE,
        )
        GoalCheckIn.objects.create(
            goal=g,
            participant=participant,
            created_by=user_kolkata,
            period_date=timezone.now().date(),
            status=GoalCheckIn.Status.COMPLETED,
            checked_at=timezone.now(),
        )

        # Schedule digest
        r1 = schedule_weekly_digest(user_kolkata)
        assert r1 is not None
        assert r1.event_type == "digest.weekly"

        # Scheduling again for same week is idempotent
        r2 = schedule_weekly_digest(user_kolkata)
        assert r2.id == r1.id
        assert Reminder.objects.filter(entity_type="DIGEST", user=user_kolkata).count() == 1

    @pytest.mark.django_db
    def test_zero_activity_user_receives_no_digest(self, user_kolkata):
        r = schedule_weekly_digest(user_kolkata)
        assert r is None
        assert Reminder.objects.filter(entity_type="DIGEST", user=user_kolkata).count() == 0


class TestNotificationHistoryAPI:
    @pytest.mark.django_db
    def test_notification_history_endpoint(self, user_kolkata):
        client = APIClient()
        client.force_authenticate(user=user_kolkata)

        # Create a dispatched reminder
        now = timezone.now()
        rem = Reminder.objects.create(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=uuid.uuid4(),
            event_type="commitment.due_now",
            identity_key=f"{user_kolkata.id}:COMMITMENT:1:due_now:{now.isoformat()}",
            scheduled_for=now,
            status=Reminder.ReminderStatus.DISPATCHED,
            dispatched_at=now,
        )

        response = client.get("/api/v1/notifications/history/")
        assert response.status_code == 200
        data = response.json()
        assert "results" in data
        assert len(data["results"]) == 1
        item = data["results"][0]
        assert item["id"] == str(rem.id)
        assert item["category"] == "COMMITMENT"
        assert item["title"] == "Due now"
        assert item["status"] == "DISPATCHED"


class TestSharedGoalAndSecurityNotifications:
    @pytest.mark.django_db
    def test_shared_goal_joined_and_left_notifications(self, user_kolkata, user_new_york, mock_fcm):
        prefs = get_or_create_preferences(user_kolkata)
        prefs.quiet_hours_enabled = False
        prefs.save()

        UserDevice.objects.create(
            user=user_kolkata,
            fcm_token="token-kolkata",
            device_id="dev-kolkata",
            is_active=True,
        )

        g = Goal.objects.create(
            created_by=user_kolkata,
            title="Mindfulness Meditation",
            is_shared=True,
            start_date=timezone.now().date(),
            status=Goal.Status.ACTIVE,
        )
        GoalParticipant.objects.create(
            goal=g,
            user=user_kolkata,
            role=GoalParticipant.Role.OWNER,
            status=GoalParticipant.Status.ACTIVE,
        )

        # Participant joined event
        handle_goal_event({
            "event_type": "goal.participant.joined",
            "payload": {
                "goal_id": str(g.id),
                "actor_id": str(user_new_york.id),
                "user_name": "NY User",
            },
        })

        reminders = Reminder.objects.filter(entity_id=g.id, event_type="goal.participant.joined")
        assert reminders.count() == 1
        rem = reminders.first()
        assert rem.user == user_kolkata

        # Dispatch
        res = dispatch_due_reminders(fcm_client=mock_fcm)
        assert res.dispatched >= 1
        assert mock_fcm.send.call_count >= 1

    @pytest.mark.django_db
    def test_security_alert_scheduling(self, user_kolkata, mock_fcm):
        UserDevice.objects.create(
            user=user_kolkata,
            fcm_token="token-security",
            device_id="dev-sec",
            is_active=True,
        )

        rem = schedule_security_alert(user_kolkata, "security.new_device_login")
        assert rem.entity_type == Reminder.EntityType.SECURITY
        assert rem.event_type == "security.new_device_login"

        res = dispatch_due_reminders(fcm_client=mock_fcm)
        assert res.dispatched == 1
        call_kwargs = mock_fcm.send.call_args.kwargs
        assert call_kwargs["priority"] == "high"
        assert call_kwargs["title"] == "New device login"


class TestObservabilityMetrics:
    @pytest.mark.django_db
    def test_notification_metrics_snapshot(self, user_kolkata):
        from apps.core.observability import get_notification_metrics

        now = timezone.now()
        Reminder.objects.create(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=uuid.uuid4(),
            event_type="commitment.due_soon",
            identity_key=f"rem-sched-{uuid.uuid4()}",
            scheduled_for=now,
            status=Reminder.ReminderStatus.SCHEDULED,
        )
        Reminder.objects.create(
            user=user_kolkata,
            entity_type="COMMITMENT",
            entity_id=uuid.uuid4(),
            event_type="commitment.due_now",
            identity_key=f"rem-disp-{uuid.uuid4()}",
            scheduled_for=now,
            status=Reminder.ReminderStatus.DISPATCHED,
            dispatched_at=now,
        )

        metrics = get_notification_metrics()
        assert metrics["scheduled_count"] >= 1
        assert metrics["dispatched_count"] >= 1
        assert "suppressed_count" in metrics
        assert "cancelled_count" in metrics
