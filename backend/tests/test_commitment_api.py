from datetime import timedelta

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient

from apps.commitments.models import Commitment, CommitmentEvent, CommitmentParticipant
from apps.commitments.services import create_commitment

User = get_user_model()

COMMITMENTS_URL = "/api/v1/commitments/"
LOGIN_URL = "/api/v1/auth/login/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"
UNAUTHENTICATED_MISSING = {
    "error": {
        "code": "UNAUTHENTICATED",
        "message": "Authentication credentials were not provided.",
    }
}
UNAUTHENTICATED_INVALID = {
    "error": {
        "code": "UNAUTHENTICATED",
        "message": "Authentication failed.",
    }
}
RESPONSE_FIELDS = {
    "id",
    "title",
    "description",
    "status",
    "due_at",
    "due_precision",
    "source",
    "snoozed_until",
    "completed_at",
    "cancelled_at",
    "created_at",
    "updated_at",
    "is_overdue",
}
HIDDEN_FIELDS = {
    "created_by",
    "participants",
    "events",
    "password",
    "metadata",
    "refresh_token_hmac",
}


@pytest.fixture(autouse=True)
def auth_settings(settings):
    settings.AUTH_REFRESH_TOKEN_PEPPER = "test-only-refresh-pepper"
    settings.AUTH_REFRESH_TOKEN_ENCRYPTION_KEY = (
        "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
    )
    settings.JWT_SIGNING_KEY = TEST_SIGNING_KEY
    settings.JWT_SIGNING_KEY_PREVIOUS = ""
    settings.JWT_KEY_ID = "test-key"
    settings.JWT_KEY_ID_PREVIOUS = ""
    settings.JWT_ISSUER = "promise-api"
    settings.JWT_AUDIENCE = "promise-client"
    settings.JWT_ALGORITHM = "HS256"
    settings.JWT_ACCESS_TTL_SECONDS = 900
    settings.JWT_LEEWAY_SECONDS = 30


@pytest.fixture
def client():
    return APIClient()


@pytest.fixture
def arjun(db):
    return User.objects.create_user(
        email="arjun@example.com",
        name="Arjun",
        password=STRONG_PASSWORD,
    )


@pytest.fixture
def rahul(db):
    return User.objects.create_user(
        email="rahul@example.com",
        name="Rahul",
        password=STRONG_PASSWORD,
    )


def _token(client, email):
    response = client.post(
        LOGIN_URL,
        {"email": email, "password": STRONG_PASSWORD},
        format="json",
    )
    assert response.status_code == 200
    return response.json()["tokens"]["access_token"]


def _auth(client, token):
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")


def _detail(commitment_id):
    return f"{COMMITMENTS_URL}{commitment_id}/"


def _action(commitment_id, name):
    return f"{COMMITMENTS_URL}{commitment_id}/{name}/"


def _assert_commitment_payload(body):
    assert set(body) == RESPONSE_FIELDS
    for hidden in HIDDEN_FIELDS:
        assert hidden not in body
    assert isinstance(body["is_overdue"], bool)


def _list_items(response):
    body = response.json()
    assert "results" in body
    return body["results"]


@pytest.mark.django_db
def test_create_requires_authentication(client):
    response = client.post(
        COMMITMENTS_URL,
        {"title": "Send credentials"},
        format="json",
    )

    assert response.status_code == 401
    assert response.json() == UNAUTHENTICATED_MISSING


@pytest.mark.django_db
def test_create_rejects_invalid_token(client):
    _auth(client, "not-a-jwt")
    response = client.post(
        COMMITMENTS_URL,
        {"title": "Send credentials"},
        format="json",
    )

    assert response.status_code == 401
    assert response.json() == UNAUTHENTICATED_INVALID


