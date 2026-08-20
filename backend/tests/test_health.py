from rest_framework.test import APIClient


def test_health_returns_ok():
    client = APIClient()
    response = client.get("/api/v1/health/")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_health_returns_json_for_browser_accept():
    client = APIClient()
    response = client.get(
        "/api/v1/health/",
        HTTP_ACCEPT="text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    )

    assert response.status_code == 200
    assert response["Content-Type"].startswith("application/json")
    assert response.json() == {"status": "ok"}
