# Promise --- Master Project Plan

> **Promise** is an AI-powered personal commitment and accountability
> platform.
>
> **Core promise:** Remember what you promised others. Remember what you
> promised yourself.

## 1. Product Vision

Promise combines two problems normally handled by separate apps:

1.  **External commitments** --- things the user said they would do for
    another person.
2.  **Personal commitments** --- things the user wants to do
    consistently, such as studying, exercising, reading, or reducing an
    unwanted behavior.

The product should feel like a calm, intelligent accountability layer
rather than a traditional task manager or aggressive habit tracker.

### Product principles

-   AI removes friction; it is not AI for the sake of AI.
-   Core tracking must work even when AI is unavailable.
-   Calm accountability is preferred over guilt and excessive
    gamification.
-   Consistency and recovery matter more than fragile streaks.
-   Privacy is a product requirement.
-   PostgreSQL is the source of truth.
-   Redis is for fast/temporary state.
-   Kafka is for asynchronous business events.
-   Start with a modular monolith and split services only when
    justified.

------------------------------------------------------------------------

# 2. Core Domain Model

## 2.1 Commitment

A commitment is something the user said they would do.

Example:

> "I'll send Rahul the revised proposal tomorrow morning."

Structured representation:

``` json
{
  "action": "Send revised proposal",
  "person": "Rahul",
  "deadline": "tomorrow morning",
  "status": "PENDING"
}
```

States:

-   `PENDING`
-   `COMPLETED`
-   `OVERDUE`
-   `SNOOZED`
-   `CANCELLED`
-   `WAITING`

## 2.2 Goal

A goal is something the user wants to do consistently.

Examples:

-   Study DSA every weekday at 8 PM.
-   Gym 4 times per week.
-   Read 20 pages every night.
-   Practice coding for 60 minutes.

A goal contains:

-   name
-   description
-   tracking mode
-   frequency
-   schedule
-   target
-   start date
-   optional end date
-   reminder time
-   status

## 2.3 Check-in

A check-in records what happened for a goal during a period.

Possible states:

-   `COMPLETED`
-   `MISSED`
-   `PARTIAL`
-   `SKIPPED`

Check-ins must preserve historical truth. A late completion should not
silently rewrite the original date.

## 2.4 Challenge

A challenge is a time-boxed goal.

Examples:

-   30-day coding challenge
-   14-day gym challenge
-   30-day reading challenge

Challenges are a post-MVP feature.

------------------------------------------------------------------------

# 3. Goal Tracking Modes

Support:

-   **Binary:** Yes/No.
-   **Count:** pages, problems, units, etc.
-   **Duration:** minutes/hours.
-   **Frequency:** N times per week.
-   **Scheduled:** specific days/times.
-   **Reduction:** move from a baseline toward a target.

The UI should remain simple even when the underlying model is flexible.

------------------------------------------------------------------------

# 4. MVP Features

## Commitments

-   Create
-   Edit
-   Complete
-   Snooze
-   Cancel
-   Mark as waiting
-   Upcoming view
-   Overdue view
-   Reminder

## Goals

-   Create
-   Edit
-   Pause
-   Resume
-   Archive
-   Schedule
-   Reminder
-   Tracking mode

## Daily check-in

The target interaction is approximately 30--60 seconds:

``` text
Goal
 ↓
YES / NO
 ↓
Next goal
 ↓
YES / NO
 ↓
Done
```

Optional:

-   reason
-   note
-   quantity
-   duration

## Progress

-   Daily completion
-   Weekly completion
-   Monthly completion
-   Consistency percentage
-   Recovery information

## Notifications

-   Goal reminder
-   Commitment due reminder
-   Overdue reminder
-   Daily check-in reminder
-   Weekly insight

## AI capture

-   Android Share Sheet
-   Text capture
-   Screenshot capture
-   OCR
-   Commitment extraction
-   Goal classification
-   User confirmation

------------------------------------------------------------------------

# 5. Future Features

After the MVP:

-   Weekly AI review
-   Behavioral insights
-   Smart follow-up drafting
-   Waiting-for tracking
-   Calendar integration
-   Email capture
-   Voice capture
-   Widgets
-   Natural-language goal creation
-   Cross-device sync
-   Personalized scheduling recommendations
-   Advanced challenges

