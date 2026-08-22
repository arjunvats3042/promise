import json
from datetime import datetime, timedelta, timezone as tz
from unittest.mock import MagicMock, patch
import urllib.error

import pytest
from rest_framework.test import APIClient

from apps.ai.exceptions import (
    AiBadRequestError,
    AiInvalidResponseError,
    AiRefusalError,
    AiUnavailableError,
)
from apps.ai.prompts import PROMPT_VERSION_V1
from apps.ai.providers.gemini import GeminiProvider
from apps.ai.rate_limits import enforce_ai_rate_limit
from apps.ai.routing import get_model_for_feature
from apps.ai.services import (
    build_goal_suggestion,
    generate_shared_goal_weekly_summary,
    generate_weekly_insights,
    parse_and_execute_command,
    parse_thought_into_promises,
    plan_commitments,
    reflect_on_stuck_item,
    refine_commitment,
    summarize_goal_chat,
)
from apps.commitments.models import Commitment
from apps.goals.models import ChatMessage, Goal, GoalCheckIn, GoalParticipant
from apps.users.models import User
from config.exceptions import RateLimitedError

STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"


@pytest.fixture(autouse=True)
def auth_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = (
        "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
    )
    settings.JWT_SIGNING_KEY = TEST_SIGNING_KEY
    settings.JWT_SIGNING_KEY_PREVIOUS = ""
    settings.JWT_KEY_ID = "test-key"
    settings.JWT_KEY_ID_PREVIOUS = ""
    settings.JWT_ISSUER = "promise-api"
    settings.JWT_AUDIENCE = "promise-client"
    settings.JWT_ALGORITHM = "HS256"
    settings.JWT_ACCESS_TTL_SECONDS = 900
    settings.JWT_LEEWAY_SECONDS = 30


@pytest.fixture
def auth_user(db):
    return User.objects.create_user(
        email="testai@example.com",
        name="Test AI User",
        password=STRONG_PASSWORD,
        timezone="UTC",
    )


@pytest.fixture
def other_user(db):
    return User.objects.create_user(
        email="other@example.com",
        name="Other User",
        password=STRONG_PASSWORD,
        timezone="UTC",
    )


@pytest.fixture
def client(auth_user):
    api_client = APIClient()
    resp = api_client.post(
        "/api/v1/auth/login/",
        {"email": auth_user.email, "password": STRONG_PASSWORD},
        format="json",
    )
    token = resp.json()["tokens"]["access_token"]
    api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")
    return api_client


# =========================================================================
# 1. PROVIDER & KEY FALLBACK TESTS
# =========================================================================

def test_gemini_provider_key1_success():
    provider = GeminiProvider(api_keys=["key1", "key2", "key3"])

    mock_resp = MagicMock()
    mock_resp.status = 200
    mock_resp.__enter__.return_value = mock_resp
    mock_resp.read.return_value = json.dumps({
        "candidates": [
            {
                "content": {
                    "parts": [{"text": json.dumps({"title": "Read 30 mins"})}]
                },
                "finishReason": "STOP",
            }
        ]
    }).encode("utf-8")

    with patch("urllib.request.urlopen", return_value=mock_resp) as mock_urlopen:
        result = provider.generate_structured(prompt="test prompt")
        assert result["title"] == "Read 30 mins"
        req = mock_urlopen.call_args[0][0]
        assert req.get_header("X-goog-api-key") == "key1"


def test_gemini_provider_fallback_chain_key1_fails_key2_succeeds():
    provider = GeminiProvider(api_keys=["key1", "key2", "key3"])

    success_resp = MagicMock()
    success_resp.status = 200
    success_resp.__enter__.return_value = success_resp
    success_resp.read.return_value = json.dumps({
        "candidates": [
            {
                "content": {
                    "parts": [{"text": json.dumps({"status": "ok"})}]
                },
                "finishReason": "STOP",
            }
        ]
    }).encode("utf-8")

    http_429_err = urllib.error.HTTPError(
        url="https://api", code=429, msg="Rate limit", hdrs={}, fp=MagicMock(read=lambda **kw: b"Quota exceeded")
    )

    with patch("urllib.request.urlopen", side_effect=[http_429_err, success_resp]) as mock_urlopen:
        result = provider.generate_structured(prompt="test prompt")
        assert result["status"] == "ok"
        assert mock_urlopen.call_count == 2
        req1 = mock_urlopen.call_args_list[0][0][0]
        req2 = mock_urlopen.call_args_list[1][0][0]
        assert req1.get_header("X-goog-api-key") == "key1"
        assert req2.get_header("X-goog-api-key") == "key2"


