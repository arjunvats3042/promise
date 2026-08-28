from django.urls import path
from apps.sync.views import sync_outbox_view

urlpatterns = [
    path("outbox/", sync_outbox_view, name="sync_outbox"),
]
