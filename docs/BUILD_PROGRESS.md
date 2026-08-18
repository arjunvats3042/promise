# Promise — Build Progress

This file records **actual** implementation progress.

It does **not** replace:

- `docs/PROJECT_PLAN.md` — product and plan
- `docs/ARCHITECTURE.md` — intended architecture
- `docs/DEVELOPMENT.md` — intended roadmap

Update this file after every meaningful, verified development result. Never store secrets.

---

## Current Status

- **Current phase:** Phase 4 — Authentication (not started)
- **Current step:** Phase 3 **COMPLETE**. Authentication architecture is designed (`docs/AUTHENTICATION_DESIGN.md`). Authentication **implementation is NOT started** and is Phase 4.
- **Overall status:** Django 6.1 + DRF foundation is in place: custom `users.User` (`AUTH_USER_MODEL`), `/api/v1/`, error envelope, logging, abstract `BaseModel`. No auth APIs, JWT, session models, or auth packages.
- **Last completed milestone:** Phase 3 — Django Backend Foundation (final review 2026-08-19)
- **Immediate next step:** Phase 4 authentication **implementation**, following [`docs/AUTHENTICATION_DESIGN.md`](AUTHENTICATION_DESIGN.md). Do not recreate `users.User`. Phase 4 is **not** complete.

Phase checklist:

- Phase 1 Repository Setup: **COMPLETE**
- Phase 2 Local Infrastructure: **COMPLETE**
- Phase 3 Django Backend Foundation: **COMPLETE**
- Phase 4 Authentication: **NOT STARTED**

---

## Completed Work

### 2026-08-18 — Phase 1 — Repository setup

- **What was implemented:** Local Git repository, `.gitignore`, `README.md`, empty `android/`, `backend/`, and `infrastructure/` placeholders, personal GitHub remote
- **Files changed:** `.gitignore`, `README.md`
- **Commands/tools used:** Git, GitHub (`origin` → `git@github-personal:arjunvats3042/promise.git`)
- **Verification:** `git status` clean after initial commit; `git push -u origin main` succeeded
- **Result:** Repository exists on GitHub; secrets rules ignore `.env`
- **Git commit:** `6b9cb00` — `chore: initialize Promise project`

### 2026-08-18 — Phase 1 — Product / architecture / development docs

- **What was implemented:** `PROJECT_PLAN.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md`
- **Files changed:** `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPMENT.md`
- **Verification:** Files present in the repo and committed
- **Result:** Intended product, architecture, and roadmap documented
- **Git commit:** `5284662` — `docs: add architecture, development plan, and project plan documentation`

### 2026-08-18 — Phase 2 — Docker Compose infrastructure

- **What was implemented:** Root `docker-compose.yml` for PostgreSQL 17, Redis 8, and Apache Kafka 4.3.1 in KRaft mode (no ZooKeeper). Named volumes, `promise-network`, healthchecks, host ports 5432 / 6379 / 9092. Password via Compose substitution from `.env`. Restart policy later set to `"no"`.
- **Files changed:** `docker-compose.yml`, `.env.example`
- **Commands/tools used:** `docker compose config` (renders successfully; does not start containers)
- **Verification:** Compose file validates. `.env` is gitignored. `.env.example` contains only the placeholder `POSTGRES_PASSWORD=changeme`.
- **Result:** Local infrastructure definition is committed
- **Git commit:** `c4fac57` — `feat: add local infrastructure`

### 2026-08-18 — Phase 2 — PostgreSQL / Redis / Kafka verification

- **What was implemented:** Services were started from Compose and exercised locally
- **Commands/tools used:** Docker Compose; PostgreSQL 17.11 inside `promise-postgres`; Redis 8 inside `promise-redis`; Kafka 4.3.1 KRaft inside `promise-kafka`
- **Verification performed:**
  - PostgreSQL became ready and accepted connections on 5432; data directory persisted across a restart (`Skipping initialization`)
  - Redis responded and accepted connections on 6379 (`PING` → `PONG` during local verification)
  - Kafka broker started in KRaft mode
  - Kafka producer → topic → consumer path was successfully tested
