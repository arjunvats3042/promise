# Promise — Architecture

## 1. Architecture Goal

Promise should be technically strong enough to support a real Play Store launch while remaining simple enough for a small team to build and operate.

Primary architectural principles:

- PostgreSQL is the source of truth.
- Django ORM is the primary database access layer.
- Redis is a cache/temporary coordination layer, never the primary database.
- Kafka is the asynchronous event backbone, not a replacement for synchronous APIs.
- Start as a modular monolith.
- Use workers for asynchronous AI, notification, and analytics workloads.
- Core functionality must continue working when AI or another external service is unavailable.
- Prefer explicit boundaries over premature microservices.

---

## 2. System Context

```text
Android
   ↓
Django + Django REST Framework
   ↓
PostgreSQL
   ├── Redis
   └── Kafka
```

```text
                    +------------------+
                    |   Android App    |
                    | Kotlin + Compose |
                    +--------+---------+
                             |
                         HTTPS / JSON
                             |
                    +--------v---------+
                    | Django + DRF     |
                    | API / Auth       |
                    +---+---------+----+
                        |         |
                        |         |
                        v         v
                 PostgreSQL     Redis
                 Source of      Cache /
                   Truth       Temporary
                        |
                        v
               Transactional Outbox
                        |
                        v
                      Kafka
                        |
          +-------------+-------------+
          |             |             |
          v             v             v
      AI Worker   Notification    Analytics
                    Worker          Worker
          |             |
          v             v
       AI Provider      FCM
```

---

## 3. Component Responsibilities

### Android

Responsible for:

- UI
- navigation
- local UI state
- local persistence where useful
- authentication state
- API calls
- notification handling
- Share Sheet capture
- user confirmation of AI-generated suggestions

Android must not contain business rules that belong to the backend.

### Django + Django REST Framework

Responsible for:

- authentication and authorization
- API validation
- synchronous commands
- transactions
- domain services
- persistence through Django ORM
- creating outbox events

Django/DRF should return quickly for asynchronous operations rather than waiting for AI/Kafka workers.

### PostgreSQL

Canonical source of application state.

Stores:

- users
- commitments
- goals
- schedules
- check-ins
- notifications
- captures
- AI insights
- outbox events
- audit/history records

### Redis

Used for:

- cache
- rate limiting
- distributed locks
- temporary processing state
- short-lived coordination
- notification-related coordination where appropriate

Redis loss must not cause permanent application-data loss.

### Kafka

Used for:

- asynchronous business events
- fan-out
- worker communication
- replay
- decoupling

Kafka is the event backbone for asynchronous/domain events.

Kafka should not be placed between Android and Django/DRF for ordinary CRUD.

### Workers

Workers consume Kafka events and perform work that does not need to block the user's HTTP request.

Examples:

- AI processing
- notification scheduling/delivery
- analytics
- insight generation

---

## 4. Backend Architecture

Use a modular monolith initially.

```text
backend/
├── manage.py
├── config/
│   ├── settings/
│   │   ├── base.py
│   │   └── local.py
│   ├── urls.py
│   ├── asgi.py
│   └── wsgi.py
├── apps/
│   ├── users/
│   ├── commitments/
│   ├── goals/
│   ├── challenges/
│   └── notifications/
├── tests/
├── pyproject.toml
└── README.md
```

Workers remain part of the modular monolith and consume Kafka events. Do not create a separate microservice for every domain during MVP.

### Layer responsibilities

**API layer (Django REST Framework)**

- HTTP concerns
- authentication and permission classes
- serializers
- status codes

**Service/domain layer**

- business rules
- state transitions
- transaction orchestration

**Django ORM layer**

- models
- managers/querysets
- persistence

**Event layer**

- event schemas
- event publishing through the transactional outbox
- consumer contracts

**Worker layer**

- asynchronous processing

Do not allow DRF views to contain large business workflows.

---

## 5. Request Flow

For synchronous operations:

