import uuid
import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient

from apps.commitments.models import Commitment
from apps.goals.models import ChatMessage, Goal, GoalParticipant
from apps.goals.services import create_goal, invite_participant, accept_invitation, send_chat_message

User = get_user_model()

SEARCH_URL = "/api/v1/search/"
PEOPLE_LOOKUP_URL = "/api/v1/users/lookup/"
STRONG_PASSWORD = "correct-horse-battery-staple"
TEST_SIGNING_KEY = "test-only-jwt-signing-key-not-for-production"


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
def alice(db):
    return User.objects.create_user(
        email="alice@example.com",
        name="Alice Walker",
        password=STRONG_PASSWORD,
        timezone="UTC",
    )


@pytest.fixture
def bob(db):
    return User.objects.create_user(
        email="bob@example.com",
        name="Bob Builder",
        password=STRONG_PASSWORD,
        timezone="America/New_York",
    )


@pytest.fixture
def charlie(db):
    return User.objects.create_user(
        email="charlie@example.com",
        name="Charlie Chaplin",
        password=STRONG_PASSWORD,
        timezone="UTC",
    )


def _token(client, email):
    response = client.post(
        "/api/v1/auth/login/",
        {"email": email, "password": STRONG_PASSWORD},
        format="json",
    )
    assert response.status_code == 200
    return response.json()["tokens"]["access_token"]


def _auth(client, token):
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")


# ============================================================================
# 1. GLOBAL SEARCH TESTS
# ============================================================================


@pytest.mark.django_db
def test_global_search_requires_auth(client):
    response = client.get(SEARCH_URL, {"q": "test"})
    assert response.status_code == 401


@pytest.mark.django_db
def test_global_search_validation(client, alice):
    _auth(client, _token(client, alice.email))

    # Missing query
    res = client.get(SEARCH_URL)
    assert res.status_code == 400

    # Blank query
    res = client.get(SEARCH_URL, {"q": "   "})
    assert res.status_code == 400

    # Valid query
    res = client.get(SEARCH_URL, {"q": "reading"})
    assert res.status_code == 200
    data = res.json()
    assert "commitments" in data
    assert "goals" in data
    assert "shared_goals" in data


@pytest.mark.django_db
def test_global_search_commitments_own_only(client, alice, bob):
    # Alice's commitments
    Commitment.objects.create(
        created_by=alice,
        title="Submit tax return report",
        status=Commitment.Status.PENDING,
    )
    Commitment.objects.create(
        created_by=alice,
        title="Review daily report summary",
        status=Commitment.Status.COMPLETED,
    )
    # Bob's commitment
    Commitment.objects.create(
        created_by=bob,
        title="Confidential quarterly report",
        status=Commitment.Status.PENDING,
    )

    _auth(client, _token(client, alice.email))
    res = client.get(SEARCH_URL, {"q": "report"})
    assert res.status_code == 200
    data = res.json()

    # Alice sees only her 2 commitments
    titles = [c["title"] for c in data["commitments"]]
    assert len(titles) == 2
    assert "Submit tax return report" in titles
    assert "Review daily report summary" in titles
    assert "Confidential quarterly report" not in titles


@pytest.mark.django_db
def test_global_search_goals_personal_and_shared(client, alice, bob, charlie):
    # Alice personal goal
    g_personal = create_goal(
        creator=alice,
        title="Morning Yoga Practice",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=False,
    )

    # Alice creates shared goal with Bob
    g_shared = create_goal(
        creator=alice,
        title="Yoga With Friends",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=True,
    )
    invite = invite_participant(actor=alice, goal_id=g_shared.id, user_id=bob.id)
    accept_invitation(actor=bob, goal_id=g_shared.id)

    # Charlie creates private shared goal (Alice NOT a participant)
    g_unrelated = create_goal(
        creator=charlie,
        title="Yoga Secret Circle",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=True,
    )

    # Search as Alice
    _auth(client, _token(client, alice.email))
    res = client.get(SEARCH_URL, {"q": "Yoga"})
    assert res.status_code == 200
    data = res.json()

    personal_titles = [g["title"] for g in data["goals"]]
    shared_titles = [g["title"] for g in data["shared_goals"]]

    assert "Morning Yoga Practice" in personal_titles
    assert "Yoga With Friends" in shared_titles
    assert "Yoga Secret Circle" not in shared_titles
    assert "Yoga Secret Circle" not in personal_titles

    # Search as Bob (should see shared goal, but not Alice's personal goal)
    _auth(client, _token(client, bob.email))
    res_bob = client.get(SEARCH_URL, {"q": "Yoga"})
    assert res_bob.status_code == 200
    bob_data = res_bob.json()

    assert len(bob_data["goals"]) == 0
    assert len(bob_data["shared_goals"]) == 1
    assert bob_data["shared_goals"][0]["title"] == "Yoga With Friends"


