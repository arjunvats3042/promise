from django.conf import settings
from rest_framework import status
from rest_framework.decorators import (
    api_view,
    authentication_classes,
    permission_classes,
)
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response

from apps.authentication.rate_limits import (
    enforce_email_verification_request_rate_limits,
    enforce_google_auth_rate_limits,
    enforce_login_rate_limits,
    enforce_password_reset_request_rate_limits,
    enforce_refresh_rate_limits,
    enforce_register_rate_limits,
)
from apps.authentication.serializers import (
    ChangePasswordSerializer,
    GoogleAuthSerializer,
    LoginSerializer,
    PasswordResetConfirmSerializer,
    PasswordResetRequestSerializer,
    RefreshSerializer,
    RegisterSerializer,
    SetPasswordSerializer,
    UserResponseSerializer,
    VerifyEmailConfirmSerializer,
)
from apps.authentication.services import (
    change_user_password,
    confirm_email_verification,
    confirm_password_reset,
    delete_user_account,
    google_login_or_register,
    login_user,
    logout_all_sessions,
    logout_session,
    refresh_tokens,
    register_user,
    request_email_verification,
    request_password_reset,
    set_user_password,
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


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def register(request):
    serializer = RegisterSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    enforce_register_rate_limits(request, serializer.validated_data["email"])
    result = register_user(**serializer.validated_data)
    return Response(_authentication_response(result), status=status.HTTP_201_CREATED)


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def login(request):
    serializer = LoginSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    enforce_login_rate_limits(request, serializer.validated_data["email"])
    result = login_user(**serializer.validated_data)
    return Response(_authentication_response(result), status=status.HTTP_200_OK)


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def google_auth(request):
    serializer = GoogleAuthSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    enforce_google_auth_rate_limits(request)
    result = google_login_or_register(**serializer.validated_data)
    return Response(_authentication_response(result), status=status.HTTP_200_OK)


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def verify_email_request(request):
    enforce_email_verification_request_rate_limits(request, request.user.id)
    raw_token = request_email_verification(user=request.user)
    # In production, this token is delivered via email service
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
