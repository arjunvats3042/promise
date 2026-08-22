from django.urls import re_path

from apps.goals.consumers import ChatConsumer

websocket_urlpatterns = [
    re_path(r"^ws/goals/(?P<goal_id>[0-9a-f-]+)/chat/?$", ChatConsumer.as_asgi()),
]
