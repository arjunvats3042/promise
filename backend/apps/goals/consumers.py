import json
import logging
import uuid

from asgiref.sync import sync_to_async
from channels.generic.websocket import AsyncJsonWebsocketConsumer
from django.contrib.auth import get_user_model

from apps.authentication.access_tokens import decode_access_token, AccessTokenError
from apps.authentication.denylist import is_session_denied
from apps.authentication.models import AuthSession
from apps.core.redis import get_redis_client
from apps.goals.models import Goal, GoalParticipant

logger = logging.getLogger("promise")
User = get_user_model()

PRESENCE_TTL_SECONDS = 60


def presence_key(goal_id: str, user_id: str) -> str:
    return f"promise:chat_presence:{goal_id}:{user_id}"


def update_presence(goal_id: str, user_id: str, ttl: int = PRESENCE_TTL_SECONDS):
    try:
        get_redis_client().set(presence_key(goal_id, user_id), "1", ex=ttl)
    except Exception:
        pass


def remove_presence(goal_id: str, user_id: str):
    try:
        get_redis_client().delete(presence_key(goal_id, user_id))
    except Exception:
        pass


def is_user_present_in_chat(goal_id: str, user_id: str) -> bool:
    try:
        return bool(get_redis_client().exists(presence_key(goal_id, user_id)))
    except Exception:
        return False


def _authenticate_scope_headers(headers: list[tuple[bytes, bytes]]) -> tuple[User, uuid.UUID] | None:
    auth_header = None
    for name, value in headers:
        if name.lower() == b"authorization":
            auth_header = value
            break

    if not auth_header:
        return None

    try:
        header_str = auth_header.decode("utf-8")
    except UnicodeDecodeError:
        return None

    parts = header_str.split()
    if len(parts) != 2 or parts[0].lower() != "bearer":
        return None

    token = parts[1]
    try:
        claims = decode_access_token(token)
    except AccessTokenError:
        return None

    if is_session_denied(claims.session_id):
        return None

    user = User.objects.filter(id=claims.user_id).first()
    if user is None or not user.is_active:
        return None

    session = AuthSession.objects.filter(id=claims.session_id, user=user).first()
    if session is None or not session.is_active():
        return None

    return user, session.id


def _validate_goal_membership(goal_id: str, user: User) -> tuple[bool, int]:
    """Returns (is_valid, close_code).

    Close codes:
    4401: Unauthenticated
    4403: Forbidden (invited, left, removed)
    4404: Not Found (personal goal or unrelated user)
    """
    try:
        goal_uuid = uuid.UUID(str(goal_id))
    except ValueError:
        return False, 4404

    goal = Goal.objects.filter(id=goal_uuid).first()
    if goal is None or not goal.is_shared:
        return False, 4404

    participant = GoalParticipant.objects.filter(goal=goal, user=user).first()
    if participant is None:
        return False, 4404

    if participant.status != GoalParticipant.Status.ACTIVE or goal.status != Goal.Status.ACTIVE:
        return False, 4403

    return True, 0


class ChatConsumer(AsyncJsonWebsocketConsumer):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.goal_id = None
        self.room_group_name = None
        self.user = None
        self.session_id = None

    async def connect(self):
        headers = self.scope.get("headers", [])
        auth_result = await sync_to_async(_authenticate_scope_headers)(headers)
        if not auth_result:
            await self.close(code=4401)
            return

        self.user, self.session_id = auth_result
        self.goal_id = self.scope["url_route"]["kwargs"].get("goal_id")
        if not self.goal_id:
            await self.close(code=4404)
            return

        is_valid, close_code = await sync_to_async(_validate_goal_membership)(self.goal_id, self.user)
        if not is_valid:
            await self.close(code=close_code)
            return

        self.room_group_name = f"goal_chat_{self.goal_id}"

        await self.channel_layer.group_add(
            self.room_group_name,
            self.channel_name,
        )
        await self.accept()

        await sync_to_async(update_presence)(str(self.goal_id), str(self.user.id))
        logger.info(
            "WebSocket connected goal_id=%s user_id=%s",
            self.goal_id,
            self.user.id,
        )

    async def disconnect(self, close_code):
        if self.user and self.goal_id:
            await sync_to_async(remove_presence)(str(self.goal_id), str(self.user.id))
        if self.room_group_name:
            await self.channel_layer.group_discard(
                self.room_group_name,
                self.channel_name,
            )
        logger.info(
            "WebSocket disconnected goal_id=%s user_id=%s code=%s",
            self.goal_id,
            getattr(self.user, "id", None),
            close_code,
        )

    async def receive_json(self, content, **kwargs):
        msg_type = content.get("type")
        if msg_type == "ping":
            if self.user and self.goal_id:
                await sync_to_async(update_presence)(str(self.goal_id), str(self.user.id))
            await self.send_json({"type": "pong"})

    async def chat_message_created(self, event):
        """Handler for message broadcast to the room."""
        await self.send_json({
            "type": "message.created",
            "message": event["message"],
        })

    async def chat_participant_revoked(self, event):
        """Handler for membership revocation (left/removed)."""
        revoked_user_id = event.get("user_id")
        if self.user and str(self.user.id) == str(revoked_user_id):
            await self.send_json({
                "type": "chat.error",
                "code": "FORBIDDEN",
                "detail": "Membership is no longer active.",
            })
            await self.close(code=4403)
