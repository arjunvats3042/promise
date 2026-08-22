from django.urls import path

from apps.ai.views import (
    chat_summary_view,
    command_parser_view,
    commitment_refinement_view,
    goal_suggestion_view,
    planner_view,
    reflection_view,
    shared_goal_summary_view,
    thought_parser_view,
    weekly_insights_view,
)

urlpatterns = [
    path("goals/suggest/", goal_suggestion_view, name="ai_goal_suggest"),
    path("commitments/refine/", commitment_refinement_view, name="ai_commitment_refine"),
    path("parse-thought/", thought_parser_view, name="ai_parse_thought"),
    path("insights/weekly/", weekly_insights_view, name="ai_weekly_insights"),
    path("command/", command_parser_view, name="ai_command_parser"),
    path("planner/", planner_view, name="ai_planner"),
    path("reflection/", reflection_view, name="ai_reflection"),
    path("goals/<uuid:goal_id>/summary/weekly/", shared_goal_summary_view, name="ai_shared_goal_summary"),
    path("goals/<uuid:goal_id>/chat/summarize/", chat_summary_view, name="ai_chat_summary"),
]