- **Result:** Phase 2 verification succeeded
- **Git commit:** none beyond `c4fac57` (runtime verification only)
- **Note:** Inspected again at 22:50 IST. All three containers were **stopped** (postgres/redis exit 0, kafka exit 143). `restart: "no"` is intentional, so they do not come back by themselves. Phase 2 remains complete; the stack is not running right now.

### 2026-08-18 — Docs — Backend stack decision (FastAPI → Django)

- **What was implemented:** Documentation updated to Django + Django REST Framework + Django ORM. APIs versioned under `/api/v1/`. Modular monolith layout documented. Outbox retained.
- **Files changed:** `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPMENT.md`
- **Verification:** Search of those three files for `FastAPI` / `fastapi` returned zero matches
- **Result:** Intended backend is Django/DRF. No application code created. No Compose or Android changes.
- **Git commit:** **not committed yet** (working tree still has these doc edits)

### 2026-08-18 — Phase 3 — Django backend foundation (partial)

- **What was implemented:** Django 6.1 modular-monolith skeleton in `backend/` using the existing `backend/.venv` (Python 3.12.14). Split settings (`config.settings.base` / `local`). DRF configured with no authentication. `GET /api/v1/health/` returns `{"status": "ok"}`. PostgreSQL via `DATABASE_URL`. `REDIS_URL` and `KAFKA_BOOTSTRAP_SERVERS` loaded from env but not used. pytest + pytest-django health test added.
- **Files changed:** `backend/manage.py`, `backend/config/**`, `backend/apps/__init__.py`, `backend/tests/**`, `backend/pyproject.toml`, `.env.example` (placeholders only). Gitignored `.env` received Django keys; no secrets committed.
- **Commands/tools used:** existing `backend/.venv`; `pytest -v`
- **Verification:** `tests/test_health.py::test_health_returns_ok PASSED` — status 200 and JSON `{"status": "ok"}`. Python 3.12.14, Django 6.1.
- **Result:** Health endpoint works. Phase 3 is not finished (no logging, error handlers, migrations, or domain apps).
- **Git commit:** not committed yet
- **Not done:** `runserver` was not started. Redis/Kafka clients were not added. Auth was not added.

### 2026-08-18 — Phase 3 — Foundation verification

- **What was implemented:** `.env.example` aligned with Docker + Django placeholders (`POSTGRES_PASSWORD` kept for Compose). No `migrate`. No `runserver`.
- **Verification:**
  - `.env` is gitignored (`.gitignore:4:.env`; `git status` shows `!! .env`)
  - `DATABASE_URL` parses to PostgreSQL `promise@localhost:5432/promise` (password not logged)
  - `python manage.py check` — System check identified no issues (0 silenced)
  - `python manage.py showmigrations` — **failed**: PostgreSQL container is stopped (`connection refused` on localhost:5432)
  - `pytest -v` — `tests/test_health.py::test_health_returns_ok PASSED` (1 passed in 0.05s)
- **Pending migrations (not applied; listed from installed apps on disk):**
  - `contenttypes`: `0001_initial`, `0002_remove_content_type_name`
  - `auth`: `0001_initial` through `0012_alter_user_first_name_max_length`
- **Result:** Config and health endpoint verified. Migrations not applied. Postgres must be running before `showmigrations`/`migrate`.
- **Git commit:** not committed yet

### 2026-08-18 — Phase 3 — Django database initialization: COMPLETE

