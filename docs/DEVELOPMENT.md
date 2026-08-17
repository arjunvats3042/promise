# Promise — Development Plan

## 1. Development Philosophy

Build Promise incrementally.

Never generate the entire application at once.

For every feature:

```text
Requirement
    ↓
Understand
    ↓
Design
    ↓
Implement
    ↓
Test
    ↓
Verify
    ↓
Commit
```

The goal is to always have a working project.

---

# 2. Development Phases

## Phase 0 — Environment

### Completed

- Git
- Java 17
- Python 3.12
- Docker Desktop
- Docker Compose
- Android Studio
- Android SDK / ADB
- Cursor
- Postman
- DataGrip

---

# 3. Phase 1 — Repository and Documentation

### Completed

- local Git repository
- personal GitHub repository
- monorepo structure
- `.gitignore`
- `README.md`
- `docs/PROJECT_PLAN.md`

### Current documentation target

```text
docs/
├── PROJECT_PLAN.md
├── ARCHITECTURE.md
└── DEVELOPMENT.md
```

After creating architecture/development documents, commit:

```text
docs: add architecture and development plan
```

---

# 4. Phase 2 — Local Infrastructure

## Goal

Run the infrastructure locally through Docker Compose.

Services:

```text
PostgreSQL
Redis
Kafka
```

Later:

```text
FastAPI
AI Worker
Notification Worker
```

## Tasks

### 2.1 Docker Compose

Create:

```text
docker-compose.yml
```

Configure:

- PostgreSQL
- Redis
- Kafka in KRaft mode
- named volumes
- health checks
- local ports
- Docker network
- ARM64-compatible images

### 2.2 PostgreSQL verification

Verify:

- container starts
- port is accessible
- database exists
- credentials work
- DataGrip can connect

### 2.3 Redis verification

Verify:

```text
PING
→ PONG
```

### 2.4 Kafka verification

Verify:

- broker starts
- topic can be created
- producer can publish
- consumer can receive
- persistence works

### 2.5 Infrastructure commit

```text
feat: add local infrastructure
```

---

# 5. Phase 3 — Backend Foundation

## Goal

Create a production-structured FastAPI modular monolith.

Target:

```text
backend/
├── app/
├── tests/
├── Dockerfile
├── pyproject.toml
└── ...
```

## Tasks

### 3.1 Python environment

Create a project environment using Python 3.12.

### 3.2 FastAPI

Implement:

```text
GET /health
```

Expected:

```json
{
  "status": "ok"
}
```

### 3.3 Configuration

Create typed configuration for:

- environment
- database URL
- Redis URL
- Kafka configuration
- auth configuration
- AI configuration

### 3.4 Database

Add:

- SQLAlchemy
- Alembic
- PostgreSQL connection
- migration framework

### 3.5 Logging

Add structured application logging.

### 3.6 Error handling

Add:

- consistent error format
- application error codes
- exception handlers

### 3.7 Testing

Add:

- health test
- configuration test
- database connectivity test

Commit:

```text
feat: bootstrap FastAPI backend
```

---

# 6. Phase 4 — Authentication

## Goal

Create secure user identity.

Tasks:

- choose authentication provider/strategy
- user model
- token verification
- current-user dependency
- protected routes
- authorization checks

Rules:

- never trust client `user_id`
- every protected resource must be scoped to the authenticated user
- secrets remain outside Git

Commit:

```text
feat: add authentication
```

---

# 7. Phase 5 — Commitment Domain

## Goal

Implement the first core product capability.

## Database

Create:

```text
commitments
commitment_events
```

Potential fields:

```text
id
user_id
action
person_id
description
due_at
status
source
created_at
updated_at
completed_at
```

## Backend

Implement:

- model
- schema
- repository
- service
- routes
- state transitions

API:

```text
POST   /v1/commitments
GET    /v1/commitments
GET    /v1/commitments/{id}
PATCH  /v1/commitments/{id}
POST   /v1/commitments/{id}/complete
POST   /v1/commitments/{id}/snooze
POST   /v1/commitments/{id}/cancel
```

## Events

Create:

```text
commitment.created
commitment.completed
commitment.overdue
```

Use transactional outbox.

## Tests

Test:

- create
- update
- completion
- snooze
- cancellation
- overdue
- authorization
- idempotency
- event creation

Commit:

```text
feat: add commitment domain
```

---

# 8. Phase 6 — Goal Domain

