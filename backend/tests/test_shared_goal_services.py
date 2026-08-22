import datetime
import pytest
from django.utils import timezone

from apps.goals.exceptions import (
    GoalAlreadyParticipantError,
    GoalCannotRemoveOwnerError,
    GoalChatInvalidCursorError,
    GoalChatInvalidMessageError,
    GoalInvalidParticipantStateError,
    GoalNotFoundError,
    GoalOwnerCannotLeaveError,
)
from apps.goals.models import ChatMessage, Goal, GoalCheckIn, GoalChatReadState, GoalEvent, GoalParticipant
from apps.goals.services import (
    accept_invitation,
    calculate_collective_progress,
    can_check_in,
    can_edit_goal,
    can_manage_members,
    can_send_chat,
    can_view_chat,
    can_view_goal,
    create_goal,
    create_shared_goal,
    decline_invitation,
    get_chat_summary,
    get_goal_membership,
    invite_participant,
    leave_goal,
    list_chat_messages,
    mark_chat_read,
    record_check_in,
    remove_participant,
    send_chat_message,
)
from apps.outbox.models import OutboxEvent
from apps.users.models import User


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
def test_create_shared_goal_provisions_owner_participant(owner_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Shared Meditation",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.BINARY,
    )
    assert goal.is_shared is True
    membership = get_goal_membership(owner_user, goal)
    assert membership is not None
    assert membership.role == GoalParticipant.Role.OWNER
    assert membership.status == GoalParticipant.Status.ACTIVE

    assert can_view_goal(owner_user, goal) is True
    assert can_edit_goal(owner_user, goal) is True
    assert can_manage_members(owner_user, goal) is True
    assert can_check_in(owner_user, goal) is True
    assert can_view_chat(owner_user, goal) is True
    assert can_send_chat(owner_user, goal) is True


