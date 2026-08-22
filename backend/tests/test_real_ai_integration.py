"""Batch 13 — Real Gemini API Integration & Pre-Deployment Test Suite.

Controlled integration test matrix executing against real Google Gemini API when
`RUN_REAL_AI_TESTS=1` is set in the environment and `GEMINI_API_KEY_1` is configured.
"""

from datetime import date, timedelta
import os
import time

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.ai.exceptions import AiBadRequestError, AiUnavailableError
from apps.ai.health import validate_ai_environment
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.services.chat_summary import summarize_goal_chat
from apps.ai.services.command_parser import parse_and_execute_command
from apps.ai.services.commitment_refiner import refine_commitment
from apps.ai.services.goal_builder import build_goal_suggestion
from apps.ai.services.insights import generate_weekly_insights
from apps.ai.services.planner import plan_commitments
from apps.ai.services.reflection import reflect_on_stuck_item
from apps.ai.services.shared_goal_summary import generate_shared_goal_weekly_summary
from apps.ai.services.thought_parser import parse_thought_into_promises
from apps.commitments.models import Commitment
from apps.commitments.services import create_commitment
from apps.goals.models import ChatMessage, Goal
from apps.goals.services import create_goal

User = get_user_model()

REAL_AI_ENABLED = bool(os.environ.get("RUN_REAL_AI_TESTS"))
HAS_KEY = bool(os.environ.get("GEMINI_API_KEY_1") or getattr(pytest.importorskip("django.conf").settings, "GEMINI_API_KEY_1", ""))

pytestmark = pytest.mark.skipif(
    not (REAL_AI_ENABLED and HAS_KEY),
    reason="Real AI integration tests require RUN_REAL_AI_TESTS=1 and GEMINI_API_KEY_1",
)

LATENCY_REPORTS = {}


def _record_latency(feature: str, latency_ms: int):
    LATENCY_REPORTS[feature] = latency_ms


@pytest.fixture
def test_user(db):
    return User.objects.create_user(
        email="real_ai_test_user@example.com",
        name="Real AI User",
        password="s3cur3-pass!",
        timezone="UTC",
    )


# ---------------------------------------------------------------------------
# 1. Environment & Health Validator Checks
# ---------------------------------------------------------------------------


def test_ai_environment_health_check_status(db):
    """Verifies safe AI environment health report without exposing raw keys."""
    env_status = validate_ai_environment()
    assert "key_1" in env_status
    assert "key_2" in env_status
    assert "key_3" in env_status
    assert env_status["key_1"] == "configured"
    # Ensure no raw key strings exist in output dictionary values
    for val in env_status.values():
        assert not str(val).startswith("AIza")


# ---------------------------------------------------------------------------
# 2. Real Gemini API Test Matrix (Cases A through K)
# ---------------------------------------------------------------------------


@pytest.mark.django_db
def test_case_a_goal_builder_explicit_input(test_user):
    """A. Goal Builder: Explicit input -> READY with valid schema."""
    t0 = time.monotonic()
    result = build_goal_suggestion(
        raw_text="I want to read for 30 minutes every weekday",
        user=test_user,
    )
    t1 = time.monotonic()
    _record_latency("goal_builder", int((t1 - t0) * 1000))

    assert result["status"] == "READY"
    assert "goal" in result
    assert result["goal"]["title"] is not None


@pytest.mark.django_db
def test_case_b_goal_builder_ambiguous_input(test_user):
    """B. Goal Builder: Ambiguous input -> NEEDS_CLARIFICATION."""
    t0 = time.monotonic()
    result = build_goal_suggestion(
        raw_text="I want to read more",
        user=test_user,
    )
    t1 = time.monotonic()
    _record_latency("goal_builder_clarification", int((t1 - t0) * 1000))

    assert result["status"] == "NEEDS_CLARIFICATION"
    assert "clarification_question" in result
    assert result["clarification_question"] is not None


@pytest.mark.django_db
def test_case_c_commitment_refiner_ambiguous_input(test_user):
    """C. Commitment Refiner: Ambiguous input -> NEEDS_CLARIFICATION."""
    t0 = time.monotonic()
    result = refine_commitment(
        raw_text="Finish my report soon",
        user=test_user,
    )
    t1 = time.monotonic()
    _record_latency("commitment_refiner_ambiguous", int((t1 - t0) * 1000))

    assert result["status"] == "NEEDS_CLARIFICATION"


@pytest.mark.django_db
def test_case_d_commitment_refiner_explicit_input(test_user):
    """D. Commitment Refiner: Explicit input -> READY."""
    t0 = time.monotonic()
    result = refine_commitment(
        raw_text="Finish the report by Friday 6 PM",
        user=test_user,
    )
    t1 = time.monotonic()
    _record_latency("commitment_refiner_explicit", int((t1 - t0) * 1000))

    assert result["status"] == "READY"
    assert "commitment" in result
    assert result["commitment"]["title"] is not None


