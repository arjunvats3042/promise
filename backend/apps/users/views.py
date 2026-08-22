from rest_framework.decorators import api_view
from rest_framework.response import Response

from apps.users.exceptions import UserNotFoundError
from apps.users.models import User
from apps.users.rate_limits import (
    enforce_user_lookup_rate_limit,
    enforce_user_search_rate_limit,
)
from apps.users.serializers import UserLookupQuerySerializer, UserLookupSerializer


@api_view(["GET"])
def user_lookup(request):
    query = UserLookupQuerySerializer(data=request.query_params.dict())
    query.is_valid(raise_exception=True)

    if "email" in query.validated_data:
        email = query.validated_data["email"]
        enforce_user_lookup_rate_limit(request.user, email)
        user = User.objects.filter(email=email, is_active=True).first()
        if user is None:
            raise UserNotFoundError()
        return Response(UserLookupSerializer(user).data)

    q = query.validated_data["q"]
    enforce_user_search_rate_limit(request.user)
    if "@" in q:
        users = User.objects.filter(email__istartswith=q, is_active=True).order_by("email")[:5]
    else:
        users = User.objects.filter(name__istartswith=q, is_active=True).order_by("name")[:5]

    return Response({"results": UserLookupSerializer(users, many=True).data})
