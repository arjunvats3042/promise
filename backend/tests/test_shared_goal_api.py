import pytest
from django.urls import reverse
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from apps.goals.models import Goal, GoalCheckIn, GoalParticipant
from apps.goals.services import (
    accept_invitation,
    create_goal,
    create_shared_goal,
    invite_participant,
)
from apps.users.models import User


@pytest.fixture
def api_client():
    return APIClient()


@pytest.fixture
def owner_user(db):
    return User.objects.create_user(
        email="owner@example.com",
        name="Owner User",
        timezone="UTC",
        password="password123",
    )


@pytest.fixture
def participant_user(db):
    return User.objects.create_user(
        email="participant@example.com",
        name="Participant User",
        timezone="UTC",
        password="password123",
    )


@pytest.fixture
def third_user(db):
    return User.objects.create_user(
        email="third@example.com",
        name="Third User",
        timezone="UTC",
        password="password123",
    )


@pytest.mark.django_db
def test_create_and_detail_shared_goal_api(api_client, owner_user, participant_user):
    api_client.force_authenticate(user=owner_user)

    # 1. Create Shared Goal
    payload = {
        "title": "DSA Practice Group",
        "description": "Daily coding practice together",
        "timezone": "UTC",
        "recurrence_kind": Goal.RecurrenceKind.DAILY,
        "tracking_kind": Goal.TrackingKind.COUNT,
        "target_value": 5,
        "target_unit": "problems",
        "is_shared": True,
    }
    url = reverse("goals:collection")
    res = api_client.post(url, payload, format="json")
    assert res.status_code == status.HTTP_201_CREATED
    goal_id = res.data["id"]
    assert res.data["is_shared"] is True
    assert res.data["membership_role"] == GoalParticipant.Role.OWNER
    assert res.data["membership_status"] == GoalParticipant.Status.ACTIVE
    assert "collective_progress" in res.data
    assert len(res.data["participants"]) == 1

    # 2. Detail Shared Goal
    detail_url = reverse("goals:detail", kwargs={"goal_id": goal_id})
    detail_res = api_client.get(detail_url)
    assert detail_res.status_code == status.HTTP_200_OK
    assert detail_res.data["id"] == goal_id


