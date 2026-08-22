import datetime
import uuid
import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient

from apps.goals.models import Goal, GoalCheckIn, GoalEvent, GoalParticipant
from apps.goals.services import (
    accept_invitation,
    complete_goal,
    create_goal,
    invite_participant,
    leave_goal,
    pause_goal,
    record_check_in,
    remove_participant,
    resume_goal,
)

User = get_user_model()


@pytest.fixture
def owner(db):
    return User.objects.create_user(
        email="owner@example.com",
        password="password123",
        name="Arjun Owner",
    )


@pytest.fixture
def member(db):
    return User.objects.create_user(
        email="member@example.com",
        password="password123",
        name="Rahul Member",
    )


@pytest.fixture
def third_user(db):
    return User.objects.create_user(
        email="third@example.com",
        password="password123",
        name="Ishika Third",
    )


@pytest.fixture
def unrelated_user(db):
    return User.objects.create_user(
        email="stranger@example.com",
        password="password123",
        name="Stranger",
    )


@pytest.fixture
def shared_goal(owner, member):
    goal = create_goal(
        creator=owner,
        title="Morning Workout",
        description="Daily shared habit",
        timezone="UTC",
        start_date=datetime.date(2026, 8, 1),
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.BINARY,
        is_shared=True,
    )
    invite_participant(actor=owner, goal_id=goal.id, user_id=member.id)
    accept_invitation(actor=member, goal_id=goal.id)
    return goal


@pytest.mark.django_db
def test_activity_list_contains_expected_events(owner, member, shared_goal):
    # Member records a checkin with a private note
    record_check_in(
        actor=member,
        goal_id=shared_goal.id,
        period_date=datetime.date(2026, 8, 22),
        status=GoalCheckIn.Status.COMPLETED,
        note="TOP SECRET PRIVATE NOTE - DO NOT EXPOSE",
    )
    # Owner pauses and resumes
    pause_goal(actor=owner, goal_id=shared_goal.id)
    resume_goal(actor=owner, goal_id=shared_goal.id)

    client = APIClient()
    client.force_authenticate(user=owner)

    url = f"/api/v1/goals/{shared_goal.id}/activity/"
    response = client.get(url)
    assert response.status_code == 200

    items = response.json()
    assert len(items) >= 4  # created, joined, checkin, paused, resumed

    # Verify descending ordering by created_at
    created_ats = [item["created_at"] for item in items]
    assert created_ats == sorted(created_ats, reverse=True)

    # Verify summaries
    summaries = [item["summary"] for item in items]
    assert "Arjun Owner resumed the goal" in summaries[0]
    assert "Arjun Owner paused the goal" in summaries[1]
    assert "Rahul Member completed today's practice" in summaries[2]
    assert "Rahul Member joined the goal" in summaries[3]

    # Verify NO private notes are present anywhere in payload
    response_str = str(response.json())
    assert "TOP SECRET PRIVATE NOTE" not in response_str


@pytest.mark.django_db
def test_activity_count_goal_formats_value_and_unit(owner, member):
    count_goal = create_goal(
        creator=owner,
        title="Read Pages",
        description="Reading challenge",
        timezone="UTC",
        start_date=datetime.date(2026, 8, 1),
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.COUNT,
        target_value=20,
        target_unit="pages",
        is_shared=True,
    )
    invite_participant(actor=owner, goal_id=count_goal.id, user_id=member.id)
    accept_invitation(actor=member, goal_id=count_goal.id)

    record_check_in(
        actor=member,
        goal_id=count_goal.id,
        period_date=datetime.date(2026, 8, 22),
        status=GoalCheckIn.Status.COMPLETED,
        value=15,
    )

    client = APIClient()
    client.force_authenticate(user=member)

    response = client.get(f"/api/v1/goals/{count_goal.id}/activity/")
    assert response.status_code == 200
    items = response.json()

    checkin_item = next(it for it in items if it["event_type"] == "CHECKIN_RECORDED")
    assert checkin_item["summary"] == "Rahul Member checked in with 15 pages"


@pytest.mark.django_db
def test_activity_participant_removed_formats_target_and_actor(owner, member, third_user, shared_goal):
    invite_participant(actor=owner, goal_id=shared_goal.id, user_id=third_user.id)
    accept_invitation(actor=third_user, goal_id=shared_goal.id)

    part = GoalParticipant.objects.get(goal=shared_goal, user=third_user)
    remove_participant(actor=owner, goal_id=shared_goal.id, participant_id=part.id)

    client = APIClient()
    client.force_authenticate(user=member)

    response = client.get(f"/api/v1/goals/{shared_goal.id}/activity/")
    assert response.status_code == 200
    items = response.json()

    removed_item = next(it for it in items if it["event_type"] == "PARTICIPANT_REMOVED")
    assert removed_item["summary"] == "Ishika Third was removed by Arjun Owner"
    assert removed_item["actor"]["name"] == "Arjun Owner"
    assert removed_item["target_user"]["name"] == "Ishika Third"


@pytest.mark.django_db
def test_activity_personal_goal_returns_404(owner):
    personal = create_goal(
        creator=owner,
        title="Solo Meditation",
        description="",
        timezone="UTC",
        start_date=datetime.date(2026, 8, 1),
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.BINARY,
        is_shared=False,
    )

    client = APIClient()
    client.force_authenticate(user=owner)

    response = client.get(f"/api/v1/goals/{personal.id}/activity/")
    assert response.status_code == 404


@pytest.mark.django_db
def test_activity_unrelated_user_returns_404(unrelated_user, shared_goal):
    client = APIClient()
    client.force_authenticate(user=unrelated_user)

    response = client.get(f"/api/v1/goals/{shared_goal.id}/activity/")
    assert response.status_code == 404


@pytest.mark.django_db
def test_activity_removed_user_immediately_loses_access(owner, member, shared_goal):
    part = GoalParticipant.objects.get(goal=shared_goal, user=member)
    remove_participant(actor=owner, goal_id=shared_goal.id, participant_id=part.id)

    client = APIClient()
    client.force_authenticate(user=member)

    response = client.get(f"/api/v1/goals/{shared_goal.id}/activity/")
    assert response.status_code == 404


@pytest.mark.django_db
def test_activity_left_user_immediately_loses_access(member, shared_goal):
    leave_goal(actor=member, goal_id=shared_goal.id)

    client = APIClient()
    client.force_authenticate(user=member)

    response = client.get(f"/api/v1/goals/{shared_goal.id}/activity/")
    assert response.status_code == 404


@pytest.mark.django_db
def test_activity_cursor_pagination(owner, shared_goal):
    # Create several events
    for day in range(1, 10):
        record_check_in(
            actor=owner,
            goal_id=shared_goal.id,
            period_date=datetime.date(2026, 8, day),
            status=GoalCheckIn.Status.COMPLETED,
        )

    client = APIClient()
    client.force_authenticate(user=owner)

    # Page 1 (limit 3)
    p1 = client.get(f"/api/v1/goals/{shared_goal.id}/activity/?limit=3")
    assert p1.status_code == 200
    items_p1 = p1.json()
    assert len(items_p1) == 3

    last_item = items_p1[-1]
    # Page 2 using cursor
    p2 = client.get(
        f"/api/v1/goals/{shared_goal.id}/activity/?limit=3&before_created_at={last_item['created_at']}&before_id={last_item['id']}"
    )
    assert p2.status_code == 200
    items_p2 = p2.json()
    assert len(items_p2) == 3

    p1_ids = {it["id"] for it in items_p1}
    p2_ids = {it["id"] for it in items_p2}
    assert len(p1_ids.intersection(p2_ids)) == 0