```text
Android
  ↓
HTTP
  ↓
Django REST Framework view
  ↓
Validation (DRF serializer)
  ↓
Service
  ↓
Django ORM
  ↓
PostgreSQL
  ↓
Response
```

For asynchronous operations:

```text
Android
  ↓
Django + Django REST Framework
  ↓
PostgreSQL transaction
  ├── application state
  └── outbox event
  ↓
HTTP response
  ↓
Outbox publisher
  ↓
Kafka
  ↓
Worker
  ↓
Result / side effect
```

---

## 6. Transactional Outbox

When a database change must produce an event:

```text
BEGIN
    UPDATE/INSERT domain state
    INSERT outbox_events row
COMMIT
```

The outbox publisher reads unpublished rows and publishes them to Kafka.

After successful publication, mark the outbox event as published.

The system must tolerate duplicate publication because a crash can happen after Kafka accepts an event but before the database records publication success.

Intended flow:

```text
Application transaction
    ↓
PostgreSQL
    ↓
Outbox event
    ↓
Kafka
    ↓
Consumers
```

Do not replace the outbox with direct database-to-Kafka publishing.

Therefore:

- every event has a unique `event_id`
- consumers are idempotent
- consumers may maintain processed-event records where necessary

---

## 7. Kafka Architecture

Initial topics:

```text
promise.commitment.v1
promise.goal.v1
promise.checkin.v1
promise.capture.v1
promise.ai.v1
promise.notification.v1
```

The exact topic split can be adjusted after implementation experience.

Example events:

```text
commitment.created
commitment.completed
goal.created
goal.checkin.created
shared.promise.completed
challenge.joined
notification.requested
```

Event envelope:

```json
{
  "event_id": "uuid",
  "event_type": "goal.checkin.created",
  "version": 1,
  "occurred_at": "2026-08-18T20:00:00Z",
  "user_id": "uuid",
  "aggregate_id": "uuid",
  "payload": {}
}
```

For user-scoped events:

```text
partition_key = user_id
```

This gives ordering for events belonging to the same user while allowing horizontal distribution across users.

---

## 8. Kafka Consumer Groups

Example:

```text
ai-service
notification-service
analytics-service
```

Each group receives the event independently.

Example:

```text
goal.checkin.created
          |
        Kafka
     /     |      \
    /      |       \
   v       v        v
  AI    Notification Analytics
```

Consumers must be:

- idempotent
- retryable
- observable

Failed messages should eventually reach a dead-letter mechanism.

---

## 9. Redis Architecture

Implemented product keys (denylist + rate limits) are in `docs/REDIS_DESIGN.md`. That document is canonical. The examples below are future cache / AI / lock sketches.

Example keys:

```text
promise:cache:user:{user_id}:today
promise:cache:user:{user_id}:goals
promise:cache:user:{user_id}:insights

promise:ratelimit:ai:user:{user_id}

promise:lock:notification:{notification_id}

promise:processing:capture:{capture_id}
```

Redis may also be used for notification-related coordination where appropriate.

### TTL policy

Caches and temporary processing keys should have explicit TTLs.

Do not introduce indefinite Redis keys without a reason.

### Cache pattern

```text
Request
  ↓
Redis?
  ├── HIT → return
  |
  └── MISS
       ↓
   PostgreSQL
       ↓
     Redis
       ↓
    return
```

Important writes should invalidate/update relevant cache entries.

---

## 10. Database Architecture

PostgreSQL is the primary/source-of-truth database.

Django ORM is the primary database access layer.

Use Django migrations for schema evolution.

Initial entities:

```text
users
people
commitments
commitment_events
goals
goal_schedules
check_ins
notifications
captures
ai_insights
outbox_events
processed_events
```

Use UUIDs for public identifiers.

Use timestamps with explicit timezone semantics.

---

## 11. Commitment State Machine

```text
                 +----------+
                 | PENDING  |
                 +----+-----+
                  /   |   \
                 /    |    \
                v     v     v
          COMPLETED OVERDUE SNOOZED
                              |
                              v
                           PENDING

PENDING → WAITING
WAITING → COMPLETED
WAITING → CANCELLED

PENDING → CANCELLED
OVERDUE → COMPLETED
```