- **What was implemented:** Django built-in initial migrations applied to local PostgreSQL. No domain models. No custom User. No authentication.
- **Commands/tools used:** `python manage.py showmigrations`, `python manage.py migrate`, `pytest`
- **Verification:**
  - Django 6.1 on Python 3.12.14
  - Django REST Framework configured
  - PostgreSQL connection verified (`promise@localhost:5432/promise`)
  - `contenttypes`: `0001_initial`, `0002_remove_content_type_name` — all applied `[X]`
  - `auth`: `0001_initial` through `0012_alter_user_first_name_max_length` — all applied `[X]`
  - `pytest`: 1 passed
  - `GET /api/v1/health/`: HTTP 200 with `{"status": "ok"}`
- **Result:** Database initialization step complete. Phase 3 is not complete.
- **Git commit:** not committed yet

### 2026-08-18 — Phase 3 — Custom User model: COMPLETE

- **Decision:** Promise uses a custom `users.User` model, not Django's default `auth.User`. Email is `USERNAME_FIELD`. No username identifier.
- **Reason:** Email is the natural login identifier; UUID primary keys; keep identity fields explicit (`name`, user timezone preference). Django requires `AUTH_USER_MODEL` to be set before the first `migrate` for a clean schema.
- **Local database reset:** The local Docker database `promise` (`localhost:5432`, user `promise`, container `promise-postgres`) was dropped and recreated. Docker volumes were not removed. `docker-compose.yml` was not changed. Individual tables were not altered by hand.
- **Why the reset was safe:** There was no production database and no real application data. Before reset, tables were only Django built-ins (`auth_*`, `django_content_type`, `django_migrations`). `auth_user` had **0 rows**. No commitments/goals/challenges tables existed.
- **Why reset was needed:** Custom User was introduced after Django's initial `auth` migrations had already been applied (`auth_user` already existed). Applying `users.0001` on top would have created a second user table.
- **What was implemented:** `apps.users` with `AbstractBaseUser` + `PermissionsMixin`, `UserManager.create_user` / `create_superuser`, `AUTH_USER_MODEL = "users.User"`. Migration `users.0001_initial_custom_user` applied after the reset.
- **Verification:**
  - `python manage.py migrate` — applied `contenttypes`, `auth`, `users.0001_initial_custom_user`
  - `python manage.py showmigrations` — all `[X]`
  - `python manage.py check` — no issues
  - `pytest -v` — 5 passed
  - PostgreSQL has `users_user`; `auth_user` is **not** present
- **Not done:** superuser not created; `runserver` not started; no login/JWT/registration APIs
- **Git commit:** not committed yet

### 2026-08-18 — Phase 3 — API versioning, errors, logging: COMPLETE

- **API versioning:** All application APIs are under `/api/v1/`. `GET /api/v1/health/` is unchanged: HTTP 200, `{"status": "ok"}`. Health does not query PostgreSQL, Redis, or Kafka.
- **Error response convention:** API errors use `{ "error": { "code": "...", "message": "..." } }`. Validation errors add optional `details` (field-level). Unexpected exceptions return `INTERNAL_SERVER_ERROR` / `"An unexpected error occurred."` and are logged; clients never receive stack traces, DB errors, or exception messages.
- **HTTP status conventions (established, not all implemented as features):** 400 invalid/validation, 401 unauthenticated, 403 forbidden, 404 not found, 409 conflict (mapped if raised), 429 rate limited (mapped if raised), 500 unexpected. Authentication and rate limiting are **not** implemented yet.
- **Logging:** Django `LOGGING` in `config.settings.base` — console handler, `{asctime} {levelname} {name} {message}`, root/django/promise loggers. No Elasticsearch, Loki, or OpenTelemetry.
- **Implementation notes:** Project-level DRF `EXCEPTION_HANDLER` is `config.exceptions.api_exception_handler`. Unmatched `/api/v1/` paths hit a catch-all that returns the 404 error shape (new v1 routes must be registered **before** that catch-all). Test-only views live in `tests/foundation_urls.py` and are not production URLs.
- **Files changed:** `backend/config/exceptions.py` (new), `backend/config/urls.py`, `backend/config/views.py`, `backend/config/settings/base.py`, `backend/tests/test_api_errors.py` (new), `backend/tests/foundation_urls.py` (new)
- **Verification:**
  - `python manage.py check` — System check identified no issues (0 silenced)
  - `pytest -v` — **10 passed** in 1.26s (Python 3.12.14, Django 6.1)