@pytest.mark.django_db
def test_create_success_makes_creator_responsible_and_created_event(client, arjun):
    _auth(client, _token(client, arjun.email))
    due_at = (timezone.now() + timedelta(hours=2)).isoformat()
    response = client.post(
        COMMITMENTS_URL,
        {
            "title": "Send credentials",
            "description": "Send the production credentials",
            "due_at": due_at,
            "due_precision": "DATETIME",
            "source": "MANUAL",
            "created_by": str(arjun.id),
            "status": "COMPLETED",
        },
        format="json",
    )

    assert response.status_code == 201
    body = response.json()
    _assert_commitment_payload(body)
    assert body["title"] == "Send credentials"
    assert body["status"] == Commitment.Status.PENDING
    assert body["source"] == Commitment.Source.MANUAL
    assert body["is_overdue"] is False
    commitment = Commitment.objects.get(id=body["id"])
    participant = commitment.participants.get()
    assert commitment.created_by == arjun
    assert participant.user == arjun
    assert participant.role == CommitmentParticipant.Role.RESPONSIBLE
    assert commitment.events.get().event_type == CommitmentEvent.EventType.CREATED
    assert commitment.events.get().actor == arjun


@pytest.mark.django_db
def test_create_validation_errors(client, arjun):
    _auth(client, _token(client, arjun.email))
    missing = client.post(COMMITMENTS_URL, {}, format="json")
    inconsistent = client.post(
        COMMITMENTS_URL,
        {"title": "No due", "due_precision": "DATETIME"},
        format="json",
    )

    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "VALIDATION_ERROR"
    assert inconsistent.status_code == 400
    assert inconsistent.json()["error"]["code"] == "VALIDATION_ERROR"
    assert Commitment.objects.count() == 0


@pytest.mark.django_db
def test_list_returns_only_visible_commitments(client, arjun, rahul):
    mine = create_commitment(creator=arjun, title="Mine")
    theirs = create_commitment(creator=rahul, title="Theirs")
    shared = create_commitment(creator=rahul, title="Shared with Arjun")
    CommitmentParticipant.objects.filter(commitment=shared, user=rahul).update(
        role=CommitmentParticipant.Role.RESPONSIBLE
    )
    CommitmentParticipant.objects.create(
        commitment=shared,
        user=arjun,
        role=CommitmentParticipant.Role.RECIPIENT,
        joined_at=timezone.now(),
    )

    _auth(client, _token(client, arjun.email))
    response = client.get(COMMITMENTS_URL)

    assert response.status_code == 200
    items = _list_items(response)
    ids = {item["id"] for item in items}
    assert str(mine.id) in ids
    assert str(shared.id) in ids
    assert str(theirs.id) not in ids
    for item in items:
        _assert_commitment_payload(item)


@pytest.mark.django_db
def test_list_filters_status_source_and_overdue(client, arjun):
    overdue = create_commitment(
        creator=arjun,
        title="Overdue",
        due_at=timezone.now() - timedelta(hours=1),
        due_precision=Commitment.DuePrecision.DATETIME,
    )
    create_commitment(
        creator=arjun,
        title="Future",
        due_at=timezone.now() + timedelta(days=1),
        due_precision=Commitment.DuePrecision.DATETIME,
        source=Commitment.Source.IMPORT,
    )
    create_commitment(creator=arjun, title="No due")

    _auth(client, _token(client, arjun.email))
    overdue_list = _list_items(client.get(COMMITMENTS_URL, {"is_overdue": "true"}))
    import_list = _list_items(client.get(COMMITMENTS_URL, {"source": "IMPORT"}))
    pending_list = _list_items(client.get(COMMITMENTS_URL, {"status": "PENDING"}))

    assert {item["id"] for item in overdue_list} == {str(overdue.id)}
    assert overdue_list[0]["is_overdue"] is True
    assert {item["title"] for item in import_list} == {"Future"}
    assert {item["title"] for item in pending_list} == {"Overdue", "Future", "No due"}


