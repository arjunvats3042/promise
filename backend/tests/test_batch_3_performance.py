import uuid
import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.commitments.models import Commitment
from apps.commitments.services import create_commitment, get_visible_commitment
from apps.goals.models import Goal, GoalParticipant
from apps.goals.services import create_goal, get_visible_goal, list_visible_goals
from apps.goals.views import _serialize_goal_list
from rest_framework.test import APIRequestFactory

User = get_user_model()


@pytest.fixture
def user(db):
    return User.objects.create_user(
        email="perf@example.com",
        password="ValidPassword123!",
        name="Perf User",
        timezone="UTC",
    )


@pytest.fixture
def partner(db):
    return User.objects.create_user(
        email="partner@example.com",
        password="ValidPassword123!",
        name="Partner User",
        timezone="UTC",
    )


@pytest.mark.django_db
def test_goal_list_serialization_query_count_is_constant(django_assert_num_queries, user, partner):
    """Verify that serializing a list of 5 goals does NOT fire N+1 queries per goal."""
    goals = []
    for i in range(5):
        g = create_goal(
            creator=user,
            title=f"Goal {i}",
            recurrence_kind=Goal.RecurrenceKind.DAILY,
            is_shared=(i % 2 == 0),
        )
        if i % 2 == 0:
            GoalParticipant.objects.create(
                goal=g,
                user=partner,
                role=GoalParticipant.Role.PARTICIPANT,
                status=GoalParticipant.Status.ACTIVE,
                joined_at=timezone.now(),
            )
        goals.append(g)

    factory = APIRequestFactory()
    request = factory.get("/api/v1/goals/")
    request.user = user

    # Query count should be exactly 5 queries for the prefetch (goals, check_ins, events, participants, users),
    # and 0 additional queries during serialization!
    with django_assert_num_queries(5):
        qs = list_visible_goals(viewer=user)
        goals_list = list(qs)
        data = _serialize_goal_list(goals_list, request)
        assert len(data) == 5


@pytest.mark.django_db
def test_dual_id_lookup_executes_single_query_for_numeric_and_uuid(django_assert_num_queries, user):
    """Verify that both integer ID and UUID string lookup execute a single targeted query."""
    goal = create_goal(
        creator=user,
        title="Lookup Test Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
    )
    commitment = create_commitment(
        creator=user,
        title="Lookup Test Commitment",
    )

    # 1. Numeric ID lookup on Goal: 1 goal query + 4 prefetches
    with django_assert_num_queries(5):
        g1 = get_visible_goal(viewer=user, goal_id=str(goal.numeric_id))
        assert g1.id == goal.id

    # 2. UUID string lookup on Goal: 1 goal query + 4 prefetches
    with django_assert_num_queries(5):
        g2 = get_visible_goal(viewer=user, goal_id=str(goal.id))
        assert g2.id == goal.id

    # 3. Numeric ID lookup on Commitment: exactly 1 query
    with django_assert_num_queries(1):
        c1 = get_visible_commitment(viewer=user, commitment_id=str(commitment.numeric_id))
        assert c1.id == commitment.id

    # 4. UUID string lookup on Commitment: exactly 1 query
    with django_assert_num_queries(1):
        c2 = get_visible_commitment(viewer=user, commitment_id=str(commitment.id))
        assert c2.id == commitment.id