- **Tests added:** health 200; API 404 error structure; invalid JSON 400 `INVALID_REQUEST`; validation 400 `VALIDATION_ERROR` with `details`; unexpected exception 500 generic body (internal message not leaked)
- **Not done:** Phase 3 not marked complete. No Promise/Goal/Challenge/Notification models. No auth/JWT. No Redis/Kafka/Celery/AI. `docker-compose.yml` and Android untouched. `runserver` not started.
- **Git commit:** not committed yet

### 2026-08-18 — Phase 3 — Shared model conventions (`BaseModel`): COMPLETE

- **Shared model convention:** New domain models inherit `apps.core.models.BaseModel` (`class SomeModel(BaseModel)`). `BaseModel` is abstract (`Meta.abstract = True`) and provides UUID `id`, `created_at` (`auto_now_add`), `updated_at` (`auto_now`).
- **User exception:** `users.User` already has UUID `id`, `created_at`, and `updated_at`. It does **not** inherit `BaseModel` (would duplicate fields). Confirmed by test.
- **UUID decision:** Promise uses UUID v4 primary keys instead of sequential integers so public API identifiers are stable across services, do not require a shared sequence, and are not trivially enumerable. UUIDs help future scaling (merging, sharding, outbox/event payloads). They are **not** a security control by themselves — authorization still applies.
- **Timestamp convention:** `USE_TZ = True` and `TIME_ZONE = "UTC"`. Database datetime fields are timezone-aware; Django stores/compares in UTC. `User.timezone` is a display/preference field only. APIs should serialize timezone-aware timestamps (ISO-8601).
- **Migrations:** `makemigrations --check` → `No changes detected`. No `core` migration file. No new database table (abstract model).
- **Files changed:** `backend/apps/core/` (new), `backend/config/settings/base.py` (`CoreConfig` in `INSTALLED_APPS`), `backend/tests/test_base_model.py` (new). Probe model exists only in the test suite (temporary table via schema editor).
- **Verification:**
  - `python manage.py check` — no issues (0 silenced)
  - `pytest -v` — **15 passed** in 1.22s
  - `python manage.py makemigrations --check` — No changes detected
- **Not done:** Phase 3 not marked complete. No Promise/Goal/Challenge/Notification models. No auth/JWT. No Redis/Kafka/Celery/AI. Compose and Android untouched. `runserver` not started by this step.
- **Git commit:** not committed yet

### 2026-08-18 — Authentication architecture design: COMPLETE (implementation NOT started)

- **What was done:** Design-only document [`docs/AUTHENTICATION_DESIGN.md`](AUTHENTICATION_DESIGN.md). No authentication code, packages, JWT issuance, session models, migrations, or API routes.
- **DECIDED:** Option B — short-lived JWT access tokens (15 minutes) + PostgreSQL `AuthSession` rows + opaque rotating refresh tokens (30-day sliding idle, 90-day absolute cap), reuse detection, per-session and logout-all revoke.
- **User model:** Keep existing `users.User` (UUID, email `USERNAME_FIELD`, Django password hashing). Future `UserIdentity` table for Google/Apple; no provider ids on `User`. Soft email verification (do not block MVP).
- **Files changed:** `docs/AUTHENTICATION_DESIGN.md` (new), `docs/BUILD_PROGRESS.md`
- **Not done:** Authentication **implementation** is not complete (Phase 4). No `djangorestframework-simplejwt` / PyJWT. No `AuthSession` model. `REST_FRAMEWORK` still has empty authentication and `AllowAny`. `docker-compose.yml`, Android, Django models, and migrations were not modified in the design step.
- **Git commit:** not committed yet

### 2026-08-19 — Phase 3 — Django Backend Foundation: COMPLETE

