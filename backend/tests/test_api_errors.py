from django.test import override_settings
from rest_framework.test import APIClient


def test_health_returns_ok():
    client = APIClient()
    response = client.get("/api/v1/health/")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_api_404_uses_error_structure():
    client = APIClient()
    response = client.get("/api/v1/does-not-exist/")

    assert response.status_code == 404
    assert response.json() == {
        "error": {
            "code": "NOT_FOUND",
            "message": "Resource was not found.",
        }
    }


@override_settings(ROOT_URLCONF="tests.foundation_urls")
def test_invalid_json_uses_error_structure():
    client = APIClient()
    response = client.post(
        "/api/v1/_test/validate/",
        data="{not-json",
        content_type="application/json",
    )

    assert response.status_code == 400
    body = response.json()
    assert body["error"]["code"] == "INVALID_REQUEST"
    assert body["error"]["message"] == "Request is invalid."
    assert "secret" not in str(body).lower()
    assert "traceback" not in str(body).lower()


@override_settings(ROOT_URLCONF="tests.foundation_urls")
def test_validation_error_uses_error_structure():
    client = APIClient()
    response = client.post(
        "/api/v1/_test/validate/",
        {"email": "not-an-email"},
        format="json",
    )

    assert response.status_code == 400
    body = response.json()
    assert body["error"]["code"] == "VALIDATION_ERROR"
    assert body["error"]["message"] == "Request validation failed"
    assert "email" in body["error"]["details"]


@override_settings(ROOT_URLCONF="tests.foundation_urls")
def test_unexpected_exception_returns_generic_500():
    client = APIClient()
    response = client.get("/api/v1/_test/boom/")

    assert response.status_code == 500
    assert response.json() == {
        "error": {
            "code": "INTERNAL_SERVER_ERROR",
            "message": "An unexpected error occurred.",
        }
    }
    assert "secret internal details" not in response.content.decode()
