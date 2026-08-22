import uuid
from datetime import timedelta
from django.contrib.auth import get_user_model
from django.utils import timezone
import pytest
from rest_framework import status
from rest_framework.test import APIClient

from apps.goals.exceptions import (
    GoalCannotTransferOwnershipToSelfError,
    GoalInviteExpiredError,
    GoalOwnerRequiredError,
    GoalParticipantLimitReachedError,
)
from apps.goals.models import Goal, GoalCheckIn, GoalEvent, GoalParticipant
from apps.goals.services import (
    MAX_SHARED_GOAL_PARTICIPANTS,
    accept_invitation,
    create_shared_goal,
    decline_invitation,
    invite_participant,
    leave_goal,
    record_check_in,
    reinvite_participant,
    remove_participant,
    revoke_invitation,
    shared_goal_group_summary,
    shared_goal_milestones,
    shared_goal_weekly_reflection,
    transfer_goal_ownership,
)

User = get_user_model()


@pytest.fixture
def api_client():
    return APIClient()


@pytest.fixture
def owner_user(db):
    return User.objects.create_user(
        email="owner@test.com",
        password="ValidPassword123!",
        name="Owner User",
    )


@pytest.fixture
def participant_user_1(db):
    return User.objects.create_user(
        email="p1@test.com",
        password="ValidPassword123!",
        name="Participant One",
    )


@pytest.fixture
def participant_user_2(db):
    return User.objects.create_user(
        email="p2@test.com",
        password="ValidPassword123!",
        name="Participant Two",
    )


@pytest.fixture
def shared_goal(owner_user):
    return create_shared_goal(
        creator=owner_user,
        title="Morning Meditation",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        tracking_kind=Goal.TrackingKind.BINARY,
    )


