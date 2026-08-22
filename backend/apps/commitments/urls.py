from django.urls import path

from apps.commitments.views import (
    commitment_cancel,
    commitment_collection,
    commitment_complete,
    commitment_detail,
    commitment_snooze,
    commitment_unsnooze,
    commitment_wait,
)

app_name = "commitments"

urlpatterns = [
    path("", commitment_collection, name="collection"),
    path("<str:commitment_id>/", commitment_detail, name="detail"),
    path("<str:commitment_id>/complete/", commitment_complete, name="complete"),
    path("<str:commitment_id>/snooze/", commitment_snooze, name="snooze"),
    path("<str:commitment_id>/unsnooze/", commitment_unsnooze, name="unsnooze"),
    path("<str:commitment_id>/wait/", commitment_wait, name="wait"),
    path("<str:commitment_id>/cancel/", commitment_cancel, name="cancel"),
]
