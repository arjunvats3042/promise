from django.urls import path

from apps.users.views import user_lookup

app_name = "users"

urlpatterns = [
    path("lookup/", user_lookup, name="lookup"),
]
