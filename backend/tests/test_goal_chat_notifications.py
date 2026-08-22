import datetime
import pytest
from django.utils import timezone

from apps.goals.models import ChatMessage, Goal, GoalParticipant
from apps.notifications.dispatcher import dispatch_due_reminders
from apps.notifications.fcm import MockFcmClient
from apps.notifications.models import NotificationDelivery, Reminder, UserDevice, UserNotificationPreferences
from apps.notifications.services import handle_goal_event
from apps.users.models import User


@pytest.fixture
def owner(db):
    user = User.objects.create_user(
        email="owner@example.com",
        name="Owner User",
        password="password123",
        timezone="UTC",
    )
    UserNotificationPreferences.objects.create(user=user, enabled=True, quiet_hours_enabled=False)
    return user


@pytest.fixture
def participant_b(db):
    user = User.objects.create_user(
        email="participant_b@example.com",
        name="Participant B",
        password="password123",
        timezone="UTC",
    )
    UserNotificationPreferences.objects.create(user=user, enabled=True, quiet_hours_enabled=False)
    return user


@pytest.fixture
def participant_c(db):
    user = User.objects.create_user(
        email="participant_c@example.com",
        name="Participant C",
        password="password123",
        timezone="UTC",
    )
    UserNotificationPreferences.objects.create(user=user, enabled=True, quiet_hours_enabled=False)
    return user


@pytest.fixture
def shared_goal(db, owner, participant_b, participant_c):
    goal = Goal.objects.create(
        created_by=owner,
        title="DSA Practice Together",
        status=Goal.Status.ACTIVE,
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.BINARY,
        is_shared=True,
        timezone="UTC",
        start_date=timezone.now().date(),
    )
    GoalParticipant.objects.create(
        goal=goal,
        user=owner,
        role=GoalParticipant.Role.OWNER,
        status=GoalParticipant.Status.ACTIVE,
    )
    GoalParticipant.objects.create(
        goal=goal,
        user=participant_b,
        role=GoalParticipant.Role.PARTICIPANT,
        status=GoalParticipant.Status.ACTIVE,
    )
    GoalParticipant.objects.create(
        goal=goal,
        user=participant_c,
        role=GoalParticipant.Role.PARTICIPANT,
        status=GoalParticipant.Status.ACTIVE,
    )
    return goal


@pytest.fixture
def chat_message(db, shared_goal, owner):
    return ChatMessage.objects.create(
        goal=shared_goal,
        sender=owner,
        body="Finished today's graph problem! 🚀",
    )


def build_chat_envelope(goal, msg, sender):
    return {
        "event_id": "test-event-123",
        "event_type": "goal.chat.message_created",
        "event_version": 1,
        "occurred_at": msg.created_at.isoformat(),
        "aggregate_type": "goal",
        "aggregate_id": str(goal.id),
        "payload": {
            "message_id": str(msg.id),
            "goal_id": str(goal.id),
            "sender_id": str(sender.id),
            "created_at": msg.created_at.isoformat(),
        },
    }


@pytest.mark.django_db
def test_chat_event_creates_reminders_for_active_recipients_excluding_sender(shared_goal, chat_message, owner, participant_b, participant_c):
    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    # Owner (sender) must NOT have a reminder
    assert not Reminder.objects.filter(user=owner, entity_id=shared_goal.id).exists()

    # Participant B and C must have reminders
    rem_b = Reminder.objects.filter(user=participant_b, entity_id=shared_goal.id).first()
    rem_c = Reminder.objects.filter(user=participant_c, entity_id=shared_goal.id).first()

    assert rem_b is not None
    assert rem_b.event_type == "goal.chat.message_created"
    assert rem_b.status == Reminder.ReminderStatus.SCHEDULED
    assert rem_b.identity_key == f"{participant_b.id}:GOAL:{shared_goal.id}:goal.chat.message_created:{chat_message.id}"

    assert rem_c is not None
    assert rem_c.event_type == "goal.chat.message_created"
    assert rem_c.status == Reminder.ReminderStatus.SCHEDULED
    assert rem_c.identity_key == f"{participant_c.id}:GOAL:{shared_goal.id}:goal.chat.message_created:{chat_message.id}"


@pytest.mark.django_db
def test_inactive_and_removed_participants_excluded(shared_goal, chat_message, owner, participant_b, participant_c):
    GoalParticipant.objects.filter(goal=shared_goal, user=participant_c).update(
        status=GoalParticipant.Status.REMOVED
    )

    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    assert Reminder.objects.filter(user=participant_b, entity_id=shared_goal.id).exists()
    assert not Reminder.objects.filter(user=participant_c, entity_id=shared_goal.id).exists()


@pytest.mark.django_db
def test_duplicate_kafka_event_is_idempotent(shared_goal, chat_message, owner, participant_b):
    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)
    count_after_first = Reminder.objects.filter(user=participant_b, entity_id=shared_goal.id).count()

    handle_goal_event(envelope)
    count_after_second = Reminder.objects.filter(user=participant_b, entity_id=shared_goal.id).count()

    assert count_after_first == 1
    assert count_after_second == 1


