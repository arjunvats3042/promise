from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from apps.goals.exceptions import GoalValidationError
from apps.goals.models import Goal, GoalParticipant
from apps.goals.services import (
    convert_goal_to_shared,
    create_goal,
    invite_participant,
    send_chat_message,
    update_goal,
)
from apps.users.models import User


class GoalConversionAndChatIndicatorTests(TestCase):
    def setUp(self):
        self.user1 = User.objects.create_user(
            email="user1@example.com",
            name="Alice",
            timezone="Asia/Kolkata",
        )
        self.user2 = User.objects.create_user(
            email="user2@example.com",
            name="Bob",
            timezone="Asia/Kolkata",
        )
        self.client = APIClient()
        self.client.force_authenticate(user=self.user1)

    def test_convert_individual_goal_to_shared(self):
        goal = create_goal(
            creator=self.user1,
            title="Read Daily",
            recurrence_kind=Goal.RecurrenceKind.DAILY,
            is_shared=False,
        )
        self.assertFalse(goal.is_shared)

        # Convert to shared
        converted = convert_goal_to_shared(actor=self.user1, goal_id=goal.id)
        self.assertTrue(converted.is_shared)
        goal.refresh_from_db()
        self.assertTrue(goal.is_shared)

        # Re-converting is a safe no-op
        converted_again = convert_goal_to_shared(actor=self.user1, goal_id=goal.id)
        self.assertTrue(converted_again.is_shared)

    def test_cannot_convert_shared_goal_to_individual(self):
        goal = create_goal(
            creator=self.user1,
            title="Team Fitness",
            recurrence_kind=Goal.RecurrenceKind.DAILY,
            is_shared=True,
        )
        self.assertTrue(goal.is_shared)

        # Attempting to set is_shared=False should raise GoalValidationError
        with self.assertRaises(GoalValidationError):
            update_goal(
                actor=self.user1,
                goal_id=goal.id,
                is_shared=False,
            )

    def test_convert_to_shared_api_endpoint(self):
        goal = create_goal(
            creator=self.user1,
            title="Study Algorithms",
            recurrence_kind=Goal.RecurrenceKind.DAILY,
            is_shared=False,
        )
        response = self.client.post(f"/api/v1/goals/{goal.id}/convert-to-shared/")
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.data["is_shared"])

    def test_serializer_chat_indicators(self):
        goal = create_goal(
            creator=self.user1,
            title="Group Workout",
            recurrence_kind=Goal.RecurrenceKind.DAILY,
            is_shared=True,
        )
        # Invite user2
        invite_participant(actor=self.user1, goal_id=goal.id, user_id=self.user2.id)

        # user2 accepts invitation
        from apps.goals.services import accept_invitation
        accept_invitation(actor=self.user2, goal_id=goal.id)

        # user2 sends a chat message
        send_chat_message(
            sender=self.user2,
            goal_id=goal.id,
            body="Let's crush this workout!",
        )

        # user1 checks the goal response
        response = self.client.get(f"/api/v1/goals/{goal.id}/")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["unread_chat_count"], 1)
        self.assertIsNotNone(response.data["latest_chat_message"])
        self.assertEqual(response.data["latest_chat_message"]["text"], "Let's crush this workout!")
        self.assertEqual(response.data["latest_chat_message"]["sender_name"], "Bob")
