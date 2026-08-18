from rest_framework.decorators import api_view
from rest_framework.exceptions import NotFound
from rest_framework.response import Response


@api_view(["GET"])
def health(_request):
    return Response({"status": "ok"})


@api_view(["GET", "POST", "PUT", "PATCH", "DELETE"])
def api_not_found(_request, resource=None):
    raise NotFound()
