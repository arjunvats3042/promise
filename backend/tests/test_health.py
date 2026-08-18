from rest_framework.test import APIClient


def test_health_returns_ok():
    client = APIClient()
    response = client.get("/api/v1/health/")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