@pytest.mark.django_db
def test_invited_user_sees_preview_only_until_accepted(api_client, owner_user, participant_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Invitation Preview Test",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)

    api_client.force_authenticate(user=participant_user)
    detail_url = reverse("goals:detail", kwargs={"goal_id": goal.id})
    res = api_client.get(detail_url)
    assert res.status_code == status.HTTP_200_OK
    # Preview serializer fields
    assert res.data["invitation_status"] == GoalParticipant.Status.INVITED
    assert "inviter_name" in res.data
    assert "collective_progress" not in res.data
    assert "participants" not in res.data

    # Accept invitation
    accept_url = reverse("goals:participants-accept", kwargs={"goal_id": goal.id})
    accept_res = api_client.post(accept_url)
    assert accept_res.status_code == status.HTTP_200_OK
    assert accept_res.data["membership_status"] == GoalParticipant.Status.ACTIVE
    assert "collective_progress" in accept_res.data


@pytest.mark.django_db
def test_participants_api_lifecycle(api_client, owner_user, participant_user, third_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Participants Lifecycle Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    api_client.force_authenticate(user=owner_user)

    # 1. Invite participant -> 201 Created
    invite_url = reverse("goals:participants", kwargs={"goal_id": goal.id})
    res1 = api_client.post(invite_url, {"user_id": str(participant_user.id)}, format="json")
    assert res1.status_code == status.HTTP_201_CREATED
    participant_id = res1.data["id"]

    # 2. Duplicate invite when pending -> 200 OK
    res_dup = api_client.post(invite_url, {"user_id": str(participant_user.id)}, format="json")
    assert res_dup.status_code == status.HTTP_200_OK
    assert res_dup.data["id"] == participant_id

    # 3. Accept
    api_client.force_authenticate(user=participant_user)
    accept_url = reverse("goals:participants-accept", kwargs={"goal_id": goal.id})
    accept_res = api_client.post(accept_url)
    assert accept_res.status_code == status.HTTP_200_OK

    # 4. Repeated invite after active -> 409 Conflict
    api_client.force_authenticate(user=owner_user)
    res_active = api_client.post(invite_url, {"user_id": str(participant_user.id)}, format="json")
    assert res_active.status_code == status.HTTP_409_CONFLICT
    assert res_active.data["error"]["code"] == "GOAL_ALREADY_PARTICIPANT"

    # 5. Remove participant
    delete_url = reverse("goals:participant-detail", kwargs={"goal_id": goal.id, "participant_id": participant_id})
    del_res = api_client.delete(delete_url)
    assert del_res.status_code == status.HTTP_200_OK
    assert del_res.data["status"] == GoalParticipant.Status.REMOVED


@pytest.mark.django_db
def test_leave_shared_goal_api(api_client, owner_user, participant_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Leave Test",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    accept_invitation(actor=participant_user, goal_id=goal.id)

    # Owner cannot leave -> 409
    api_client.force_authenticate(user=owner_user)
    leave_url = reverse("goals:leave", kwargs={"goal_id": goal.id})
    res_owner = api_client.post(leave_url)
    assert res_owner.status_code == status.HTTP_409_CONFLICT
    assert res_owner.data["error"]["code"] == "GOAL_OWNER_CANNOT_LEAVE"

    # Participant can leave -> 200
    api_client.force_authenticate(user=participant_user)
    res_part = api_client.post(leave_url)
    assert res_part.status_code == status.HTTP_200_OK
    assert res_part.data["status"] == GoalParticipant.Status.LEFT


@pytest.mark.django_db
def test_check_in_isolation_and_note_privacy(api_client, owner_user, participant_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Check-in Isolation Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    accept_invitation(actor=participant_user, goal_id=goal.id)

    today = timezone.now().date()

    # 1. Owner check-in
    api_client.force_authenticate(user=owner_user)
    ci_url = reverse("goals:check-ins", kwargs={"goal_id": goal.id})
    res_owner = api_client.post(
        ci_url,
        {"period_date": str(today), "status": GoalCheckIn.Status.COMPLETED, "note": "Owner secret thoughts"},
        format="json",
    )
    assert res_owner.status_code == status.HTTP_201_CREATED

    # 2. Participant check-in
    api_client.force_authenticate(user=participant_user)
    res_part = api_client.post(
        ci_url,
        {"period_date": str(today), "status": GoalCheckIn.Status.COMPLETED, "note": "Participant secret thoughts"},
        format="json",
    )
    assert res_part.status_code == status.HTTP_201_CREATED

    # 3. Participant listing check-ins: only sees own check-ins
    list_res = api_client.get(ci_url)
    assert list_res.status_code == status.HTTP_200_OK
    assert len(list_res.data["results"]) == 1
    assert list_res.data["results"][0]["note"] == "Participant secret thoughts"


@pytest.mark.django_db
def test_chat_messages_rest_api_full_flow(api_client, owner_user, participant_user, third_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Study Group Chat",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    accept_invitation(actor=participant_user, goal_id=goal.id)

    chat_url = reverse("goals:chat-messages", kwargs={"goal_id": goal.id})
    summary_url = reverse("goals:chat-summary", kwargs={"goal_id": goal.id})
    read_url = reverse("goals:chat-read", kwargs={"goal_id": goal.id})

    # 1. Unrelated user gets 404
    api_client.force_authenticate(user=third_user)
    assert api_client.get(chat_url).status_code == status.HTTP_404_NOT_FOUND
    assert api_client.post(chat_url, {"body": "Hi"}).status_code == status.HTTP_404_NOT_FOUND
    assert api_client.get(summary_url).status_code == status.HTTP_404_NOT_FOUND

    # 2. Owner sends message
    api_client.force_authenticate(user=owner_user)
    send_res = api_client.post(chat_url, {"body": "Welcome to the group! 🚀"}, format="json")
    assert send_res.status_code == status.HTTP_201_CREATED
    msg1_id = send_res.data["id"]
    assert send_res.data["body"] == "Welcome to the group! 🚀"
    assert send_res.data["sender"]["id"] == str(owner_user.id)
    assert send_res.data["sender"]["name"] == owner_user.name

    # 3. Validation: blank message -> 400
    assert api_client.post(chat_url, {"body": "   "}).status_code == status.HTTP_400_BAD_REQUEST

    # 4. Validation: > 2000 chars -> 400
    assert api_client.post(chat_url, {"body": "a" * 2001}).status_code == status.HTTP_400_BAD_REQUEST

    # 5. Participant checks summary: unread count = 1
    api_client.force_authenticate(user=participant_user)
    sum_res = api_client.get(summary_url)
    assert sum_res.status_code == status.HTTP_200_OK
    assert sum_res.data["unread_count"] == 1
    assert sum_res.data["latest_message"]["id"] == msg1_id

    # 6. Participant lists messages
    list_res = api_client.get(chat_url)
    assert list_res.status_code == status.HTTP_200_OK
    assert len(list_res.data) == 1
    assert list_res.data[0]["id"] == msg1_id

    # 7. Participant sends message
    send2_res = api_client.post(chat_url, {"body": "Excited to practice!"}, format="json")
    assert send2_res.status_code == status.HTTP_201_CREATED
    msg2_id = send2_res.data["id"]

    # 8. Owner marks msg2 as read
    api_client.force_authenticate(user=owner_user)
    sum_owner = api_client.get(summary_url)
    assert sum_owner.data["unread_count"] == 1

    read_res = api_client.post(read_url, {"last_read_message_id": msg2_id}, format="json")
    assert read_res.status_code == status.HTTP_200_OK
    assert read_res.data["last_read_message_id"] == str(msg2_id)

    sum_owner_after = api_client.get(summary_url)
    assert sum_owner_after.data["unread_count"] == 0


@pytest.mark.django_db
def test_personal_goal_chat_endpoints_return_404(api_client, owner_user):
    goal = create_goal(
        creator=owner_user,
        title="Personal Solitary Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        is_shared=False,
    )
    api_client.force_authenticate(user=owner_user)

    chat_url = reverse("goals:chat-messages", kwargs={"goal_id": goal.id})
    summary_url = reverse("goals:chat-summary", kwargs={"goal_id": goal.id})
    read_url = reverse("goals:chat-read", kwargs={"goal_id": goal.id})

    assert api_client.get(chat_url).status_code == status.HTTP_404_NOT_FOUND
    assert api_client.post(chat_url, {"body": "Hi"}).status_code == status.HTTP_404_NOT_FOUND
    assert api_client.get(summary_url).status_code == status.HTTP_404_NOT_FOUND
    assert api_client.post(read_url, {"last_read_message_id": "00000000-0000-0000-0000-000000000000"}).status_code == status.HTTP_404_NOT_FOUND
