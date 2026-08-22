from django.urls import path

from apps.authentication.views import (
    change_password,
    delete_account,
    google_auth,
    login,
    logout,
    logout_all,
    me,
    password_reset_confirm,
    password_reset_request,
    refresh,
    register,
    set_password,
    verify_email_confirm,
    verify_email_request,
)

app_name = "authentication"

urlpatterns = [
    path("register/", register, name="register"),
    path("login/", login, name="login"),
    path("google/", google_auth, name="google-auth"),
    path("verify-email/request/", verify_email_request, name="verify-email-request"),
    path("verify-email/confirm/", verify_email_confirm, name="verify-email-confirm"),
    path("password-reset/request/", password_reset_request, name="password-reset-request"),
    path("password-reset/confirm/", password_reset_confirm, name="password-reset-confirm"),
    path("set-password/", set_password, name="set-password"),
    path("change-password/", change_password, name="change-password"),
    path("delete-account/", delete_account, name="delete-account"),
    path("refresh/", refresh, name="refresh"),
    path("logout/", logout, name="logout"),
    path("logout-all/", logout_all, name="logout-all"),
    path("me/", me, name="me"),
]