- **Final review:** READY TO COMPLETE. Docs cleanup only (this file, `README.md`, `docs/DEVELOPMENT.md`). No backend, model, migration, Compose, or auth-code changes.
- **Verification (2026-08-19):**
  - `python manage.py check` — **PASS** (no issues, 0 silenced)
  - `python manage.py showmigrations` — all required migrations applied (`contenttypes`, `auth`, `users.0001_initial_custom_user`; `core` has no migrations)
  - `python manage.py makemigrations --check` — **PASS** (No changes detected)
  - `pytest -v` — **15 passed**
- **Authentication:** Architecture designed in [`docs/AUTHENTICATION_DESIGN.md`](AUTHENTICATION_DESIGN.md). Implementation belongs to **Phase 4** and is **not** complete.
- **Git commit:** not committed yet

---

## Current Work

Phase 3 is complete. Phase 4 authentication implementation has **not** started. **No auth APIs exist.**

Still uncommitted:

- Django/DRF documentation updates
- `docs/BUILD_PROGRESS.md`
- `docs/AUTHENTICATION_DESIGN.md`
- `docs/DEVELOPMENT.md` checkpoint / Phase 4 wording
- `README.md` (Django stack)
- `backend/` Django project
- `.env.example` Django placeholders

---

## Next Steps

1. Commit pending documentation and Django foundation when ready
2. Phase 4: implement authentication using [`docs/AUTHENTICATION_DESIGN.md`](AUTHENTICATION_DESIGN.md). Keep existing `users.User`. Do not mark Phase 4 complete until that work is done.
3. Do not add commitments, goals, challenges, Redis/Kafka clients, Celery, AI, or Android yet

---

## Environment

Verified on this machine (2026-08-18), no secrets:

- macOS 26.6.1
- Git 2.50.1
- Docker 29.7.2
- Docker Compose v5.4.0
- Default `python3` on PATH: **3.14.6**
- `backend/.venv`: **Python 3.12.14**, Django **6.1** (used for Phase 3)
- Java: not on PATH in this inspection. `docs/DEVELOPMENT.md` lists Java 17 as a Phase 0 item; confirm before Android work
- Android Studio / SDK: listed complete in `DEVELOPMENT.md`; no Android app exists yet
- DataGrip / Postman: listed in Phase 0; not re-verified here

Never stored here: passwords, API keys, tokens, private keys, `.env` contents.

---

## Infrastructure Status

### PostgreSQL

- **Version:** 17.11 (`postgres:17`, linux/arm64)
- **Purpose:** Source of truth
- **Current status:** Running. Local database `promise` was reset and re-migrated with `AUTH_USER_MODEL = users.User`.
- **Last verification:** 2026-08-18 — `users_user` present; `auth_user` absent; all Django migrations `[X]`

### Redis

- **Version:** Redis 8 (`redis:8`)
- **Purpose:** Cache, locks, rate limiting, temporary state, notification coordination (not yet used by app code)
- **Current status:** Configured and previously verified. Container `promise-redis` is **stopped**
- **Last verification:** 2026-08-18 — port 6379, AOF enabled, `PING` → `PONG`, volume `promise_redis_data`

### Kafka

- **Version:** Apache Kafka 4.3.1 (`apache/kafka:4.3.1`, KRaft, no ZooKeeper)
- **Purpose:** Asynchronous / domain events
- **Current status:** Configured and previously verified. Container `promise-kafka` is **stopped**
- **Last verification:** 2026-08-18 — broker started; host bootstrap `localhost:9092`; in-network bootstrap `promise-kafka:19092`; producer → topic → consumer succeeded. Volume `promise_kafka_data`

### Docker Compose

- **File:** `docker-compose.yml` (repository root)
- **Services:** postgres, redis, kafka only
- **Network:** `promise-network`
- **Volumes:** `promise_postgres_data`, `promise_redis_data`, `promise_kafka_data`
- **Current status:** Valid (`docker compose config` succeeded). PostgreSQL was reachable for Django migrate on 2026-08-18.
- **Last verification:** 2026-08-18 — Django applied built-in migrations against `localhost:5432`

