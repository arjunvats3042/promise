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
    enforce_login_rate_limits,
    enforce_refresh_rate_limits,
    enforce_register_rate_limits,
)
from apps.authentication.serializers import (
    LoginSerializer,
    RefreshSerializer,
    RegisterSerializer,
    UserResponseSerializer,
)
from apps.authentication.services import (
    login_user,
    logout_all_sessions,
    logout_session,
    refresh_tokens,
    register_user,
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
    logout_session(user=request.user, **serializer.validated_data)
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