Potential differentiator:

> Find forgotten commitments that the user made days ago but never
> completed.

------------------------------------------------------------------------

# 6. UX / UI Direction

The UI should be:

-   smooth
-   modern
-   premium
-   calm
-   minimal
-   responsive

Use an Apple-inspired level of restraint and motion quality without
copying Apple's visual identity.

## Main screens

### Home

Answers:

> What needs my attention today?

Contains:

-   greeting
-   date
-   today's commitments
-   today's goals
-   quick check-in
-   contextual AI insight

### Goals

Answers:

> What am I trying to improve?

### Goal Detail

Answers:

> How am I doing?

Shows history, consistency, schedule, target, notes and insights.

### Commitment Detail

Shows action, person, deadline, source, status, reminder and history.

### Check-in

Sequential, fast, one goal at a time.

------------------------------------------------------------------------

# 7. Motion Design

Use Jetpack Compose animation APIs.

Examples:

### Completion

``` text
Incomplete
   ↓
Fill
   ↓
Check
   ↓
Card compresses
   ↓
Card moves away
```

### Check-in

Smooth transition from one goal to the next.

### Progress

Animate changes rather than replacing values abruptly.

### Haptics

Use subtle haptic feedback for completion and important milestones.

Avoid animation that adds no functional value.

------------------------------------------------------------------------

# 8. Technology Stack

## Android

-   Kotlin
-   Jetpack Compose
-   Android SDK
-   Navigation
-   ViewModel
-   Room
-   Retrofit / OkHttp
-   Kotlin Coroutines
-   WorkManager
-   Firebase Cloud Messaging
-   Hilt or equivalent dependency injection

## Backend

-   Python 3.12
-   FastAPI
-   Pydantic
-   SQLAlchemy
-   Alembic
-   pytest

## Database

**PostgreSQL**

Source of truth for application state.

## Cache / temporary state

**Redis**

Use for:

-   caching
-   rate limiting
-   distributed locks
-   temporary processing state
-   short-lived coordination

Redis must never be the primary database.

## Event streaming

**Apache Kafka**

Use for:

-   asynchronous processing
-   fan-out
-   replayable business events
-   loose coupling

Do not use Kafka for ordinary synchronous CRUD.

## AI

Development:

-   Ollama
-   local LLM

Production:

-   external LLM provider as justified by quality, cost and privacy
    requirements.

AI use cases:

-   commitment extraction
-   goal classification
-   OCR-assisted understanding
-   weekly insights
-   behavioral summaries
-   follow-up drafting

## Infrastructure

Development:

-   Docker
-   Docker Compose

Production direction:

-   Dockerized FastAPI
-   Cloud Run or comparable container platform
-   Managed PostgreSQL
-   Managed Redis
-   Managed Kafka
-   Object storage
-   Firebase Cloud Messaging

------------------------------------------------------------------------

# 9. High-Level Architecture

``` text
                         Android App
                              |
                         HTTPS / JSON
                              |
                     +--------v--------+
                     |     FastAPI     |
                     |    API Layer    |
                     +---+---------+---+
                         |         |
                         v         v
                   PostgreSQL     Redis
                   SOURCE OF      CACHE /
                     TRUTH       TEMP STATE
                         |
                         v
                    Transactional
                       Outbox
                         |
                         v
                       Kafka
                         |
          +--------------+---------------+
          |              |               |
          v              v               v
       AI Worker   Notification     Analytics
                      Worker          Worker
          |              |
          v              v
       AI Provider       FCM
          |
          v
      AI Insights
```

## Responsibility boundaries

**FastAPI:** What does the user want to do?

**PostgreSQL:** What is true?

**Redis:** What temporary/fast-access information is useful?

**Kafka:** What happened?

**AI:** What does unstructured information mean, and what useful pattern
can be derived?

**Android:** How does the user interact with the system?

------------------------------------------------------------------------

# 10. Backend Architecture

Start as a modular monolith.