def test_gemini_provider_fallback_chain_all_keys_fail_raises_503():
    provider = GeminiProvider(api_keys=["key1", "key2", "key3"])

    http_503_err = urllib.error.HTTPError(
        url="https://api", code=503, msg="Unavailable", hdrs={}, fp=MagicMock(read=lambda **kw: b"Service Unavailable")
    )

    with patch("urllib.request.urlopen", side_effect=[http_503_err, http_503_err, http_503_err]):
        with pytest.raises(AiUnavailableError) as exc_info:
            provider.generate_structured(prompt="test prompt")
        assert exc_info.value.status_code == 503
        assert exc_info.value.code == "AI_UNAVAILABLE"


def test_gemini_provider_non_retryable_400_fails_fast():
    provider = GeminiProvider(api_keys=["key1", "key2", "key3"])

    http_400_err = urllib.error.HTTPError(
        url="https://api", code=400, msg="Bad Request", hdrs={}, fp=MagicMock(read=lambda **kw: b"Invalid input")
    )

    with patch("urllib.request.urlopen", side_effect=http_400_err) as mock_urlopen:
        with pytest.raises(AiBadRequestError):
            provider.generate_structured(prompt="test prompt")
        assert mock_urlopen.call_count == 1


def test_gemini_provider_never_logs_api_keys(caplog):
    provider = GeminiProvider(api_keys=["super-secret-key-12345"])

    http_500_err = urllib.error.HTTPError(
        url="https://api", code=500, msg="Server Error", hdrs={}, fp=MagicMock(read=lambda **kw: b"Error")
    )

    with patch("urllib.request.urlopen", side_effect=http_500_err):
        with pytest.raises(AiUnavailableError):
            provider.generate_structured(prompt="test prompt")

    for record in caplog.records:
        assert "super-secret-key-12345" not in record.getMessage()


# =========================================================================
# 2. FEATURE SERVICES & REFINEMENT TESTS
# =========================================================================

@pytest.mark.django_db
def test_goal_builder_ambiguity_needs_clarification(client):
    mock_clarification = {
        "status": "NEEDS_CLARIFICATION",
        "clarification_question": "How often would you like to read?",
        "title": "",
        "description": "",
        "recurrence_kind": "DAILY",
        "weekdays": [],
        "period_unit": None,
        "times_per_period": None,
        "tracking_kind": "BINARY",
        "target_value": None,
        "target_unit": "",
        "reasoning": "Missing frequency/cadence information",
    }
    with patch("apps.ai.views.build_goal_suggestion", return_value=mock_clarification):
        resp = client.post("/api/v1/ai/goals/suggest/", {"prompt": "I want to read more"})
        assert resp.status_code == 200
        assert resp.data["status"] == "NEEDS_CLARIFICATION"
        assert resp.data["clarification_question"] == "How often would you like to read?"


@pytest.mark.django_db
def test_goal_builder_clear_input_ready(client):
    mock_suggestion = {
        "status": "READY",
        "clarification_question": None,
        "title": "Read daily",
        "description": "Read 30 minutes every weekday",
        "recurrence_kind": "WEEKLY_DAYS",
        "weekdays": [0, 1, 2, 3, 4],
        "period_unit": None,
        "times_per_period": None,
        "tracking_kind": "COUNT",
        "target_value": 30.0,
        "target_unit": "minutes",
        "reasoning": "Fits your 30 minute weekday availability",
    }
    with patch("apps.ai.views.build_goal_suggestion", return_value=mock_suggestion):
        resp = client.post("/api/v1/ai/goals/suggest/", {"prompt": "I want to read 30 mins on weekdays"})
        assert resp.status_code == 200
        assert resp.data["status"] == "READY"
        assert resp.data["title"] == "Read daily"
        assert resp.data["recurrence_kind"] == "WEEKLY_DAYS"
        assert resp.data["weekdays"] == [0, 1, 2, 3, 4]


@pytest.mark.django_db
def test_commitment_refinement_ambiguity_detection(client):
    mock_refinement = {
        "status": "NEEDS_CLARIFICATION",
        "current_interpretation": "User intends to finish tax filing",
        "missing_information": "Target completion date/time is unspecified",
        "clarifying_question": "When would you like to finish your taxes by?",
        "refined_title": "Finish taxes",
        "refined_description": "",
        "suggested_due_at": None,
        "suggested_due_precision": None,
        "reasoning": "Vague deadline 'soon' cannot be reliably scheduled without user input",
    }
    with patch("apps.ai.views.refine_commitment", return_value=mock_refinement):
        resp = client.post("/api/v1/ai/commitments/refine/", {"prompt": "Finish taxes soon"})
        assert resp.status_code == 200
        assert resp.data["status"] == "NEEDS_CLARIFICATION"
        assert resp.data["missing_information"] == "Target completion date/time is unspecified"
        assert resp.data["suggested_due_at"] is None


