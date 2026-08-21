from rest_framework.decorators import api_view
from rest_framework.response import Response

from apps.users.exceptions import UserNotFoundError
from apps.users.models import User
from apps.users.rate_limits import enforce_user_lookup_rate_limit
from apps.users.serializers import UserLookupQuerySerializer, UserLookupSerializer


@api_view(["GET"])
def user_lookup(request):
    query = UserLookupQuerySerializer(data=request.query_params.dict())
    query.is_valid(raise_exception=True)
    email = query.validated_data["email"]
    enforce_user_lookup_rate_limit(request.user, email)
    user = User.objects.filter(email=email, is_active=True).first()
    if user is None:
        raise UserNotFoundError()
    return Response(UserLookupSerializer(user).data)