State transitions must be enforced by domain logic rather than arbitrary database updates from controllers.

---

## 12. Goal / Check-in Architecture

```text
Goal
 |
 +-- schedule
 |
 +-- target
 |
 +-- tracking mode
 |
 +-- check-ins
       |
       +-- date
       +-- status
       +-- value
       +-- note
```

Check-in creation is a business operation, not merely an INSERT.

It should:

1. validate the goal
2. validate the requested date
3. validate tracking value
4. create/update the check-in according to idempotency rules
5. update relevant derived state
6. create an event through the outbox

---

## 13. AI Architecture

AI is asynchronous whenever latency is not required for the immediate user interaction.

### Capture flow

```text
Android Share Sheet
       ↓
Capture API
       ↓
Temporary storage
       ↓
capture.received
       ↓
Kafka
       ↓
AI worker
       ↓
OCR if needed
       ↓
LLM
       ↓
Structured schema validation
       ↓
Confidence evaluation
       ↓
Suggestion
       ↓
Android confirmation
       ↓
Commitment created
```

AI should suggest structured data, not directly mutate critical user state without confirmation.

---

## 14. AI Failure Handling

If the AI provider is unavailable:

```text
capture
  ↓
queued
  ↓
retry
  ↓
dead-letter / failed state
```

The rest of Promise must continue functioning.

AI calls should have:

- timeout
- retry policy
- provider error handling
- rate limits
- cost tracking
- schema validation

---

## 15. Notification Architecture

```text
Goal / Commitment
       ↓
Notification record
       ↓
Scheduler
       ↓
Kafka / Worker
       ↓
FCM
       ↓
Android
```

The database records the intended notification.

The worker handles delivery.

Use idempotency to avoid duplicate notifications.

Support:

- quiet hours
- per-goal reminders
- global notification settings
- user dismissal
- notification history

---

## 16. Android Architecture

Use a clean separation:

```text
UI
 ↓
ViewModel
 ↓
Use Case / Repository
 ↓
API / Local Storage
```

Recommended project direction:

```text
android/
└── app/
    └── src/main/java/...
        ├── ui/
        ├── navigation/
        ├── domain/
        ├── data/
        │   ├── remote/
        │   └── local/
        ├── di/
        └── core/
```

Jetpack Compose owns presentation.

ViewModels expose observable UI state.

Repositories abstract data sources.

---

## 17. Android Local Data

Room can be used for:

- cached goals
- recent commitments
- offline-friendly check-in state
- UI support data

The backend remains authoritative.

When offline:

```text
Android
  ↓
Local state
  ↓
Sync queue
  ↓
Backend
```

Offline behavior should be implemented carefully around idempotency and conflict resolution.

It is not required for the first MVP unless it materially improves the experience.

---

## 18. Authentication

Authentication is **Option B**: short-lived JWT access tokens plus server-side rotating opaque refresh sessions (`AuthSession` in PostgreSQL). Details are in [`AUTHENTICATION_DESIGN.md`](AUTHENTICATION_DESIGN.md).

```text
Android
  ↓
POST /api/v1/auth/login/ or /auth/refresh/
  ↓
Bearer access JWT (15 min)
  ↓
Django REST Framework (JWTAccessAuthentication)
  ↓
Authenticated user (request.user)
```

Refresh tokens are `{session_id}.{secret}`. HMAC-SHA256 of the secret is the canonical verifier. HMAC cannot reconstruct the current secret, so that secret is also stored as Fernet ciphertext (`AUTH_REFRESH_TOKEN_ENCRYPTION_KEY`) for a 30-second retry-grace only. Previous secrets are HMAC-only. After the grace window, presenting the previous token revokes the session. Plaintext refresh secrets are never persisted. Refresh concurrency uses PostgreSQL `SELECT FOR UPDATE` (Redis lock is deferred). Logout and logout-all set `revoked_at` on `AuthSession` rows and add each `sid` to Redis `promise:auth:denylist:sid:{sid}` with TTL = access JWT lifetime + leeway. Redis is a fast deny hint: if Redis is down, denylist reads/writes are skipped and PostgreSQL session checks still run. JWTs remain cryptographically valid until `exp`.

