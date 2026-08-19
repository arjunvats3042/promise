from dataclasses import dataclass
from uuid import UUID

from django.contrib.auth import get_user_model
from rest_framework.authentication import BaseAuthentication, get_authorization_header
from rest_framework.exceptions import AuthenticationFailed

from apps.authentication.denylist import is_session_denied
from apps.authentication.models import AuthSession

User = get_user_model()


@dataclass(frozen=True)
class AccessAuthenticationContext:
    session_id: UUID
    token_id: str


class JWTAccessAuthentication(BaseAuthentication):
    keyword = "Bearer"

    def authenticate(self, request):
        from apps.authentication.access_tokens import decode_access_token
        from apps.authentication.exceptions import AccessTokenError

        header = get_authorization_header(request)
        if not header:
            return None

        parts = header.split()
        if (
            len(parts) != 2
            or parts[0].lower() != self.keyword.lower().encode()
            or not parts[1]
        ):
            raise AuthenticationFailed()

        try:
            token = parts[1].decode("utf-8")
            claims = decode_access_token(token)
        except (UnicodeDecodeError, AccessTokenError):
            raise AuthenticationFailed() from None

        if is_session_denied(claims.session_id):
            raise AuthenticationFailed()

        user = User.objects.filter(id=claims.user_id).first()
        if user is None or not user.is_active:
            raise AuthenticationFailed()

        session = AuthSession.objects.filter(
            id=claims.session_id,
            user=user,
        ).first()
        if session is None or not session.is_active():
            raise AuthenticationFailed()

        return (
            user,
            AccessAuthenticationContext(
                session_id=session.id,
                token_id=claims.jti,
            ),
        )

    def authenticate_header(self, request):
        return self.keyword
