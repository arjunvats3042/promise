from django.urls import path
from rest_framework.decorators import api_view, authentication_classes, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.serializers import EmailField, Serializer

from config.urls import urlpatterns as production_urlpatterns


class _EmailSerializer(Serializer):
    email = EmailField()


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
def _validate_email(request):
    serializer = _EmailSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    return Response({"status": "ok"})


@api_view(["GET"])
@authentication_classes([])
@permission_classes([AllowAny])
def _unexpected_error(_request):
    raise RuntimeError("secret internal details")


urlpatterns = [
    path("api/v1/_test/validate/", _validate_email),
    path("api/v1/_test/boom/", _unexpected_error),
] + production_urlpatterns