@pytest.mark.django_db
def test_thought_parser_confidence_and_decomposition(client):
    mock_items = {
        "items": [
            {
                "type": "commitment",
                "title": "Call mom",
                "description": "",
                "confidence": "HIGH",
                "due_at": "2026-08-23T12:00:00Z",
                "due_precision": "DAY",
            },
            {
                "type": "goal",
                "title": "Read 20 mins",
                "confidence": "HIGH",
                "recurrence_kind": "DAILY",
                "tracking_kind": "COUNT",
                "target_value": 20,
                "target_unit": "mins",
            },
        ]
    }
    with patch("apps.ai.views.parse_thought_into_promises", return_value=mock_items):
        resp = client.post("/api/v1/ai/parse-thought/", {"thought": "Call mom this weekend and read 20 mins every day"})
        assert resp.status_code == 200
        assert len(resp.data["items"]) == 2
        assert resp.data["items"][0]["confidence"] == "HIGH"
        assert resp.data["items"][0]["type"] == "commitment"
        assert resp.data["items"][1]["type"] == "goal"


@pytest.mark.django_db
def test_weekly_insights_deterministic_grounding(client, auth_user):
    Commitment.objects.create(
        created_by=auth_user,
        title="Completed task 1",
        status=Commitment.Status.COMPLETED,
        completed_at=datetime.now(tz=tz.utc).replace(hour=19),
    )
    Goal.objects.create(
        created_by=auth_user,
        title="Workout",
        status=Goal.Status.ACTIVE,
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        timezone="UTC",
        start_date=datetime.now(tz=tz.utc).date(),
    )

    mock_insights = {
        "summary": "You completed 1 commitment this week.",
        "observed_patterns": ["Your completion rate was higher for evening commitments"],
        "constructive_suggestion": "Keep the momentum going tomorrow.",
    }
    with patch("apps.ai.services.insights.GeminiProvider") as mock_gemini:
        mock_gemini.return_value.generate_structured.return_value = mock_insights
        resp = client.get("/api/v1/ai/insights/weekly/")
        assert resp.status_code == 200
        assert resp.data["facts"]["commitments_completed"] == 1
        assert resp.data["facts"]["completion_time_slots"]["evening_after_6pm"] == 1
        assert resp.data["insights"]["observed_patterns"] == ["Your completion rate was higher for evening commitments"]


@pytest.mark.django_db
def test_command_parser_interpreted_preview(client, auth_user):
    c1 = Commitment.objects.create(
        created_by=auth_user,
        title="Sprint Planning",
        status=Commitment.Status.PENDING,
    )

    mock_command_filter = {
        "entity": "commitments",
        "status": "open",
        "period": "all",
        "query_text": "Sprint",
        "interpreted_query_preview": "Showing open commitments mentioning 'Sprint'",
    }
    with patch("apps.ai.services.command_parser.GeminiProvider") as mock_gemini:
        mock_gemini.return_value.generate_structured.return_value = mock_command_filter
        resp = client.post("/api/v1/ai/command/", {"query": "Find open tasks about Sprint"})
        assert resp.status_code == 200
        assert resp.data["interpreted_query_preview"] == "Showing open commitments mentioning 'Sprint'"
        assert resp.data["results_count"] == 1
        assert resp.data["results"][0]["id"] == str(c1.id)


@pytest.mark.django_db
def test_planning_assistant_fixed_deadline_and_conflicts(client, auth_user):
    c1 = Commitment.objects.create(
        created_by=auth_user,
        title="Board Meeting",
        status=Commitment.Status.PENDING,
        due_at=datetime.now(tz=tz.utc),
    )
    c2 = Commitment.objects.create(
        created_by=auth_user,
        title="Write Followup",
        status=Commitment.Status.PENDING,
    )

    mock_plan = {
        "planned_order": [
            {
                "commitment_id": str(c1.id),
                "suggested_time_slot": "Morning",
                "priority_rank": 1,
                "is_fixed_deadline": True,
                "note": "Fixed time meeting",
            },
            {
                "commitment_id": str(c2.id),
                "suggested_time_slot": "Afternoon",
                "priority_rank": 2,
                "is_fixed_deadline": False,
                "note": "Can be done after the board meeting",
            },
        ],
        "conflict_notes": None,
        "summary_advice": "Board meeting is fixed in the morning. Complete followup in afternoon.",
    }
    with patch("apps.ai.services.planner.GeminiProvider") as mock_gemini:
        mock_gemini.return_value.generate_structured.return_value = mock_plan
        resp = client.post("/api/v1/ai/planner/", {"prompt": "Plan my day"})
        assert resp.status_code == 200
        assert resp.data["planned_order"][0]["is_fixed_deadline"] is True
        c1.refresh_from_db()
        assert c1.status == Commitment.Status.PENDING