Use DRF authentication classes to verify credentials/tokens and permission classes to authorize access.

Every protected resource must be scoped to the authenticated user.

Never trust a client-provided `user_id` for authorization.

---

## 19. API / Domain Boundary

API schemas and database models should not be treated as the same object.

REST APIs are served by Django REST Framework and versioned under `/api/v1/`.

Example:

```text
GET /api/v1/health/
```

```text
HTTP request
    ↓
DRF serializer
    ↓
Domain/service logic
    ↓
Django ORM model
```

This keeps external contracts stable even if database implementation changes.

---

## 20. Error Handling

Use stable application error codes.

Example:

```json
{
  "error": {
    "code": "COMMITMENT_NOT_FOUND",
    "message": "Commitment was not found."
  }
}
```

Do not expose internal stack traces to clients.

Log internal details separately.

---

## 21. Idempotency

Important mutation APIs should support idempotency where duplicate requests are possible.

Example:

```text
POST /api/v1/goals/{id}/check-ins/
Idempotency-Key: <uuid>
```

A retry should not create two check-ins for the same logical operation.

Kafka consumers follow the same principle through event IDs and business keys.

---

## 22. Security Boundaries

Trust boundaries:

```text
Android
   |
   | untrusted network
   v
Django / DRF
   |
   +-- authentication
   +-- authorization
   +-- validation
   |
   v
Internal services
```

Never trust:

- client-provided roles
- client-provided user IDs
- AI output
- uploaded file metadata
- arbitrary event payloads

---

## 23. Observability Architecture

Every request/event should have correlation information where practical.

Track:

- request ID
- user ID where appropriate and privacy-safe
- event ID
- aggregate ID

Metrics:

- API latency/error rate
- DB latency
- Redis hit rate
- Kafka consumer lag
- worker failures
- AI latency/failure/cost
- Android crashes/ANRs

---

## 24. Deployment Architecture

### Development

```text
Docker Compose
├── Django
├── PostgreSQL
├── Redis
├── Kafka
└── Workers
```

### Production direction

```text
                    Google Play
                         |
                      Android
                         |
                       HTTPS
                         |
              Django API (WSGI/ASGI)
                         |
        +----------------+----------------+
        |                |                |
   PostgreSQL          Redis            Kafka
    managed           managed          managed
                                          |
                              +-----------+-----------+
                              |           |           |
                              v           v           v
                             AI      Notification  Analytics
```

Use managed infrastructure where it reduces operational burden.

The Django application should be served through an appropriate production WSGI/ASGI setup. Do not introduce a specific deployment implementation yet.

---

## 25. Scaling Strategy

### Initial

One backend deployment with separate worker processes.

### Growth

Scale:

- API independently
- AI workers independently
- notification workers independently
- Kafka partitions as required

### Only later

Consider:

- service extraction
- read replicas
- dedicated analytics storage
- advanced orchestration

Scaling decisions must be based on measured bottlenecks.

---

## 26. Architecture Invariants

These are non-negotiable unless explicitly changed:

1. PostgreSQL remains the source of truth.
2. Redis is not the primary database.
3. Kafka is not used for synchronous CRUD.
4. AI cannot silently create important user commitments.
5. Consumers must be idempotent.
6. DB + event publication uses transactional outbox.
7. Core functionality works without AI.
8. Secrets never enter source control.
9. User authorization is enforced server-side.
10. New infrastructure must have a concrete reason.

---

## 27. Architectural Decision Process

Before changing architecture:

1. Identify the problem.
2. Explain why the current architecture is insufficient.
3. List alternatives.
4. Compare trade-offs.
5. Choose the smallest solution that solves the problem.
6. Update architecture documentation.
7. Implement.
8. Test.

Do not let an AI coding assistant silently change system architecture.