---

## Architecture Decisions

| Decision | Reason | Date / phase |
|---|---|---|
| Monorepo | One product; coordinated Android + backend changes | 2026-08-18 / Phase 1 |
| Android-first client | Native Kotlin + Compose for the initial product | 2026-08-18 / Phase 1 |
| iOS | Not decided. A separate client or a later cross-platform choice | future |
| PostgreSQL as source of truth | Transactions and relational consistency | 2026-08-18 / Phase 1 |
| Django ORM as DB access layer | Primary access layer; Django migrations | 2026-08-18 / pre-Phase 3 |
| Redis for cache / locks / temp state | Fast temporary coordination; never the primary DB | 2026-08-18 / Phase 1 |
| Kafka for async domain events | Fan-out, replay, decoupling. Not for synchronous CRUD | 2026-08-18 / Phase 1 |
| Kafka in KRaft mode (no ZooKeeper) | Current Kafka; simpler local broker | 2026-08-18 / Phase 2 |
| Dual Kafka listeners (INTERNAL + EXTERNAL) | Host uses `localhost:9092`; other containers use `promise-kafka:19092` | 2026-08-18 / Phase 2 |
| `restart: "no"` for local containers | Failures must stay visible in development | 2026-08-18 / Phase 2 |
| Modular monolith | Avoid premature microservices | 2026-08-18 / Phase 1 |
| Transactional outbox (planned) | Reliable DB → Kafka; no direct publish from the request path | 2026-08-18 / Phase 1 |
| **Django + DRF instead of FastAPI** | Batteries-included backend, ORM, REST APIs | 2026-08-18 / pre-Phase 3 |
| Django **6.1** on Python **3.12** | Current Django; matches project Python target | 2026-08-18 / Phase 3 |
| Custom `users.User`; email as `USERNAME_FIELD` | Application identity is email + UUID, not Django's username `auth.User`. Must be set before first migrate for a clean schema. | 2026-08-18 / Phase 3 |
| APIs under `/api/v1/` | Versioned DRF REST surface | 2026-08-18 / pre-Phase 3 |
| Consistent API error envelope `{error: {code, message}}` | Clients get stable, non-leaky error JSON | 2026-08-18 / Phase 3 |
| Django stdlib console logging | Production-quality timestamps/levels without an external log stack | 2026-08-18 / Phase 3 |
| Abstract `apps.core.models.BaseModel` for domain models | UUID pk + created/updated timestamps without duplicating fields; User stays independent | 2026-08-18 / Phase 3 |
| UUID v4 primary keys (not sequential ints) | Stable public IDs across services; no shared sequence; reduced ID enumeration. Not a security control. | 2026-08-18 / Phase 3 |
| Timezone-aware UTC storage; user TZ is preference | `USE_TZ=True`, `TIME_ZONE=UTC`; APIs serialize aware timestamps | 2026-08-18 / Phase 3 |
| **Auth Option B:** JWT access + server-side rotating refresh sessions | Per-device revoke, rotation, reuse detection; no User rewrite later. Documented in `docs/AUTHENTICATION_DESIGN.md`. **Not implemented.** | 2026-08-18 / design |
| Access JWT 15 min; refresh 30d sliding / 90d cap; HS256 with dedicated key | Short stolen-access window; mobile-friendly re-auth; key rotation via `kid` | 2026-08-18 / design |
| Opaque refresh `{sid}.{secret}` stored as HMAC; never plaintext | DB dump is not a session store; reuse detectable via `sid` | 2026-08-18 / design |
| Future `UserIdentity`; no `google_id`/`apple_id` on User | Multiple providers without duplicating users | 2026-08-18 / design |
| Soft email verification (non-blocking for MVP) | Onboarding friction vs later sharing/email guarantees | 2026-08-18 / design |
| Local AI via Ollama later | Zero API cost during experimentation | 2026-08-18 / Phase 1 |

