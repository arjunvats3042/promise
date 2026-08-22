import datetime
import uuid
import pytest
from django.core.exceptions import ValidationError
from django.db import IntegrityError
from django.utils import timezone

from apps.goals.models import ChatMessage, Goal, GoalCheckIn, GoalChatReadState, GoalParticipant
from apps.goals.services import create_goal, invite_participant, record_check_in
from apps.users.models import User


@pytest.fixture
def test_user(db):
    return User.objects.create_user(
        email="owner@example.com",
        name="Goal Owner",
        timezone="UTC",
        password="correct-horse-battery-staple",
    )


@pytest.fixture
def other_user(db):
    return User.objects.create_user(
        email="participant@example.com",
        name="Goal Participant",
        timezone="UTC",
        password="correct-horse-battery-staple",
    )


@pytest.fixture
def third_user(db):
    return User.objects.create_user(
        email="third@example.com",
        name="Third Participant",
        timezone="UTC",
        password="correct-horse-battery-staple",
    )


@pytest.mark.django_db
def test_personal_goal_automatically_creates_owner_participant(test_user):
    goal = create_goal(
        creator=test_user,
        title="Personal DSA Practice",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.BINARY,
    )

    assert goal.is_shared is False
    participants = GoalParticipant.objects.filter(goal=goal)
    assert participants.count() == 1

    owner_part = participants.first()
    assert owner_part.user == test_user
    assert owner_part.role == GoalParticipant.Role.OWNER
    assert owner_part.status == GoalParticipant.Status.ACTIVE


@pytest.mark.django_db
def test_shared_goal_participant_uniqueness(test_user, other_user):
    goal = create_goal(
        creator=test_user,
        title="Shared DSA Practice",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )

    part1 = invite_participant(actor=test_user, goal_id=goal.id, user_id=other_user.id)
    assert part1.user == other_user
    assert part1.role == GoalParticipant.Role.PARTICIPANT

    goal.refresh_from_db()
    assert goal.is_shared is True

    # Duplicate creation triggers IntegrityError due to uniq_goal_participant_user
    with pytest.raises(IntegrityError):
        GoalParticipant.objects.create(
            goal=goal,
            user=other_user,
            role=GoalParticipant.Role.PARTICIPANT,
            status=GoalParticipant.Status.ACTIVE,
        )


