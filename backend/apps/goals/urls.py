from django.urls import path

from apps.goals.views import (
    goal_activity,
    goal_cancel,
    goal_chat_messages,
    goal_chat_read,
    goal_chat_summary,
    goal_check_ins,
    goal_collection,
    goal_complete,
    goal_detail,
    goal_leave,
    goal_participant_detail,
    goal_participants,
    goal_participants_accept,
    goal_participants_decline,
    goal_pause,
    goal_resume,
)

app_name = "goals"

urlpatterns = [
    path("", goal_collection, name="collection"),
    path("<str:goal_id>/", goal_detail, name="detail"),
    path("<str:goal_id>/pause/", goal_pause, name="pause"),
    path("<str:goal_id>/resume/", goal_resume, name="resume"),
    path("<str:goal_id>/complete/", goal_complete, name="complete"),
    path("<str:goal_id>/cancel/", goal_cancel, name="cancel"),
    path("<str:goal_id>/leave/", goal_leave, name="leave"),
    path("<str:goal_id>/check-ins/", goal_check_ins, name="check-ins"),
    path(
        "<str:goal_id>/participants/",
        goal_participants,
        name="participants",
    ),
    path(
        "<str:goal_id>/participants/accept/",
        goal_participants_accept,
        name="participants-accept",
    ),
    path(
        "<str:goal_id>/participants/decline/",
        goal_participants_decline,
        name="participants-decline",
    ),
    path(
        "<str:goal_id>/participants/<str:participant_id>/",
        goal_participant_detail,
        name="participant-detail",
    ),
    path(
        "<str:goal_id>/chat/messages/",
        goal_chat_messages,
        name="chat-messages",
    ),
    path(
        "<str:goal_id>/chat/read/",
        goal_chat_read,
        name="chat-read",
    ),
    path(
        "<str:goal_id>/chat/summary/",
        goal_chat_summary,
        name="chat-summary",
    ),
    path(
        "<str:goal_id>/activity/",
        goal_activity,
        name="activity",
    ),
]
