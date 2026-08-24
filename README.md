# Promise

Personal commitment, goal accountability, and shared achievement platform.

---

## Current Status (Latest Dev / Batch 16+)

- **Feature Implementation**: **Complete** through Latest Dev (Profile Avatars with local & remote sync, Native Google Credential Manager Auth, AI Sheets & Cards, Round-Robin Multi-Key Gemini 3.5 Routing, Resilient Weekly Insights Dashboard, Daily Motivation Quotes, Personal Goal Conversion to Shared Status, Shared Goals with Real-Time WebSocket Chat, In-App Software Update mechanism via Firebase App Distribution, Notifications & In-App Delivery History, Custom Morning/Evening Anchor & Quiet Hours Time Pickers, Redis Rate Limiting, Kafka Transactional Outbox, Product Analytics, Search & Discovery, Feature-Selling FAQs, Developer Tech Signature, Finger-Synced Horizontal Pager, Layered Dark Mode, Skeleton Loaders).
- **Deployment Readiness**: **Prepared** for Railway multi-service deployment with Aiven Managed Kafka.
- **Public Availability**: **Not yet publicly deployed.** Infrastructure preparation and local container validations complete; live cloud provisioning pending.
- **Multi-Device QA**: Verified across physical hardware; release builds signed and verified.

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

## Production Deployment

Promise is configured for automated deployment to **Railway** across 4 dedicated services sharing internal networking:
- `promise-web` (Daphne ASGI Server)
- `promise-outbox-worker` (Transactional Outbox Publisher)
- `promise-goal-worker` (Kafka Goal Event Consumer)
- `promise-notification-worker` (FCM Reminder Dispatcher)

See [`docs/PRODUCTION_DEPLOYMENT.md`](docs/PRODUCTION_DEPLOYMENT.md) and [`docs/DEPLOYMENT_CHECKLIST.md`](docs/DEPLOYMENT_CHECKLIST.md) for runbooks and checklists.
