import asyncio
import datetime
import pytest
from asgiref.sync import sync_to_async
from channels.testing import WebsocketCommunicator
from django.db import transaction
from django.utils import timezone

from apps.authentication.services import _create_session_and_tokens
from apps.goals.consumers import (
    is_user_present_in_chat,
    update_presence,
    remove_presence,
)
from apps.goals.models import Goal, GoalParticipant
from apps.goals.services import (
    leave_goal,
    remove_participant,
    send_chat_message,
)
from apps.notifications.dispatcher import dispatch_due_reminders
from apps.notifications.fcm import MockFcmClient
from apps.notifications.models import UserDevice, UserNotificationPreferences
from apps.notifications.services import handle_goal_event
from apps.users.models import User
from config.asgi import application


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
def unrelated_user(db):
    return User.objects.create_user(
        email="unrelated@example.com",
        name="Unrelated User",
        password="password123",
        timezone="UTC",
    )


@pytest.fixture
def shared_goal(db, owner, participant_b):
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
    return goal


@pytest.fixture
def personal_goal(db, owner):
    return Goal.objects.create(
        created_by=owner,
        title="Personal Journaling",
        status=Goal.Status.ACTIVE,
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.BINARY,
        is_shared=False,
        timezone="UTC",
        start_date=timezone.now().date(),
    )


def create_token_for_user(user):
    return _create_session_and_tokens(user).access_token


@pytest.mark.django_db(transaction=True)
@pytest.mark.asyncio
async def test_websocket_handshake_authenticated_active_participant(shared_goal, participant_b):
    token = await sync_to_async(create_token_for_user)(participant_b)
    communicator = WebsocketCommunicator(
        application,
        f"/ws/goals/{shared_goal.id}/chat/",
        headers=[(b"authorization", f"Bearer {token}".encode("utf-8"))],
    )
    connected, subprotocol = await communicator.connect()
    assert connected

    # Ping / pong test
    await communicator.send_json_to({"type": "ping"})
    response = await communicator.receive_json_from()
    assert response == {"type": "pong"}

    await communicator.disconnect()


@pytest.mark.django_db(transaction=True)
@pytest.mark.asyncio
async def test_websocket_handshake_rejects_missing_or_invalid_auth(shared_goal):
    # No auth header
    communicator = WebsocketCommunicator(
        application,
        f"/ws/goals/{shared_goal.id}/chat/",
    )
    connected, _ = await communicator.connect()
    assert not connected

    # Invalid bearer token
    communicator2 = WebsocketCommunicator(
        application,
        f"/ws/goals/{shared_goal.id}/chat/",
        headers=[(b"authorization", b"Bearer invalid-jwt-token")],
    )
    connected2, _ = await communicator2.connect()
    assert not connected2


@pytest.mark.django_db(transaction=True)
@pytest.mark.asyncio
async def test_websocket_handshake_rejects_unrelated_user(shared_goal, unrelated_user):
    token = await sync_to_async(create_token_for_user)(unrelated_user)
    communicator = WebsocketCommunicator(
        application,
        f"/ws/goals/{shared_goal.id}/chat/",
        headers=[(b"authorization", f"Bearer {token}".encode("utf-8"))],
    )
    connected, _ = await communicator.connect()
    assert not connected


@pytest.mark.django_db(transaction=True)
@pytest.mark.asyncio
async def test_websocket_handshake_rejects_personal_goal(personal_goal, owner):
    token = await sync_to_async(create_token_for_user)(owner)
    communicator = WebsocketCommunicator(
        application,
        f"/ws/goals/{personal_goal.id}/chat/",
        headers=[(b"authorization", f"Bearer {token}".encode("utf-8"))],
    )
    connected, _ = await communicator.connect()
    assert not connected


@pytest.mark.django_db(transaction=True)
@pytest.mark.asyncio
async def test_websocket_handshake_rejects_left_or_removed_participant(shared_goal, participant_b):
    await sync_to_async(
        lambda: GoalParticipant.objects.filter(goal=shared_goal, user=participant_b).update(
            status=GoalParticipant.Status.REMOVED
        )
    )()
    token = await sync_to_async(create_token_for_user)(participant_b)
    communicator = WebsocketCommunicator(
        application,
        f"/ws/goals/{shared_goal.id}/chat/",
        headers=[(b"authorization", f"Bearer {token}".encode("utf-8"))],
    )
    connected, _ = await communicator.connect()
    assert not connected


