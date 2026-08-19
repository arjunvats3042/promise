from rest_framework.decorators import api_view, authentication_classes, permission_classes
from rest_framework.exceptions import NotFound
from rest_framework.permissions import AllowAny
from rest_framework.response import Response


@api_view(["GET"])
@authentication_classes([])
@permission_classes([AllowAny])
def health(_request):
    return Response({"status": "ok"})


@api_view(["GET", "POST", "PUT", "PATCH", "DELETE"])
@authentication_classes([])
@permission_classes([AllowAny])
def api_not_found(_request, resource=None):
    raise NotFound()