``` text
backend/
└── app/
    ├── api/
    │   ├── auth.py
    │   ├── commitments.py
    │   ├── goals.py
    │   ├── checkins.py
    │   ├── captures.py
    │   └── insights.py
    ├── domain/
    │   ├── commitment/
    │   ├── goal/
    │   ├── checkin/
    │   └── notification/
    ├── services/
    ├── repositories/
    ├── models/
    ├── events/
    │   ├── producer.py
    │   └── schemas.py
    ├── workers/
    │   ├── ai_worker.py
    │   ├── notification_worker.py
    │   └── analytics_worker.py
    ├── config/
    └── main.py
```

Do not create a separate microservice for every domain during MVP.

------------------------------------------------------------------------

# 11. Database Design

Core tables:

``` text
users
people
commitments
commitment_events
goals
goal_schedules
check_ins
notifications
ai_insights
captures
outbox_events
```

Important indexes:

``` text
commitments(user_id, status, due_at)
commitments(user_id, due_at)
goals(user_id, status)
check_ins(goal_id, date)
check_ins(user_id, date)
notifications(status, scheduled_at)
outbox_events(published_at, created_at)
```

## Data retention

Raw screenshots/conversation captures should have short configurable
retention.

Structured commitments can remain longer.

Users must be able to delete their data.

------------------------------------------------------------------------

# 12. Redis Design

Redis key conventions:

``` text
promise:cache:user:{user_id}:goals
promise:cache:user:{user_id}:today
promise:cache:user:{user_id}:insights

promise:ratelimit:user:{user_id}:ai

promise:lock:notification:{notification_id}

promise:processing:capture:{capture_id}
```

Use TTLs for cache and temporary state.

Redis failures should not destroy core application state.

------------------------------------------------------------------------

# 13. Kafka Architecture

Initial events:

``` text
user.created

commitment.created
commitment.completed
commitment.overdue

goal.created

goal.checkin.created
goal.checkin.missed

conversation.received

commitment.detected

ai.insight.generated
```

Example event:

``` json
{
  "event_id": "uuid",
  "event_type": "goal.checkin.created",
  "version": 1,
  "occurred_at": "ISO-8601",
  "user_id": "uuid",
  "aggregate_id": "uuid",
  "payload": {}
}
```

Use `user_id` as the partition key for user-scoped events.

Consumer groups:

``` text
notification-service
analytics-service
ai-service
```

Each group independently consumes relevant events.

------------------------------------------------------------------------

# 14. Transactional Outbox

Never rely on:

``` text
DB write
 ↓
Kafka publish
```

because the second operation can fail after the first succeeds.

Instead:

``` text
BEGIN
  write application state
  write outbox event
COMMIT

Outbox publisher
      ↓
    Kafka
```

The outbox publisher retries unsent events.

Consumers must be idempotent.

Use:

-   event IDs
-   unique business keys
-   retry with backoff
-   dead-letter topics

------------------------------------------------------------------------

# 15. AI Architecture

## Commitment extraction

Input:

> "I'll send Rahul the revised proposal tomorrow morning."

Possible structured output:

``` json
{
  "type": "COMMITMENT",
  "action": "Send revised proposal",
  "person": "Rahul",
  "deadline": "tomorrow morning",
  "confidence": 0.94
}
```

Pipeline:

``` text
Capture
 ↓
OCR if image
 ↓
Normalize text
 ↓
LLM extraction
 ↓
Schema validation
 ↓
Confidence evaluation
 ↓
User confirmation
 ↓
Commitment
```

High-confidence output still requires user confirmation before creating
important commitments.

Low-confidence output should ask for clarification.

## Goal classification

Input:

> "I want to study DSA every weekday at 8 PM."

Possible output:

``` json
{
  "goal_name": "DSA Study",
  "frequency": "WEEKDAYS",
  "reminder_time": "20:00",
  "tracking_mode": "BINARY"
}
```

## Behavioral insights

Use:

-   check-in history
-   completion time
-   missed reasons
-   commitment load
-   schedule

Example:

> "You complete study sessions more consistently around 8 PM than 10
> PM."

Insights should be concise and evidence-based.

------------------------------------------------------------------------

# 16. AI Guardrails

-   AI output is untrusted input.
-   Validate with strict schemas.
-   Do not silently create important commitments.
-   Do not expose unnecessary raw conversation content.
-   Do not infer sensitive attributes unnecessarily.
-   Core product must work when AI is unavailable.
-   Users control whether AI-generated insights and follow-ups are
    enabled.

