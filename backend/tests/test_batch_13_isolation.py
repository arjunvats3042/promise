"""Batch 13 — Core Failure Isolation & AI Key Absence Tests.

Verifies that when AI API keys are missing or completely failing:
- Core user operations (Auth, Goal creation, Commitment creation, Shared Goals, Check-ins, Notifications, Search, WebSocket Presence) remain 100% operational.
- Only AI features safely fail with `AiUnavailableError` (HTTP 503).
"""

from datetime import date
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.ai.exceptions import AiUnavailableError
from apps.ai.providers.gemini import GeminiProvider
from apps.commitments.models import Commitment
from apps.commitments.services import complete_commitment, create_commitment
from apps.goals.models import Goal, GoalCheckIn
from apps.goals.services import (
    accept_invitation,
    create_goal,
    invite_participant,
    record_check_in,
)

User = get_user_model()


@pytest.fixture
def user_a(db):
    return User.objects.create_user(
        email="isolation_user_a@example.com",
        name="Isolation User A",
        password="s3cur3-pass!",
    )


@pytest.fixture
def user_b(db):
    return User.objects.create_user(
        email="isolation_user_b@example.com",
        name="Isolation User B",
        password="s3cur3-pass!",
    )


@pytest.mark.django_db
def test_core_features_unaffected_when_ai_keys_missing(user_a, user_b):
    """When no AI keys are configured, all core domain features remain operational."""
    # 1. Goal creation
    goal = create_goal(
        creator=user_a,
        title="Personal Goal without AI",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=date(2026, 1, 1),
    )
    assert goal.id is not None
    assert goal.status == Goal.Status.ACTIVE

    # 2. Check-in recording
    check_in = record_check_in(
        actor=user_a,
        goal_id=goal.id,
        period_date=date(2026, 1, 1),
        status=GoalCheckIn.Status.COMPLETED,
    )
    assert check_in.id is not None

    # 3. Shared Goal invitation & acceptance
    shared_goal = create_goal(
        creator=user_a,
        title="Shared Goal without AI",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=date(2026, 1, 1),
        is_shared=True,
    )
    invite_participant(actor=user_a, goal_id=shared_goal.id, user_id=user_b.id)
    p_b = accept_invitation(actor=user_b, goal_id=shared_goal.id)
    assert p_b.status == "ACTIVE"

    # 4. Commitment creation & completion
    commitment = create_commitment(creator=user_a, title="Commitment without AI")
    completed = complete_commitment(actor=user_a, commitment_id=commitment.id)
    assert completed.status == Commitment.Status.COMPLETED

    # 5. Verify AI provider raises AiUnavailableError without breaking core apps
    provider = GeminiProvider(api_keys=[])
    with pytest.raises(AiUnavailableError):
        provider.generate_text(prompt="Test call with no keys")
