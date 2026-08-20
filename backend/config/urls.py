from django.urls import include, path

from config.views import api_not_found, health

v1_urlpatterns = [
    path("health/", health),
    path("auth/", include("apps.authentication.urls")),
    path("commitments/", include("apps.commitments.urls")),
    path("goals/", include("apps.goals.urls")),
    path("<path:resource>", api_not_found),  # keep last
]

urlpatterns = [
    path("api/v1/", include(v1_urlpatterns)),
]