## Goal

Implement recurring personal commitments.

Tables:

```text
goals
goal_schedules
check_ins
```

## Goal creation

Support:

- binary
- count
- duration
- frequency
- schedule
- reduction

## Check-in API

```text
POST /v1/goals/{id}/check-ins
GET  /v1/goals/{id}/history
```

## Events

```text
goal.created
goal.checkin.created
goal.checkin.missed
```

## Tests

Test:

- schedule validation
- check-in creation
- duplicate check-in prevention
- partial completion
- missed check-in
- timezone behavior
- consistency calculations

Commit:

```text
feat: add goals and check-ins
```

---

# 9. Phase 7 — Redis Integration

## Goal

Introduce Redis only where it provides a concrete benefit.

First use cases:

### Cache

Cache:

```text
today's dashboard
active goals
recent insights
```

### Rate limiting

Start with AI endpoint rate limiting.

### Distributed lock

Use for scheduled notification processing.

### Temporary state

Use for capture processing status if appropriate.

## Tasks

- Redis client
- key naming convention
- TTL policy
- cache invalidation
- rate limiter
- lock helper
- tests

Commit:

```text
feat: integrate redis
```

---

# 10. Phase 8 — Kafka Integration

## Goal

Create reliable asynchronous processing.

Tasks:

- Kafka client
- producer abstraction
- event envelope
- topic configuration
- outbox publisher
- consumer abstraction
- retries
- dead-letter handling
- idempotency

Start with:

```text
goal.checkin.created
commitment.created
commitment.completed
```

Consumers:

```text
analytics
notification
```

AI consumer will come later.

Commit:

```text
feat: add kafka event pipeline
```

---

# 11. Phase 9 — Android Foundation

## Goal

Create the Android application.

Use:

- Kotlin
- Jetpack Compose
- Navigation
- ViewModel
- Retrofit
- Coroutines
- Room where useful
- dependency injection

Target structure:

```text
android/
└── app/
    └── src/main/java/...
        ├── ui/
        ├── navigation/
        ├── domain/
        ├── data/
        ├── di/
        └── core/
```

Tasks:

1. Create Gradle Android project.
2. Configure package/application ID.
3. Create Compose theme.
4. Create navigation.
5. Create API client.
6. Add backend environment configuration.
7. Create authentication flow.
8. Build Home shell.

Commit:

```text
feat: bootstrap android app
```

---

# 12. Phase 10 — Android Commitment UX

Screens:

- Home
- Commitments
- Commitment Detail
- Create Commitment

Actions:

- complete
- snooze
- cancel
- edit

Implement:

- loading states
- error states
- empty states
- optimistic UI only where safe
- retry behavior

Commit:

```text
feat: add commitment experience
```

---

# 13. Phase 11 — Android Goal UX

Screens:

- Goals
- Create Goal
- Goal Detail
- Check-in

Build:

- goal creation
- schedules
- quick check-in
- progress
- history

Focus heavily on interaction speed.

Commit:

```text
feat: add goal and check-in experience
```

---

# 14. Phase 12 — Motion and Polish

Implement:

- Compose transitions
- completion animation
- progress animations
- screen transitions
- subtle haptics
- loading skeletons
- pull-to-refresh if useful

Rule:

Animation must support comprehension and feedback.

Avoid decorative animation that slows the user.

Commit:

```text
feat: polish mobile experience
```

---

# 15. Phase 13 — Notifications

Backend:

- notification model
- scheduler
- notification worker
- FCM integration

Android:

- notification permission
- channels
- deep links
- notification actions

Support:

- commitment reminders
- goal reminders
- daily check-in
- overdue reminders

Commit:

```text
feat: add notifications
```

---

# 16. Phase 14 — AI Capture

## Text capture

```text
Android Share
 ↓
Promise
 ↓
POST /v1/captures
 ↓
Kafka
 ↓
AI worker
```

## Screenshot capture

```text
Screenshot
 ↓
Upload
 ↓
Object storage / temporary processing
 ↓
OCR
 ↓
LLM
```

## AI output

Must contain:

```text
type
action
person
deadline
confidence
```

Then:

```text
AI suggestion
 ↓
User confirmation
 ↓
Create commitment
```

Never silently create a critical commitment.

Commit:

```text
feat: add ai commitment capture
```

---

# 17. Phase 15 — AI Insights

Build:

- weekly summary
- goal consistency insight
- reminder-time insight
- missed-pattern insight
- commitment load insight

Store structured insights.

Example:

```text
insight_type
title
body
evidence
generated_at
```

Commit:

```text
feat: add ai insights
```

---

# 18. Phase 16 — Analytics

Track:

```text
onboarding.completed
commitment.created
commitment.completed
goal.created
goal.checkin.completed
goal.checkin.missed
ai.extraction.confirmed
ai.extraction.corrected
notification.opened
```

Never send raw conversation text to analytics.

Commit:

```text
feat: add product analytics
```

---

# 19. Phase 17 — Reliability and Observability

Implement:

- health checks
- structured logs
- request IDs
- event IDs
- metrics
- Kafka lag monitoring
- worker monitoring
- AI failure metrics
- crash reporting

Test:

- DB failure
- Redis failure
- Kafka unavailable
- AI provider unavailable
- duplicate event
- worker restart

Commit:

```text
chore: improve reliability and observability
```

---

# 20. Phase 18 — Security and Privacy

Audit:

- authentication
- authorization
- secrets
- file upload limits
- input validation
- rate limits
- data deletion
- capture retention
- privacy settings
- logs for accidental sensitive data

Create:

- privacy policy
- terms
- data deletion flow

Commit:

```text
chore: harden security and privacy
```

---

# 21. Phase 19 — Deployment

## Backend

Build Docker image.

Deploy to chosen container platform.

Configure:

- managed PostgreSQL
- managed Redis
- managed Kafka
- secrets
- HTTPS
- domain
- logging
- monitoring

## Android

Configure:

- release signing
- application ID
- production API URL
- Play App Signing
- AAB

---

# 22. Phase 20 — CI/CD

GitHub Actions:

### Backend

```text
push
 ↓
lint
 ↓
test
 ↓
integration test
 ↓
docker build
 ↓
deploy staging
```

### Android

```text
push
 ↓
build
 ↓
test
 ↓
generate AAB
```

Production deployment should require explicit approval initially.

---

# 23. Phase 21 — Play Store

Checklist:

- app name
- icon
- screenshots
- feature graphic if required
- privacy policy
- Data Safety
- content declarations
- permissions review
- release signing
- internal testing
- closed testing if required
- production rollout

Start with staged rollout.

---

# 24. Definition of Done for Every Feature

A feature is not complete when code compiles.

It is complete when:

- requirements are understood
- architecture is respected
- implementation is complete
- tests exist
- error cases are handled
- logs/metrics are appropriate
- security is considered
- manual flow is verified
- documentation is updated where needed
- Git commit is made

---

# 25. Cursor Workflow

For each task, give Cursor a narrow prompt.

Example:

> Read `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md`, and `docs/DEVELOPMENT.md`. Implement Phase 3.2 only: the FastAPI health endpoint. Do not add authentication, database models, Kafka, Redis, or unrelated abstractions. Run the relevant tests and report exactly what changed.

Then inspect the code.

Never blindly accept large generated changes.

---

# 26. Commit Strategy

Prefer small, meaningful commits.

Examples:

```text
chore: initialize Promise project
docs: add architecture and development plan
feat: add local infrastructure
feat: bootstrap FastAPI backend
feat: add authentication
feat: add commitment domain
feat: add goals and check-ins
feat: integrate redis
feat: add kafka event pipeline
feat: bootstrap android app
feat: add commitment experience
feat: add goal and check-in experience
feat: add notifications
feat: add ai commitment capture
feat: add ai insights
```

Avoid:

```text
build entire application
fix stuff
changes
final
```

---

# 27. Current Checkpoint

Completed:

```text
[x] Environment
[x] Local Git repository
[x] Personal GitHub repository
[x] Git identity isolation
[x] Monorepo directories
[x] README
[x] .gitignore
[x] PROJECT_PLAN.md
```

Next:

```text
[ ] ARCHITECTURE.md
[ ] DEVELOPMENT.md
[ ] Documentation commit
[ ] Docker Compose
[ ] PostgreSQL
[ ] Redis
[ ] Kafka
```

---

# 28. Immediate Next Task

Do not start backend or Android implementation yet.

First complete:

1. `docs/ARCHITECTURE.md`
2. `docs/DEVELOPMENT.md`
3. Review both
4. Commit documentation
5. Start Docker infrastructure

This ensures implementation begins from an agreed architecture rather than from generated code.
