from django.conf import settings
from rest_framework import status
from rest_framework.decorators import (
    api_view,
    authentication_classes,
    permission_classes,
)
from rest_framework.exceptions import NotFound
from rest_framework.pagination import PageNumberPagination
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response

from apps.authentication.models import AuthSession, SecurityEvent
from apps.authentication.rate_limits import (
    enforce_email_change_confirm_rate_limits,
    enforce_email_change_request_rate_limits,
    enforce_email_verification_request_rate_limits,
    enforce_google_auth_rate_limits,
    enforce_google_link_rate_limits,
    enforce_google_unlink_rate_limits,
    enforce_login_rate_limits,
    enforce_password_reset_request_rate_limits,
    enforce_refresh_rate_limits,
    enforce_register_rate_limits,
    enforce_session_revocation_rate_limits,
)
from apps.authentication.serializers import (
    AuthSessionSerializer,
    ChangePasswordSerializer,
    EmailChangeConfirmSerializer,
    EmailChangeRequestSerializer,
    GoogleAuthSerializer,
    GoogleLinkSerializer,
    LoginSerializer,
    PasswordResetConfirmSerializer,
    PasswordResetRequestSerializer,
    RefreshSerializer,
    RegisterSerializer,
    SecurityEventSerializer,
    SetPasswordSerializer,
    UserResponseSerializer,
    VerifyEmailConfirmSerializer,
)
from apps.authentication.services import (
    change_user_password,
    confirm_email_change,
    confirm_email_verification,
    confirm_password_reset,
    delete_user_account,
    google_login_or_register,
    link_google_account,
    list_user_sessions,
    login_user,
    logout_all_sessions,
    logout_session,
    refresh_tokens,
    register_user,
    request_email_change,
    request_email_verification,
    request_password_reset,
    revoke_other_sessions,
    revoke_user_session,
    set_user_password,
    unlink_google_account,
)


def _token_payload(result):
    return {
        "access_token": result.access_token,
        "refresh_token": result.refresh_token,
        "token_type": "Bearer",
        "expires_in": settings.JWT_ACCESS_TTL_SECONDS,
    }


def _authentication_response(result):
    return {
        "user": UserResponseSerializer(result.user).data,
        "tokens": _token_payload(result),
    }