@pytest.mark.django_db
def test_detail_authorized_ok_unrelated_404(client, arjun, rahul):
    commitment = create_commitment(creator=arjun, title="Mine")

    _auth(client, _token(client, arjun.email))
    ok = client.get(_detail(commitment.id))
    assert ok.status_code == 200
    _assert_commitment_payload(ok.json())
    assert ok.json()["id"] == str(commitment.id)

    _auth(client, _token(client, rahul.email))
    missing = client.get(_detail(commitment.id))
    assert missing.status_code == 404
    assert missing.json()["error"]["code"] == "COMMITMENT_NOT_FOUND"


@pytest.mark.django_db
def test_patch_updates_allowed_fields_and_writes_updated_event(client, arjun):
    commitment = create_commitment(creator=arjun, title="Old")
    _auth(client, _token(client, arjun.email))
    response = client.patch(
        _detail(commitment.id),
        {
            "title": "New title",
            "description": "Updated",
            "status": "COMPLETED",
            "created_by": str(arjun.id),
        },
        format="json",
    )

    assert response.status_code == 200
    body = response.json()
    _assert_commitment_payload(body)
    assert body["title"] == "New title"
    assert body["description"] == "Updated"
    assert body["status"] == Commitment.Status.PENDING
    commitment.refresh_from_db()
    assert commitment.created_by == arjun
    assert commitment.events.filter(event_type=CommitmentEvent.EventType.UPDATED).count() == 1
    assert commitment.events.get(
        event_type=CommitmentEvent.EventType.UPDATED
    ).metadata["fields"] == ["title", "description"]


@pytest.mark.django_db
def test_patch_unauthorized_and_terminal(client, arjun, rahul):
    commitment = create_commitment(creator=arjun, title="Mine")
    _auth(client, _token(client, rahul.email))
    forbidden = client.patch(
        _detail(commitment.id), {"title": "Hacked"}, format="json"
    )
    assert forbidden.status_code == 404
    assert forbidden.json()["error"]["code"] == "COMMITMENT_NOT_FOUND"

    _auth(client, _token(client, arjun.email))
    client.post(_action(commitment.id, "complete"), {}, format="json")
    terminal = client.patch(
        _detail(commitment.id), {"title": "Too late"}, format="json"
    )
    assert terminal.status_code == 409
    assert terminal.json()["error"]["code"] == "COMMITMENT_INVALID_TRANSITION"


@pytest.mark.django_db
def test_complete_success_idempotent_unauthorized_and_invalid(client, arjun, rahul):
    commitment = create_commitment(creator=arjun, title="Do it")
    cancelled = create_commitment(creator=arjun, title="Cancel me")
    _auth(client, _token(client, arjun.email))
    client.post(_action(cancelled.id, "cancel"), {}, format="json")

    first = client.post(_action(commitment.id, "complete"), {}, format="json")
    second = client.post(_action(commitment.id, "complete"), {}, format="json")
    invalid = client.post(_action(cancelled.id, "complete"), {}, format="json")

    assert first.status_code == 200
    assert second.status_code == 200
    _assert_commitment_payload(first.json())
    assert first.json()["status"] == Commitment.Status.COMPLETED
    assert first.json()["completed_at"] is not None
    assert first.json()["cancelled_at"] is None
    assert (
        CommitmentEvent.objects.filter(
            commitment=commitment, event_type=CommitmentEvent.EventType.COMPLETED
        ).count()
        == 1
    )
    assert invalid.status_code == 409
    assert invalid.json()["error"]["code"] == "COMMITMENT_INVALID_TRANSITION"

    _auth(client, _token(client, rahul.email))
    other = client.post(_action(commitment.id, "complete"), {}, format="json")
    assert other.status_code == 404
    assert other.json()["error"]["code"] == "COMMITMENT_NOT_FOUND"


