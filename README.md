# Promise

Personal commitment and accountability platform.

## Stack

- Backend: Python 3.12, Django 6.1, Django REST Framework
- PostgreSQL
- Redis
- Kafka
- Local infrastructure: Docker Compose
- Android: Kotlin + Jetpack Compose (not started)

## Repository

```text
android/             Android application (not started)
backend/             Django + Django REST Framework
docs/                Product, architecture, and development documentation
docker-compose.yml   PostgreSQL, Redis, Kafka
```

## Current API

```text
GET /api/v1/health/   →  {"status": "ok"}
```