@pytest.mark.django_db(transaction=True)
@pytest.mark.asyncio
async def test_send_chat_message_broadcasts_to_connected_participant(shared_goal, owner, participant_b):
    token_b = await sync_to_async(create_token_for_user)(participant_b)
    communicator = WebsocketCommunicator(
        application,
        f"/ws/goals/{shared_goal.id}/chat/",
        headers=[(b"authorization", f"Bearer {token_b}".encode("utf-8"))],
    )
    connected, _ = await communicator.connect()
    assert connected

    def do_send():
        with transaction.atomic():
            send_chat_message(
                sender=owner,
                goal_id=shared_goal.id,
                body="Live WebSocket message test! ⚡",
            )

    await sync_to_async(do_send)()

    # Participant B receives message.created
    event = await communicator.receive_json_from()
    assert event["type"] == "message.created"
    assert event["message"]["body"] == "Live WebSocket message test! ⚡"
    assert event["message"]["sender"]["name"] == "Owner User"

    await communicator.disconnect()


@pytest.mark.django_db(transaction=True)
@pytest.mark.asyncio
async def test_db_rollback_does_not_broadcast(shared_goal, owner, participant_b):
    token_b = await sync_to_async(create_token_for_user)(participant_b)
    communicator = WebsocketCommunicator(
        application,
        f"/ws/goals/{shared_goal.id}/chat/",
        headers=[(b"authorization", f"Bearer {token_b}".encode("utf-8"))],
    )
    connected, _ = await communicator.connect()
    assert connected

    def do_rollback():
        try:
            with transaction.atomic():
                send_chat_message(
                    sender=owner,
                    goal_id=shared_goal.id,
                    body="Rolled back message",
                )
                raise RuntimeError("Force Rollback")
        except RuntimeError:
            pass

    await sync_to_async(do_rollback)()

    # No message should be received
    with pytest.raises(asyncio.TimeoutError):
        await asyncio.wait_for(communicator.receive_json_from(), timeout=0.3)

    await communicator.disconnect()


@pytest.mark.django_db(transaction=True)
@pytest.mark.asyncio
async def test_participant_revocation_closes_socket(shared_goal, owner, participant_b):
    token_b = await sync_to_async(create_token_for_user)(participant_b)
    communicator = WebsocketCommunicator(
        application,
        f"/ws/goals/{shared_goal.id}/chat/",
        headers=[(b"authorization", f"Bearer {token_b}".encode("utf-8"))],
    )
    connected, _ = await communicator.connect()
    assert connected

    def do_remove():
        with transaction.atomic():
            remove_participant(actor=owner, goal_id=shared_goal.id, user_id=participant_b.id)

    await sync_to_async(do_remove)()

    # Participant B should receive error event and close
    event = await communicator.receive_json_from()
    assert event["type"] == "chat.error"
    assert event["code"] == "FORBIDDEN"

    await communicator.disconnect()


@pytest.mark.django_db
def test_active_presence_suppresses_fcm_notification(shared_goal, owner, participant_b):
    msg = send_chat_message(sender=owner, goal_id=shared_goal.id, body="Active presence test")
    envelope = {
        "event_id": "test-ev-1",
        "event_type": "goal.chat.message_created",
        "event_version": 1,
        "occurred_at": msg.created_at.isoformat(),
        "aggregate_type": "goal",
        "aggregate_id": str(shared_goal.id),
        "payload": {
            "message_id": str(msg.id),
            "goal_id": str(shared_goal.id),
            "sender_id": str(owner.id),
            "created_at": msg.created_at.isoformat(),
        },
    }
    handle_goal_event(envelope)

    UserDevice.objects.create(
        user=participant_b,
        fcm_token="fcm_token_b_presence",
        device_id="dev_b_pres",
        is_active=True,
    )

    # Set presence for participant B
    update_presence(str(shared_goal.id), str(participant_b.id))
    assert is_user_present_in_chat(str(shared_goal.id), str(participant_b.id))

    mock_fcm = MockFcmClient()
    result = dispatch_due_reminders(fcm_client=mock_fcm)

    # Participant B reminder suppressed due to presence
    assert result.suppressed >= 1
    assert len(mock_fcm.sent_messages) == 0

    # Remove presence and test that next reminder sends
    remove_presence(str(shared_goal.id), str(participant_b.id))
    assert not is_user_present_in_chat(str(shared_goal.id), str(participant_b.id))