------------------------------------------------------------------------

# 17. Capture Privacy

Initial approach:

``` text
Android Share Sheet
      ↓
User explicitly shares text/image
      ↓
Promise
```

Do not build direct access to WhatsApp/SMS databases.

This reduces permissions and privacy risk.

Raw captures should have short configurable retention.

------------------------------------------------------------------------

# 18. API Design

Version under `/v1`.

## Commitments

``` text
POST   /v1/commitments
GET    /v1/commitments
GET    /v1/commitments/{id}
PATCH  /v1/commitments/{id}

POST   /v1/commitments/{id}/complete
POST   /v1/commitments/{id}/snooze
POST   /v1/commitments/{id}/cancel
```

## Goals

``` text
POST   /v1/goals
GET    /v1/goals
GET    /v1/goals/{id}
PATCH  /v1/goals/{id}

POST   /v1/goals/{id}/check-ins
GET    /v1/goals/{id}/history
```

## AI captures

``` text
POST /v1/captures
GET  /v1/captures/{id}
POST /v1/captures/{id}/confirm
```

API conventions:

-   UUID public IDs
-   ISO-8601 timestamps
-   explicit timezone handling
-   stable error codes
-   pagination
-   request validation
-   idempotency keys for relevant mutations

------------------------------------------------------------------------

# 19. Notifications

Types:

``` text
commitment_due
commitment_overdue
goal_checkin
daily_summary
weekly_insight
follow_up_suggestion
```

Flow:

``` text
Goal / Commitment
       ↓
Notification record
       ↓
Scheduler
       ↓
Notification Worker
       ↓
Firebase Cloud Messaging
       ↓
Android
```

Support:

-   quiet hours
-   global notification controls
-   per-goal reminder times
-   AI insight opt-in/out
-   follow-up settings

Avoid repeated notifications after explicit dismissal.

------------------------------------------------------------------------

# 20. Security / Privacy

Requirements:

-   authentication
-   authorization
-   encrypted transport
-   secure storage
-   least-privilege credentials
-   secret management
-   rate limiting
-   input validation
-   secure file uploads
-   deletion/export controls
-   privacy policy
-   Play Store Data Safety compliance

Never commit:

-   passwords
-   API keys
-   JWT secrets
-   provider credentials
-   production configuration secrets

Use `.env` locally and `.env.example` for documented placeholders.

------------------------------------------------------------------------

# 21. Reliability

Use:

-   transactional outbox
-   idempotent consumers
-   retries
-   exponential backoff
-   dead-letter topics
-   timeouts
-   circuit breakers for external AI providers
-   database backups
-   health checks
-   graceful shutdown

Critical rule:

> If AI is down, users must still be able to track commitments and
> goals.

------------------------------------------------------------------------

# 22. Observability

Track:

## API

-   latency
-   error rate
-   throughput
-   auth failures

## PostgreSQL

-   query latency
-   connections
-   locks
-   storage

## Redis

-   hit/miss ratio
-   memory
-   latency

## Kafka

-   consumer lag
-   throughput
-   retries
-   dead letters

## AI

-   latency
-   provider failures
-   schema validation failures
-   confidence
-   token/cost usage

## Android

-   crashes
-   ANRs
-   startup time
-   performance

------------------------------------------------------------------------

# 23. Product Analytics

Useful events:

``` text
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

Never send raw conversation text as normal analytics data.

Candidate North Star Metric:

> **Weekly follow-through:** percentage of active users who complete at
> least one meaningful commitment or goal action during a week.

------------------------------------------------------------------------

# 24. Local Development

Docker Compose should eventually run:

``` text
PostgreSQL
Redis
Kafka
FastAPI
AI Worker
```

The Android app runs through the emulator or a physical device.

Do not install PostgreSQL, Redis or Kafka directly on macOS unless there
is a specific reason.

------------------------------------------------------------------------

# 25. Deployment

## Local

``` text
Mac
 ↓
Docker Compose
 ↓
PostgreSQL + Redis + Kafka + Backend + AI Worker
```

## Staging

Target direction:

``` text
Android test build
       ↓