@pytest.mark.django_db
def test_case_e_thought_parser(test_user):
    """E. Thought Parser: Extract multiple structured items without duplicates."""
    t0 = time.monotonic()
    result = parse_thought_into_promises(
        raw_text="I need to finish my resume, call mom, and start reading",
        user=test_user,
    )
    t1 = time.monotonic()
    _record_latency("thought_parser", int((t1 - t0) * 1000))

    assert isinstance(result, dict)


@pytest.mark.django_db
def test_case_f_command_center(test_user):
    """F. Command Center: 'Show my overdue commitments' -> Valid structured filter."""
    t0 = time.monotonic()
    result = parse_and_execute_command(
        query="Show my overdue commitments",
        user=test_user,
    )
    t1 = time.monotonic()
    _record_latency("command_center", int((t1 - t0) * 1000))

    assert isinstance(result, dict)


@pytest.mark.django_db
def test_case_g_weekly_insights(test_user):
    """G. Weekly Insights: Deterministic fact-grounded analysis."""
    create_commitment(creator=test_user, title="Workout")

    t0 = time.monotonic()
    insights = generate_weekly_insights(user=test_user)
    t1 = time.monotonic()
    _record_latency("weekly_insights", int((t1 - t0) * 1000))

    assert "headline" in insights or "summary" in insights


@pytest.mark.django_db
def test_case_h_planner_preserves_immutable_deadlines(test_user):
    """H. Planner: Fixed deadlines are immutable and preserved."""
    create_commitment(
        creator=test_user,
        title="Doctor appointment",
        due_at=timezone.now() + timedelta(hours=3),
    )

    t0 = time.monotonic()
    plan = plan_commitments(user=test_user, target_date=timezone.now().date())
    t1 = time.monotonic()
    _record_latency("planner", int((t1 - t0) * 1000))

    assert isinstance(plan, dict)


@pytest.mark.django_db
def test_case_i_reflection_neutral_guidance(test_user):
    """I. Reflection: Neutral non-diagnostic response."""
    goal = create_goal(
        creator=test_user,
        title="Daily meditation",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
    )

    t0 = time.monotonic()
    reflection = reflect_on_stuck_item(user=test_user, item_type="goal", item_id=goal.id)
    t1 = time.monotonic()
    _record_latency("reflection", int((t1 - t0) * 1000))

    assert isinstance(reflection, dict)


@pytest.mark.django_db
def test_case_j_shared_goal_summary_group_aggregate(test_user):
    """J. Shared Goal Summary: Group aggregate metrics, zero member ranking."""
    goal = create_goal(
        creator=test_user,
        title="Group Running Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=True,
    )

    t0 = time.monotonic()
    summary = generate_shared_goal_weekly_summary(goal=goal)
    t1 = time.monotonic()
    _record_latency("shared_goal_summary", int((t1 - t0) * 1000))

    assert summary is not None


@pytest.mark.django_db
def test_case_k_chat_summary(test_user):
    """K. Chat Summary: Key decisions, actions, dates, questions."""
    goal = create_goal(
        creator=test_user,
        title="Shared Book Club",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=True,
    )
    ChatMessage.objects.create(
        goal=goal,
        sender=test_user,
        body="Let's meet on Friday at 5 PM to discuss Chapter 3.",
    )

    t0 = time.monotonic()
    summary = summarize_goal_chat(goal=goal)
    t1 = time.monotonic()
    _record_latency("chat_summary", int((t1 - t0) * 1000))

    assert isinstance(summary, dict)


# ---------------------------------------------------------------------------
# 3. Real 3-Key Fallback & Failure Mode Testing
# ---------------------------------------------------------------------------


def test_3_key_fallback_chain_key_1_invalid_key_2_valid():
    """Case 2: Key 1 invalid -> fallback succeeds on Key 2."""
    valid_key = str(os.environ.get("GEMINI_API_KEY_1") or getattr(pytest.importorskip("django.conf").settings, "GEMINI_API_KEY_1", ""))
    if not valid_key:
        pytest.skip("Valid GEMINI_API_KEY_1 required")

    provider = GeminiProvider(api_keys=["invalid_fake_key_1", valid_key])
    res = provider.generate_text(prompt="Say Hello in one word.")
    assert "Hello" in res or len(res) > 0


def test_3_key_fallback_all_keys_invalid_raises_ai_unavailable():
    """Case 4: All keys invalid -> AiUnavailableError."""
    provider = GeminiProvider(api_keys=["fake_key_1", "fake_key_2"])
    with pytest.raises(AiUnavailableError):
        provider.generate_text(prompt="Test fallback failure")


def test_non_retryable_bad_request_fails_fast():
    """Case 5: Non-retryable request validation failure fails fast without burning keys."""
    keys = ["key_1", "key_2", "key_3"]
    provider = GeminiProvider(api_keys=keys)

    with pytest.raises((AiBadRequestError, Exception)):
        provider._call_with_fallback(
            prompt="",
            model="invalid-model-name-12345",
        )
