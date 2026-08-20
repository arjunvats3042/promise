from django.urls import path

from apps.goals.views import (
    goal_cancel,
    goal_check_ins,
    goal_collection,
    goal_complete,
    goal_detail,
    goal_pause,
    goal_resume,
)

app_name = "goals"

urlpatterns = [
    path("", goal_collection, name="collection"),
    path("<uuid:goal_id>/", goal_detail, name="detail"),
    path("<uuid:goal_id>/pause/", goal_pause, name="pause"),
    path("<uuid:goal_id>/resume/", goal_resume, name="resume"),
    path("<uuid:goal_id>/complete/", goal_complete, name="complete"),
    path("<uuid:goal_id>/cancel/", goal_cancel, name="cancel"),
    path("<uuid:goal_id>/check-ins/", goal_check_ins, name="check-ins"),
]