def _request_client_meta(request):
    ip = request.META.get("REMOTE_ADDR") or None
    ua = request.META.get("HTTP_USER_AGENT", "")
    return ip, ua


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def register(request):
    serializer = RegisterSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    enforce_register_rate_limits(request, serializer.validated_data["email"])
    ip, ua = _request_client_meta(request)
    device_id = request.headers.get("X-Device-Id") or request.data.get("device_id", "")
    result = register_user(
        device_id=device_id,
        ip_address=ip,
        user_agent=ua,
        **serializer.validated_data,
    )
    return Response(_authentication_response(result), status=status.HTTP_201_CREATED)


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def login(request):
    serializer = LoginSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    enforce_login_rate_limits(request, serializer.validated_data["email"])
    ip, ua = _request_client_meta(request)
    device_name = request.data.get("device_name", "")
    platform = request.data.get("platform", "unknown")
    device_id = request.headers.get("X-Device-Id") or request.data.get("device_id", "")
    result = login_user(
        device_name=device_name,
        platform=platform,
        device_id=device_id,
        ip_address=ip,
        user_agent=ua,
        **serializer.validated_data,
    )
    return Response(_authentication_response(result), status=status.HTTP_200_OK)


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def google_auth(request):
    serializer = GoogleAuthSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    enforce_google_auth_rate_limits(request)
    ip, ua = _request_client_meta(request)
    device_id = request.headers.get("X-Device-Id") or request.data.get("device_id", "")
    result = google_login_or_register(
        device_id=device_id,
        ip_address=ip,
        user_agent=ua,
        **serializer.validated_data,
    )
    return Response(_authentication_response(result), status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def verify_email_request(request):
    enforce_email_verification_request_rate_limits(request, request.user.id)
    raw_token = request_email_verification(user=request.user)
    return Response(
        {
            "detail": "Verification email sent.",
            "debug_token": raw_token if settings.DEBUG else None,
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def verify_email_confirm(request):
    serializer = VerifyEmailConfirmSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = confirm_email_verification(token=serializer.validated_data["token"])
    return Response(
        {"user": UserResponseSerializer(user).data, "detail": "Email successfully verified."},
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def password_reset_request(request):
    serializer = PasswordResetRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    email = serializer.validated_data["email"]
    enforce_password_reset_request_rate_limits(request, email)
    raw_token = request_password_reset(email=email)
    return Response(
        {
            "detail": "If that email is registered, password reset instructions have been sent.",
            "debug_token": raw_token if settings.DEBUG else None,
        },
        status=status.HTTP_202_ACCEPTED,
    )


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def password_reset_confirm(request):
    serializer = PasswordResetConfirmSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = confirm_password_reset(
        token=serializer.validated_data["token"],
        new_password=serializer.validated_data["password"],
    )
    return Response(
        {"user": UserResponseSerializer(user).data, "detail": "Password successfully reset."},
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def set_password(request):
    serializer = SetPasswordSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = set_user_password(user=request.user, password=serializer.validated_data["password"])
    return Response(
        {"user": UserResponseSerializer(user).data, "detail": "Password set successfully."},
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def change_password(request):
    serializer = ChangePasswordSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = change_user_password(
        user=request.user,
        old_password=serializer.validated_data["old_password"],
        new_password=serializer.validated_data["new_password"],
    )
    return Response(
        {"user": UserResponseSerializer(user).data, "detail": "Password changed successfully."},
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def delete_account(request):
    delete_user_account(user=request.user)
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def refresh(request):
    serializer = RefreshSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    enforce_refresh_rate_limits(request, serializer.validated_data["refresh_token"])
    result = refresh_tokens(**serializer.validated_data)
    return Response({"tokens": _token_payload(result)}, status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def logout(request):
    serializer = RefreshSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    device_id = request.headers.get("X-Device-Id") or request.data.get("device_id")
    logout_session(user=request.user, device_id=device_id, **serializer.validated_data)
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def logout_all(request):
    logout_all_sessions(user=request.user)
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def me(request):
    return Response({"user": UserResponseSerializer(request.user).data})


# --- Batch 6 Security Endpoints ---


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def list_sessions(request):
    current_session_id = getattr(request.auth, "session_id", None)
    if current_session_id is None and isinstance(request.auth, dict):
        current_session_id = request.auth.get("sid")
    sessions = list_user_sessions(request.user)
    serializer = AuthSessionSerializer(sessions, many=True, context={"current_session_id": current_session_id})
    return Response({"sessions": serializer.data})


@api_view(["DELETE"])
@permission_classes([IsAuthenticated])
def revoke_session(request, session_id):
    enforce_session_revocation_rate_limits(request, request.user.id)
    try:
        revoke_user_session(request.user, session_id)
    except AuthSession.DoesNotExist:
        raise NotFound("Session not found.")
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def revoke_all_sessions_view(request):
    enforce_session_revocation_rate_limits(request, request.user.id)
    current_session_id = getattr(request.auth, "get", lambda k, d=None: None)("sid") if isinstance(request.auth, dict) else None
    except_current = request.data.get("except_current", False)
    if except_current and current_session_id:
        revoke_other_sessions(request.user, current_session_id=current_session_id)
    else:
        logout_all_sessions(user=request.user)
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def list_security_events(request):
    paginator = PageNumberPagination()
    paginator.page_size = 20
    qs = SecurityEvent.objects.filter(user=request.user)
    page = paginator.paginate_queryset(qs, request)
    serializer = SecurityEventSerializer(page, many=True)
    return paginator.get_paginated_response(serializer.data)


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def google_link(request):
    enforce_google_link_rate_limits(request, request.user.id)
    serializer = GoogleLinkSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = link_google_account(request.user, serializer.validated_data["id_token"])
    return Response(
        {"user": UserResponseSerializer(user).data, "detail": "Google account linked successfully."},
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def google_unlink(request):
    enforce_google_unlink_rate_limits(request, request.user.id)
    user = unlink_google_account(request.user)
    return Response(
        {"user": UserResponseSerializer(user).data, "detail": "Google account unlinked successfully."},
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def email_change_request(request):
    enforce_email_change_request_rate_limits(request, request.user.id)
    serializer = EmailChangeRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    raw_token = request_email_change(
        request.user,
        serializer.validated_data["new_email"],
        current_password=serializer.validated_data.get("current_password"),
    )
    return Response(
        {
            "detail": "Verification email sent to new email address.",
            "debug_token": raw_token if settings.DEBUG else None,
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def email_change_confirm(request):
    enforce_email_change_confirm_rate_limits(request)
    serializer = EmailChangeConfirmSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = confirm_email_change(serializer.validated_data["token"])
    return Response(
        {"user": UserResponseSerializer(user).data, "detail": "Email address updated and verified successfully."},
        status=status.HTTP_200_OK,
    )