@pytest.mark.django_db
def test_invitation_lifecycle(owner_user, participant_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Shared Book Reading",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )

    # 1. Invite
    part = invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    assert part.status == GoalParticipant.Status.INVITED
    assert part.role == GoalParticipant.Role.PARTICIPANT
    assert can_check_in(participant_user, goal) is False
    assert can_view_chat(participant_user, goal) is False

    # 2. Duplicate invite is idempotent
    part_dup = invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    assert part_dup.id == part.id

    # 3. Accept
    part_accepted = accept_invitation(actor=participant_user, goal_id=goal.id)
    assert part_accepted.status == GoalParticipant.Status.ACTIVE
    assert can_check_in(participant_user, goal) is True
    assert can_view_chat(participant_user, goal) is True

    # 4. Leave
    part_left = leave_goal(actor=participant_user, goal_id=goal.id)
    assert part_left.status == GoalParticipant.Status.LEFT
    assert can_check_in(participant_user, goal) is False
    assert can_view_chat(participant_user, goal) is False


@pytest.mark.django_db
def test_owner_protections_and_restrictions(owner_user, participant_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Fitness Challenge",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    accept_invitation(actor=participant_user, goal_id=goal.id)

    # Owner cannot leave
    with pytest.raises(GoalOwnerCannotLeaveError):
        leave_goal(actor=owner_user, goal_id=goal.id)

    # Participant cannot remove owner
    with pytest.raises(GoalNotFoundError):
        remove_participant(actor=participant_user, goal_id=goal.id, user_id=owner_user.id)

    # Owner cannot remove self
    with pytest.raises(GoalCannotRemoveOwnerError):
        remove_participant(actor=owner_user, goal_id=goal.id, user_id=owner_user.id)

    # Cannot self-invite
    with pytest.raises(GoalInvalidParticipantStateError):
        invite_participant(actor=owner_user, goal_id=goal.id, user_id=owner_user.id)


@pytest.mark.django_db
def test_check_ins_independent_and_attributed(owner_user, participant_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Daily Code Practice",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=5,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    accept_invitation(actor=participant_user, goal_id=goal.id)

    today = timezone.now().date()

    # Owner checks in 5 problems
    ci_owner = record_check_in(
        actor=owner_user,
        goal_id=goal.id,
        period_date=today,
        status=GoalCheckIn.Status.COMPLETED,
        value=5,
    )
    assert ci_owner.created_by == owner_user
    assert ci_owner.participant.user == owner_user

    # Participant checks in 3 problems
    ci_part = record_check_in(
        actor=participant_user,
        goal_id=goal.id,
        period_date=today,
        status=GoalCheckIn.Status.COMPLETED,
        value=3,
    )
    assert ci_part.created_by == participant_user
    assert ci_part.participant.user == participant_user

    # Both check-ins exist independently
    assert GoalCheckIn.objects.filter(goal=goal, period_date=today).count() == 2


@pytest.mark.django_db
def test_collective_progress_calculation(owner_user, participant_user, third_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Group Daily Steps",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=10000,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    accept_invitation(actor=participant_user, goal_id=goal.id)

    # Third user invited but not active -> should be excluded from collective required count
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=third_user.id)

    today = timezone.now().date()

    # Owner: 10,000 steps (completed)
    record_check_in(
        actor=owner_user,
        goal_id=goal.id,
        period_date=today,
        status=GoalCheckIn.Status.COMPLETED,
        value=10000,
    )
    # Participant: 8,000 steps
    record_check_in(
        actor=participant_user,
        goal_id=goal.id,
        period_date=today,
        status=GoalCheckIn.Status.COMPLETED,
        value=8000,
    )

    prog = calculate_collective_progress(goal, as_of_date=today)
    curr = prog["current_period"]

    assert curr["required_participants"] == 2
    assert curr["completed_participants"] == 1
    assert curr["value_sum"] == 10000
    assert curr["target_sum"] == 20000


@pytest.mark.django_db
def test_chat_service_full_lifecycle(owner_user, participant_user, third_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Study Squad",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    accept_invitation(actor=participant_user, goal_id=goal.id)

    # 1. Validation: blank message rejected
    with pytest.raises(GoalChatInvalidMessageError):
        send_chat_message(sender=owner_user, goal_id=goal.id, body="   ")

    # 2. Validation: > 2000 chars rejected
    with pytest.raises(GoalChatInvalidMessageError):
        send_chat_message(sender=owner_user, goal_id=goal.id, body="a" * 2001)

    # 3. Privacy: Unrelated user cannot view or send chat
    with pytest.raises(GoalNotFoundError):
        send_chat_message(sender=third_user, goal_id=goal.id, body="Hello")
    with pytest.raises(GoalNotFoundError):
        list_chat_messages(viewer=third_user, goal_id=goal.id)

    # 4. Send messages
    msg1 = send_chat_message(sender=owner_user, goal_id=goal.id, body="Welcome everyone! 🎉")
    assert msg1.body == "Welcome everyone! 🎉"
    assert msg1.sender == owner_user

    # Outbox event written
    outbox_event = OutboxEvent.objects.filter(
        aggregate_type="goal",
        aggregate_id=goal.id,
        event_type="goal.chat.message_created",
    ).first()
    assert outbox_event is not None
    assert outbox_event.payload["message_id"] == str(msg1.id)
    assert outbox_event.payload["sender_id"] == str(owner_user.id)

    # Participant hasn't read msg1 yet -> unread_count = 1
    summary_part_before = get_chat_summary(viewer=participant_user, goal_id=goal.id)
    assert summary_part_before["unread_count"] == 1
    assert summary_part_before["latest_message"].id == msg1.id

    msg2 = send_chat_message(sender=participant_user, goal_id=goal.id, body="Excited to be here!")
    assert msg2.body == "Excited to be here!"

    # 5. List messages
    messages = list_chat_messages(viewer=participant_user, goal_id=goal.id)
    assert len(messages) == 2
    assert messages[0].id == msg1.id
    assert messages[1].id == msg2.id

    # 6. Read state for owner: owner has not read msg2 yet -> unread_count = 1
    summary_owner = get_chat_summary(viewer=owner_user, goal_id=goal.id)
    assert summary_owner["unread_count"] == 1
    assert summary_owner["latest_message"].id == msg2.id

    # Mark msg2 as read by owner
    read_state = mark_chat_read(viewer=owner_user, goal_id=goal.id, last_read_message_id=msg2.id)
    assert read_state.last_read_message == msg2

    summary_owner_after = get_chat_summary(viewer=owner_user, goal_id=goal.id)
    assert summary_owner_after["unread_count"] == 0


@pytest.mark.django_db
def test_chat_read_cursor_monotonicity(owner_user, participant_user):
    goal = create_shared_goal(
        creator=owner_user,
        title="Monotonic Cursor Test",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    invite_participant(actor=owner_user, goal_id=goal.id, user_id=participant_user.id)
    accept_invitation(actor=participant_user, goal_id=goal.id)

    msg1 = send_chat_message(sender=owner_user, goal_id=goal.id, body="Msg 1")
    msg2 = send_chat_message(sender=owner_user, goal_id=goal.id, body="Msg 2")

    # Mark msg2 as read
    rs = mark_chat_read(viewer=participant_user, goal_id=goal.id, last_read_message_id=msg2.id)
    assert rs.last_read_message == msg2

    # Attempt to mark earlier msg1 as read -> cursor must NOT move backward
    rs_backward = mark_chat_read(viewer=participant_user, goal_id=goal.id, last_read_message_id=msg1.id)
    assert rs_backward.last_read_message == msg2


@pytest.mark.django_db
def test_personal_goal_chat_returns_404_for_privacy(owner_user):
    goal = create_goal(
        creator=owner_user,
        title="Personal Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        is_shared=False,
    )
    with pytest.raises(GoalNotFoundError):
        send_chat_message(sender=owner_user, goal_id=goal.id, body="Hello")
    with pytest.raises(GoalNotFoundError):
        list_chat_messages(viewer=owner_user, goal_id=goal.id)
    with pytest.raises(GoalNotFoundError):
        get_chat_summary(viewer=owner_user, goal_id=goal.id)
