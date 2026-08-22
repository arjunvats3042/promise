from django.urls import include, path

from config.views import api_not_found, health, ready

v1_urlpatterns = [
    path("health/", health),
    path("health/ready/", ready),
    path("auth/", include("apps.authentication.urls")),
    path("users/", include("apps.users.urls")),
    path("commitments/", include("apps.commitments.urls")),
    path("goals/", include("apps.goals.urls")),
    path("notifications/", include("apps.notifications.urls")),
    path("search/", include("apps.search.urls")),
    path("<path:resource>", api_not_found),  # keep last
]

urlpatterns = [
    path("api/v1/", include(v1_urlpatterns)),
]
