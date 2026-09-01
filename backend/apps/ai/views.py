from django.shortcuts import get_object_or_404
from rest_framework import permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from apps.ai.exceptions import AiServiceError
from apps.ai.rate_limits import enforce_ai_rate_limit
from apps.ai.serializers import (
    ChatSummaryRequestSerializer,
    ChatSummaryResponseSerializer,
    CommandParserRequestSerializer,
    CommandParserResponseSerializer,
    CommitmentRefinementRequestSerializer,
    CommitmentRefinementResponseSerializer,
    GoalSuggestionRequestSerializer,
    GoalSuggestionResponseSerializer,
    PlannerRequestSerializer,
    PlannerResponseSerializer,
    ReflectionRequestSerializer,
    ReflectionResponseSerializer,
    SharedGoalWeeklySummaryResponseSerializer,
    SupportBotRequestSerializer,
    SupportBotResponseSerializer,
    ThoughtParserRequestSerializer,
    ThoughtParserResponseSerializer,
    WeeklyInsightsResponseSerializer,
    DailyMotivationQuoteSerializer,
)
from apps.ai.services import (
    ask_promise_support_bot,
    build_goal_suggestion,
    generate_shared_goal_weekly_summary,
    generate_weekly_insights,
    get_or_create_daily_quote,
    parse_and_execute_command,
    parse_thought_into_promises,
    plan_commitments,
    reflect_on_stuck_item,
    refine_commitment,
    summarize_goal_chat,
)
from apps.goals.models import Goal, GoalParticipant


@api_view(["GET"])
@permission_classes([permissions.IsAuthenticated])
def daily_motivation_view(request):
    """Daily Motivation Quote shared by all users per calendar day."""
    quote = get_or_create_daily_quote()
    serializer = DailyMotivationQuoteSerializer(quote)
    return Response(serializer.data, status=status.HTTP_200_OK)


@api_view(["GET"])
@permission_classes([permissions.IsAuthenticated])
def weekly_insights_view(request):
    """Feature 4: Weekly Personal Insights with reliable deterministic fallback."""
    enforce_ai_rate_limit(request.user, "insights")
    result = generate_weekly_insights(user=request.user)
    return Response(result, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def goal_suggestion_view(request):
    """Feature 1: AI Goal Builder suggestion."""
    enforce_ai_rate_limit(request.user, "goal_builder")
    serializer = GoalSuggestionRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    result = build_goal_suggestion(
        user_prompt=serializer.validated_data["prompt"],
        timezone=serializer.validated_data.get("timezone", getattr(request.user, "timezone", "Asia/Kolkata")),
    )
    return Response(result, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def commitment_refinement_view(request):
    """Feature 2: AI Commitment Refinement."""
    enforce_ai_rate_limit(request.user, "commitment_refiner")
    serializer = CommitmentRefinementRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    result = refine_commitment(
        user_prompt=serializer.validated_data["prompt"],
        timezone=serializer.validated_data.get("timezone", getattr(request.user, "timezone", "Asia/Kolkata")),
    )
    return Response(result, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def thought_parser_view(request):
    """Feature 3: Turn Thought into Promise."""
    enforce_ai_rate_limit(request.user, "thought_parser")
    serializer = ThoughtParserRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    result = parse_thought_into_promises(
        user_thought=serializer.validated_data["thought"],
        timezone=serializer.validated_data.get("timezone", getattr(request.user, "timezone", "Asia/Kolkata")),
    )
    return Response(result, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def command_parser_view(request):
    """Feature 5: Natural Language Command Center."""
    enforce_ai_rate_limit(request.user, "command_parser")
    serializer = CommandParserRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    result = parse_and_execute_command(
        user=request.user,
        natural_query=serializer.validated_data["query"],
        timezone=serializer.validated_data.get("timezone", getattr(request.user, "timezone", "Asia/Kolkata")),
    )
    return Response(result, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def planner_view(request):
    """Feature 6: AI Planning Assistant."""
    enforce_ai_rate_limit(request.user, "planner")
    serializer = PlannerRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    result = plan_commitments(
        user=request.user,
        user_prompt=serializer.validated_data.get("prompt"),
        commitment_ids=serializer.validated_data.get("commitment_ids"),
    )
    return Response(result, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def reflection_view(request):
    """Feature 7: AI Stuck / Reflection Assistant."""
    enforce_ai_rate_limit(request.user, "reflection")
    serializer = ReflectionRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    result = reflect_on_stuck_item(
        user=request.user,
        item_type=serializer.validated_data["item_type"],
        item_id=serializer.validated_data.get("item_id", ""),
        user_notes=serializer.validated_data.get("notes"),
    )
    return Response(result, status=status.HTTP_200_OK)


@api_view(["GET"])
@permission_classes([permissions.IsAuthenticated])
def shared_goal_summary_view(request, goal_id):
    """Feature 8: Shared Goal Weekly Summary."""
    enforce_ai_rate_limit(request.user, "shared_goal_summary")
    goal = get_object_or_404(
        Goal,
        id=goal_id,
        is_shared=True,
        participants__user=request.user,
        participants__status=GoalParticipant.Status.ACTIVE,
    )
    result = generate_shared_goal_weekly_summary(goal=goal)
    return Response(result, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def chat_summary_view(request, goal_id):
    """Feature 9: Shared Goal Chat Summary."""
    enforce_ai_rate_limit(request.user, "chat_summary")
    serializer = ChatSummaryRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    goal = get_object_or_404(
        Goal,
        id=goal_id,
        is_shared=True,
        participants__user=request.user,
        participants__status=GoalParticipant.Status.ACTIVE,
    )

    result = summarize_goal_chat(
        goal=goal,
        limit=serializer.validated_data.get("limit", 50),
    )
    return Response(result, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def support_bot_view(request):
    """Promise AI Concierge: Interactive Product Support & Knowledge Assistant."""
    enforce_ai_rate_limit(request.user, "support_bot")
    serializer = SupportBotRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    result = ask_promise_support_bot(
        question=serializer.validated_data["question"],
        conversation_history=serializer.validated_data.get("conversation_history", []),
        timezone=serializer.validated_data.get("timezone", getattr(request.user, "timezone", "Asia/Kolkata")),
    )
    return Response(result, status=status.HTTP_200_OK)