Cloud Run / container platform
       ↓
Managed PostgreSQL
       ↓
Managed Redis
       ↓
Managed Kafka
```

## Production

``` text
Google Play
     ↓
Android
     ↓ HTTPS
Container API
     |
     +---- PostgreSQL
     +---- Redis
     +---- Kafka
     +---- AI provider
     +---- FCM
```

Cloud provider and managed-service choices should be finalized using
current pricing and availability when deployment begins.

------------------------------------------------------------------------

# 26. CI/CD

Use GitHub Actions.

Backend:

``` text
Push
 ↓
Lint
 ↓
Unit tests
 ↓
Integration tests
 ↓
Docker build
 ↓
Staging
 ↓
Smoke tests
 ↓
Production
```

Android:

``` text
Push
 ↓
Build
 ↓
Unit tests
 ↓
Instrumented tests
 ↓
Signed AAB
 ↓
Play Console
```

------------------------------------------------------------------------

# 27. Testing Strategy

## Backend

Unit tests for:

-   domain logic
-   services
-   state transitions
-   consistency calculations
-   validation

Integration tests for:

-   PostgreSQL
-   Redis
-   Kafka
-   API/database
-   outbox/Kafka
-   consumers

## Android

Test:

-   ViewModels
-   repositories
-   UI state
-   navigation
-   critical Compose interactions

## End-to-end

Critical flow:

``` text
Create Goal
 ↓
Reminder
 ↓
Check-in
 ↓
Database
 ↓
Kafka
 ↓
Analytics / Insight
```

AI flow:

``` text
Share screenshot
 ↓
OCR
 ↓
AI extraction
 ↓
Confirmation
 ↓
Commitment
 ↓
Reminder
 ↓