---

## Testing & Verification

| Date | What | Result |
|---|---|---|
| 2026-08-18 | `docker compose config` | Pass — file renders; containers not started |
| 2026-08-18 | PostgreSQL start, DB/user, port 5432, persistence | Pass |
| 2026-08-18 | Redis `PING` → `PONG`, port 6379 | Pass |
| 2026-08-18 | Kafka broker start (KRaft) | Pass |
| 2026-08-18 | Kafka producer → topic → consumer | Pass |
| 2026-08-18 | `.env` ignored by Git; `.env.example` is a placeholder | Pass |
| 2026-08-18 | Docs search for remaining `FastAPI` references | Pass — zero in the three architecture/plan docs |
| 2026-08-18 | `pytest -v` (`tests/test_health.py::test_health_returns_ok`) | **Pass** — 1 passed in 0.04s. HTTP 200, `{"status": "ok"}`. Django 6.1, Python 3.12.14 |
| 2026-08-18 | `.env` gitignored | Pass — `.gitignore` line `.env`; `git status --ignored` shows `!! .env` |
| 2026-08-18 | `DATABASE_URL` → local Postgres | Pass — engine `django.db.backends.postgresql`, `promise@localhost:5432/promise` |
| 2026-08-18 | `python manage.py check` | Pass — no issues (0 silenced) |
| 2026-08-18 | `python manage.py showmigrations` | **Blocked** — Postgres stopped, connection refused on 5432. No migrate run. |
| 2026-08-18 | `pytest -v` (verification rerun) | **Pass** — 1 passed in 0.05s |
| 2026-08-18 | `python manage.py migrate` | **Pass** — applied `contenttypes` 0001–0002 and `auth` 0001–0012 |
| 2026-08-18 | `python manage.py showmigrations` after migrate | **Pass** — all `contenttypes` and `auth` migrations `[X]` |
| 2026-08-18 | `pytest` after migrate | **Pass** — 1 passed in 0.06s. `GET /api/v1/health/` HTTP 200 `{"status": "ok"}` |
| 2026-08-18 | Local `promise` DB reset + `migrate` with custom User | **Pass** — database dropped/recreated in `promise-postgres` only; all migrations `[X]` |
| 2026-08-18 | `python manage.py check` after custom User migrate | Pass — no issues |
| 2026-08-18 | `pytest -v` after custom User migrate | **Pass** — 5 passed in 1.22s |
| 2026-08-18 | User tables | **Pass** — `users_user` present; `auth_user` absent |
| 2026-08-18 | `python manage.py check` after API error/logging foundation | **Pass** — no issues (0 silenced) |
| 2026-08-18 | `pytest -v` after API error/logging foundation | **Pass** — 10 passed in 1.26s |
| 2026-08-18 | `python manage.py check` after `BaseModel` | **Pass** — no issues (0 silenced) |
| 2026-08-18 | `pytest -v` after `BaseModel` | **Pass** — 15 passed in 1.22s |
| 2026-08-18 | `python manage.py makemigrations --check` after `BaseModel` | **Pass** — No changes detected; no `core` table/migration |
| 2026-08-18 | Auth design step — backend code | **Unchanged** — docs only (`AUTHENTICATION_DESIGN.md`, `BUILD_PROGRESS.md`). No models, migrations, APIs, `pyproject.toml`, or tests modified. Last pytest result remains **15 passed**. |
| 2026-08-19 | Phase 3 final review + docs cleanup | **Pass** — `manage.py check` no issues; `makemigrations --check` no changes; `pytest -v` **15 passed** in 1.23s. No backend code changed. |

---

## Known Issues

