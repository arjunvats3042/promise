# Promise

Personal commitment, goal accountability, and shared achievement platform.

---

## Current Status (Batch 14B-PRE)

- **Feature Implementation**: **Complete** through Batch 14A (Authentication, Shared Goals, Real-Time WebSocket Chat, Notifications/FCM, Redis Rate Limiting, Kafka Transactional Outbox, Google Gemini AI, Product Analytics, Search & Discovery, Production Hardening).
- **Deployment Readiness**: **Prepared** for Railway multi-service deployment with Aiven Managed Kafka.
- **Public Availability**: **Not yet publicly deployed.** Infrastructure preparation and local container validations complete; live cloud provisioning pending.
- **Multi-Device QA**: Release qualification on physical two-device hardware pending.

---

## Technology Stack

- **Backend**: Python 3.12, Django 6.1, Django REST Framework, Django Channels (Daphne ASGI).
- **Primary Database**: PostgreSQL 17 (ACID domain state, outbox, processed events).
- **Cache & Real-Time Layer**: Redis 8 (Rate limiting, session denylist, chat presence, Channels fanout).
- **Event Streaming**: Apache Kafka (Transactional Outbox events on `promise.goal.v1` and `promise.commitment.v1`).
- **AI Intelligence**: Google Gemini (3-Key fallback chain, structured outputs, strict privacy boundaries).
- **Android Client**: Kotlin, Jetpack Compose, Material 3, Hilt, Coroutines/Flow, Retrofit, OkHttp WebSocket.
- **Target Deployment Platform**: Railway (Web & 3 Background Workers) + Aiven (Managed Kafka).

---

## Repository Structure

```text
android/               Android application (Kotlin + Jetpack Compose)
backend/               Django ASGI application & workers
docs/                  Architecture, Security, AI, Analytics, Deployment runbooks
docker-compose.yml     Local infrastructure (PostgreSQL, Redis, Kafka)
railway.toml           Railway multi-service deployment configuration
Procfile               Process manager definitions
```

---

## Local Development

### 1. Start Local Infrastructure
```bash
docker compose up -d
```

### 2. Start Django Backend & Workers
```bash
cd backend
source .venv/bin/activate
python manage.py migrate
python manage.py runserver 8000
```

### 3. Build & Run Android App
```bash
cd android
./gradlew assembleDebug
```

---

## Testing

```bash
# Backend Automated Tests (651 tests)
cd backend
./.venv/bin/pytest -v

# Django System & Migration Checks
./.venv/bin/python manage.py check
./.venv/bin/python manage.py makemigrations --check

# Android JVM Unit Tests
cd android
./gradlew test

# Real Gemini AI External Integration (Optional / Manual)
cd backend
RUN_REAL_AI_TESTS=1 GEMINI_API_KEY_1="AIzaSy..." ./.venv/bin/pytest tests/test_real_ai_integration.py -v
```

See [`docs/TESTING.md`](docs/TESTING.md) for full testing documentation.

---

## Production Deployment

Promise is configured for automated deployment to **Railway** across 4 dedicated services sharing internal networking:
- `promise-web` (Daphne ASGI Server)
- `promise-outbox-worker` (Transactional Outbox Publisher)
- `promise-goal-worker` (Kafka Goal Event Consumer)
- `promise-notification-worker` (FCM Reminder Dispatcher)

See [`docs/PRODUCTION_DEPLOYMENT.md`](docs/PRODUCTION_DEPLOYMENT.md) and [`docs/DEPLOYMENT_CHECKLIST.md`](docs/DEPLOYMENT_CHECKLIST.md) for runbooks and checklists.
