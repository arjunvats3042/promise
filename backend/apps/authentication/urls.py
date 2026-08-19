from django.urls import path

from apps.authentication.views import login, logout, logout_all, me, refresh, register

app_name = "authentication"

urlpatterns = [
    path("register/", register, name="register"),
    path("login/", login, name="login"),
    path("refresh/", refresh, name="refresh"),
    path("logout/", logout, name="logout"),
    path("logout-all/", logout_all, name="logout-all"),
    path("me/", me, name="me"),
]