@pytest.mark.django_db
def test_global_search_ordering_exact_prefix_substring(client, alice):
    Commitment.objects.create(created_by=alice, title="Sprint Planning Review", status=Commitment.Status.PENDING)
    Commitment.objects.create(created_by=alice, title="Sprint", status=Commitment.Status.PENDING)
    Commitment.objects.create(created_by=alice, title="Sprint Velocity", status=Commitment.Status.PENDING)
    Commitment.objects.create(created_by=alice, title="End of Sprint", status=Commitment.Status.PENDING)

    _auth(client, _token(client, alice.email))
    res = client.get(SEARCH_URL, {"q": "Sprint"})
    assert res.status_code == 200
    titles = [c["title"] for c in res.json()["commitments"]]

    # Exact match first ("Sprint"), then prefixes ("Sprint Velocity", "Sprint Planning Review"), then substring ("End of Sprint")
    assert titles[0] == "Sprint"
    assert titles[-1] == "End of Sprint"


@pytest.mark.django_db
def test_global_search_filter_type(client, alice):
    Commitment.objects.create(created_by=alice, title="Read 20 pages", status=Commitment.Status.PENDING)
    create_goal(creator=alice, title="Read 1 book per month", recurrence_kind=Goal.RecurrenceKind.DAILY, start_date=timezone.now().date(), is_shared=False)

    _auth(client, _token(client, alice.email))

    # type=commitments
    res_c = client.get(SEARCH_URL, {"q": "Read", "type": "commitments"})
    assert res_c.status_code == 200
    assert len(res_c.json()["commitments"]) == 1
    assert len(res_c.json()["goals"]) == 0

    # type=goals
    res_g = client.get(SEARCH_URL, {"q": "Read", "type": "goals"})
    assert res_g.status_code == 200
    assert len(res_g.json()["commitments"]) == 0
    assert len(res_g.json()["goals"]) == 1


# ============================================================================
# 2. PEOPLE SEARCH TESTS (Shared Goal Invites)
# ============================================================================


@pytest.mark.django_db
def test_people_search_exact_email(client, alice, bob):
    _auth(client, _token(client, alice.email))
    res = client.get(PEOPLE_LOOKUP_URL, {"email": "bob@example.com"})
    assert res.status_code == 200
    data = res.json()
    assert data["id"] == str(bob.id)
    assert data["name"] == "Bob Builder"
    assert data["email"] == "bob@example.com"
    # Verify no private leakage
    assert "timezone" not in data
    assert "password" not in data


@pytest.mark.django_db
def test_people_search_name_prefix(client, alice, bob, charlie):
    _auth(client, _token(client, alice.email))

    # Search by name prefix "Cha"
    res = client.get(PEOPLE_LOOKUP_URL, {"q": "Cha"})
    assert res.status_code == 200
    results = res.json()["results"]
    assert len(results) == 1
    assert results[0]["name"] == "Charlie Chaplin"
    assert results[0]["email"] == "charlie@example.com"


@pytest.mark.django_db
def test_people_search_email_prefix(client, alice, bob):
    _auth(client, _token(client, alice.email))

    # Search by email prefix "bob@"
    res = client.get(PEOPLE_LOOKUP_URL, {"q": "bob@"})
    assert res.status_code == 200
    results = res.json()["results"]
    assert len(results) == 1
    assert results[0]["email"] == "bob@example.com"


@pytest.mark.django_db
def test_people_search_minimum_query_length(client, alice):
    _auth(client, _token(client, alice.email))
    res = client.get(PEOPLE_LOOKUP_URL, {"q": "a"})
    assert res.status_code == 400
    assert res.json()["error"]["code"] == "VALIDATION_ERROR"


