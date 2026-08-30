<div align="center">

# Promise

**Keep every promise you make to yourself.**

A calm, local-first daily commitment and habit accountability platform with quiet AI parsing, home screen widgets, and offline synchronization.

[Features](#features) • [Tech Stack](#tech-stack) • [Architecture](#architecture) • [Quick Start](#quick-start) • [Documentation](#documentation)

---

</div>

## Overview

Promise is designed around a single idea: **a quiet desk** — paper, ink, and disciplined follow-through. It replaces noisy, cluttered productivity tools with zero-latency optimistic interactions, structured daily integrity tracking, and complete offline reliability.

### Key Capabilities

- **Offline-First Resilience**: Built on local SQLite (Room) with background outbox synchronization (`WorkManager`). Create tasks, check in on habits, and track streaks with zero network latency.
- **Thought → Promise (Voice & AI Engine)**: Drop in raw, messy thoughts or voice speech transcripts. Gemini decomposes compound sentences into discrete commitments and habits with natural language temporal parsing.
- **Dynamic Year Progress Wallpaper**: Generates a 365-day constellation lock screen wallpaper and home widget dynamically scheduled to advance every night at midnight via WorkManager.
- **Glance Home Screen Widget Suite**: Complete daily tasks, check in on goals, review year progress, and trigger 1-tap floating mic voice capture straight from Android launcher.
- **Daily Focus & Integrity Streaks**: Clear visual separation between today's immediate priorities and upcoming commitments. Streaks are preserved across midnight rollovers.
- **Shared Accountability & Real-Time Rooms**: Form shared habits with friends or teammates via in-app invites, track shared momentum, and message in real-time with deterministic epoch timestamp ordering.
- **Quiet, Meaningful Reminders**: Intentional notifications with 1-tap actions (`✓ Complete`, `⏰ Snooze 1h`, `✓ Check In`), inline direct chat replies, and smart streak protection.

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Android Client** | Kotlin, Jetpack Compose, Glance Widgets, Room Database, Hilt, Coroutines & Flow, Material 3 |
| **Backend API** | Python 3.12, Django, Django REST Framework, Daphne (ASGI), Django Channels |
| **Databases** | PostgreSQL (Primary Source of Truth), Redis (Sessions & Rate Limiting), SQLite / Room (Mobile) |
| **Event Streaming** | Apache Kafka (Transactional Outbox events on `promise.goal.v1` and `promise.commitment.v1`) |
| **AI Intelligence** | Google Gemini (Structured outputs with dynamic temporal reference grounding) |
| **Deployment** | Docker, Railway (ASGI Server + Background Workers) |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Client                         │
│  Jetpack Compose • Glance Widget • Room Local DB • Outbox   │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS / WebSockets
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    Django ASGI Backend                      │
│     DRF REST API • Channels WebSockets • apps.sync Outbox    │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐┌─────────────────────────────┐
│     PostgreSQL Database      ││     Redis Cache & PubSub    │
│  ACID Domain Records & Audit ││   Rate Limiting & Realtime  │
└──────────────┬───────────────┘└─────────────────────────────┘
               │ Transactional Outbox
               ▼
┌─────────────────────────────────────────────────────────────┐
│                  Kafka Event Backbone                       │
│    Goal Worker • AI Worker • Push Notification Worker       │
└─────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Android Studio Ladybug+ / JDK 17+
- Python 3.12+

### 1. Start Infrastructure
```bash
docker compose up -d
```

### 2. Start Django Backend
```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py runserver 8000
```

### 3. Run Android App
Open the `/android` directory in Android Studio, or build the debug APK directly via command line:
```bash
cd android
./gradlew assembleDebug
```

---

## Project Structure

```text
promise/
├── android/                 # Native Android app (Kotlin, Compose, Room, Glance)
│   └── app/src/main/java/   # Core, Data, Domain, DI, UI, Widgets
├── backend/                 # Django modular backend
│   ├── apps/
│   │   ├── ai/              # Gemini AI decomposition & summaries
│   │   ├── authentication/  # JWT & Google Credential Manager auth
│   │   ├── commitments/     # One-off promises & task lifecycles
│   │   ├── goals/           # Recurring habits & shared goals
│   │   ├── notifications/   # Push reminder rules & FCM dispatch
│   │   ├── outbox/          # Transactional Kafka outbox worker
│   │   └── sync/            # Offline batch outbox synchronization
│   └── config/              # ASGI, WSGI, URLs, Settings
└── docs/                    # Architectural specs & design guides
```

---

## Documentation

Detailed architecture specifications and runbooks are available in the [`docs/`](docs/) directory:

- [System Architecture](docs/ARCHITECTURE.md)
- [Android Architecture & Design](docs/ANDROID_DESIGN.md)
- [AI Engine & Prompt Architecture](docs/AI.md)
- [Authentication & Security](docs/AUTHENTICATION_DESIGN.md)
- [Commitment System Design](docs/COMMITMENT_DESIGN.md)
- [Goal & Habit Design](docs/GOAL_DESIGN.md)
- [Production Deployment Runbook](docs/PRODUCTION_DEPLOYMENT.md)

---

## License

Private & Proprietary. All rights reserved.