@pytest.mark.django_db
def test_snooze_unsnooze_wait_cancel(client, arjun):
    commitment = create_commitment(
        creator=arjun,
        title="Timed",
        due_at=timezone.now() + timedelta(days=1),
        due_precision=Commitment.DuePrecision.DATETIME,
    )
    due_at = commitment.due_at
    _auth(client, _token(client, arjun.email))
    snoozed_until = (timezone.now() + timedelta(hours=3)).isoformat()

    snooze = client.post(
        _action(commitment.id, "snooze"),
        {"snoozed_until": snoozed_until},
        format="json",
    )
    repeat_snooze = client.post(
        _action(commitment.id, "snooze"),
        {"snoozed_until": snoozed_until},
        format="json",
    )
    missing = client.post(_action(commitment.id, "snooze"), {}, format="json")
    past = client.post(
        _action(commitment.id, "snooze"),
        {"snoozed_until": (timezone.now() - timedelta(hours=1)).isoformat()},
        format="json",
    )

    assert snooze.status_code == 200
    assert repeat_snooze.status_code == 200
    assert snooze.json()["status"] == Commitment.Status.SNOOZED
    assert snooze.json()["due_at"] == due_at.isoformat().replace("+00:00", "Z")
    assert (
        CommitmentEvent.objects.filter(
            commitment=commitment, event_type=CommitmentEvent.EventType.SNOOZED
        ).count()
        == 1
    )
    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "VALIDATION_ERROR"
    assert past.status_code == 400

    unsnooze = client.post(_action(commitment.id, "unsnooze"), {}, format="json")
    unsnooze_again = client.post(_action(commitment.id, "unsnooze"), {}, format="json")
    assert unsnooze.status_code == 200
    assert unsnooze_again.status_code == 200
    assert unsnooze.json()["status"] == Commitment.Status.PENDING
    assert (
        CommitmentEvent.objects.filter(
            commitment=commitment, event_type=CommitmentEvent.EventType.UNSNOOZED
        ).count()
        == 1
    )

    wait = client.post(_action(commitment.id, "wait"), {}, format="json")
    wait_again = client.post(_action(commitment.id, "wait"), {}, format="json")
    assert wait.status_code == 200
    assert wait_again.status_code == 200
    assert wait.json()["status"] == Commitment.Status.WAITING
    assert (
        CommitmentEvent.objects.filter(
            commitment=commitment, event_type=CommitmentEvent.EventType.WAITING
        ).count()
        == 1
    )

    other = create_commitment(creator=arjun, title="Snoozed wait")
    client.post(
        _action(other.id, "snooze"),
        {"snoozed_until": (timezone.now() + timedelta(hours=2)).isoformat()},
        format="json",
    )
    invalid_wait = client.post(_action(other.id, "wait"), {}, format="json")
    assert invalid_wait.status_code == 409

    cancel = client.post(_action(commitment.id, "cancel"), {}, format="json")
    cancel_again = client.post(_action(commitment.id, "cancel"), {}, format="json")
    assert cancel.status_code == 200
    assert cancel_again.status_code == 200
    assert cancel.json()["status"] == Commitment.Status.CANCELLED
    assert cancel.json()["cancelled_at"] is not None
    assert (
        CommitmentEvent.objects.filter(
            commitment=commitment, event_type=CommitmentEvent.EventType.CANCELLED
        ).count()
        == 1
    )


@pytest.mark.django_db
def test_recipient_cannot_mutate_but_can_view(client, arjun, rahul):
    commitment = create_commitment(creator=arjun, title="Rahul owns this")
    CommitmentParticipant.objects.filter(commitment=commitment, user=arjun).update(
        role=CommitmentParticipant.Role.RECIPIENT
    )
    CommitmentParticipant.objects.create(
        commitment=commitment,
        user=rahul,
        role=CommitmentParticipant.Role.RESPONSIBLE,
        joined_at=timezone.now(),
    )

    _auth(client, _token(client, arjun.email))
    viewed = client.get(_detail(commitment.id))
    mutate = client.post(_action(commitment.id, "complete"), {}, format="json")
    assert viewed.status_code == 200
    assert mutate.status_code == 403
    assert mutate.json()["error"]["code"] == "FORBIDDEN"
    assert "error" in mutate.json()
    assert set(mutate.json()["error"]) >= {"code", "message"}
