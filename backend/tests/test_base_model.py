import uuid
from datetime import timedelta
from unittest.mock import patch

import pytest
from django.contrib.auth import get_user_model
from django.db import connection
from django.test.utils import isolate_apps
from django.utils import timezone

from apps.core.models import BaseModel


@pytest.fixture
def probe_model(db):
    with isolate_apps("apps.core"):
        class Probe(BaseModel):
            class Meta:
                app_label = "core"
                abstract = False

        with connection.schema_editor() as schema_editor:
            schema_editor.create_model(Probe)
        try:
            yield Probe
        finally:
            with connection.schema_editor() as schema_editor:
                schema_editor.delete_model(Probe)


def test_base_model_is_abstract():
    assert BaseModel._meta.abstract is True


def test_user_does_not_inherit_base_model():
    assert not issubclass(get_user_model(), BaseModel)


def test_uuid_ids_are_generated(probe_model):
    obj = probe_model.objects.create()

    assert isinstance(obj.id, uuid.UUID)
    assert obj.id.version == 4


def test_created_at_is_populated(probe_model):
    before = timezone.now()
    obj = probe_model.objects.create()

    assert obj.created_at is not None
    assert timezone.is_aware(obj.created_at)
    assert obj.created_at >= before
    assert obj.updated_at is not None
    assert timezone.is_aware(obj.updated_at)


def test_updated_at_changes_on_save(probe_model):
    first = timezone.now()
    with patch("django.utils.timezone.now", return_value=first):
        obj = probe_model.objects.create()

    later = first + timedelta(seconds=5)
    with patch("django.utils.timezone.now", return_value=later):
        obj.save()
    obj.refresh_from_db()

    assert obj.created_at == first
    assert obj.updated_at == later
    assert timezone.is_aware(obj.updated_at)
