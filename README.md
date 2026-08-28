# Promise

Personal commitment tracking, daily habit accountability, and shared achievement platform with Gemini AI insights and interactive Android widgets.

---

## Highlights & Features

- **World-Class Modern UI/UX**:
  - **Atmospheric Designer Login**: Ambient radial lighting, custom Compose Canvas 4-color Google badge, and interactive feature showcase bento cards.
  - **Ergonomic Bottom Navigation Bar**: 64dp elevated container with `.navigationBarsPadding()`, 24dp icons, dedicated typography labels, and spring physics micro-interactions.
  - **Streamlined 3-Category Commitments**: Reduced cognitive clutter with focused tabs: `All Open` (active to-dos), `Overdue` (zero-overdue attention), and `Completed` (archived).
  - **Instantaneous Tab Transitions & Shimmer Skeletons**: Zero-latency tab pill highlights paired with elegant shimmer skeleton placeholders to eliminate stale or false empty-state flashes.
  - **Celebration Confetti & Physics**: Multi-layered particle celebration bursts for milestone achievements.

- **Autonomous Interactive Glance Widgets**:
  - **Full Scrollable Lists**: Glance `LazyColumn` for viewing all daily goals and commitments on the home screen.
  - **1-Tap In-Widget Check-Ins**: Mark habits complete or finish commitments directly from the home screen widget without launching the app.
  - **Live Backend Sync (`🔄`)**: Dedicated refresh action to pull fresh data on demand.
  - **Deep-Linking**: Tapping any goal or commitment jumps directly into its targeted detail screen.

- **Rich Interactive Notifications**:
  - **1-Tap Actions**: `✓ Complete`, `⏰ Snooze 1h`, `✓ Check In` with immediate background synchronization and widget refresh.
  - **Inline Direct Reply (`RemoteInput`)**: Reply to shared goal community chats directly from the Android notification shade.
  - **Deterministic Entity Deduplication**: Prevents duplicate notification spam by updating notifications in-place per entity.
  - **Designer Typography & Urgency Styling**: `BigTextStyle` formatting with streak badges (`🔥`), urgency tags (`⚠️ OVERDUE`, `🚨 Due Right Now`), and Promise Indigo branding.

- **Gemini AI Intelligence**:
  - Round-robin multi-key Gemini 3.5 routing with automatic fallbacks.
  - Weekly behavioral digests, daily motivation quotes, and AI goal/commitment refiners.

---

## Technology Stack

- **Android Client**: Kotlin, Jetpack Compose, Glance AppWidgets, Material 3, Hilt, Coroutines & Flow, Retrofit, OkHttp WebSocket, WorkManager.
- **Backend**: Python 3.12, Django 6.1, Django REST Framework, Django Channels (Daphne ASGI).
- **Primary Database**: PostgreSQL 17 (ACID domain state, outbox, processed events).
- **Cache & Real-Time Layer**: Redis 8 (Rate limiting, session denylist, chat presence, Channels fanout).
- **Event Streaming**: Apache Kafka (Transactional Outbox events on `promise.goal.v1` and `promise.commitment.v1`).
- **AI Engine**: Google Gemini API (Structured outputs, fallback chains, behavioral pattern analysis).
- **Target Deployment Platform**: Railway (Web & 3 Background Workers) + Aiven (Managed Kafka).

---

## Repository Structure

```text
android/               Android application (Kotlin + Jetpack Compose + Glance)
backend/               Django ASGI application & workers (apps: ai, analytics, authentication, commitments, goals, notifications, outbox)
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