Completion
```

------------------------------------------------------------------------

# 28. Development Roadmap

## Phase 0 --- Environment

-   Git
-   Java
-   Python
-   Docker
-   Android tooling
-   Cursor
-   Postman
-   DataGrip

## Phase 1 --- Repository

-   Git repository
-   GitHub
-   monorepo
-   documentation
-   conventions

## Phase 2 --- Local Infrastructure

-   Docker Compose
-   PostgreSQL
-   Redis
-   Kafka
-   networking
-   volumes
-   health checks

## Phase 3 --- Backend Foundation

-   FastAPI
-   configuration
-   database
-   migrations
-   health endpoint
-   errors
-   logging
-   tests

## Phase 4 --- Commitment Domain

-   model
-   repository
-   service
-   API
-   state transitions
-   events
-   outbox

## Phase 5 --- Goal Domain

-   model
-   schedules
-   check-ins
-   consistency
-   API
-   events

## Phase 6 --- Android Foundation

-   Compose project
-   design system
-   navigation
-   API client
-   local state
-   authentication

## Phase 7 --- Core UX

-   Home
-   Goals
-   Goal Detail
-   Commitment Detail
-   Check-in
-   animations
-   haptics

## Phase 8 --- Notifications

-   scheduler
-   worker
-   FCM
-   actions
-   quiet hours

## Phase 9 --- AI

-   Share Sheet
-   capture API
-   OCR
-   AI extraction
-   schema validation
-   confidence
-   confirmation

## Phase 10 --- Event Architecture

-   Kafka topics
-   schemas
-   producers
-   consumers
-   outbox
-   retries
-   idempotency
-   dead letters

## Phase 11 --- Insights

-   weekly summaries
-   patterns
-   AI insights
-   insight UI

## Phase 12 --- Production Readiness

-   monitoring
-   security
-   privacy
-   backups
-   deployment
-   CI/CD

## Phase 13 --- Play Store

-   application ID
-   signing
-   AAB
-   screenshots
-   icon
-   privacy policy
-   Data Safety
-   testing
-   release

------------------------------------------------------------------------

# 29. Cost Strategy

Development target: **essentially ₹0**.

Use:

-   local Docker
-   local PostgreSQL
-   local Redis
-   local Kafka
-   local AI with Ollama
-   free tiers where appropriate

Do not pay for production infrastructure before it is necessary.

Public Play Store distribution has a developer registration cost.

Production AI usage may eventually create API costs.

------------------------------------------------------------------------

# 30. Engineering Decisions

  -----------------------------------------------------------------------
  Decision                Choice                  Reason
  ----------------------- ----------------------- -----------------------
  Repository              Monorepo                One product,
                                                  coordinated changes

  Backend                 FastAPI                 Fast iteration + Python
                                                  AI ecosystem

  Database                PostgreSQL              Transactions +
                                                  relational consistency

  Cache                   Redis                   Fast temporary state +
                                                  rate limits + locks

  Events                  Kafka                   Async processing +
                                                  fan-out + replay

  Android                 Kotlin + Compose        Native Android + modern
                                                  UI

  Architecture            Modular monolith first  Avoid premature
                                                  microservices

  AI development          Local LLM               Zero API cost during
                                                  experimentation

  Capture                 Android Share Sheet     Privacy + least
                                                  privilege

  Event reliability       Transactional outbox    Reliable DB → Kafka
                                                  publication
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 31. What Not To Over-Engineer

Do not:

-   create dozens of microservices
-   use Kafka for synchronous CRUD
-   use Redis as the primary database
-   scrape private messaging applications
-   make an AI chatbot the main interface
-   require AI for core tracking
-   build complex analytics before data exists
-   optimize for millions of users before retention is proven
-   add every integration during MVP

------------------------------------------------------------------------

# 32. Cursor Development Rules

Before implementation, Cursor should read:

``` text
docs/PROJECT_PLAN.md
docs/ARCHITECTURE.md
docs/DEVELOPMENT.md
```

when available.

Rules:

1.  Do not change architecture silently.
2.  Do not introduce a new technology without explaining why.
3.  PostgreSQL is the source of truth.
4.  Redis is not the source of truth.
5.  Kafka is for asynchronous business events.
6.  Do not use Kafka for simple synchronous CRUD.
7.  Core functionality must work when AI is unavailable.
8.  Validate all AI output using strict schemas.
9.  Never hard-code secrets.
10. Never commit `.env`.
11. Follow the existing project structure.
12. Prefer simple, maintainable implementations.
13. Avoid premature abstraction.
14. Avoid premature microservices.
15. Write tests for important business logic.
16. Use migrations for database changes.
17. Consumers must be idempotent.
18. Use transactional outbox for DB-to-Kafka publication.
19. Explain significant trade-offs before architectural changes.
20. Do not delete or rewrite unrelated code.
21. Preserve API/event compatibility where possible.
22. Keep privacy requirements in mind when processing user content.
23. Do not silently perform important user-facing actions based solely
    on AI.
24. Ask for clarification if requirements conflict with this plan.

------------------------------------------------------------------------

# 33. Development Workflow

Use this loop for every feature:

``` text
Requirement
    ↓
Understand
    ↓
Design
    ↓
HLD / LLD if needed
    ↓
Implement
    ↓
Unit tests
    ↓
Integration tests
    ↓
Manual verification
    ↓
Commit
```

Do not ask Cursor to generate the entire application in one prompt.

Instead, give Cursor small, explicit implementation tasks.

------------------------------------------------------------------------

# 34. Definition of MVP Done

The MVP is done when:

-   user can create a commitment
-   user can complete/snooze/cancel a commitment
-   user can create a recurring goal
-   user can configure a goal schedule
-   user can perform a daily check-in
-   progress is persisted correctly
-   notifications work
-   core flows work without AI
-   user can share text/screenshot for AI extraction
-   AI output requires confirmation
-   Kafka events work for asynchronous flows
-   Redis is used appropriately
-   PostgreSQL remains the source of truth
-   basic crash/error monitoring exists
-   user can delete data
-   Android UI is smooth and polished
-   release AAB can be generated

------------------------------------------------------------------------

# 35. Final Accountability Loop

The product should ultimately create this loop:

``` text
Promise / Goal
      ↓
Reminder
      ↓
User action
      ↓
Check-in
      ↓
Recorded outcome
      ↓
Pattern
      ↓
AI insight
      ↓
Better next action
      ↓
Better follow-through
```

The goal is not to build the most complicated system.

The goal is to build a product that reliably helps people do what they
said they would do --- to others and to themselves.

Start small. Prove the accountability loop. Then add intelligence and
scale.