@pytest.mark.django_db
def test_dispatcher_dispatches_fcm_with_correct_payload(shared_goal, chat_message, owner, participant_b):
    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    device = UserDevice.objects.create(
        user=participant_b,
        fcm_token="fcm_token_b_1",
        device_id="device_b_1",
        platform="ANDROID",
        is_active=True,
    )

    mock_fcm = MockFcmClient()
    result = dispatch_due_reminders(fcm_client=mock_fcm)

    # Participant B had a device -> dispatched
    # Participant C had no device -> suppressed
    assert result.dispatched == 1
    assert result.suppressed == 1
    assert len(mock_fcm.sent_messages) == 1

    msg = mock_fcm.sent_messages[0]
    assert msg["token"] == "fcm_token_b_1"
    assert msg["title"] == "DSA Practice Together"
    assert msg["body"] == "Owner User: Finished today's graph problem! 🚀"
    assert msg["data"]["event_type"] == "goal.chat.message_created"
    assert msg["data"]["deep_link"] == f"promise://goal/{shared_goal.id}/chat"

    delivery = NotificationDelivery.objects.get(user_device=device)
    assert delivery.status == NotificationDelivery.DeliveryStatus.SENT


@pytest.mark.django_db
def test_dispatcher_suppresses_when_user_master_disabled(shared_goal, chat_message, owner, participant_b):
    UserNotificationPreferences.objects.filter(user=participant_b).update(enabled=False)
    UserDevice.objects.create(
        user=participant_b,
        fcm_token="fcm_token_b_disabled",
        device_id="device_b_dis",
        is_active=True,
    )

    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    mock_fcm = MockFcmClient()
    result = dispatch_due_reminders(fcm_client=mock_fcm)

    # Participant B disabled + Participant C no devices -> 2 suppressed
    assert result.suppressed == 2
    assert len(mock_fcm.sent_messages) == 0


@pytest.mark.django_db
def test_dispatcher_suppresses_when_recipient_no_longer_active(shared_goal, chat_message, owner, participant_b):
    UserDevice.objects.create(
        user=participant_b,
        fcm_token="fcm_token_b_left",
        device_id="device_b_left",
        is_active=True,
    )
    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    # Participant B leaves before dispatcher runs
    GoalParticipant.objects.filter(goal=shared_goal, user=participant_b).update(
        status=GoalParticipant.Status.LEFT
    )

    mock_fcm = MockFcmClient()
    result = dispatch_due_reminders(fcm_client=mock_fcm)

    # Participant B no longer active + Participant C no devices -> 2 suppressed
    assert result.suppressed == 2
    assert len(mock_fcm.sent_messages) == 0


@pytest.mark.django_db
def test_dispatcher_cancels_when_goal_completed_or_cancelled(shared_goal, chat_message, owner, participant_b):
    UserDevice.objects.create(
        user=participant_b,
        fcm_token="fcm_token_b_comp",
        device_id="device_b_comp",
        is_active=True,
    )
    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    # Goal is marked COMPLETED
    shared_goal.status = Goal.Status.COMPLETED
    shared_goal.save()

    mock_fcm = MockFcmClient()
    result = dispatch_due_reminders(fcm_client=mock_fcm)

    # Both B and C reminders cancelled
    assert result.cancelled == 2
    assert len(mock_fcm.sent_messages) == 0


@pytest.mark.django_db
def test_dispatcher_defers_during_quiet_hours(shared_goal, chat_message, owner, participant_b):
    # Set quiet hours to cover current time
    now_utc = timezone.now()
    local_time = now_utc.time()
    q_start = (datetime.datetime.combine(datetime.date.today(), local_time) - datetime.timedelta(hours=1)).time()
    q_end = (datetime.datetime.combine(datetime.date.today(), local_time) + datetime.timedelta(hours=2)).time()

    UserNotificationPreferences.objects.filter(user=participant_b).update(
        enabled=True,
        quiet_hours_enabled=True,
        quiet_hours_start=q_start,
        quiet_hours_end=q_end,
    )
    UserDevice.objects.create(
        user=participant_b,
        fcm_token="fcm_token_b_qh",
        device_id="device_b_qh",
        is_active=True,
    )

    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    mock_fcm = MockFcmClient()
    result = dispatch_due_reminders(fcm_client=mock_fcm)

    assert result.retried >= 1
    assert len(mock_fcm.sent_messages) == 0

    rem = Reminder.objects.get(user=participant_b, entity_id=shared_goal.id)
    assert rem.status == Reminder.ReminderStatus.SCHEDULED
    assert rem.scheduled_for > now_utc


@pytest.mark.django_db
def test_multi_device_recipient_creates_per_device_deliveries(shared_goal, chat_message, owner, participant_b):
    UserDevice.objects.create(
        user=participant_b,
        fcm_token="fcm_token_b_phone",
        device_id="device_b_phone",
        is_active=True,
    )
    UserDevice.objects.create(
        user=participant_b,
        fcm_token="fcm_token_b_tablet",
        device_id="device_b_tablet",
        is_active=True,
    )

    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    mock_fcm = MockFcmClient()
    result = dispatch_due_reminders(fcm_client=mock_fcm)

    assert result.dispatched == 1
    assert len(mock_fcm.sent_messages) == 2
    tokens = {m["token"] for m in mock_fcm.sent_messages}
    assert tokens == {"fcm_token_b_phone", "fcm_token_b_tablet"}
    assert NotificationDelivery.objects.filter(reminder__user=participant_b, status=NotificationDelivery.DeliveryStatus.SENT).count() == 2


@pytest.mark.django_db
def test_invalid_device_token_deactivates_device(shared_goal, chat_message, owner, participant_b):
    device = UserDevice.objects.create(
        user=participant_b,
        fcm_token="invalid_token_b",
        device_id="device_b_inv",
        is_active=True,
    )

    envelope = build_chat_envelope(shared_goal, chat_message, owner)
    handle_goal_event(envelope)

    mock_fcm = MockFcmClient()
    mock_fcm.unregistered_tokens.add("invalid_token_b")
    result = dispatch_due_reminders(fcm_client=mock_fcm)

    device.refresh_from_db()
    assert not device.is_active
    assert result.suppressed >= 1