@pytest.mark.django_db
def test_reflection_assistant_tone_and_micro_actions(client, auth_user):
    g1 = Goal.objects.create(
        created_by=auth_user,
        title="Guitar Practice",
        status=Goal.Status.ACTIVE,
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        timezone="UTC",
        start_date=datetime.now(tz=tz.utc).date(),
    )
    mock_reflection = {
        "reflection_summary": "Finding 30 minutes daily might be creating friction after work.",
        "suggested_adjustments": ["Reduce target to 10 minutes", "Practice right after dinner"],
        "smaller_next_action": "Would a 10-minute version be easier to restart today?",
    }
    with patch("apps.ai.services.reflection.GeminiProvider") as mock_gemini:
        mock_gemini.return_value.generate_structured.return_value = mock_reflection
        resp = client.post("/api/v1/ai/reflection/", {
            "item_type": "goal",
            "item_id": str(g1.id),
            "notes": "Too tired after work",
        })
        assert resp.status_code == 200
        assert resp.data["smaller_next_action"] == "Would a 10-minute version be easier to restart today?"


@pytest.mark.django_db
def test_shared_goal_summary_no_rankings(client, auth_user, other_user):
    shared_goal = Goal.objects.create(
        created_by=other_user,
        title="Shared Book Club",
        status=Goal.Status.ACTIVE,
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        timezone="UTC",
        start_date=datetime.now(tz=tz.utc).date(),
        is_shared=True,
    )
    GoalParticipant.objects.create(
        goal=shared_goal,
        user=auth_user,
        role=GoalParticipant.Role.PARTICIPANT,
        status=GoalParticipant.Status.ACTIVE,
    )

    mock_summary = {
        "group_summary": "Your group completed 5 check-ins this week.",
        "collective_completion_rate": "80%",
        "encouragement": "Great collective progress together!",
    }
    with patch("apps.ai.services.shared_goal_summary.GeminiProvider") as mock_gemini:
        mock_gemini.return_value.generate_structured.return_value = mock_summary
        resp = client.get(f"/api/v1/ai/goals/{shared_goal.id}/summary/weekly/")
        assert resp.status_code == 200
        assert "5" in resp.data["summary"]["group_summary"]


@pytest.mark.django_db
def test_chat_summary_open_questions_and_empty_handling(client, auth_user, other_user):
    shared_goal = Goal.objects.create(
        created_by=other_user,
        title="Project X",
        status=Goal.Status.ACTIVE,
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        timezone="UTC",
        start_date=datetime.now(tz=tz.utc).date(),
        is_shared=True,
    )
    GoalParticipant.objects.create(
        goal=shared_goal,
        user=auth_user,
        role=GoalParticipant.Role.PARTICIPANT,
        status=GoalParticipant.Status.ACTIVE,
    )
    ChatMessage.objects.create(
        goal=shared_goal,
        sender=other_user,
        body="Should we launch on Tuesday or Wednesday? Let's decide on Monday.",
    )

    mock_chat_summary = {
        "summary": "Team discussing launch timing for next week.",
        "key_decisions": [],
        "agreed_actions": ["Decide launch date on Monday"],
        "important_dates": ["Monday"],
        "open_questions": ["Launch on Tuesday or Wednesday?"],
    }
    with patch("apps.ai.services.chat_summary.GeminiProvider") as mock_gemini:
        mock_gemini.return_value.generate_structured.return_value = mock_chat_summary
        resp = client.post(f"/api/v1/ai/goals/{shared_goal.id}/chat/summarize/", {"limit": 20})
        assert resp.status_code == 200
        assert resp.data["open_questions"] == ["Launch on Tuesday or Wednesday?"]


def test_model_routing():
    assert get_model_for_feature("goal_builder") == "gemini-3.6-flash"
    assert get_model_for_feature("planner") == "gemini-3.6-flash"
    assert get_model_for_feature("insights") == "gemini-3.6-flash"