@pytest.mark.django_db
def test_check_in_participant_attribution(test_user, other_user):
    goal = create_goal(
        creator=test_user,
        title="Daily Meditation",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    participant = GoalParticipant.objects.get(goal=goal, user=test_user)

    now = timezone.now()
    check_in = GoalCheckIn.objects.create(
        goal=goal,
        participant=participant,
        created_by=test_user,
        period_date=datetime.date(2026, 8, 22),
        status=GoalCheckIn.Status.COMPLETED,
        checked_at=now,
    )

    assert check_in.participant == participant
    assert check_in.created_by == test_user
    assert check_in.participant.user == check_in.created_by

    # Uniqueness constraint (goal, participant, period_date)
    with pytest.raises(IntegrityError):
        GoalCheckIn.objects.create(
            goal=goal,
            participant=participant,
            created_by=test_user,
            period_date=datetime.date(2026, 8, 22),
            status=GoalCheckIn.Status.COMPLETED,
            checked_at=now,
        )


@pytest.mark.django_db
def test_chat_message_creation_and_ordering(test_user, other_user):
    goal = create_goal(
        creator=test_user,
        title="Team Fitness Challenge",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    goal.is_shared = True
    goal.save()

    msg1 = ChatMessage.objects.create(
        goal=goal,
        sender=test_user,
        body="Welcome to the shared goal!",
    )
    msg2 = ChatMessage.objects.create(
        goal=goal,
        sender=other_user,
        body="Happy to be here! 🔥",
    )

    messages = list(ChatMessage.objects.filter(goal=goal).order_by("created_at", "id"))
    assert len(messages) == 2
    assert messages[0].id == msg1.id
    assert messages[1].id == msg2.id
    assert "🔥" in messages[1].body


@pytest.mark.django_db
def test_chat_read_state_uniqueness(test_user, other_user):
    goal = create_goal(
        creator=test_user,
        title="Study Group",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    goal.is_shared = True
    goal.save()

    msg1 = ChatMessage.objects.create(
        goal=goal,
        sender=test_user,
        body="First message",
    )

    read_state = GoalChatReadState.objects.create(
        goal=goal,
        user=other_user,
        last_read_message=msg1,
        last_read_at=timezone.now(),
    )
    assert read_state.last_read_message == msg1

    # Unique constraint (goal, user) prevents duplicate read state rows
    with pytest.raises(IntegrityError):
        GoalChatReadState.objects.create(
            goal=goal,
            user=other_user,
            last_read_message=msg1,
            last_read_at=timezone.now(),
        )


@pytest.mark.django_db
def test_goal_deletion_cascades_participants_chat_and_read_states(test_user, other_user):
    goal = create_goal(
        creator=test_user,
        title="Temporary Practice",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    goal.is_shared = True
    goal.save()

    part = GoalParticipant.objects.create(
        goal=goal,
        user=other_user,
        role=GoalParticipant.Role.PARTICIPANT,
        status=GoalParticipant.Status.ACTIVE,
    )
    msg = ChatMessage.objects.create(
        goal=goal,
        sender=test_user,
        body="Hello world",
    )
    read_state = GoalChatReadState.objects.create(
        goal=goal,
        user=other_user,
        last_read_message=msg,
        last_read_at=timezone.now(),
    )

    goal_id = goal.id
    goal.delete()

    assert GoalParticipant.objects.filter(goal_id=goal_id).count() == 0
    assert ChatMessage.objects.filter(goal_id=goal_id).count() == 0
    assert GoalChatReadState.objects.filter(goal_id=goal_id).count() == 0


@pytest.mark.django_db
def test_backfill_distinguishes_personal_and_shared_goals(test_user, other_user):
    import importlib
    from django.apps import apps

    migration_mod = importlib.import_module(
        "apps.goals.migrations.0007_goal_is_shared_chatmessage_goalchatreadstate_and_more"
    )
    backfill_is_shared = migration_mod.backfill_is_shared

    # 1. Personal goal with OWNER participant only
    personal_goal = Goal.objects.create(
        created_by=test_user,
        title="Personal Running",
        timezone="UTC",
        start_date=datetime.date(2026, 8, 22),
        is_shared=False,
    )
    GoalParticipant.objects.create(
        goal=personal_goal,
        user=test_user,
        role=GoalParticipant.Role.OWNER,
        status=GoalParticipant.Status.ACTIVE,
    )

    # 2. Shared goal with OWNER + PARTICIPANT
    shared_goal = Goal.objects.create(
        created_by=test_user,
        title="Group Running",
        timezone="UTC",
        start_date=datetime.date(2026, 8, 22),
        is_shared=False,
    )
    GoalParticipant.objects.create(
        goal=shared_goal,
        user=test_user,
        role=GoalParticipant.Role.OWNER,
        status=GoalParticipant.Status.ACTIVE,
    )
    GoalParticipant.objects.create(
        goal=shared_goal,
        user=other_user,
        role=GoalParticipant.Role.PARTICIPANT,
        status=GoalParticipant.Status.ACTIVE,
    )

    # Execute backfill
    backfill_is_shared(apps, None)

    personal_goal.refresh_from_db()
    shared_goal.refresh_from_db()

    # Personal goal must remain is_shared=False
    assert personal_goal.is_shared is False

    # Genuinely shared goal must become is_shared=True
    assert shared_goal.is_shared is True


@pytest.mark.django_db
def test_multi_participant_check_in_isolation(test_user, other_user):
    goal = create_goal(
        creator=test_user,
        title="Shared Book Club",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    owner_part = GoalParticipant.objects.get(goal=goal, user=test_user)
    other_part = invite_participant(actor=test_user, goal_id=goal.id, user_id=other_user.id)
    other_part.status = GoalParticipant.Status.ACTIVE
    other_part.save(update_fields=["status"])

    target_date = datetime.date(2026, 8, 22)
    now = timezone.now()

    # Owner checks in
    ci_owner = GoalCheckIn.objects.create(
        goal=goal,
        participant=owner_part,
        created_by=test_user,
        period_date=target_date,
        status=GoalCheckIn.Status.COMPLETED,
        checked_at=now,
    )

    # Other participant checks in for the exact same date
    ci_other = GoalCheckIn.objects.create(
        goal=goal,
        participant=other_part,
        created_by=other_user,
        period_date=target_date,
        status=GoalCheckIn.Status.COMPLETED,
        checked_at=now,
    )

    assert ci_owner.participant == owner_part
    assert ci_owner.created_by == test_user
    assert ci_other.participant == other_part
    assert ci_other.created_by == other_user

    # Total check-ins for the day = 2 without uniqueness constraint conflict
    assert GoalCheckIn.objects.filter(goal=goal, period_date=target_date).count() == 2