@pytest.mark.django_db
class TestOwnershipTransfer:
    def test_owner_can_transfer_ownership_to_active_participant(self, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        accept_invitation(actor=participant_user_1, goal_id=shared_goal.id)

        transfer_goal_ownership(actor=owner_user, goal_id=shared_goal.id, target_participant_id=p1.id)

        shared_goal.refresh_from_db()
        assert shared_goal.created_by_id == participant_user_1.id

        old_owner_row = GoalParticipant.objects.get(goal=shared_goal, user=owner_user)
        assert old_owner_row.role == GoalParticipant.Role.PARTICIPANT
        assert old_owner_row.status == GoalParticipant.Status.ACTIVE

        new_owner_row = GoalParticipant.objects.get(goal=shared_goal, user=participant_user_1)
        assert new_owner_row.role == GoalParticipant.Role.OWNER
        assert new_owner_row.status == GoalParticipant.Status.ACTIVE

        assert GoalParticipant.objects.filter(goal=shared_goal, role=GoalParticipant.Role.OWNER, status=GoalParticipant.Status.ACTIVE).count() == 1

        event = GoalEvent.objects.filter(goal=shared_goal, event_type=GoalEvent.EventType.GOAL_OWNERSHIP_TRANSFERRED).latest("created_at")
        assert event.actor_id == owner_user.id
        assert event.metadata["new_owner_id"] == str(participant_user_1.id)

    def test_non_owner_cannot_transfer_ownership(self, owner_user, participant_user_1, participant_user_2, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        accept_invitation(actor=participant_user_1, goal_id=shared_goal.id)

        p2 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_2.id)
        accept_invitation(actor=participant_user_2, goal_id=shared_goal.id)

        with pytest.raises(GoalOwnerRequiredError):
            transfer_goal_ownership(actor=participant_user_1, goal_id=shared_goal.id, target_participant_id=p2.id)

    def test_owner_cannot_transfer_to_self(self, owner_user, shared_goal):
        owner_part = GoalParticipant.objects.get(goal=shared_goal, user=owner_user)
        with pytest.raises(GoalCannotTransferOwnershipToSelfError):
            transfer_goal_ownership(actor=owner_user, goal_id=shared_goal.id, target_participant_id=owner_part.id)

    def test_ownership_transfer_api_endpoint(self, api_client, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        accept_invitation(actor=participant_user_1, goal_id=shared_goal.id)

        api_client.force_authenticate(user=owner_user)
        response = api_client.post(
            f"/api/v1/goals/{shared_goal.id}/ownership/transfer/",
            {"participant_id": str(p1.id)},
            format="json",
        )
        assert response.status_code == status.HTTP_200_OK
        assert response.data["membership_role"] == GoalParticipant.Role.PARTICIPANT


@pytest.mark.django_db
class TestInvitationLifecycleAndExpiry:
    def test_invitation_has_7_day_expiry(self, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        assert p1.invitation_expires_at is not None
        assert p1.invitation_expires_at > timezone.now()
        assert p1.invitation_expires_at <= timezone.now() + timedelta(days=7, seconds=5)

    def test_expired_invitation_cannot_be_accepted(self, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        p1.invitation_expires_at = timezone.now() - timedelta(minutes=5)
        p1.save(update_fields=["invitation_expires_at"])

        with pytest.raises(GoalInviteExpiredError):
            accept_invitation(actor=participant_user_1, goal_id=shared_goal.id)

    def test_reinvite_declined_participant(self, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        decline_invitation(actor=participant_user_1, goal_id=shared_goal.id)

        reinvited = reinvite_participant(actor=owner_user, goal_id=shared_goal.id, participant_id=p1.id)
        assert reinvited.status == GoalParticipant.Status.INVITED
        assert reinvited.invitation_expires_at > timezone.now()

        event = GoalEvent.objects.filter(goal=shared_goal, event_type=GoalEvent.EventType.PARTICIPANT_REINVITED).latest("created_at")
        assert event.actor_id == owner_user.id

    def test_reinvite_removed_participant(self, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        accept_invitation(actor=participant_user_1, goal_id=shared_goal.id)
        remove_participant(actor=owner_user, goal_id=shared_goal.id, participant_id=p1.id)

        reinvited = reinvite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        assert reinvited.status == GoalParticipant.Status.INVITED
        assert reinvited.invitation_expires_at > timezone.now()

    def test_reinvite_api_endpoint(self, api_client, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        decline_invitation(actor=participant_user_1, goal_id=shared_goal.id)

        api_client.force_authenticate(user=owner_user)
        response = api_client.post(
            f"/api/v1/goals/{shared_goal.id}/participants/reinvite/",
            {"user_id": str(participant_user_1.id)},
            format="json",
        )
        assert response.status_code == status.HTTP_200_OK
        assert response.data["status"] == GoalParticipant.Status.INVITED


@pytest.mark.django_db
class TestParticipantCapacity:
    def test_max_10_participants_enforced_on_invite(self, owner_user, shared_goal):
        for i in range(1, 10):
            user = User.objects.create_user(email=f"member{i}@test.com", password="ValidPassword123!", name=f"Member {i}")
            invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=user.id)
            accept_invitation(actor=user, goal_id=shared_goal.id)

        assert GoalParticipant.objects.filter(goal=shared_goal, status=GoalParticipant.Status.ACTIVE).count() == 10

        extra_user = User.objects.create_user(email="extra@test.com", password="ValidPassword123!", name="Extra")
        with pytest.raises(GoalParticipantLimitReachedError):
            invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=extra_user.id)

    def test_max_10_participants_enforced_on_accept(self, owner_user, shared_goal):
        pending_user = User.objects.create_user(email="pending@test.com", password="ValidPassword123!", name="Pending")
        invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=pending_user.id)

        for i in range(1, 10):
            user = User.objects.create_user(email=f"filler{i}@test.com", password="ValidPassword123!", name=f"Filler {i}")
            invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=user.id)
            accept_invitation(actor=user, goal_id=shared_goal.id)

        assert GoalParticipant.objects.filter(goal=shared_goal, status=GoalParticipant.Status.ACTIVE).count() == 10

        with pytest.raises(GoalParticipantLimitReachedError):
            accept_invitation(actor=pending_user, goal_id=shared_goal.id)


@pytest.mark.django_db
class TestGroupSummaryAndMilestonesAndReflection:
    def test_group_summary_calculation(self, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        accept_invitation(actor=participant_user_1, goal_id=shared_goal.id)

        today = timezone.now().date()
        record_check_in(actor=owner_user, goal_id=shared_goal.id, period_date=today, status=GoalCheckIn.Status.COMPLETED)

        summary = shared_goal_group_summary(shared_goal)
        assert summary["active_participants_count"] == 2
        assert summary["today_completed_count"] == 1
        assert summary["today_completion_rate"] == 0.5
        assert "1 of 2 completed today" in summary["headline"]

    def test_milestones_calculation(self, owner_user, participant_user_1, shared_goal):
        today = timezone.now().date()
        shared_goal.start_date = today - timedelta(days=20)
        shared_goal.save(update_fields=["start_date"])

        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        accept_invitation(actor=participant_user_1, goal_id=shared_goal.id)

        for d in range(10):
            pdate = today - timedelta(days=d)
            record_check_in(actor=owner_user, goal_id=shared_goal.id, period_date=pdate, status=GoalCheckIn.Status.COMPLETED)

        milestones = shared_goal_milestones(shared_goal)
        p10 = next(m for m in milestones if m["key"] == "PRACTICES_10")
        assert p10["achieved"] is True
        assert p10["current"] >= 10

        p50 = next(m for m in milestones if m["key"] == "PRACTICES_50")
        assert p50["achieved"] is False

    def test_weekly_reflection_calculation(self, owner_user, participant_user_1, shared_goal):
        p1 = invite_participant(actor=owner_user, goal_id=shared_goal.id, user_id=participant_user_1.id)
        accept_invitation(actor=participant_user_1, goal_id=shared_goal.id)

        today = timezone.now().date()
        record_check_in(actor=owner_user, goal_id=shared_goal.id, period_date=today, status=GoalCheckIn.Status.COMPLETED)
        record_check_in(actor=participant_user_1, goal_id=shared_goal.id, period_date=today, status=GoalCheckIn.Status.COMPLETED)

        reflection = shared_goal_weekly_reflection(shared_goal)
        assert reflection is not None
        assert reflection["completed"] >= 2
        assert "All active members checked in this week" in reflection["trend_text"]