- **Compose `restart: "no"`.** Containers do not restart themselves after Docker Desktop or an explicit stop. PostgreSQL was up for the 2026-08-18 migrate and 2026-08-19 final review.
- **Python version mismatch on PATH.** Project target is 3.12; default `python3` is 3.14.6. Backend work uses `backend/.venv` (3.12.14).
- **Empty placeholder directories.** `android/` and `infrastructure/{postgres,redis,kafka}` exist but have no app code. Compose lives at the repo root. `backend/` has the Django foundation.
- **Health test does not require a database query.** The health endpoint still returns `{"status": "ok"}` without hitting application tables. PostgreSQL is used for applied Django migrations.
- **Custom `AUTH_USER_MODEL` applied after a local DB reset.** The empty local `promise` database was dropped and recreated (not the Docker volume). `users_user` is the user table; `auth_user` is gone.

No open infrastructure defects from the Phase 2 verification.

---

## Git Checkpoints

| Commit | Date | Meaning |
|---|---|---|
| `6b9cb00` | 2026-08-18 | Initialize repo, `.gitignore`, `README.md` |
| `5284662` | 2026-08-18 | Add plan, architecture, and development docs |
| `c4fac57` | 2026-08-18 | Add local Docker infrastructure |

`main` tracks `origin/main` at `c4fac57`.

Not in Git yet:

- Django/DRF documentation updates
- `docs/BUILD_PROGRESS.md`
- `docs/AUTHENTICATION_DESIGN.md`
- `docs/DEVELOPMENT.md` Phase 4 / checkpoint updates
- `README.md` (Django stack)
- Django backend foundation (`backend/`)
- `.env.example` Django placeholders

---

## Change Log

- **2026-08-18:** Repository initialized and pushed to GitHub
- **2026-08-18:** Product, architecture, and development documentation added
- **2026-08-18:** Local PostgreSQL, Redis, and Kafka Compose stack added and verified
- **2026-08-18:** Container restart policy changed to `"no"` for local visibility
- **2026-08-18:** Backend decision changed from FastAPI to Django + DRF (docs only; uncommitted)
- **2026-08-18:** `BUILD_PROGRESS.md` created as the implementation journal
- **2026-08-18:** Django 6.1 backend foundation started (DRF, health endpoint, env-based PostgreSQL config, pytest). Health test passed. Phase 3 not complete.
- **2026-08-18:** Foundation verification: `.env.example` placeholders corrected; `manage.py check` clean; pytest still passing; `showmigrations` blocked because PostgreSQL is stopped; no migrate applied.
- **2026-08-18:** Django database initialization **COMPLETE** — PostgreSQL connection verified; `contenttypes` and `auth` migrations applied; pytest 1 passed; health endpoint HTTP 200. Phase 3 remains in progress.
- **2026-08-18:** Custom User model (`email` as `USERNAME_FIELD`) implemented. Tests pass on a fresh test DB. Development `migrate` **stopped**: default `auth` migrations already applied (`auth_user` exists). Step not complete.
- **2026-08-18:** Local Docker database `promise` reset (no real data). Custom User migrations applied. `users_user` verified; `auth_user` absent. pytest 5 passed. Custom User step **COMPLETE**. Phase 3 remains in progress.
- **2026-08-18:** API versioning (`/api/v1/`), DRF exception handler, error envelope, HTTP status conventions, and console logging added. Health unchanged. pytest 10 passed. Phase 3 remains in progress.
- **2026-08-18:** Shared abstract `BaseModel` (UUID pk, `created_at`, `updated_at`) added in `apps.core`. No new table/migration. pytest 15 passed. Phase 3 remains in progress.
- **2026-08-18:** Authentication architecture **design complete** (`docs/AUTHENTICATION_DESIGN.md`). Option B decided. **No authentication implementation.**
- **2026-08-19:** Phase 3 **COMPLETE** after final review. `manage.py check` PASS; `showmigrations` all required applied; `makemigrations --check` PASS; `pytest -v` 15 passed. Auth implementation remains Phase 4 (not complete). README FastAPI references removed. `DEVELOPMENT.md` checkpoint and Phase 4 wording updated.