@pytest.mark.django_db
def test_people_search_excludes_inactive_users(client, alice, bob):
    bob.is_active = False
    bob.save(update_fields=["is_active"])

    _auth(client, _token(client, alice.email))
    res = client.get(PEOPLE_LOOKUP_URL, {"q": "Bob"})
    assert res.status_code == 200
    assert len(res.json()["results"]) == 0


# ============================================================================
# 3. CHAT SEARCH TESTS (Shared Goals)
# ============================================================================


@pytest.mark.django_db
def test_chat_search_active_participant_success(client, alice, bob):
    goal = create_goal(
        creator=alice,
        title="Marathon Training",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=True,
    )
    invite_participant(actor=alice, goal_id=goal.id, user_id=bob.id)
    accept_invitation(actor=bob, goal_id=goal.id)

    send_chat_message(sender=alice, goal_id=goal.id, body="Great run today everyone! Did 10km.")
    send_chat_message(sender=bob, goal_id=goal.id, body="Awesome pace Alice, I did 5km on the track.")
    send_chat_message(sender=alice, goal_id=goal.id, body="Let's meet tomorrow at 7 AM.")

    _auth(client, _token(client, bob.email))
    url = f"/api/v1/goals/{goal.id}/chat/search/"
    res = client.get(url, {"q": "run"})
    assert res.status_code == 200
    results = res.json()["results"]
    assert len(results) == 1
    assert "Great run today" in results[0]["snippet"]
    assert results[0]["sender"]["name"] == "Alice Walker"


@pytest.mark.django_db
def test_chat_search_personal_goal_returns_404(client, alice):
    personal_goal = create_goal(
        creator=alice,
        title="Personal Journal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=False,
    )
    _auth(client, _token(client, alice.email))
    url = f"/api/v1/goals/{personal_goal.id}/chat/search/"
    res = client.get(url, {"q": "journal"})
    assert res.status_code == 404


@pytest.mark.django_db
def test_chat_search_unrelated_user_returns_404(client, alice, charlie):
    goal = create_goal(
        creator=alice,
        title="Secret Shared Goal",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=True,
    )
    send_chat_message(sender=alice, goal_id=goal.id, body="Confidential discussion")

    _auth(client, _token(client, charlie.email))
    url = f"/api/v1/goals/{goal.id}/chat/search/"
    res = client.get(url, {"q": "Confidential"})
    assert res.status_code == 404


@pytest.mark.django_db
def test_chat_search_removed_participant_returns_404(client, alice, bob):
    goal = create_goal(
        creator=alice,
        title="Cycling Club",
        recurrence_kind=Goal.RecurrenceKind.DAILY,
        start_date=timezone.now().date(),
        is_shared=True,
    )
    p_bob = invite_participant(actor=alice, goal_id=goal.id, user_id=bob.id)
    accept_invitation(actor=bob, goal_id=goal.id)
    send_chat_message(sender=alice, goal_id=goal.id, body="Route plan for Saturday")

    # Bob leaves or gets removed
    from apps.goals.services import remove_participant
    remove_participant(actor=alice, goal_id=goal.id, participant_id=p_bob.id)

    _auth(client, _token(client, bob.email))
    url = f"/api/v1/goals/{goal.id}/chat/search/"
    res = client.get(url, {"q": "Route"})
    assert res.status_code == 404


# ============================================================================
# 4. QUERY COUNT REGRESSION TESTS
# ============================================================================


@pytest.mark.django_db
def test_global_search_query_count_is_bounded(client, alice, django_assert_num_queries):
    for i in range(10):
        Commitment.objects.create(created_by=alice, title=f"Task item {i}", status=Commitment.Status.PENDING)
        create_goal(creator=alice, title=f"Goal item {i}", recurrence_kind=Goal.RecurrenceKind.DAILY, start_date=timezone.now().date(), is_shared=False)

    _auth(client, _token(client, alice.email))
    with django_assert_num_queries(6):  # 3 Auth/session queries + commitments query + personal goals query + shared goals query
        res = client.get(SEARCH_URL, {"q": "item"})
        assert res.status_code == 200
        assert len(res.json()["commitments"]) == 10
        assert len(res.json()["goals"]) == 10
