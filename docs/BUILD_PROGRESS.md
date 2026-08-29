# Promise — Build Progress

This file records **actual** implementation progress.

It does **not** replace:

- `docs/PROJECT_PLAN.md` — product and plan
- `docs/ARCHITECTURE.md` — intended architecture
- `docs/DEVELOPMENT.md` — intended roadmap
- `docs/AUTHENTICATION_DESIGN.md` — authentication (implemented in Phase 4)
- `docs/COMMITMENT_DESIGN.md` — commitment domain (implemented through Phase 5.10)
- `docs/OUTBOX_DESIGN.md` — transactional outbox and domain events (persistence 5.7; publisher 5.8; generic consumer 5.9; Phase 5.10 verified)
- `docs/GOAL_DESIGN.md` — goal domain (implemented through Phase 6.6; design in 6.1; models 6.2; services 6.3; APIs 6.4; Kafka verification 6.5; final review 6.6)
- `docs/REDIS_DESIGN.md` — Redis product layer (Phase 7 COMPLETE: 7.1 design, 7.2 primitive, 7.3 auth throttles, 7.4 mutation throttles, 7.5 verification). Auth `sid` denylist remains Phase 4.7. Domain caches and new locks are deferred.
- `docs/ANDROID_DESIGN.md` — Android foundation (9.1–9.6: design, bootstrap, auth, shell/Home, Commitments UI, Goals UI)

Update this file after every meaningful, verified development result. Never store secrets.

---

## Current Status

- **Current phase:** Latest Dev (Batch 17) — Profile Avatars, Firebase In-App Release Updates & Goal Conversion to Shared **COMPLETE**
- **Overall status:**
  - **Automated Validation:** 100% PASS (651 backend tests, 0 Django issues, 0 pending migrations, Android release build passing with verified release signing).
  - **Feature Implementation:** Batches 1 through 17 / Latest Dev **COMPLETE** (Profile Avatars with local & remote sync, Firebase App Distribution in-app software updates, Personal Goal conversion to Shared status, Google-Only Credential Manager Auth, AI Assistant Entry Points, Shared Goals, Concurrency, Real-Time WebSocket Chat, Notifications/FCM, Redis Rate Limiting, Kafka Outbox/Consumer, Gemini 3.5 AI Routing, Daily Motivation Quotes, Analytics, Search, Production Hardening, Multi-Service Railway Deployment Architecture, Visual Hierarchy, Finger-Synced Horizontal Pager, Layered Dark Mode, Skeleton Loaders).
  - **Real AI External Integration:** 15/15 feature contracts implemented, gated via `RUN_REAL_AI_TESTS=1` for external execution against live Gemini.
  - **Physical Multi-Device QA:** Verified on physical hardware across releases; final release builds signed and verified.
  - **Live Cloud Deployment:** Prepared and verified locally. **NOT deployed to live Railway/Aiven yet.**
- **Last completed milestone:** Latest Dev (Batch 17) — Profile Avatars, Firebase App Distribution Updates & Personal Goal Conversion to Shared (2026-08-24)
- **Immediate next step:** Live Railway & Aiven Infrastructure Provisioning (when requested).

Phase / Batch Checklist:
- Batch 1 Global State Synchronization: **COMPLETE**
- Batch 2 UI / UX Polish: **COMPLETE**
- Batch 2.5 Identity / Google / Email Authentication: **COMPLETE**
- Batch 3 Performance Optimization: **COMPLETE**
- Batch 4 Accessibility / Motion / Haptics: **COMPLETE**
- Batch 5 Reliability / Observability: **COMPLETE**
- Batch 6 Advanced Security & Account Management: **COMPLETE**
- Batch 7 Advanced Shared Goals: **COMPLETE**
- Batch 8 Advanced Notifications: **COMPLETE**
- Batch 9 Search & Discovery: **COMPLETE**
- Batch 10.1 AI Foundation + Gemini: **COMPLETE**
- Batch 10.2 AI Product Refinement: **COMPLETE**
- Batch 11 Production Hardening: **COMPLETE**
- Batch 12 Analytics: **COMPLETE**
- Batch 13 Final UX + Real Gemini Validation: **COMPLETE**
- Batch 14A Production Deployment Preparation: **COMPLETE**
- Batch 14B-PRE Final Test Cleanup + Documentation Sync: **COMPLETE**
- Batch 14C Visual Hierarchy, Gesture Sync & UI Polish: **COMPLETE**
- Batch 15 AI Reliability + Daily Motivation Quotes + Weekly Insights: **COMPLETE**
- Batch 16 Notification Settings + Feature FAQ + Tech Signature: **COMPLETE**
- Batch 17 Profile Avatars + Goal Shared Conversion + Firebase Updates: **COMPLETE** (Latest Dev)
- Batch 14B Live Deployment Execution: **PENDING USER AUTHORIZATION**

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
- **Git commit:** `d8e351e` — `feat: bootstrap Django backend`

### 2026-08-19 — Phase 4.1 — Authentication session foundation: COMPLETE

- **What was implemented:** `apps.authentication` with `AuthSession` (`BaseModel` UUID pk). Opaque refresh token format remains `{session_id}.{secret}`. Server stores HMAC-SHA256 of the secret with `AUTH_REFRESH_TOKEN_PEPPER` (`refresh_token_hmac` / `previous_token_hmac` for rotation/reuse). No raw refresh token, access token, or password on the session row. State is derived (`revoked_at`, `expires_at`, `absolute_expires_at`). Token helpers in `apps.authentication.tokens`.
- **Migration:** `authentication.0001_initial` created and applied. Table `authentication_authsession` only (plus FK/indexes). `users.User` not modified.
- **Not done:** Phase 4 not complete. No register/login/JWT/refresh/logout/`/me/` APIs, no DRF auth class, no Redis/Kafka, no Android.
- **Verification:**
  - `python manage.py check` — **PASS**
  - `python manage.py migrate` — `authentication.0001_initial` **OK**
  - `python manage.py showmigrations` — `authentication` `[X] 0001_initial`
  - `python manage.py makemigrations --check` — **PASS**
  - `pytest -v` — **27 passed** in 3.59s (15 Phase 3 + 12 AuthSession)
- **Git commit:** not committed yet

### 2026-08-19 — Phase 4.2 — JWT access-token infrastructure: COMPLETE

- **What was implemented:** Access JWT issue/verify in `apps.authentication.access_tokens`. Library: **PyJWT** 2.13. Algorithm: **HS256** with dedicated `JWT_SIGNING_KEY` and `kid` (not `DJANGO_SECRET_KEY`). Lifetime **15 minutes**. Claims: `sub`, `sid`, `jti`, `iat`, `exp`, `iss`, `aud`, `typ=access`. No email/password/roles/refresh token in payload. `AccessTokenError` is a generic internal exception for a future 401 mapping. Optional previous key settings exist for rotation. No DRF authentication class, no HTTP auth APIs.
- **Validation:** Rejects invalid signature, expiry, issuer, audience, `typ`, missing claims, non-UUID `sub`/`sid`, malformed tokens. Does not load User/AuthSession from the database.
- **Migration:** **none**. `makemigrations --check` — No changes detected.
- **Not done:** Phase 4 not complete. No register/login/refresh/logout/`/me/`, no DRF auth class.
- **Verification:** `python manage.py check` PASS; `pytest -v` **41 passed** in 6.08s; `makemigrations --check` PASS.
- **Git commit:** not committed yet

### 2026-08-19 — Phase 4.3 — Registration + Login: COMPLETE

- **Endpoints:** `POST /api/v1/auth/register/` (`201`) and `POST /api/v1/auth/login/` (`200`). Both remain public and issue one `AuthSession`, one 15-minute access JWT, and one opaque `{session_id}.{secret}` refresh token.
- **Registration:** Canonical lowercase email, Django validation, Argon2id password hashing through Django, duplicate email/casing protection (`409 EMAIL_ALREADY_EXISTS`), and atomic User + AuthSession + token creation.
- **Login:** Case-insensitive canonical email lookup, Django `check_password`, dummy hash check for unknown email, and the same generic `401 AUTHENTICATION_FAILED` for unknown email, wrong password, and inactive user.
- **Security:** The API never returns or logs plaintext passwords, password hashes, refresh-token HMACs, or superuser state. Only the client receives the raw refresh token. PBKDF2 remains enabled for existing password hashes.
- **Migration:** **none**. `python manage.py makemigrations --check` — No changes detected.
- **Verification:** `python manage.py check` PASS; `pytest -v` **65 passed** in 2.81s; `makemigrations --check` PASS; IDE lint diagnostics clean.
- **Manual cURL:** Not executed because `runserver` was intentionally not started.
- **Not done:** Phase 4 is not complete. No refresh/logout/logout-all/`me`, DRF authentication class, Redis/Kafka, or Android work.
- **Git commit:** not committed yet

### 2026-08-19 — Phase 4.4 — DRF JWT authentication + `/auth/me/`: COMPLETE

- **Authentication class:** `JWTAccessAuthentication` reuses `decode_access_token`. It requires `Authorization: Bearer <access_token>`, loads `User` from `sub`, and checks that the `sid` `AuthSession` exists and `is_active()` (not missing, revoked, or expired). PostgreSQL is the source of truth. No Redis denylist.
- **Context:** `request.auth` is `AccessAuthenticationContext(session_id, token_id=jti)`. No refresh-token material.
- **Default permissions:** `IsAuthenticated`. Public exceptions: `GET /api/v1/health/`, `POST /api/v1/auth/register/`, `POST /api/v1/auth/login/`, and the v1 404 catch-all (explicit `AllowAny` + empty authentication classes).
- **Endpoint:** `GET /api/v1/auth/me/` returns `{ "user": { id, email, name, timezone, created_at } }` with HTTP 200. No password, hash, superuser flag, tokens, or session internals.
- **Errors:** Missing access credentials → `401 UNAUTHENTICATED` `"Authentication credentials were not provided."` Invalid/malformed token, inactive user, or inactive session → `401 UNAUTHENTICATED` `"Authentication failed."` Login still uses `AUTHENTICATION_FAILED`.
- **Migration:** **none**. `python manage.py makemigrations --check` — No changes detected.
- **Verification:** `python manage.py check` PASS; `pytest -v` **88 passed** in 3.62s; `makemigrations --check` PASS; `git diff --check` PASS.
- **Manual cURL:** Not executed by this step (`runserver` was not started from the agent).
- **Not done:** Phase 4 is not complete. No refresh/logout/logout-all, Redis/Kafka, or Android work.
- **Git commit:** not committed yet

### 2026-08-19 — Phase 4.5 — Refresh-token rotation: COMPLETE

- **Endpoint:** `POST /api/v1/auth/refresh/` (`200`). Public (`AllowAny`, empty authentication classes). Request `{ "refresh_token": "<session_id>.<secret>" }`. Success envelope is `{ "tokens": { access_token, refresh_token, token_type, expires_in } }` only — no `user` object. Session id is unchanged.
- **Schema change:** `AuthSession.current_refresh_secret_ciphertext` (`TextField`, blank, default `""`). Existing rotation fields unchanged (`refresh_token_hmac`, `previous_token_hmac`, `previous_rotated_at`, `expires_at`, `absolute_expires_at`, `last_used_at`, `revoked_at`).
- **Encrypted current refresh secret:** HMAC remains the canonical verifier. The current secret is also Fernet-encrypted with dedicated `AUTH_REFRESH_TOKEN_ENCRYPTION_KEY` so a 30-second retry can return the already-issued current token. HMAC cannot reconstruct the secret. Previous secrets are HMAC-only. Plaintext secrets, composed refresh tokens, and access JWTs are never persisted. Ciphertext is used only for grace recovery, never for authentication.
- **Rotation:** Current-token match under `transaction.atomic()` + `select_for_update()`: previous HMAC ← current HMAC, `previous_rotated_at = now`, new secret R2, store HMAC(R2) + Fernet(R2), update `last_used_at`, extend idle expiry capped by `absolute_expires_at`, issue a new access JWT (new `jti`).
- **30-second grace:** Previous-token match within 30 seconds of `previous_rotated_at` does not rotate or revoke. Decrypt current ciphertext, return `{sid}.{current secret}` and a new access JWT. Absolute expiry is not extended. Rotation fields are not written.
- **Reuse detection:** Previous token after the grace window, or a secret matching neither HMAC, revokes that session (`revoked_reason=reuse`) then returns generic `401 TOKEN_INVALID`. Revoke is committed before the error is raised so the write is not rolled back. Unknown `session_id` is `TOKEN_INVALID` without a revoke. Idle/absolute expiry is `TOKEN_EXPIRED`. Already-revoked is `SESSION_REVOKED`.
- **Concurrency:** PostgreSQL row locking only. Redis was not added. Concurrent current-token refreshes serialize; the waiter follows the grace path and returns the same successor refresh token with a distinct access `jti`.
- **Migration:** `authentication.0002_authsession_current_refresh_secret_ciphertext` created and applied. `python manage.py showmigrations authentication` — `[X] 0001_initial`, `[X] 0002_authsession_current_refresh_secret_ciphertext`.
- **Tests:** Refresh coverage includes successful rotation, grace retry, post-grace reuse revoke, invalid/malformed/unknown/revoked/expired tokens, idle cap, unchanged session id, changing `jti`/secret/ciphertext, no plaintext at rest, encrypt/decrypt and wrong-key failure, and concurrent refresh. `pytest -v` **109 passed**.
- **Verification:** `python manage.py check` PASS; `python manage.py makemigrations --check` PASS (no further changes).
- **Manual cURL:** Commands documented for later use; `runserver` was not started by this step.
- **Not done:** Phase 4 is not complete. No logout/logout-all, Redis, Kafka, or Android work.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 4.6 — Logout / logout-all: COMPLETE

- **Logout current session:** `POST /api/v1/auth/logout/` requires `Authorization: Bearer <access>` and `{ "refresh_token": "<session_id>.<secret>" }`. Parses the refresh token, loads the `AuthSession` with `select_for_update()`, requires the row to belong to `request.user`, verifies HMAC (current or previous), sets `revoked_at` / `revoked_reason=logout`, and returns **204** with an empty body. Does not create or rotate tokens. Does not delete the row.
- **Logout-all:** `POST /api/v1/auth/logout-all/` requires a valid access JWT and no body. In one transaction, locks then updates all of `request.user`'s rows with `revoked_at IS NULL` to `revoked_reason=logout_all`. Other users' sessions are untouched. Already-revoked rows keep their previous reason.
- **Idempotency:** Repeating logout for an already-revoked **own** session with a still-valid access JWT from another session returns 204. Logout-all leaves already-revoked rows unchanged and still returns 204. If the access JWT's own session is already revoked, `JWTAccessAuthentication` returns `401 UNAUTHENTICATED` before the view (existing class, unchanged).
- **Immediate refresh revocation:** Refresh against a logged-out session returns `401 SESSION_REVOKED`. No Redis.
- **Access-token behavior before Redis denylist:** `decode_access_token` still accepts the JWT until `exp`. Protected routes (`/auth/me/`) reject it because PostgreSQL shows the session revoked (`401 UNAUTHENTICATED`). Immediate cryptographic kill of access JWTs is deferred to the Redis denylist phase.
- **Errors:** Malformed refresh, unknown session, HMAC mismatch, and another user's session all return the same `401 TOKEN_INVALID`. Missing access credentials → `401 UNAUTHENTICATED`. Missing `refresh_token` → `400 VALIDATION_ERROR`. No session existence leak.
- **Migration:** **none**. `python manage.py makemigrations --check` — No changes detected.
- **Tests:** `tests/test_auth_logout.py` covers own logout, refresh failure, idempotency, cross-user rejection, malformed/unknown tokens, logout-all, other-user isolation, `/me/` after logout, and no token/hash/secret leakage. Existing tests kept.
- **Manual cURL:** Placeholders only (no real tokens). `runserver` was not started by this step.

```bash
curl -i -X POST http://127.0.0.1:8000/api/v1/auth/logout/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{"refresh_token": "<REFRESH_TOKEN>"}'

curl -i -X POST http://127.0.0.1:8000/api/v1/auth/refresh/ \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "<REFRESH_TOKEN>"}'

curl -i -X POST http://127.0.0.1:8000/api/v1/auth/logout-all/ \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```
- **Not done:** Phase 4 is not complete. No Redis, Kafka, or Android work.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 4.7 — Redis authentication integration: COMPLETE

- **Client:** Synchronous `redis-py` via `REDIS_URL` in `apps.core.redis`. Short socket timeouts. Lazy connect so health/`runserver` do not require Redis. `incr_with_ttl` is a reusable counter primitive; no endpoint throttles were wired.
- **Denylist key:** `promise:auth:denylist:sid:{session_id}`. Value is `1` (not a JWT, refresh token, or password).
- **TTL:** `JWT_ACCESS_TTL_SECONDS + JWT_LEEWAY_SECONDS` (900 + 30 = 930s), covering any still-valid access JWT for that `sid` including leeway. Keys are not indefinite.
- **Auth flow:** After JWT cryptographic validation, `JWTAccessAuthentication` checks the denylist, then PostgreSQL user + `AuthSession.is_active()`. PostgreSQL validation was not removed.
- **Logout:** After Postgres revoke, each affected `sid` is written to Redis (logout, logout-all, and refresh reuse). Redis write failures are logged without secrets and do not fail the request.
- **Fallback:** Redis down → skip denylist read/write (fail-open on Redis). PostgreSQL session checks still run, so logout still makes `/auth/me/` return 401. Refresh is unchanged (Postgres only).
- **Refresh lock:** **Not implemented.** `SELECT FOR UPDATE` already serializes rotation.
- **Tests:** fakeredis (no Docker Redis required). Coverage includes allow, deny-while-Postgres-active, logout/logout-all keys + TTL, expiry, Redis-down fallback, and Postgres still authoritative if the denylist key is missing. `pytest -v` **138 passed**.
- **Migration:** **none**.
- **Manual cURL:** Placeholders only. Restart the already-running server so it loads the Redis client. Docker Redis is optional: logout still revokes in PostgreSQL if Redis is down.

```bash
curl -sS -X POST http://127.0.0.1:8000/api/v1/auth/login/ \
  -H "Content-Type: application/json" \
  -d '{"email":"<email>","password":"<password>"}'

curl -i http://127.0.0.1:8000/api/v1/auth/me/ \
  -H "Authorization: Bearer <ACCESS_TOKEN>"

curl -i -X POST http://127.0.0.1:8000/api/v1/auth/logout/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{"refresh_token": "<REFRESH_TOKEN>"}'

curl -i http://127.0.0.1:8000/api/v1/auth/me/ \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```
- **Not done (deferred):** Login/register/refresh rate limits, Kafka user events, and Android. Phase 4 APIs 4.1–4.7 were verified in 4.8.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 4.8 — Final authentication verification: COMPLETE

- **Automated tests:** `pytest -v` **138 passed** in 7.93s. No tests were weakened or rewritten.
- **Configuration:** `python manage.py check` PASS (0 issues). `python manage.py makemigrations --check` PASS (no changes). `authentication` migrations `[X] 0001_initial`, `[X] 0002_authsession_current_refresh_secret_ciphertext`. `git diff --check` PASS.
- **Secrets:** `.env` is gitignored (`.gitignore:4:.env`); not tracked. JWT signing key, refresh pepper, and refresh encryption key are loaded from the environment. `.env.example` has placeholders only (`changeme`). Production secrets are not in source.
- **Source of truth:** PostgreSQL `AuthSession` remains authoritative. Redis denylist is a TTL'd `sid` hint (`promise:auth:denylist:sid:{session_id}`).
- **Redis fallback (automated):** `test_redis_unavailable_on_read_falls_back_to_postgres_allow` and `test_redis_unavailable_on_logout_still_revokes_in_postgres` PASS. Redis down skips the denylist; PostgreSQL still allows active sessions and rejects revoked ones. `test_revoked_postgres_session_fails_even_without_denylist_key` PASS.
- **Lifecycle (automated):** register 201; login 200 new session; `/me/` 200/401; refresh rotation with unchanged session id and new `jti`; 30s grace; post-grace reuse revoke; logout 204; logout-all 204 without touching other users; denylist write on logout.
- **Security review:** No blocking issues. Passwords/hashes/HMACs/ciphertext are not in API bodies. Logs use session/user ids only. Login failures are generic (`AUTHENTICATION_FAILED`). JWTs carry `sub`/`sid`/`jti`/`iat`/`exp`/`iss`/`aud`/`typ` — no email/name/password/roles. Default DRF permission is `IsAuthenticated`. Logout sets `revoked_at` and does not delete rows. Refresh uses `select_for_update()`. Access TTL 15 minutes; refresh idle 30 days / absolute 90 days.
- **Manual test status:** **Automated verification complete; manual verification pending.** Live cURL was not executed in this step. `runserver` was not started by this verification.
- **Final authentication status:** Phase 4 **COMPLETE** for the implemented Option B APIs (4.1–4.7). Deferred by design and out of 4.8 scope: rate limits, password reset, email verification, social login, Kafka, Android.
- **Git commit:** `be52efe` — `feat: implement authentication`; docs follow-up `0655fe9` — `plan update`

### 2026-08-20 — Phase 5 — Commitment domain design: COMPLETE (implementation not started)

- **What was implemented:** Design only. `docs/COMMITMENT_DESIGN.md` defines Commitment, CommitmentParticipant, CommitmentEvent, stored vs derived status (OVERDUE derived), personal-vs-shared participant model, snooze, APIs, authorization, and outbox/Kafka plan.
- **Files changed:** `docs/COMMITMENT_DESIGN.md`, `docs/BUILD_PROGRESS.md`
- **Not done:** No Django `commitments` app. No models, migrations, serializers, views, or tests. PostgreSQL / Redis / Kafka / Android / authentication unchanged.
- **Result:** Phase 5 design is the source of truth. Phase 5 is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 5.2 — Commitment domain models: COMPLETE

- **What was implemented:** `apps.commitments` with `Commitment`, `CommitmentParticipant`, and `CommitmentEvent` inheriting `BaseModel`. Stored statuses exclude OVERDUE (`is_overdue()` is derived). No `save()` workflows. Personal and shared promises use the same tables.
- **Files changed:** `backend/apps/commitments/` (`apps.py`, `models.py`, `migrations/0001_initial.py`), `backend/config/settings/base.py` (`INSTALLED_APPS`), `backend/tests/test_commitment_models.py`, `docs/BUILD_PROGRESS.md`
- **Migration:** `commitments.0001_initial` applied. Tables `commitments_commitment`, `commitments_commitmentparticipant`, `commitments_commitmentevent`.
- **Constraints/indexes:** `uniq_commitment_participant_user`; `commitments_creator_status_due`; `commitments_status_due_at`; `commitments_participant_user`; `commitments_event_created`. FKs: `created_by` PROTECT; participant `user` CASCADE; event `actor` SET_NULL; child rows CASCADE from commitment.
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **155 passed** in 10.51s. PostgreSQL tables confirmed via `pg_tables`.
- **Not done:** No serializers, views, APIs, Kafka, outbox, Redis usage, notifications, AI, goals, or Android. Phase 5 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 5.3 — Commitment domain services: COMPLETE

- **Schema cleanup:** `CommitmentParticipant.user` is **PROTECT** (matches design). Redundant `commitments_participant_user` index removed; Django’s FK index remains. `source=SHARED` kept as origin/context, not a separate type. Migration `commitments.0002_protect_participant_user_and_drop_redundant_index` applied.
- **Services:** `apps.commitments.services` — `create_commitment`, `complete_commitment`, `snooze_commitment`, `unsnooze_commitment`, `set_waiting`, `clear_waiting`, `cancel_commitment`, plus `can_view` / `can_complete` / `can_snooze` / `can_unsnooze` / `can_wait` / `can_cancel`.
- **Authorization:** Creator and active participants may view. Only an **ACTIVE RESPONSIBLE** participant may mutate. Unrelated users get `CommitmentNotFoundError`. Recipients/observers/creator-only get `CommitmentForbiddenError`.
- **Transactions:** `transaction.atomic()` around create (commitment + participant + event) and every mutation (row lock + event). Event failure rolls back state.
- **Concurrency:** `select_for_update()` on the commitment row.
- **Events:** One `CommitmentEvent` per successful mutation (idempotent no-ops write none). Actor is the requesting user. `clear_waiting` uses `UPDATED` with `{from, to}` because there is no `WAITING_CLEARED` type. No Kafka/outbox.
- **Idempotency:** complete if already COMPLETED — no-op, no second event. cancel if CANCELLED — same. set_waiting if WAITING — no-op. unsnooze/clear_waiting if already PENDING — no-op. snooze with the same `snoozed_until` — no-op; a new time writes another SNOOZED event. Create is not idempotent.
- **Create:** Personal only: creator is inserted as RESPONSIBLE. Shared invite APIs are not implemented; `source=SHARED` does not add extra participants.
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **177 passed** in 10.26s.
- **Not done:** Serializers, views, URLs, Kafka, outbox, Redis, notifications, AI, Android. Phase 5 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 5.4 — Commitment REST APIs: COMPLETE

- **What was implemented:** Authenticated commitment REST APIs under `/api/v1/commitments/`. Function-based DRF views (same style as auth). Create/list/detail/patch plus complete, snooze, unsnooze, wait, cancel. Views call the service layer; they do not duplicate state transitions.
- **Routes (trailing slashes):**
  - `POST /api/v1/commitments/` — 201
  - `GET /api/v1/commitments/` — 200, page-number pagination (`page_size` 20, max 100)
  - `GET /api/v1/commitments/{id}/` — 200
  - `PATCH /api/v1/commitments/{id}/` — 200
  - `POST /api/v1/commitments/{id}/complete/` — 200
  - `POST /api/v1/commitments/{id}/snooze/` — 200; body `{ "snoozed_until": "<iso8601>" }`
  - `POST /api/v1/commitments/{id}/unsnooze/` — 200
  - `POST /api/v1/commitments/{id}/wait/` — 200
  - `POST /api/v1/commitments/{id}/cancel/` — 200
- **Authentication:** JWT `Authorization: Bearer <access>`. Missing token → 401 `UNAUTHENTICATED`. Invalid token → 401 `UNAUTHENTICATED`. Health, register, and login remain public.
- **Authorization:** View = creator or active participant. Mutate = active `RESPONSIBLE` participant. Unrelated users → 404 `COMMITMENT_NOT_FOUND` (no 403 resource discovery). Recipients/observers who can view but cannot mutate → 403 `FORBIDDEN`.
- **Create:** `created_by` is always `request.user` (ignored if sent). Creator is inserted as `RESPONSIBLE`. `CREATED` event. `due_precision=NONE` iff `due_at` is null.
- **PATCH:** Only `title`, `description`, `due_at`, `due_precision`. Status timestamps, `created_by`, and participants are not writable. Terminal commitments → 409. `UPDATED` event with `{ "fields": [...] }` when something actually changes.
- **List:** Visible rows only. Filters: `status`, `source`, `is_overdue` (`true`/`false`). Order: `due_at` nulls last, then `created_at` desc. `Exists` subquery (no join+distinct). Derived `is_overdue` is serialized, not stored.
- **Response fields:** `id`, `title`, `description`, `status`, `due_at`, `due_precision`, `source`, `snoozed_until`, `completed_at`, `cancelled_at`, `created_at`, `updated_at`, `is_overdue`. No `created_by`, participants, events, or auth internals.
- **Error mapping:** `CommitmentNotFoundError` → 404 `COMMITMENT_NOT_FOUND`; `CommitmentForbiddenError` → 403 `FORBIDDEN`; `CommitmentInvalidTransitionError` → 409 `COMMITMENT_INVALID_TRANSITION`; serializer / `CommitmentValidationError` → 400 `VALIDATION_ERROR`; `CommitmentInvalidSnoozeError` → 400 `COMMITMENT_INVALID_SNOOZE`. Envelope `{ "error": { "code", "message" } }` (+ `details` on validation).
- **Idempotency:** Service-layer only. Repeat complete/cancel/wait/unsnooze/same-time snooze return 200 and do not write a second event.
- **Files changed:** `backend/apps/commitments/serializers.py`, `views.py`, `urls.py` (new); `backend/apps/commitments/services.py` (`get_visible_commitment`, `list_visible_commitments`, `update_commitment`, `can_update`); `backend/config/urls.py`; `backend/tests/test_commitment_api.py`; `docs/BUILD_PROGRESS.md`
- **Migration:** none. `makemigrations --check` — No changes detected.
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **189 passed** in 13.09s (177 existing + 12 API).
- **Manual cURL:** **pending**. Automated only. Do not claim live HTTP passed. Commands are below; `runserver` was not started.
- **Not done:** Participant invite APIs, `unwait` HTTP (`clear_waiting` remains service-only), Kafka, outbox, Redis, notifications, AI, goals, challenges, Android, authentication changes. Phase 5 remains in progress.
- **Git commit:** not committed yet

Manual cURL (requires a running server and `ACCESS_TOKEN` from login). Expected statuses in comments. **Not executed this step.**

```bash
# 0. login (public) — 200
curl -sS -X POST http://127.0.0.1:8000/api/v1/auth/login/ \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"your-password"}'
# export ACCESS_TOKEN=...

# 1. create — 201
curl -i -X POST http://127.0.0.1:8000/api/v1/commitments/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "title": "Send credentials",
    "description": "Send production credentials",
    "due_at": "2026-08-20T18:00:00Z",
    "due_precision": "DATETIME",
    "source": "MANUAL"
  }'
# export COMMITMENT_ID=...

# 2. list — 200
curl -i http://127.0.0.1:8000/api/v1/commitments/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 3. get — 200
curl -i http://127.0.0.1:8000/api/v1/commitments/$COMMITMENT_ID/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 4. patch — 200
curl -i -X PATCH http://127.0.0.1:8000/api/v1/commitments/$COMMITMENT_ID/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{"title": "Send production credentials"}'

# 5. complete — 200 (repeat is 200, no second COMPLETED event)
curl -i -X POST http://127.0.0.1:8000/api/v1/commitments/$COMMITMENT_ID/complete/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Use a second open commitment for snooze/wait/cancel (completed rows reject those with 409).

# 6. snooze — 200
curl -i -X POST http://127.0.0.1:8000/api/v1/commitments/$COMMITMENT_ID/snooze/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{"snoozed_until":"2026-08-20T20:00:00Z"}'

# 7. unsnooze — 200
curl -i -X POST http://127.0.0.1:8000/api/v1/commitments/$COMMITMENT_ID/unsnooze/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 8. wait — 200
curl -i -X POST http://127.0.0.1:8000/api/v1/commitments/$COMMITMENT_ID/wait/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 9. cancel — 200
curl -i -X POST http://127.0.0.1:8000/api/v1/commitments/$COMMITMENT_ID/cancel/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 10. unauthorized — 401 missing; 401 invalid; 404 other user's id
curl -i http://127.0.0.1:8000/api/v1/commitments/
curl -i http://127.0.0.1:8000/api/v1/commitments/ \
  -H "Authorization: Bearer not-a-jwt"
curl -i http://127.0.0.1:8000/api/v1/commitments/$SOMEONE_ELSES_ID/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

---

### 2026-08-20 — Application table naming cleanup

- **Convention:** Application-owned tables use concise explicit `Meta.db_table` names. Django internal tables (`auth_group`, `auth_permission`, `auth_group_permissions`, `django_content_type`, `django_migrations`, …) keep Django defaults.
- **What was implemented:** `db_table` on `User`, `AuthSession`, `Commitment`, `CommitmentParticipant`, `CommitmentEvent`. Migrations rename existing tables (`ALTER TABLE … RENAME TO`). No drop/recreate, no database reset, no field/API/service changes.
- **Migrations created and applied:**
  - `users.0002_rename_application_tables`
  - `authentication.0003_rename_application_tables` (depends on `users.0002`)
  - `commitments.0003_rename_application_tables` (depends on `users.0002`)
- **Old → new:**
  - `users_user` → `users`
  - `authentication_authsession` → `auth_sessions`
  - `commitments_commitment` → `commitments`
  - `commitments_commitmentparticipant` → `commitment_participants`
  - `commitments_commitmentevent` → `commitment_events`
- **Django auto M2M (PermissionsMixin, not framework tables):** `users_user_groups` → `users_groups`; `users_user_user_permissions` → `users_user_permissions`. Data preserved.
- **Row counts (dev DB, identical before/after):** `users` 2; `auth_sessions` 7; `commitments` 3; `commitment_participants` 3; `commitment_events` 3.
- **FKs after rename:** `auth_sessions.user_id` → `users.id`; `commitments.created_by_id` → `users.id`; `commitment_participants.commitment_id` → `commitments.id`; `commitment_participants.user_id` → `users.id`; `commitment_events.commitment_id` → `commitments.id`; `commitment_events.actor_id` → `users.id`.
- **Old application tables:** absent. No duplicate tables.
- **Verification:** `manage.py check` PASS; `showmigrations` all `[X]`; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **189 passed** in 11.99s.
- **Not done:** Phase 5 remains in progress. No API, auth, Redis, Kafka, or Android changes.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 5.6 — Transactional outbox and domain event design: COMPLETE (implementation not started)

- **What was implemented:** Design only. `docs/OUTBOX_DESIGN.md` distinguishes `CommitmentEvent` (audit) from `OutboxEvent` (transport), defines `outbox_events` schema, envelope, commitment Kafka event mapping, transaction boundary, publisher/retry (deferred), consumer idempotency, topic `promise.commitment.v1`, failure modes, retention, and security rules.
- **Files changed:** `docs/OUTBOX_DESIGN.md`, `docs/BUILD_PROGRESS.md`
- **Not done:** No `OutboxEvent` model, migration, publisher, Kafka client, consumer, or service-layer outbox writes. Commitment models/services/APIs, authentication, Android, Redis, and Compose are unchanged.
- **Result:** Outbox design is the source of truth for DB→Kafka. Phase 5 is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 5.7 — Transactional outbox persistence: COMPLETE

- **What was implemented:** `apps.outbox` with `OutboxEvent` (`db_table = outbox_events`). Commitment mutations that write a `CommitmentEvent` also write one `OutboxEvent` in the same `transaction.atomic()`. Helper `record_outbox_event()`. No Kafka.
- **Schema:** UUID `id` (event_id); `aggregate_type`, `aggregate_id`, `event_type`, `event_version` (1), `payload`, `occurred_at`, `published_at`, `attempts`, `next_attempt_at`, `last_error`, `created_at`, `updated_at`. Partial index `outbox_unpublished_due` on `(next_attempt_at, created_at) WHERE published_at IS NULL AND next_attempt_at IS NOT NULL`.
- **Mapping:** `CREATED→commitment.created`, `UPDATED→commitment.updated` (including `clear_waiting`), `COMPLETED→commitment.completed`, `SNOOZED→commitment.snoozed`, `UNSNOOZED→commitment.unsnoozed`, `WAITING→commitment.waiting`, `CANCELLED→commitment.cancelled`. No-ops write neither row.
- **Payload:** compact JSON (`commitment_event_id`, `commitment_id`, `created_by_user_id`, `actor_user_id`, `status`, plus event-specific fields). No titles, secrets, or tokens. Max 16 KiB.
- **Migration:** `outbox.0001_initial_outbox_event` applied. Table `outbox_events` created.
- **Tests:** `backend/tests/test_outbox.py` — create atomic; outbox failure rolls back commitment+event+outbox; complete is 1+1; no-ops write nothing; mapping; oversized payload rejected.
- **DB verification (PostgreSQL, inspectable in DataGrip):** `outbox_events` present. Live `create_commitment` on the local `promise` DB: commitments 3→4, commitment_events 3→4, outbox_events 0→1 (`event_type=commitment.created`, `published_at` null). Kafka not used.
- **Files changed:** `backend/apps/outbox/` (new), `backend/apps/commitments/services.py`, `backend/config/settings/base.py` (`INSTALLED_APPS`), `backend/tests/test_outbox.py`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **197 passed** in 12.18s.
- **Not done:** Publisher, Kafka client, consumers, notifications, AI, Android. Phase 5 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 5.8 — Outbox Kafka publisher: COMPLETE

- **What was implemented:** Django management command `python manage.py publish_outbox`. Bounded batches of 50 due unpublished rows, `SELECT … FOR UPDATE SKIP LOCKED`, sync Kafka produce with broker ack, then `published_at`. No consumers.
- **Command:** `publish_outbox` — one-shot: drain due rows in batches and exit. Prints `published=N failed=M`.
- **Kafka topic:** `promise.commitment.v1`. Key = `aggregate_id`. Value = JSON envelope (`event_id`, `event_type`, `event_version`, `occurred_at`, `aggregate_type`, `aggregate_id`, `payload`). `event_id` = `outbox_events.id`, stable across retries.
- **Client:** `confluent-kafka` (`acks=all`, wait for delivery callback / flush). Bootstrap from existing `KAFKA_BOOTSTRAP_SERVERS`.
- **Retry / backoff:** attempts incremented on every try. Failure: `last_error` sanitized (exception type + generic message, max 2048, no payloads/secrets), `next_attempt_at` = now + 5s, 15s, 45s, 2 min, 5 min, 15 min, then **30 min cap**.
- **Parking:** after 8 attempts, `next_attempt_at = NULL`, `published_at` remains `NULL`. Parked rows are skipped.
- **Concurrency:** PostgreSQL `SKIP LOCKED` only. No ownership column. Concurrent publishers cannot claim the same row.
- **Tests:** `backend/tests/test_outbox_publisher.py` — success envelope/topic/key; `published_at`; stable `event_id`; failures; backoff; 30 min cap; park after 8; duplicate invocation; already-published skipped; no due rows; invalid payload; safe `last_error`; bounded batches; concurrent publishers; management command; mock Kafka; unavailable broker (`127.0.0.1:1`) without changing Compose.
- **Automated verification and local Kafka integration verification completed.**
- **Local Kafka integration (agent-run, not manual):** Docker running; `promise-kafka` healthy; topic `promise.commitment.v1` created. Live `create_commitment` wrote an unpublished `outbox_events` row. `python manage.py publish_outbox` exited 0 (`published=2 failed=0`, including the earlier Phase 5.7 unpublished row). Success row `id=9909d468-6635-465c-8cd8-a5cb0ba41322`: `published_at` set, `attempts=1`, `event_id` unchanged, row not deleted, no duplicate outbox for that commitment. Consumed from `promise.commitment.v1` (Python consumer + `kafka-console-consumer.sh --timeout-ms 5000`): key = aggregate_id `6884a2e9-b054-4985-97b4-58f7eee7d4e1`, `event_id` = outbox id, `event_type=commitment.created`, `event_version=1`, payload matched, no title/password on the bus.
- **Failure-path verification:** pytest unavailable-broker test plus live DB row (`id=0c79535d-5ba8-4cc4-ad89-11bfccea08b6`) using a producer aimed at `127.0.0.1:1` (Compose unchanged). `published_at` NULL, `attempts=1`, `next_attempt_at` in the future, `last_error` length 40 and secret-free.
- **DB verification:** `to_regclass('public.outbox_events')` = `outbox_events`. Published event has `published_at`. `event_id` unchanged. No duplicate outbox rows for the new commitment. Database was not reset.
- **Files changed:** `backend/apps/outbox/publisher.py`, `backend/apps/outbox/kafka.py`, `backend/apps/outbox/management/commands/publish_outbox.py`, `backend/tests/test_outbox_publisher.py`, `backend/pyproject.toml` (`confluent-kafka`), `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **214 passed** in 14.67s.
- **Not done:** Kafka consumers, notifications, AI, analytics, Android, authentication changes, Redis changes, `docker-compose.yml` changes. Phase 5 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 5.9 — Generic Kafka consumer + event idempotency: COMPLETE

- **What was implemented:** Reusable Kafka consumer foundation in `apps.outbox` plus `python manage.py consume_commitment_events`. Envelope validation, no-op commitment processor, `processed_events` idempotency. No product side effects (no notifications/AI/analytics).
- **Command:** `consume_commitment_events` — subscribe to `promise.commitment.v1` as group `promise-commitment-events`. `--max-messages` / `--timeout` for bounded runs. SIGINT/`close()` shutdown. `enable.auto.commit=false`, `enable.auto.offset.store=false`. Offset committed only after successful handling.
- **Consumer architecture:** `parse_envelope` → skip or `process_and_record` (insert `processed_events` then processor, same DB transaction; unique `(consumer_group, event_id)` is the race guard) → commit Kafka offset. Processor logs `event_id`, `event_type`, `aggregate_id` only.
- **Envelope policy:** structurally invalid / unknown `event_type` / `event_version` ≠ 1 → log, do not process, **commit offset** (permanent skip; no DLT). Transient processor/DB failure → **do not commit offset** (Kafka redelivers). Supported types: `commitment.created|updated|completed|snoozed|unsnoozed|waiting|cancelled` at version 1.
- **Idempotency:** table `processed_events` (`id` UUID, `consumer_group`, `event_id`, `processed_at`, BaseModel timestamps). Unique `(consumer_group, event_id)`. Same `event_id` in a second group is allowed. INSERT then process; `IntegrityError` = duplicate, skip processor, still commit offset.
- **Offset / crash:** A before DB commit → redeliver, process. B DB committed, offset not → redeliver, duplicate. C both committed → done. At-least-once.
- **Retries:** Kafka redelivery only. Future notification consumer may add retry limits, parking, DLT — not in this phase.
- **Tests:** `backend/tests/test_outbox_consumer.py` (21) — valid/missing/invalid envelope; first vs second delivery; independent groups; unique constraint; processor rollback; offset commit/no-commit; unknown type; unsupported version; logging without payload/secrets; concurrent duplicate insert; command flags; duplicate count + close.
- **Automated verification and local Kafka integration verification completed.**
- **Local Kafka integration (agent-run, not manual):** topic `promise.commitment.v1` present; produced `event_id=a072a060-2a2f-44d1-92a9-8be0c7323327`; `consume_commitment_events --max-messages 1 --timeout 15` → `processed=1`, `processed_events` row created; reproduced the same event; second consume → `duplicates=1 processed=0`; still one row, `id` and `processed_at` unchanged. Database not reset.
- **Migration:** `outbox.0002_processed_event` applied. Table `processed_events`. Commitment/outbox schemas unchanged.
- **Files changed:** `backend/apps/outbox/models.py`, `backend/apps/outbox/envelope.py`, `backend/apps/outbox/consumer.py`, `backend/apps/outbox/kafka.py`, `backend/apps/outbox/management/commands/consume_commitment_events.py`, `backend/apps/outbox/migrations/0002_processed_event.py`, `backend/tests/test_outbox_consumer.py`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -q` **235 passed** in 13.69s.
- **Not done:** notifications, AI, analytics, activity feed, Android, authentication, Redis, new Commitment APIs. Phase 5 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 5.10 — Final Commitment/Event Architecture Verification: COMPLETE

- **What was verified:** Full HTTP → service → `Commitment` + `CommitmentEvent` + `OutboxEvent` → PostgreSQL COMMIT → `publish_outbox` → Kafka `promise.commitment.v1` → `consume_commitment_events` → `processed_events` path. Domain status machine, event/outbox atomicity, Kafka at-least-once + dedup, application tables, and payload/log security.
- **Correctness fix (blocking):** `run_consumer` now seeks back to a failed record on processor/DB retry so a later success cannot commit the partition past it. Test: `test_run_consumer_retries_failed_record_before_later_success`. No business-behavior change.
- **Domain:** OVERDUE remains derived (`PENDING`/`WAITING` + past `due_at`). Snooze does not rewrite `due_at`. `COMPLETED`/`CANCELLED` are terminal. Idempotent commands write neither `CommitmentEvent` nor `OutboxEvent`. Unrelated users 404; viewers who are not responsible 403. Creator is the sole `RESPONSIBLE` participant on create.
- **Events:** Successful mutations write exactly one `CommitmentEvent` and one `OutboxEvent` in the same transaction. Mapping: `CREATED`→`commitment.created`, `UPDATED`→`commitment.updated`, `COMPLETED`→`commitment.completed`, `SNOOZED`→`commitment.snoozed`, `UNSNOOZED`→`commitment.unsnoozed`, `WAITING`→`commitment.waiting`, `CANCELLED`→`commitment.cancelled`. `event_version=1`. Compact payload (ids, status, type-specific timestamps/fields). No titles, passwords, JWTs, refresh tokens, or keys.
- **Outbox:** `OutboxEvent.id` is Kafka `event_id`. `aggregate_id` is the commitment UUID and the Kafka key. `published_at` is set only after broker ack. Retries keep the same `id`. Parked rows stay queryable (`next_attempt_at` NULL). No automatic destructive cleanup.
- **Kafka:** Topic `promise.commitment.v1`; group `promise-commitment-events` (stable); `enable.auto.commit=false`; durable dedup on `(consumer_group, event_id)`. Duplicate replay is a no-op processor + existing `processed_events` row.
- **Database:** Application tables present with explicit names: `users`, `auth_sessions`, `commitments`, `commitment_participants`, `commitment_events`, `outbox_events`, `processed_events`. `manage.py check` 0 issues; `showmigrations` all required `[X]`; `makemigrations --check` no changes. No new models/migrations in 5.10.
- **Security:** UUIDs are not authorization (`can_view` / `_lock_for_responsible`). Outbox/Kafka payloads and `promise` logs do not contain passwords, JWTs, refresh tokens, signing keys, encryption keys, or refresh HMACs.
- **Live Kafka integration (agent-run, not manual):** `promise-kafka` healthy; group lag 0 before the run. `create_commitment` → unpublished `outbox_events` `id=26c154f7-8d06-417c-9891-0b48b19da172` (`aggregate_id=7c17d027-bdf6-43c2-a1e1-c172e36cebae`). `publish_due_outbox_events` `published=2 failed=0` (`published_at` set; leftover unpublished row also delivered, not lost). `consume_commitment_events` `processed=2`. `processed_events` row `c0444331-976a-4b3a-88ba-67acd88e0b55`. Replay of the same envelope → `processed=0 duplicates=1`; same `processed_events` `id` and `processed_at`. Database not reset.
- **Docs:** Status banners on `COMMITMENT_DESIGN.md` and `OUTBOX_DESIGN.md` aligned with running code (not a design rewrite). `DEVELOPMENT.md` checkpoint now lists Phase 5 complete and Phase 6 next.
- **Files changed:** `backend/apps/outbox/consumer.py`, `backend/tests/test_outbox_consumer.py`, `docs/BUILD_PROGRESS.md`, `docs/COMMITMENT_DESIGN.md`, `docs/OUTBOX_DESIGN.md`, `docs/DEVELOPMENT.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **236 passed** in 15.63s.
- **Not implemented (deferred, not Phase 5 blockers):** notifications, AI, Android, shared invitation APIs, HTTP `/unwait/`, product Kafka consumers, automatic snooze expiry persistence, 14-day published-outbox deletion, DLT.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 6.1 — Goal domain design: COMPLETE (implementation not started)

- **What was implemented:** Design only. `docs/GOAL_DESIGN.md` defines Goal vs Commitment vs Check-in vs Challenge vs Task; stored statuses `ACTIVE|PAUSED|COMPLETED|CANCELLED`; recurrence on the Goal row (`DAILY` / `WEEKLY_DAYS` / `N_PER_PERIOD`); `GoalCheckIn` unique `(goal, period_date)`; derived progress/streaks; `Goal.timezone` snapshot; personal authorization; outbox plan on `promise.goal.v1`; REST contract. No Django models, migrations, or APIs.
- **Files changed:** `docs/GOAL_DESIGN.md`, `docs/BUILD_PROGRESS.md`
- **Not done:** Goal/CheckIn tables, services, HTTP, Kafka goal routing, notifications, AI, Android, commitment↔goal links, challenges.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 6.2 — Goal domain models and migrations: COMPLETE

- **What was implemented:** Django app `apps.goals`. Models `Goal`, `GoalCheckIn`, `GoalEvent` inherit `BaseModel`. Explicit tables `goals`, `goal_check_ins`, `goal_events`. Recurrence columns on Goal (`DAILY` / `WEEKLY_DAYS` / `N_PER_PERIOD`, PostgreSQL `weekdays` array). Check-in identity `UNIQUE (goal, period_date)` as `uniq_goal_check_in_period`. No services, serializers, views, URLs, progress/streak logic, or Kafka goal routing.
- **Owner / delete:** `Goal.created_by` PROTECT; `GoalCheckIn.goal` CASCADE; `GoalCheckIn.created_by` PROTECT; `GoalEvent.goal` CASCADE; `GoalEvent.actor` SET NULL.
- **Recurrence:** `weekdays` is `ArrayField` of ISO 1–7 (not JSON, not cron, not a `goal_schedules` table). Check constraints encode DAILY / WEEKLY_DAYS / N_PER_PERIOD shapes. `goals.0002` tightens N_PER_PERIOD so SQL NULL does not bypass missing `period_unit` / `times_per_period`. Recurrence locking after first check-in is **not** in `save()`.
- **Check-in value:** one nullable integer. Binary/SKIPPED: `value` null. Count: integer (`>= 0`). `tracking_kind` + `target_value` live on Goal. No duration/reduction/MISSED.
- **Timezone:** IANA string snapshotted from `User.timezone` at create if omitted. Invalid IANA rejected. Later user timezone changes do not rewrite the Goal.
- **Migration:** `goals.0001_initial` and `goals.0002_remove_goal_goal_recurrence_n_per_period_and_more` applied (`[X]`). Existing application tables unchanged. Database not reset.
- **Files changed:** `backend/apps/goals/`, `backend/config/settings/base.py` (`GoalsConfig`, `django.contrib.postgres`), `backend/tests/test_goal_models.py`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **264 passed** in 17.02s (236 existing + 28 goal model tests).
- **Database:** tables `goals`, `goal_check_ins`, `goal_events` present. Constraints include `uniq_goal_check_in_period`, recurrence/tracking checks, `goal_end_date_gte_start_date`, `goal_weekdays_iso`. Indexes `goals_creator_status`, `goals_event_created`; unique constraint covers `(goal_id, period_date)`.
- **Not done:** services, APIs, progress/streak calculation, outbox writes, notifications, AI, Android. Phase 6 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 6.3 — Goal domain services: COMPLETE

- **What was implemented:** `apps.goals.services` domain layer matching commitments: `create_goal`, `update_goal`, `pause_goal`, `resume_goal`, `complete_goal`, `cancel_goal`, `record_check_in`, `update_check_in`, `get_visible_goal`, `can_view`, plus derived `is_period_expected`, `goal_progress`, `goal_streak`. Business rules live in services, not `Goal.save()`. No serializers, views, or URLs.
- **Create:** `created_by` from the caller; title required; `timezone` snapshotted from `User.timezone` if omitted; `start_date` defaults to today in that timezone; recurrence and tracking normalized before insert; status `ACTIVE`; same transaction writes `GoalEvent(CREATED)` + `OutboxEvent` (`aggregate_type=goal`, `goal.created`).
- **Recurrence:** DAILY (no weekdays/period fields); WEEKLY_DAYS (unique sorted ISO 1–7, at least one); N_PER_PERIOD (`period_unit=WEEK`, `times_per_period` 1–7, no weekdays). Unsupported kinds/units rejected. After the first check-in, recurrence/tracking/`start_date` raise `GOAL_SCHEDULE_LOCKED`; timezone raise `GOAL_TIMEZONE_LOCKED`. Title/description/`end_date` remain patchable (`end_date` cannot precede an existing `period_date`).
- **Lifecycle:** ACTIVE↔PAUSED; complete/cancel from ACTIVE or PAUSED (terminal). Repeat pause/resume/complete/cancel are no-ops (no second event/outbox). Mutations and check-ins out of terminal states raise `GOAL_INVALID_TRANSITION`.
- **Check-ins:** upsert on `(goal, period_date)` under `transaction.atomic()` + `select_for_update` on the Goal. Identity is the local date in `Goal.timezone`; `checked_at` is UTC. Identical retry is a no-op. Changed status/value/note updates in place (`CHECKIN_UPDATED`). BINARY forbids `value`; COUNT COMPLETED requires integer `>= 0`; success is `value >= target_value`. Check-in only while `ACTIVE`. Non-owners get `GOAL_NOT_FOUND`.
- **Progress / streaks (derived, not stored):** Binary success = COMPLETED; count success = COMPLETED and `value >= target_value`. Paused local dates (from PAUSED/RESUMED events in Goal TZ) are not expected and do not break streaks. Daily/weekday streaks walk expected local dates; N×/week walks ISO weeks (Monday). Late `period_date` writes can repair history. `consistency_percent` is successful/expected in `[max(start_date, today-27), yesterday]`.
- **Events/outbox:** one `GoalEvent` + one `OutboxEvent` per mutation; no-ops write neither; rollback if outbox insert fails. Kafka names: `goal.created` / `updated` / `paused` / `resumed` / `completed` / `cancelled` / `goal.checkin.created` / `goal.checkin.updated`. Payloads omit titles, notes, and secrets. Publisher is unchanged (still commitment-only).
- **Files changed:** `backend/apps/goals/exceptions.py`, `backend/apps/goals/services.py`, `backend/tests/test_goal_services.py`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS (no new migration); `git diff --check` PASS; `pytest -v` **289 passed** in 16.69s (264 existing + 25 goal service tests).
- **Not done:** REST APIs, Kafka `goal` routing, notifications, AI, Android. Phase 6 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Goal Kafka topic routing + ISO Monday week

- **What was implemented:** Outbox publisher selects Kafka topic from `aggregate_type`. `commitment` → `promise.commitment.v1`; `goal` → `promise.goal.v1`. Kafka key remains `aggregate_id`. Unknown `aggregate_type` fails without producing (same retry/park path as other publish errors). Commitment publish behavior unchanged. No Goal consumers, APIs, or `docker-compose.yml` changes.
- **Week semantics:** `docs/GOAL_DESIGN.md` now **DECIDED** ISO-8601 Monday-start weeks (`1` = Monday, `7` = Sunday) as the single project-wide definition for Goal weekly periods. Existing `apps.goals.services` already used ISO weeks; no service code change.
- **Topic creation:** Compose unchanged. Local Apache Kafka auto-creates topics on first produce by default. `promise-kafka` was **not running** during this slice, so `promise.goal.v1` was not live-verified here. First successful `publish_outbox` of a goal event will create it locally.
- **Files changed:** `backend/apps/outbox/publisher.py`, `backend/tests/test_outbox_publisher.py`, `docs/GOAL_DESIGN.md`, `docs/OUTBOX_DESIGN.md`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS (no new migration); `git diff --check` PASS; `pytest -v` **294 passed** in 17.95s.
- **Not done:** Phase 6.4 Goal REST APIs, Goal consumers, notifications, AI, Android. Phase 6 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 6.4 — Goal REST APIs: COMPLETE

- **What was implemented:** JWT Goal REST APIs under `/api/v1/goals/`, matching commitment view/serializer/pagination style. Views call existing goal services. `created_by` always comes from `request.user`. Unknown/unowned goals return 404 `GOAL_NOT_FOUND` (no existence leak). Health, auth, and commitment routes unchanged.
- **Routes (trailing slash, JWT required):**

| Method | Path | Success |
|---|---|---|
| `POST` | `/api/v1/goals/` | **201** Goal |
| `GET` | `/api/v1/goals/` | **200** paginated `{count, next, previous, results}` |
| `GET` | `/api/v1/goals/{id}/` | **200** Goal |
| `PATCH` | `/api/v1/goals/{id}/` | **200** Goal |
| `POST` | `/api/v1/goals/{id}/pause/` | **200** Goal |
| `POST` | `/api/v1/goals/{id}/resume/` | **200** Goal |
| `POST` | `/api/v1/goals/{id}/complete/` | **200** Goal |
| `POST` | `/api/v1/goals/{id}/cancel/` | **200** Goal |
| `POST` | `/api/v1/goals/{id}/check-ins/` | **201** insert / **200** if a check-in for that period already existed |
| `GET` | `/api/v1/goals/{id}/check-ins/` | **200** paginated |

- **Create (`POST /api/v1/goals/`):** `title` required; `recurrence_kind` required; optional `description`, `timezone`, `start_date`, `end_date`, `weekdays`, `period_unit`, `times_per_period`, `tracking_kind`, `target_value`, `target_unit`. Body `created_by` / `status` / `source` are ignored. Service snapshots timezone from the user when omitted, normalizes weekdays, writes `GoalEvent(CREATED)` + `OutboxEvent(goal.created)`, sets `source=MANUAL`.
- **List (`GET /api/v1/goals/`):** owner only. Query: `status`, `recurrence_kind`, `tracking_kind`. Default excludes `COMPLETED` and `CANCELLED` unless `status` is set. Order: `-created_at`. Pagination: page size 20, max 100 (`page`, `page_size`). Prefetches `check_ins` and `events` (no N+1 for derived fields).
- **Goal response fields:** `id`, `title`, `description`, `status`, `timezone`, `start_date`, `end_date`, `recurrence_kind`, `weekdays`, `period_unit`, `times_per_period`, `tracking_kind`, `target_value`, `target_unit`, `source`, `paused_at`, `completed_at`, `cancelled_at`, `created_at`, `updated_at`, plus derived `is_ended`, `progress`, `current_streak`. Not stored. Computed with `Goal.timezone`. `progress` is `{current_period, week_progress, consistency_percent}`. List and detail use the same Goal serializer (streak is on list too). Hidden: `created_by`, events, raw check-ins, outbox, secrets.
- **PATCH:** safe fields only (`title`, `description`, `timezone`, `start_date`, `end_date`, recurrence/tracking). `status` / `created_by` / timestamps / progress / streak / check-ins are not writable. Recurrence/`start_date`/tracking lock after first check-in → 409 `GOAL_SCHEDULE_LOCKED`. Timezone lock after first check-in → 409 `GOAL_TIMEZONE_LOCKED`. Empty PATCH `{}` is 200 with no event/outbox. Successful change writes `GoalEvent(UPDATED)` + `OutboxEvent(goal.updated)`.
- **Lifecycle:** empty body. Idempotent no-ops are 200 with no second event. Illegal transitions → 409 `GOAL_INVALID_TRANSITION`.
- **Check-in write:** `{period_date?, status, value?, note?}`. `period_date` optional → today in `Goal.timezone`. View uses `record_check_in()` for both insert and upsert. HTTP **201** if no row existed for that period before the request; **200** if a row already existed (identical retry or in-place update). Identical retry: 200, no new event/outbox. Changed status/value/note: 200, `CHECKIN_UPDATED` + `goal.checkin.updated`. Invalid value/period → 400 `GOAL_INVALID_CHECKIN`. Check-in while not `ACTIVE` → 409 `GOAL_INVALID_TRANSITION`. GET does not mutate.
- **Check-in list:** owner only. Query: `start_date`, `end_date`, `status` (inclusive `period_date` range). Order: `-period_date`, `-created_at`. Same pagination as goals. Fields: `id`, `period_date`, `status`, `value`, `note`, `checked_at`, `created_at`, `updated_at`.
- **Authorization:** only `goal.created_by` may view, patch, pause, resume, complete, cancel, or check in. Unrelated users get 404 `GOAL_NOT_FOUND`. Unauthenticated → 401 `UNAUTHENTICATED`. Serializer/service validation → 400 `VALIDATION_ERROR`.
- **Files changed:** `backend/apps/goals/serializers.py`, `views.py`, `urls.py` (new); `backend/apps/goals/services.py` (`list_visible_goals`, `list_goal_check_ins`, prefetch on `get_visible_goal`); `backend/config/urls.py`; `backend/tests/test_goal_api.py`; `docs/BUILD_PROGRESS.md`
- **Migration:** none. `makemigrations --check` — No changes detected.
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **304 passed** in 19.06s (294 existing + 10 API).
- **Manual cURL:** **pending**. Automated only. Do not claim live HTTP passed. Commands are below; `runserver` was not started.
- **Not done:** Goal consumers, notifications, AI, Android, shared goals, challenges, authentication/Redis changes. Phase 6 remains in progress.
- **Git commit:** not committed yet

Manual cURL (requires a running server and `ACCESS_TOKEN` from login). Expected statuses in comments. **Not executed this step.**

```bash
# 1. login (public) — 200
curl -sS -X POST http://127.0.0.1:8000/api/v1/auth/login/ \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"your-password"}'
# export ACCESS_TOKEN=...

# 2. create — 201
curl -i -X POST http://127.0.0.1:8000/api/v1/goals/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "title": "Study DSA daily",
    "recurrence_kind": "DAILY",
    "tracking_kind": "BINARY"
  }'
# export GOAL_ID=...

# 3. get — 200
curl -i http://127.0.0.1:8000/api/v1/goals/$GOAL_ID/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 4. check-in (today in Goal timezone if period_date omitted) — 201
curl -i -X POST http://127.0.0.1:8000/api/v1/goals/$GOAL_ID/check-ins/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{"status":"COMPLETED"}'

# 5. get again — 200; inspect progress + current_streak
curl -i http://127.0.0.1:8000/api/v1/goals/$GOAL_ID/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 6. pause — 200
curl -i -X POST http://127.0.0.1:8000/api/v1/goals/$GOAL_ID/pause/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 7. resume — 200
curl -i -X POST http://127.0.0.1:8000/api/v1/goals/$GOAL_ID/resume/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 8. complete — 200
curl -i -X POST http://127.0.0.1:8000/api/v1/goals/$GOAL_ID/complete/ \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 2026-08-20 — Phase 6.5 — Goal/Event Integration Verification: COMPLETE

- **What was verified:** Goal mutation → `GoalEvent` + `OutboxEvent` (same transaction) → `publish_outbox` → Kafka `promise.goal.v1` → generic `handle_record` / `run_consumer` → `processed_events`. No-op mutations write neither row. Payloads omit titles, notes, and secrets. Timezone snapshot, ISO Monday weeks, period identity, pause, streak, and late-check-in repair unchanged (existing service tests). No Goal API redesign.
- **Correctness fix (blocking for consume/dedup):** generic `SUPPORTED_EVENT_TYPES` now includes Goal Kafka types. Without that, `goal.created` was skipped and never wrote `processed_events`. No new consumer command. No publisher/topic architecture change. Compose unchanged.
- **Events:** Successful create/update/pause/resume/complete/cancel/check-in create/check-in update write exactly one `GoalEvent` and one `OutboxEvent`. Mapping: `CREATED`→`goal.created`, `UPDATED`→`goal.updated`, `PAUSED`→`goal.paused`, `RESUMED`→`goal.resumed`, `COMPLETED`→`goal.completed`, `CANCELLED`→`goal.cancelled`, `CHECKIN_RECORDED`→`goal.checkin.created`, `CHECKIN_UPDATED`→`goal.checkin.updated`. Envelope: `event_id` = `OutboxEvent.id`, `event_version=1`, `aggregate_type=goal`, `aggregate_id=Goal.id`. Compact payload (ids, status, type-specific fields). No titles, descriptions, notes, passwords, JWTs, refresh tokens, signing keys, encryption keys, or HMACs.
- **Failure:** Kafka unavailable leaves Goal rows unchanged; outbox stays unpublished, `attempts` increments, backoff `next_attempt_at` is set. Outbox insert failure rolls back Goal + `GoalEvent`. Processor/DB failure does not commit the Kafka offset (`test_run_consumer_retries_failed_record_before_later_success`).
- **Timezone / recurrence:** `Goal.timezone` snapshot; ISO-8601 Monday weeks; `period_date` is the Goal-local date; paused local dates are not expected; late `period_date` can repair a streak. No design change.
- **Docs:** `GOAL_DESIGN.md` §16 aligned with the running API (same list/detail serializer including `progress`/`current_streak`; check-in **201** insert / **200** if the period existed; `GOAL_INVALID_CHECKIN`; list filters `start_date`/`end_date`/`status`; list query includes `recurrence_kind`). `OUTBOX_DESIGN.md` notes generic `handle_record` accepts Goal types; still no `consume_goal_events` command.
- **Live Kafka integration (agent-run, not manual):** `promise-kafka` started and healthy. Topic `promise.goal.v1` present after publish (1 partition, RF=1, TopicId `v7YOW-dURsmNk6nmzre3bQ`). `create_goal` → unpublished `outbox_events` `id=ab9ddb4c-af23-4bd8-9c9e-5adfbb574c40` (`aggregate_id=af9b7d0d-7bf9-4313-ba05-28c56c869511`). `python manage.py publish_outbox` → `published=1 failed=0`; `published_at` set; `event_id` unchanged. Bounded consume: topic `promise.goal.v1`, key = aggregate_id, envelope `goal.created`. First delivery `processed`; `processed_events` `id=2cd8e81a-d721-4e89-b12b-db4a9715149e` group `promise-goal-events-verify-6-5`. Replay of the same envelope → `processed=0 duplicates=1`; same `processed_events` `id` and `processed_at`. Database not reset. No interactive consumer. Compose file not modified.
- **Files changed:** `backend/apps/outbox/consumer.py` (`SUPPORTED_EVENT_TYPES` + docstring); `backend/tests/test_goal_outbox.py` (new); `backend/tests/test_outbox_consumer.py`; `docs/GOAL_DESIGN.md`; `docs/OUTBOX_DESIGN.md`; `docs/BUILD_PROGRESS.md`
- **Migration:** none. `makemigrations --check` — No changes detected.
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **313 passed** in 24.51s (304 existing + 6 Goal outbox + 3 consumer).
- **Not done:** `consume_goal_events` command, product Goal workers, notifications, AI, Android, shared goals, challenges. Phase 6 remains in progress.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 6.6 — Final Goal Domain Verification: COMPLETE

- **What was verified:** Final review of Goal domain, check-ins, REST APIs, GoalEvent+OutboxEvent dual-write, Kafka `promise.goal.v1`, migrations, payload/log security, and documentation consistency. No new product features. No API redesign. No auth/Redis/Kafka architecture changes. No `consume_goal_events` command added.
- **Domain:** Stored statuses `ACTIVE|PAUSED|COMPLETED|CANCELLED`. Terminal states reject further lifecycle/check-in mutations (`GOAL_INVALID_TRANSITION`). Recurrence normalized (DAILY / WEEKLY_DAYS unique sorted ISO 1–7 / N_PER_PERIOD WEEK 1–7). After first check-in: schedule/`start_date`/tracking → `GOAL_SCHEDULE_LOCKED`; timezone → `GOAL_TIMEZONE_LOCKED`. `Goal.timezone` snapshotted; `period_date` and streak/progress use that zone; ISO Monday weeks. Pause intervals are not expected. Late check-ins can repair streaks. Progress and `current_streak` are derived (not stored on `Goal`).
- **Check-ins:** Unique `(goal, period_date)`. Identical retry is a no-op. Changed status/value/note updates in place (`CHECKIN_UPDATED`). BINARY forbids `value`; COUNT COMPLETED requires integer `>= 0`. Missing periods are inferred, not materialized. Writes are atomic with `GoalEvent` + `OutboxEvent`.
- **API:** JWT required. Owner from `request.user` (body `created_by` ignored). Other user / missing → 404 `GOAL_NOT_FOUND`. Validation 400; illegal transition 409; schedule/timezone lock 409; invalid check-in 400 `GOAL_INVALID_CHECKIN`. Pagination 20/max 100. List filters `status`, `recurrence_kind`, `tracking_kind`. Check-in list `start_date`, `end_date`, `status`. Same Goal serializer on list and detail (`progress`, `current_streak`). Check-in POST 201 insert / 200 if the period existed.
- **Events:** Successful mutations write exactly one `GoalEvent` and one `OutboxEvent` in the same transaction. No-ops write neither. Mapping: `goal.created` / `updated` / `paused` / `resumed` / `completed` / `cancelled` / `goal.checkin.created` / `goal.checkin.updated`. `event_version=1`. Compact payload (ids, status, type-specific fields). No titles, notes, passwords, JWTs, refresh tokens, signing keys, encryption keys, or HMACs.
- **Kafka:** Topic `promise.goal.v1`; key = `aggregate_id` = Goal UUID; `event_id` = `OutboxEvent.id`. Generic `handle_record` accepts Goal types. Dedup on `(consumer_group, event_id)`.
- **Database:** Application tables: `users`, `auth_sessions`, `commitments`, `commitment_participants`, `commitment_events`, `goals`, `goal_check_ins`, `goal_events`, `outbox_events`, `processed_events`. No `goal_schedules` / generic `check_ins`. `goals.0001` + `goals.0002` applied `[X]`. No unexpected migrations. Commitment/auth tables unchanged.
- **Security:** Ownership is `goal.created_by_id == request.user.id`. UUIDs are not authorization. Outbox/Kafka payloads and `promise` logs do not contain secrets.
- **Live Kafka (agent-run, not manual):** `promise-kafka` healthy. `create_goal` → unpublished `outbox_events` `id=0c29867f-92b4-4199-a25d-f4336c108ea8` (`aggregate_id=c2473a74-ff40-46be-9c6d-3c38bf4b481c`). `publish_outbox` `published=1 failed=0`; `published_at` set; `event_id` unchanged. Bounded consume: topic `promise.goal.v1`, key = aggregate_id, `goal.created`. First delivery `processed`; `processed_events` `id=eff26616-af7f-4b30-9f8f-13571a6f4bb3` group `promise-goal-events-verify-6-6`. Replay → `processed=0 duplicates=1`; same row `id`/`processed_at`. Database not reset. No interactive consumer.
- **Docs:** `GOAL_DESIGN.md` status = Phase 6 complete. `DEVELOPMENT.md` checkpoint lists Phase 6 complete; next numbered phase is 7. `OUTBOX_DESIGN.md` status notes Goal publish/consume verification. No design-decision rewrite. `ARCHITECTURE.md` still sketches `goal_schedules` (superseded by `GOAL_DESIGN.md`; left as historical intended architecture).
- **Files changed:** `docs/BUILD_PROGRESS.md`, `docs/GOAL_DESIGN.md`, `docs/OUTBOX_DESIGN.md`, `docs/DEVELOPMENT.md`
- **Migration:** none. `makemigrations --check` — No changes detected.
- **Verification:** `manage.py check` PASS; `showmigrations` all required `[X]`; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **313 passed** in 22.88s.
- **Not implemented (deferred, not Phase 6 blockers):** notifications, AI, Android, shared goals, challenges, commitment↔goal links, dedicated product Goal consumers (`consume_goal_events`), advanced reminders / missed-check-in scheduler.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 7.1 — Redis product layer design: COMPLETE (implementation not started)

- **What was implemented:** Design only. `docs/REDIS_DESIGN.md` defines PostgreSQL as source of truth; Redis as derived/temporary/coordination; existing auth denylist (do not rebuild); MVP product work as rate limits (Lua INCR+EXPIRE), not domain caches; no new distributed locks (Postgres row locks remain); fail-open rate limits if Redis is down; never store credentials/JWTs/keys in Redis.
- **MVP Redis use cases:** auth login/register/refresh throttles + Goal/Commitment write and Goal check-in throttles. Goal list, Commitment list, progress/streak, and home summary caches are **DEFERRED**. Notification/AI/forgot throttles and notification locks are **DEFERRED**.
- **Files changed:** `docs/REDIS_DESIGN.md`, `docs/BUILD_PROGRESS.md`
- **Not done:** Lua counter helper, HTTP 429 wiring, domain caches, lock helper, denylist changes, Kafka/Android/migrations. Phase 7 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 7.2 — Secure Redis rate-limit primitive: COMPLETE

- **What was implemented:** Reusable `increment_rate_limit(key, window_seconds)` in `apps.core.redis`. One Lua `EVAL`: `INCR`; `EXPIRE` only when count == 1; return `{count, ttl}`. Keys must start with `promise:ratelimit:` (reject empty suffix, whitespace, `@`, non-strings, overlong keys). Positive integer TTL required (`bool` rejected). Does **not** decide allow/deny or emit 429. `incr_with_ttl` unchanged and is **not** used for security throttles. Auth denylist unchanged.
- **Fail-open:** `RedisError` or a malformed script result → `{"count": 0, "ttl": 0}` and a `promise` WARNING without the Redis key or exception text.
- **Tests:** `tests/test_rate_limit_redis.py` — first increment count/TTL, second increment without TTL reset, natural TTL decrease, concurrent increments, invalid key/TTL, fail-open, no permanent key, single EVAL contract, live Docker Redis (skip if down). Dev extra `fakeredis[lua]` (`lupa`) so fakeredis can run EVAL.
- **Live Redis:** `promise-redis` healthy. Test key incremented to 1 with TTL, then 2 without resetting the window; concurrent 20 increments produced 1–20; keys deleted afterward. No leftover `promise:ratelimit:test:phase72*` keys.
- **Files changed:** `backend/apps/core/redis.py`, `backend/tests/test_rate_limit_redis.py`, `backend/pyproject.toml`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **334 passed** in 23.97s (313 existing + 21 rate-limit).
- **Not done:** HTTP wiring (login/register/refresh/commitment/goal/check-in), 429 responses, domain caches, locks, denylist/Kafka/Android/Compose changes. Phase 7 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 7.3 — Authentication rate limits: COMPLETE

- **What was implemented:** `apps.authentication.rate_limits` enforces Redis buckets on `POST /api/v1/auth/login/`, `/register/`, and `/refresh/` after serializer validation and before Argon2 / token rotation. Reuses `increment_rate_limit`. Does not decide counts in the helper; service decides allow/deny. `RateLimitedError` → HTTP 429 `RATE_LIMITED` with `Retry-After` from bucket TTL. IP from `REMOTE_ADDR` only (`X-Forwarded-For` deferred). Email keys are SHA-256 of `canonicalize_email`. Refresh session/user keys only for sessions that exist in PostgreSQL; malformed/unknown tokens share `promise:ratelimit:refresh:ip:{ip}` (limit 30 / 900s) so attackers cannot create unbounded session keys. Fail-open if Redis is down. Denylist, hashing, Kafka, Compose, Goal/Commitment APIs unchanged.
- **Buckets:** login IP 20/900s, login email 10/900s, register IP 5/3600s, register email 10/3600s, refresh session 30/900s, refresh user 120/900s, refresh unknown/malformed IP fallback 30/900s.
- **Files changed:** `backend/apps/authentication/rate_limits.py`, `backend/apps/authentication/views.py`, `backend/config/exceptions.py`, `backend/tests/test_auth_rate_limits.py`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **356 passed** in 23.99s (334 existing + 22 auth throttle).
- **Live Redis:** `promise-redis` healthy. Login email bucket filled to limit then HTTP 429 with TTL; IP + email keys present; refresh session limit 429; test keys deleted (`SCAN` empty for those prefixes).
- **Not done:** Goal/Commitment/check-in throttles, domain caches, locks, trusted-proxy IP, notifications, AI, Android. Phase 7 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 7.4 — Goal and Commitment mutation rate limits: COMPLETE

- **What was implemented:** Authenticated Commitment and Goal **write** APIs increment Redis buckets after JWT auth and before domain services. GETs are exempt. Check-in POST uses a separate bucket from goal writes. Reuses `increment_rate_limit` and `RateLimitedError` (HTTP 429 `RATE_LIMITED` + `Retry-After` from TTL). Fail-open if Redis is down. Auth throttles, denylist, Kafka, Compose, models, and PostgreSQL source-of-truth behavior unchanged. Mutation keys use the **caller** `user_id`, not the resource owner.
- **Buckets:** commitment writes 60/60s (`promise:ratelimit:commitment:user:{user_id}`), goal writes 60/60s (`promise:ratelimit:goal:user:{user_id}`), goal check-ins 30/60s (`promise:ratelimit:goal_checkin:user:{user_id}`).
- **Files changed:** `backend/apps/commitments/rate_limits.py`, `backend/apps/commitments/views.py`, `backend/apps/goals/rate_limits.py`, `backend/apps/goals/views.py`, `backend/tests/test_commitment_rate_limits.py`, `backend/tests/test_goal_rate_limits.py`, `docs/REDIS_DESIGN.md`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **378 passed** in 25.69s (356 existing + 22 mutation throttle).
- **Live Redis:** `promise-redis` healthy. Commitment write bucket filled then HTTP 429; goal write and check-in buckets filled then HTTP 429; `Retry-After` present; test keys deleted in `finally`.
- **Not done:** Domain caches, locks, trusted-proxy IP, notifications, AI, Android. Phase 7 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 7.5 — Final Redis product layer verification: COMPLETE

- **What was verified:** Redis is used only as a denylist hint and TTL’d rate-limit counters. PostgreSQL remains source of truth. Canonical keys in `docs/REDIS_DESIGN.md`. Stale auth key examples in `AUTHENTICATION_DESIGN.md` aligned to the implemented convention. No domain caches, new locks, new buckets, Kafka, Android, or migrations.
- **Failure/fallback:** denylist read down → Postgres session still authoritative; auth/commitment/goal/check-in throttles fail open; no Redis 500; domain writes still occur.
- **Live Redis:** login IP TTL 900s; commitment/goal/check-in TTLs 60s; fail-open with Redis unavailable; keys deleted (`SCAN` empty for those prefixes).
- **Files changed:** `docs/REDIS_DESIGN.md`, `docs/AUTHENTICATION_DESIGN.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT_PLAN.md`, `docs/DEVELOPMENT.md`, `docs/BUILD_PROGRESS.md`
- **Verification:** `manage.py check` PASS; `makemigrations --check` PASS; `git diff --check` PASS; `pytest -v` **378 passed** in 25.46s.
- **Deferred:** domain caching, notification locks, AI throttles, trusted-proxy IP, production fail-closed login if Redis is down, cache/stampede optimization.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 9.1 — Android foundation design: COMPLETE (implementation not started)

- **What was implemented:** Design only. `docs/ANDROID_DESIGN.md` defines visual direction (quiet paper), Kotlin/Compose/Hilt/Retrofit stack, single `:app` module, memory-only access JWT + Keystore refresh, API client 401/refresh mutex, sealed UI state, four-tab navigation, design tokens, Home/Commitment/Goal UX aligned to existing APIs, online-first (no Room), FCM seams without implementation.
- **Files changed:** `docs/ANDROID_DESIGN.md`, `docs/BUILD_PROGRESS.md`
- **Commands/tools used:** none (no Gradle, no Android source, no backend changes)
- **Verification:** Documentation review against AUTHENTICATION / COMMITMENT / GOAL / REDIS designs. Last pytest result remains **378 passed**. No Android module compiled.
- **Not done:** Android project, Compose screens, API client, Keystore storage. Phase 9 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 9.2 — Android Gradle + Compose bootstrap: COMPLETE

- **What was implemented:** Single-module Kotlin/Compose app under `android/` (`applicationId` `app.promise.android`, minSdk 26, compile/target SDK 37). Hilt `PromiseApp` + `MainActivity`. Light-only paper/ink theme tokens. Auth + Main navigation graphs with bootstrap/placeholder destinations (no product screens). `LoadState` / `ActionState` / `ErrorKind`. OkHttp client with 10/20/30s timeouts; Retrofit on the classpath but no API calls. Auth storage seam only (no tokens). Debug-only cleartext to `10.0.2.2` / `localhost` / `127.0.0.1` via `network_security_config`; release keeps `usesCleartextTraffic=false`.
- **Files changed:** `android/` (Gradle wrapper, `:app` module, sources, unit tests), `docs/BUILD_PROGRESS.md`, `docs/ANDROID_DESIGN.md`, `docs/DEVELOPMENT.md`, `README.md`
- **Commands/tools used:** Temurin JDK 17.0.20; Android SDK `/Users/arjunvats/Library/Android/sdk` (platform `android-37.0`, build-tools 36.0.0); `./gradlew test assembleDebug` from `android/`
- **Verification:** `./gradlew test` **11 passed**, 0 failed. `./gradlew assembleDebug` **BUILD SUCCESSFUL** (6m 19s for the combined run). Debug APK `android/app/build/outputs/apk/debug/app-debug.apk`. `adb devices`: none attached — emulator/device launch **not performed**. Backend source unmodified. Last pytest result remains **378 passed**.
- **Not done:** Login/Register/Home/Commitments/Goals/Profile UI, Retrofit services, refresh-token handling, FCM, Room. Phase 9 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 9.3 — Android authentication + networking: COMPLETE

- **What was implemented:** Retrofit/OkHttp/kotlinx.serialization client under `data/network`. Login, refresh, logout, `/auth/me/`. Access JWT in process memory only. Refresh token AES-GCM with Android Keystore wrapping key; ciphertext in `noBackupFilesDir`. Process-wide refresh mutex (waiters share one in-flight refresh). Authenticated 401 → refresh once → retry original once. Public auth paths skip Bearer and refresh-retry. Login screen + session restore. Logout clears local state even if HTTP fails. 429 `Retry-After` parsed, not auto-retried.
- **Files changed:** `android/app/src/main/java/app/promise/android/` (network, local token store, auth UI, Hilt modules, navigation), unit tests, `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `docs/BUILD_PROGRESS.md`, `docs/ANDROID_DESIGN.md`, `docs/DEVELOPMENT.md`
- **Commands/tools used:** Temurin JDK 17.0.20; `./gradlew test assembleDebug` from `android/`
- **Verification:** `./gradlew test` **32 passed**, 0 failed. `assembleDebug` **BUILD SUCCESSFUL**. `adb devices`: none attached — emulator/device launch **not performed**. Backend unmodified. Last pytest result remains **378 passed**.
- **Not done:** Register UI, Home/Commitments/Goals product screens, logout-all UI, FCM, Room. Phase 9 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 9.4 — Android app shell + Home UI: COMPLETE

- **What was implemented:** Four-tab Main shell (Home / Commitments / Goals / Profile). Real Home screen with local preview feed (1 overdue, 1 due-today, 1 upcoming, 3 practices). Hybrid layout: Header → Today → Practices (no Attention section). Light + Dark themes (default Light, in-memory `ThemeController`). Profile: appearance toggle + sign out. Commitments/Goals design-system placeholders. `PromiseHaptics` (light on theme; confirm on complete/check-in/sign-out; none on avatar→Profile or tabs). Calm ~190ms tab transitions. Auth/session from 9.3 unchanged. No Home networking, Room, DataStore, or FCM.
- **Files changed:** `android/app/src/main/java/app/promise/android/` (home, theme, haptics, navigation shell, profile, placeholders), unit tests, `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml` (VIBRATE), `docs/BUILD_PROGRESS.md`, `docs/ANDROID_DESIGN.md`, `docs/DEVELOPMENT.md`
- **Commands/tools used:** Temurin JDK 17.0.20; `./gradlew test assembleDebug` from `android/`; `python manage.py check` + `makemigrations --check` from `backend/`
- **Verification:** `./gradlew test` **50 passed**, 0 failed. `assembleDebug` **BUILD SUCCESSFUL**. Debug APK `android/app/build/outputs/apk/debug/app-debug.apk`. `adb devices`: none attached — emulator/device visual verification **not performed**. Backend unmodified (`check` clean; `makemigrations --check` no changes). Last pytest result remains **378 passed**.
- **Not done:** Commitments/Goals product UI, Home API wiring, theme persistence, register UI, FCM, Room. Phase 9 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 9.5 — Android Commitments product UI: COMPLETE

- **What was implemented:** Commitments list with Open / Overdue / Today / Upcoming / Done filters against live backend APIs. Create bottom sheet (title, description, due + precision; no `source`/`created_by`). Detail with Complete, Snooze (presets + custom), Wait, Unsnooze, Cancel — no Edit/PATCH, no Unwait. Uses Phase 9.3 authed Retrofit stack. Light/Dark tokens, calm motion, PromiseHaptics on meaningful actions. Goals placeholder unchanged. Home preview unchanged.
- **Files changed:** `android/.../domain/Commitment*.kt`, `data/commitments/*`, `ui/commitments/*`, `di/CommitmentModule.kt`, `MainShell.kt`, `core/LoadState.kt` / `ErrorMapping.kt` (NotFound/Conflict), unit tests, `docs/BUILD_PROGRESS.md`, `docs/ANDROID_DESIGN.md`, `docs/DEVELOPMENT.md`
- **Commands/tools used:** Temurin JDK 17.0.20; `./gradlew test assembleDebug`; backend `manage.py check` + `makemigrations --check`
- **Verification:** `./gradlew test` **71 passed**, 0 failed. `assembleDebug` **BUILD SUCCESSFUL**. APK `android/app/build/outputs/apk/debug/app-debug.apk`. `adb devices`: none — emulator/device visual verification **not performed**. Backend unmodified (`check` clean; `makemigrations --check` no changes). Last pytest result remains **378 passed**.
- **Not done:** Goals product UI, Shared Goals, Edit/PATCH commitment UI, Unwait, FCM, Room, Home live commitments feed. Phase 9 remains in progress and is **not** complete.
- **Git commit:** not committed yet

### 2026-08-20 — Phase 9.6 — Android Goals product UI: COMPLETE

- **What was implemented:** Goals list with Active / Paused / Completed (Completed merges COMPLETED+CANCELLED). Create bottom sheet with progressive recurrence + tracking fields (no `source`/`created_by`/`status`). Detail with backend `progress`/`current_streak`, recent check-in history, low-friction BINARY/COUNT check-in, Pause / Resume / Complete / Cancel confirms. No Edit/PATCH UI, no Shared Goals. Reuses Phase 9.3 authed Retrofit and Phase 9.5 UI patterns. Light/Dark tokens, calm motion, PromiseHaptics on meaningful actions.
- **Files changed:** `android/.../domain/Goal*.kt`, `data/goals/*`, `ui/goals/*`, `di/GoalModule.kt`, `MainShell.kt`, `core/LoadState.kt` / `ErrorMapping.kt` (Goal lock/check-in codes), unit tests, `docs/BUILD_PROGRESS.md`, `docs/ANDROID_DESIGN.md`, `docs/DEVELOPMENT.md`
- **Commands/tools used:** Temurin JDK 17.0.20; `./gradlew test assembleDebug`; backend `manage.py check` + `makemigrations --check`
- **Verification:** `./gradlew test` **90 passed**, 0 failed. `assembleDebug` **BUILD SUCCESSFUL**. APK `android/app/build/outputs/apk/debug/app-debug.apk` (~19 MB). `adb devices`: none — emulator/device visual verification **not performed**. Backend unmodified (`check` clean; `makemigrations --check` no changes). Last pytest result remains **378 passed**.
- **Not done:** Shared Goals / participants / invites, Challenges, Edit/PATCH goal schedule UI, FCM, Room, Home live goals feed. Phase 9 remains in progress and is **not** complete.
- **Git commit:** not committed yet

---

## Current Work

Phase 9 — Android Foundation is **IN PROGRESS**. Phases 9.1–9.6 are complete. Shared Goals / FCM / Room are not implemented. Backend Phases 4–7 remain unchanged. Notifications and AI are not started.

Uncommitted:

- `backend/apps/users/models.py`
- `backend/apps/users/migrations/0002_rename_application_tables.py`
- `backend/apps/authentication/models.py`
- `backend/apps/authentication/migrations/0003_rename_application_tables.py`
- `backend/apps/commitments/`
- `backend/config/settings/base.py`
- `backend/config/urls.py`
- `backend/tests/test_commitment_models.py`
- `backend/tests/test_commitment_services.py`
- `backend/tests/test_commitment_api.py`
- `backend/apps/outbox/`
- `backend/tests/test_outbox.py`
- `backend/tests/test_outbox_publisher.py`
- `backend/tests/test_outbox_consumer.py`
- `backend/pyproject.toml`
- `docs/COMMITMENT_DESIGN.md`
- `docs/OUTBOX_DESIGN.md`
- `docs/DEVELOPMENT.md`
- `docs/BUILD_PROGRESS.md`
- `docs/GOAL_DESIGN.md`
- `docs/REDIS_DESIGN.md`
- `docs/AUTHENTICATION_DESIGN.md`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT_PLAN.md`
- `docs/ANDROID_DESIGN.md`
- `backend/apps/core/redis.py`
- `backend/tests/test_rate_limit_redis.py`
- `backend/apps/authentication/rate_limits.py`
- `backend/apps/authentication/views.py`
- `backend/config/exceptions.py`
- `backend/tests/test_auth_rate_limits.py`
- `backend/apps/goals/`
- `backend/tests/test_goal_models.py`
- `backend/tests/test_goal_services.py`
- `backend/tests/test_goal_api.py`
- `backend/tests/test_goal_outbox.py`
- `backend/tests/test_goal_rate_limits.py`
- `backend/tests/test_commitment_rate_limits.py`
- `android/` (Gradle project; `local.properties` gitignored)

---

## Next Steps

1. Remaining Phase 9 Android: Shared Goals / register / FCM / Room when requested (`docs/ANDROID_DESIGN.md`).
2. Do not implement domain caches, notification locks, or AI throttles unless requested.
3. Deferred product work: trusted-proxy client IP, production fail-closed login if Redis is down, participant invite APIs, HTTP `/unwait/`, notifications, AI, analytics, commitment↔goal links, challenges, `consume_goal_events`, 14-day published-outbox deletion, DLT.

---

## Environment

Verified on this machine (2026-08-18), no secrets:

- macOS 26.6.1
- Git 2.50.1
- Docker 29.7.2
- Docker Compose v5.4.0
- Default `python3` on PATH: **3.14.6**
- `backend/.venv`: **Python 3.12.14**, Django **6.1** (used for Phase 3)
- Java: **Temurin 17.0.20** at `/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` (not on default PATH). Used for Phase 9.2. Android Studio bundled JBR is 25.0.2 and was not used.
- Android SDK: `/Users/arjunvats/Library/Android/sdk` — platform `android-37.0`, build-tools `36.0.0`. `local.properties` (`sdk.dir`) is gitignored.
- Android Studio: installed. No emulator/AVD on this machine at 9.2 verification.
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
- **Purpose:** Cache, locks, rate limiting, temporary state, notification coordination. Auth `sid` denylist is live (Phase 4.7). Atomic rate-limit primitive is live (Phase 7.2). Auth login/register/refresh HTTP throttles are live (Phase 7.3). Goal/Commitment/check-in write throttles are live (Phase 7.4). Domain caches / locks are **deferred**. Phase 7 product layer **COMPLETE**.
- **Current status:** Running (`promise-redis` healthy) as of 2026-08-20 Phase 7.5 verification.
- **Last verification:** 2026-08-20 — live login IP TTL 900s; commitment/goal/check-in TTLs 60s; fail-open; test keys deleted. Volume `promise_redis_data`

### Kafka

- **Version:** Apache Kafka 4.3.1 (`apache/kafka:4.3.1`, KRaft, no ZooKeeper)
- **Purpose:** Asynchronous / domain events
- **Current status:** Running (`promise-kafka` healthy) as of 2026-08-20 Phase 6.5. Topics `promise.commitment.v1` and `promise.goal.v1` exist (1 partition, RF=1).
- **Last verification:** 2026-08-20 — host bootstrap `localhost:9092`; Phase 6.5 live Goal create → `publish_outbox` → consume `promise.goal.v1` → replay dedup succeeded. Volume `promise_kafka_data`

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
| Redis for cache / locks / temp state | Fast temporary coordination; never the primary DB. Phase 7 **COMPLETE** for denylist + rate limits. Caches / locks / AI throttles deferred. | 2026-08-18 / Phase 1; 2026-08-20 / Phase 7.1–7.5 |
| Kafka for async domain events | Fan-out, replay, decoupling. Not for synchronous CRUD | 2026-08-18 / Phase 1 |
| Kafka in KRaft mode (no ZooKeeper) | Current Kafka; simpler local broker | 2026-08-18 / Phase 2 |
| Dual Kafka listeners (INTERNAL + EXTERNAL) | Host uses `localhost:9092`; other containers use `promise-kafka:19092` | 2026-08-18 / Phase 2 |
| `restart: "no"` for local containers | Failures must stay visible in development | 2026-08-18 / Phase 2 |
| Modular monolith | Avoid premature microservices | 2026-08-18 / Phase 1 |
| Transactional outbox (table + publisher + generic consumer) | Reliable DB → Kafka; no direct publish from the request path. Persistence 5.7; publisher 5.8; generic consumer + `processed_events` 5.9; Phase 5.10 verified. Product consumers deferred. | 2026-08-18 / Phase 1; 2026-08-20 / Phase 5.7–5.10 |
| **Django + DRF instead of FastAPI** | Batteries-included backend, ORM, REST APIs | 2026-08-18 / pre-Phase 3 |
| Django **6.1** on Python **3.12** | Current Django; matches project Python target | 2026-08-18 / Phase 3 |
| Custom `users.User`; email as `USERNAME_FIELD` | Application identity is email + UUID, not Django's username `auth.User`. Must be set before first migrate for a clean schema. | 2026-08-18 / Phase 3 |
| APIs under `/api/v1/` | Versioned DRF REST surface | 2026-08-18 / pre-Phase 3 |
| Consistent API error envelope `{error: {code, message}}` | Clients get stable, non-leaky error JSON | 2026-08-18 / Phase 3 |
| Django stdlib console logging | Production-quality timestamps/levels without an external log stack | 2026-08-18 / Phase 3 |
| Abstract `apps.core.models.BaseModel` for domain models | UUID pk + created/updated timestamps without duplicating fields; User stays independent | 2026-08-18 / Phase 3 |
| UUID v4 primary keys (not sequential ints) | Stable public IDs across services; no shared sequence; reduced ID enumeration. Not a security control. | 2026-08-18 / Phase 3 |
| Timezone-aware UTC storage; user TZ is preference | `USE_TZ=True`, `TIME_ZONE=UTC`; APIs serialize aware timestamps | 2026-08-18 / Phase 3 |
| **Auth Option B:** JWT access + server-side rotating refresh sessions | Session table **implemented** (4.1), access JWT issue/verify **implemented** (4.2), register/login **implemented** (4.3), DRF JWT auth + `/me/` **implemented** (4.4), refresh rotation + 30s grace **implemented** (4.5), logout / logout-all **implemented** (4.6), Redis `sid` denylist **implemented** (4.7). Phase 4.8 **verified**. Login/register/refresh rate limits **implemented** (7.3). Goal/Commitment mutation rate limits **implemented** (7.4). | 2026-08-18 / design; 2026-08-19–20 / Phase 4.1–4.8; 2026-08-20 / Phase 7.3–7.4 |
| Access JWT 15 min; refresh 30d sliding / 90d cap; HS256 with dedicated key | Short stolen-access window; mobile-friendly re-auth; key rotation via `kid` | 2026-08-18 / design |
| Opaque refresh `{sid}.{secret}` stored as HMAC; current secret also Fernet ciphertext for 30s grace | HMAC cannot reconstruct the secret; ciphertext is grace recovery only; previous secrets HMAC-only; plaintext never stored | 2026-08-18 / design; 2026-08-19 / Phase 4.5 |
| Future `UserIdentity`; no `google_id`/`apple_id` on User | Multiple providers without duplicating users | 2026-08-18 / design |
| Soft email verification (non-blocking for MVP) | Onboarding friction vs later sharing/email guarantees | 2026-08-18 / design |
| Local AI via Ollama later | Zero API cost during experimentation | 2026-08-18 / Phase 1 |
| **Commitment** is a finite promise; recurrence is **Goal** | Avoid mixing one-shot accountability with habits/check-ins | 2026-08-20 / Phase 5 design |
| `created_by` + `CommitmentParticipant`; OVERDUE derived | Sharing without a second model; no overdue worker race | 2026-08-20 / Phase 5 design |

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
| 2026-08-19 | Phase 4.1 AuthSession migrate + tests | **Pass** — `authentication.0001_initial` applied; table `authentication_authsession`; `check` clean; `makemigrations --check` no changes; `pytest -v` **27 passed** in 3.59s |
| 2026-08-19 | Phase 4.2 access JWT infrastructure | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `pytest -v` **41 passed** in 6.08s |
| 2026-08-19 | Phase 4.3 register/login APIs | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `pytest -v` **65 passed** in 2.81s |
| 2026-08-19 | Phase 4.4 DRF JWT auth + `/auth/me/` | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `pytest -v` **88 passed** in 3.62s |
| 2026-08-19 | Phase 4.5 refresh-token rotation | **Pass** — `authentication.0002_authsession_current_refresh_secret_ciphertext` applied; `check` clean; `makemigrations --check` no changes; `pytest -v` **109 passed** in 4.93s |
| 2026-08-20 | Phase 4.6 logout / logout-all | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **127 passed** in 6.89s |
| 2026-08-20 | Phase 4.7 Redis auth denylist | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **138 passed** in 8.19s |
| 2026-08-20 | Phase 4.8 final authentication verification | **Pass** — `manage.py check` 0 issues; `makemigrations --check` no changes; `authentication` 0001+0002 applied; `.env` gitignored; `git diff --check` clean; `pytest -v` **138 passed** in 7.93s. Manual cURL **pending**. |
| 2026-08-20 | Phase 5 commitment design | **Docs only** — `COMMITMENT_DESIGN.md` written. No backend code, migrations, or APIs. Phase 5 **not** complete. |
| 2026-08-20 | Phase 5.2 commitment models | **Pass** — `commitments.0001_initial` applied; tables verified; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **155 passed** in 10.51s. No APIs. |
| 2026-08-20 | Phase 5.3 commitment services | **Pass** — `commitments.0002` applied (participant user PROTECT; drop redundant user index); `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **177 passed** in 10.26s. No APIs. |
| 2026-08-20 | Phase 5.4 commitment REST APIs | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **189 passed** in 13.09s. Manual cURL **pending**. |
| 2026-08-20 | Application table naming cleanup | **Pass** — `users.0002`, `authentication.0003`, `commitments.0003` applied (`ALTER TABLE … RENAME TO`); row counts unchanged; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **189 passed** in 11.99s. |
| 2026-08-20 | Phase 5.6 outbox design | **Docs only** — `OUTBOX_DESIGN.md` written. No models, migrations, publisher, or Kafka client. Phase 5 **not** complete. |
| 2026-08-20 | Phase 5.7 outbox persistence | **Pass** — `outbox.0001_initial_outbox_event` applied; table `outbox_events`; live create incremented commitments/events/outbox together; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **197 passed** in 12.18s. No Kafka. |
| 2026-08-20 | Phase 5.8 outbox Kafka publisher | **Pass** — `manage.py publish_outbox`; topic `promise.commitment.v1`; `SKIP LOCKED` batches of 50; backoff + park after 8; `confluent-kafka`; live produce/consume against `promise-kafka`; unavailable-broker failure path; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **214 passed** in 14.67s. No consumers. Compose unchanged. |
| 2026-08-20 | Phase 5.9 generic Kafka consumer + idempotency | **Pass** — `manage.py consume_commitment_events`; group `promise-commitment-events`; `processed_events` + unique `(consumer_group, event_id)`; at-least-once offset-after-DB; live produce → consume → duplicate redelivery; `outbox.0002_processed_event` applied; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -q` **235 passed** in 13.69s. No product consumers. |
| 2026-08-20 | Phase 5.10 final commitment/event architecture verification | **Pass** — full HTTP→DB→outbox→Kafka→`processed_events` path; consumer seek-on-retry; application tables verified; no unexpected migrations; live create/publish/consume/replay dedup; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **236 passed** in 15.63s. Phase 5 **COMPLETE**. Notifications/AI/Android/shared invitations not implemented. |
| 2026-08-20 | Phase 6.1 goal domain design | **Docs only** — `GOAL_DESIGN.md` written. No backend code, models, migrations, or APIs. Phase 6 **not** complete. |
| 2026-08-20 | Phase 6.2 goal models and migrations | **Pass** — `goals.0001` + `goals.0002` applied; tables `goals`, `goal_check_ins`, `goal_events`; `uniq_goal_check_in_period`; recurrence check constraints; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **264 passed** in 17.02s. No services/APIs. Phase 6 **not** complete. |
| 2026-08-20 | Phase 6.3 goal domain services | **Pass** — no new migration; services own create/lifecycle/check-in upsert, recurrence lock, derived progress/streaks, GoalEvent+OutboxEvent dual-write; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **289 passed** in 16.69s. No APIs. Phase 6 **not** complete. |
| 2026-08-20 | Goal Kafka topic routing + ISO Monday | **Pass** — publisher maps `commitment`→`promise.commitment.v1`, `goal`→`promise.goal.v1`; key=`aggregate_id`; unknown type fails without produce; no new migration; Compose unchanged; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **294 passed** in 17.95s. No Goal APIs/consumers. Phase 6 **not** complete. |
| 2026-08-20 | Phase 6.4 goal REST APIs | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **304 passed** in 19.06s. Manual cURL **pending**. Phase 6 **not** complete. |
| 2026-08-20 | Phase 6.5 goal/event integration verification | **Pass** — live `promise-kafka`; topic `promise.goal.v1`; create→unpublished outbox→`publish_outbox`→consume→replay `duplicates=1 processed=0`; generic `handle_record` accepts Goal types; no new migration; Compose unchanged; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **313 passed** in 24.51s. No `consume_goal_events` command. Phase 6 **not** complete. |
| 2026-08-20 | Phase 6.6 final Goal domain verification | **Pass** — domain/API/outbox/security review; tables `goals`/`goal_check_ins`/`goal_events` present; `goals.0001`+`0002` applied; no unexpected migrations; live `promise.goal.v1` create→publish→consume→replay dedup; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **313 passed** in 22.88s. Phase 6 **COMPLETE**. Notifications/AI/Android/shared goals not implemented. |
| 2026-08-20 | Phase 7.1 Redis product layer design | **Docs only** — `REDIS_DESIGN.md` written. No backend code, caches, rate-limit wiring, locks, or migrations. Auth denylist unchanged. Phase 7 **not** complete. Last pytest result remains **313 passed**. |
| 2026-08-20 | Phase 7.2 secure Redis rate-limit primitive | **Pass** — Lua `EVAL` INCR+EXPIRE-on-create; `increment_rate_limit` fail-open; `incr_with_ttl` unchanged; no HTTP 429 wiring; no new migration; Compose unchanged; denylist unchanged; live `promise-redis` count 1→2 without TTL reset, keys deleted; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **334 passed** in 23.97s. Phase 7 **not** complete. |
| 2026-08-20 | Phase 7.3 authentication rate limits | **Pass** — login/register/refresh Redis buckets; 429 `RATE_LIMITED` + `Retry-After`; fail-open; REMOTE_ADDR only; hashed emails; refresh unknown tokens use bounded IP fallback; no Goal/Commitment throttles; no new migration; denylist unchanged; live Redis limit+TTL then key delete; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **356 passed** in 23.99s. Phase 7 **not** complete. |
| 2026-08-20 | Phase 7.4 Goal/Commitment mutation rate limits | **Pass** — commitment writes 60/60s, goal writes 60/60s, check-ins 30/60s; GET exempt; caller `user_id` buckets; 429 `RATE_LIMITED` + `Retry-After`; fail-open; no new migration; denylist/auth throttles unchanged; live Redis fill then 429 then key delete; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **378 passed** in 25.69s. Phase 7 **not** complete. |
| 2026-08-20 | Phase 7.5 final Redis product layer verification | **Pass** — Redis = denylist hint + TTL counters only; Postgres source of truth; canonical keys in `REDIS_DESIGN.md`; AUTHENTICATION_DESIGN keys aligned; fail-open A–E; live Redis TTLs then key delete; no new code/buckets/migrations; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **378 passed** in 25.46s. Phase 7 **COMPLETE**. |
| 2026-08-20 | Phase 9.1 Android foundation design | **Docs only** — `ANDROID_DESIGN.md` written. No Android source, Gradle, backend, Docker, Redis, or Kafka changes. Phase 9 **not** complete. Last pytest result remains **378 passed**. |
| 2026-08-20 | Phase 9.2 Android Gradle + Compose bootstrap | **Pass** — single `:app` module; AGP 9.1.1 / Gradle 9.3.1 / Kotlin 2.3.21 / Compose BOM 2026.06.00 / Hilt 2.59.2; minSdk 26; compileSdk 37; `./gradlew test` **11 passed**; `assembleDebug` SUCCESS; no device attached; backend unmodified. Phase 9 **not** complete. |
| 2026-08-20 | Phase 9.3 Android authentication + networking | **Pass** — Retrofit/OkHttp client; Keystore AES-GCM refresh; memory access JWT; login + session restore; 401 refresh-once; 429 Retry-After parsed; `./gradlew test` **32 passed**; `assembleDebug` SUCCESS; no device attached; backend unmodified. Phase 9 **not** complete. |
| 2026-08-20 | Phase 9.4 Android app shell + Home UI | **Pass** — four-tab shell; Home preview feed; Light/Dark (default Light, in-memory); Profile theme + sign out; haptics on meaningful actions; `./gradlew test` **50 passed**; `assembleDebug` SUCCESS; no device attached; backend `check` + `makemigrations --check` clean. Phase 9 **not** complete. |
| 2026-08-20 | Phase 9.5 Android Commitments product UI | **Pass** — list filters; create sheet; detail Complete/Snooze/Wait/Unsnooze/Cancel; real backend APIs; no Edit/Unwait; `./gradlew test` **71 passed**; `assembleDebug` SUCCESS; no device attached; backend `check` + `makemigrations --check` clean. Phase 9 **not** complete. |
| 2026-08-20 | Phase 9.6 Android Goals product UI | **Pass** — Active/Paused/Completed list; create sheet; detail progress/streak/history; BINARY/COUNT check-in; Pause/Resume/Complete/Cancel; backend-derived progress only; no Edit/Shared Goals; `./gradlew test` **90 passed**; `assembleDebug` SUCCESS; no device attached; backend `check` + `makemigrations --check` clean. Phase 9 **not** complete. |

---

## Known Issues

- **Compose `restart: "no"`.** Containers do not restart themselves after Docker Desktop or an explicit stop. PostgreSQL was up for the 2026-08-18 migrate and 2026-08-19 final review.
- **Python version mismatch on PATH.** Project target is 3.12; default `python3` is 3.14.6. Backend work uses `backend/.venv` (3.12.14).
- **Empty placeholder directories.** `infrastructure/{postgres,redis,kafka}` exist but have no app code. Compose lives at the repo root. `android/` has the Phase 9.2–9.6 Gradle/Compose app (auth + shell + Home + Commitments + Goals). `backend/` has the Django foundation.
- **Health test does not require a database query.** The health endpoint still returns `{"status": "ok"}` without hitting application tables. PostgreSQL is used for applied Django migrations.
- **Custom `AUTH_USER_MODEL` applied after a local DB reset.** The empty local `promise` database was dropped and recreated (not the Docker volume). `users_user` is the user table; `auth_user` is gone.
- **Manual authentication cURL is pending.** Phase 4.8 automated verification passed; live HTTP against `runserver` was not executed.
- **Manual commitment cURL is pending.** Phase 5.4 automated verification passed; live HTTP against `runserver` was not executed.
- **Manual goal cURL is pending.** Phase 6.4 automated verification passed; live HTTP against `runserver` was not executed.
- **Deferred auth work (non-blocking).** Login/register/refresh rate limits are wired (Phase 7.3). Password reset, email verification, social login, Kafka user events, and trusted-proxy client IP remain deferred. Android 9.3 implements login + Keystore refresh; register UI is not built.
- **Phase 7 Redis product layer is complete.** Denylist + auth/mutation throttles are live. Domain caches, notification locks, AI throttles, trusted-proxy IP, and production fail-closed-login-if-Redis-down remain deferred.
- **Phase 9 Android is in progress.** 9.1–9.6 complete (through Goals product UI). Shared Goals, register UI, FCM, and Room are not implemented. Debug HTTP to `10.0.2.2` is allowed only in the debug `network_security_config`; release forbids cleartext.
- **Deferred commitment product work (non-blocking for Phase 6).** Shared invitation APIs, HTTP `/unwait/`, automatic snooze expiry persistence, client-writable `source` vs design “server-set MANUAL”, `DATE` end-of-local-day conversion, 14-day published-outbox deletion, DLT. Design docs still describe some of those as DECIDED for later slices.
- **Goal consumer command is not implemented (deferred, not a Phase 6 blocker).** Generic `handle_record` accepts Goal `event_type`s. Topic `promise.goal.v1` was live-verified in Phase 6.5 and 6.6. There is still no `consume_goal_events` management command or product Goal worker.

No open infrastructure defects from the Phase 2 verification.

---

## Git Checkpoints

| Commit | Date | Meaning |
|---|---|---|
| `6b9cb00` | 2026-08-18 | Initialize repo, `.gitignore`, `README.md` |
| `5284662` | 2026-08-18 | Add plan, architecture, and development docs |
| `c4fac57` | 2026-08-18 | Add local Docker infrastructure |
| `d8e351e` | 2026-08-19 | Bootstrap Django backend (Phase 3 complete) |
| `be52efe` | 2026-08-20 | Implement authentication (Phase 4) |
| `0655fe9` | 2026-08-20 | Auth/plan documentation update after Phase 4 |

`main` tracks `origin/main` at `0655fe9`.

Not in Git yet (Phase 5.1–5.10 + Phase 6.1–6.6 + Goal Kafka routing + table naming + Phase 7.1–7.5 Redis + Phase 9.1–9.6 Android):

- `docs/COMMITMENT_DESIGN.md`
- `docs/OUTBOX_DESIGN.md`
- `docs/DEVELOPMENT.md`
- `docs/BUILD_PROGRESS.md`
- `docs/GOAL_DESIGN.md`
- `docs/REDIS_DESIGN.md`
- `docs/AUTHENTICATION_DESIGN.md`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT_PLAN.md`
- `docs/ANDROID_DESIGN.md`
- `backend/apps/core/redis.py`
- `backend/tests/test_rate_limit_redis.py`
- `backend/apps/authentication/rate_limits.py`
- `backend/apps/authentication/views.py`
- `backend/config/exceptions.py`
- `backend/tests/test_auth_rate_limits.py`
- `backend/apps/goals/`
- `backend/tests/test_goal_models.py`
- `backend/tests/test_goal_services.py`
- `backend/tests/test_goal_api.py`
- `backend/tests/test_goal_outbox.py`
- `backend/tests/test_goal_rate_limits.py`
- `backend/tests/test_commitment_rate_limits.py`
- `backend/apps/outbox/`
- `backend/tests/test_outbox.py`
- `backend/tests/test_outbox_publisher.py`
- `backend/tests/test_outbox_consumer.py`
- `backend/pyproject.toml`
- `backend/apps/users/models.py`
- `backend/apps/users/migrations/0002_rename_application_tables.py`
- `backend/apps/authentication/models.py`
- `backend/apps/authentication/migrations/0003_rename_application_tables.py`
- `backend/apps/commitments/`
- `backend/config/settings/base.py`
- `backend/config/urls.py`
- `backend/tests/test_commitment_models.py`
- `backend/tests/test_commitment_services.py`
- `backend/tests/test_commitment_api.py`
- `android/` (except gitignored `local.properties` / `build/`)

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
- **2026-08-19:** Phase 3 committed (`d8e351e`).
- **2026-08-19:** Phase 4.1 **COMPLETE** — `AuthSession` + HMAC refresh-secret storage + migration. pytest 27 passed. Phase 4 APIs **not** started.
- **2026-08-19:** Phase 4.2 **COMPLETE** — HS256 access JWT issue/verify (PyJWT). pytest 41 passed. No new migration. No auth HTTP APIs.
- **2026-08-19:** Phase 4.3 **COMPLETE** — registration/login APIs, Argon2id password hashing, generic login failures, and token/session issuance. pytest 65 passed. No new migration. Phase 4 remains in progress.
- **2026-08-19:** Phase 4.4 **COMPLETE** — DRF JWT access authentication, `GET /api/v1/auth/me/`, default `IsAuthenticated` with public health/register/login. pytest 88 passed. No new migration. Phase 4 remains in progress.
- **2026-08-19:** Phase 4.5 **COMPLETE** — `POST /api/v1/auth/refresh/` with Fernet current-secret ciphertext, 30-second grace, reuse revoke, and `SELECT FOR UPDATE` concurrency. Migration `authentication.0002` applied. pytest 109 passed. Phase 4 remains in progress.
- **2026-08-20:** Phase 4.6 **COMPLETE** — logout / logout-all revoke `AuthSession` rows (no delete, no Redis denylist). Refresh fails immediately; access JWTs remain cryptographically valid until `exp` but `/auth/me/` rejects revoked sessions via PostgreSQL. pytest 127 passed. No new migration. Phase 4 remains in progress.
- **2026-08-20:** Phase 4.7 **COMPLETE** — Redis `sid` denylist after logout, JWT denylist check before PostgreSQL, fail-open on Redis with PostgreSQL fallback. Refresh Redis lock deferred. pytest 138 passed. No new migration. Phase 4 remains in progress.
- **2026-08-20:** Phase 4.8 **COMPLETE** — Final authentication verification. `manage.py check` PASS; `makemigrations --check` PASS; pytest **138 passed**. No blocking security issues. Phase 4 Authentication **COMPLETE**. Manual cURL pending. Next: Phase 5.
- **2026-08-20:** Phase 4 committed (`be52efe`, docs `0655fe9`).
- **2026-08-20:** Phase 5 commitment **design complete** (`docs/COMMITMENT_DESIGN.md`). **No implementation.** Phase 5 remains in progress.
- **2026-08-20:** Phase 5.2 **COMPLETE** — `Commitment` / `CommitmentParticipant` / `CommitmentEvent` models + `commitments.0001_initial`. pytest 155 passed. No APIs. Phase 5 remains in progress.
- **2026-08-20:** Phase 5.3 **COMPLETE** — domain services, `select_for_update` transitions, events in the same transaction, authorization helpers. `commitments.0002` (PROTECT participant user). pytest **177 passed**. No REST APIs. Phase 5 remains in progress.
- **2026-08-20:** Phase 5.4 **COMPLETE** — commitment REST APIs under `/api/v1/commitments/` (CRUD + complete/snooze/unsnooze/wait/cancel). JWT required. Unrelated users get 404. pytest **189 passed**. No new migration. Manual cURL pending. Phase 5 remains in progress.
- **2026-08-20:** Application table naming cleanup — explicit `db_table` (`users`, `auth_sessions`, `commitments`, `commitment_participants`, `commitment_events`). Rename migrations applied; row counts unchanged; pytest **189 passed**. Django internals unchanged. Phase 5 remains in progress.
- **2026-08-20:** Phase 5.6 **design complete** (`docs/OUTBOX_DESIGN.md`). **No implementation.** No outbox table, publisher, or Kafka client. Phase 5 remains in progress.
- **2026-08-20:** Phase 5.7 **COMPLETE** — `outbox_events` + same-transaction writes with `CommitmentEvent`. pytest **197 passed**. No Kafka publisher. Phase 5 remains in progress.
- **2026-08-20:** Phase 5.8 **COMPLETE** — `manage.py publish_outbox` publishes due `outbox_events` to `promise.commitment.v1` (`confluent-kafka`, `SKIP LOCKED`, backoff, park after 8). pytest **214 passed**. Automated verification and local Kafka integration verification completed. No consumers. Phase 5 remains in progress.
- **2026-08-20:** Phase 5.9 **COMPLETE** — generic `consume_commitment_events` + `processed_events` idempotency. pytest **235 passed**. Automated verification and local Kafka integration verification completed. No notifications/AI/analytics. Phase 5 remains in progress.
- **2026-08-20:** Phase 5.10 **COMPLETE** — final architecture verification. Consumer seek-on-retry for at-least-once. `manage.py check` PASS; no unexpected migrations; pytest **236 passed**; live Kafka create → publish → consume → replay dedup. Phase 5 — Commitment Domain + Event Backbone = **COMPLETE**. Notifications, AI, Android, and shared invitations are not implemented. Next: Phase 6 — Goal Domain.
- **2026-08-20:** Phase 6.1 goal **design complete** (`docs/GOAL_DESIGN.md`). **No implementation.** Phase 6 remains in progress.
- **2026-08-20:** Phase 6.2 **COMPLETE** — `Goal` / `GoalCheckIn` / `GoalEvent` models + `goals.0001` / `goals.0002`. Tables `goals`, `goal_check_ins`, `goal_events`. pytest **264 passed**. No services or APIs. Phase 6 remains in progress.
- **2026-08-20:** Phase 6.3 **COMPLETE** — Goal domain services (create/update/lifecycle, check-in upsert, derived progress/streaks, GoalEvent + OutboxEvent). pytest **289 passed**. No REST APIs. Phase 6 remains in progress.
- **2026-08-20:** Goal Kafka topic routing — publisher maps `commitment` → `promise.commitment.v1` and `goal` → `promise.goal.v1` (key = `aggregate_id`). ISO-8601 Monday-start weeks **DECIDED** in `GOAL_DESIGN.md`. pytest **294 passed**. Phase 6.4 APIs **not** started. Phase 6 remains in progress.
- **2026-08-20:** Phase 6.4 **COMPLETE** — Goal REST APIs under `/api/v1/goals/` (CRUD + pause/resume/complete/cancel + check-ins). JWT required. Unrelated users get 404. pytest **304 passed**. No new migration. Manual cURL pending. Phase 6 remains in progress.
- **2026-08-20:** Phase 6.5 **COMPLETE** — Goal/event integration verification. Live `promise.goal.v1` create → publish → consume → replay dedup. Generic consumer accepts Goal event types. pytest **313 passed**. No `consume_goal_events` command. Phase 6 remains in progress.
- **2026-08-20:** Phase 6.6 **COMPLETE** — Final Goal domain verification. `manage.py check` PASS; no unexpected migrations; pytest **313 passed**; live Kafka create → publish → consume → replay dedup. Phase 6 — Goal Domain = **COMPLETE**. Notifications, AI, Android, shared goals, challenges, commitment↔goal links, and dedicated Goal consumers are not implemented. Next: Phase 7 — Redis Integration (product cache / rate-limit / locks; auth Redis already exists).
- **2026-08-20:** Phase 7.1 Redis product **design complete** (`docs/REDIS_DESIGN.md`). **No implementation.** MVP Redis work is rate limits, not domain caches or new locks. Auth denylist unchanged. Phase 7 remains in progress and is **not** complete.
- **2026-08-20:** Phase 7.2 **COMPLETE** — atomic Redis rate-limit primitive (`increment_rate_limit`, Lua INCR+EXPIRE). Fail-open on Redis errors. `incr_with_ttl` unchanged. No HTTP 429 wiring. pytest **334 passed**. Live Redis count 1→2 without TTL reset. Phase 7 remains in progress and is **not** complete.
- **2026-08-20:** Phase 7.3 **COMPLETE** — login/register/refresh Redis rate limits. 429 `RATE_LIMITED` with `Retry-After`. Fail-open if Redis is down. pytest **356 passed**. Goal/Commitment throttles, caches, and locks are not wired. Phase 7 remains in progress and is **not** complete.
- **2026-08-20:** Phase 7.4 **COMPLETE** — Goal/Commitment mutation rate limits (commitment writes 60/min/user, goal writes 60/min/user, check-ins 30/min/user). GET exempt. 429 `RATE_LIMITED` with `Retry-After`. Fail-open if Redis is down. pytest **378 passed**. Domain caches and locks are not wired. Phase 7 remains in progress and is **not** complete.
- **2026-08-20:** Phase 7.5 **COMPLETE** — Final Redis product layer verification. pytest **378 passed**. Live Redis TTLs + fail-open + key cleanup. Canonical keys documented. Phase 7 — Redis Product Layer = **COMPLETE**. Deferred: domain caches, notification locks, AI throttles, trusted-proxy IP. Next unimplemented DEVELOPMENT phase: Android Foundation.
- **2026-08-20:** Phase 9.1 Android foundation **design complete** (`docs/ANDROID_DESIGN.md`). **No Android implementation.** Phase 9 remains in progress and is **not** complete.
- **2026-08-20:** Phase 9.2 Android Gradle + Compose bootstrap **COMPLETE**. Single `:app` module, Hilt, navigation graphs, paper/ink theme. `./gradlew test` 11 passed; `assembleDebug` SUCCESS. No emulator. Phase 9 remains in progress and is **not** complete.
- **2026-08-20:** Phase 9.3 Android authentication + networking **COMPLETE**. Login, Keystore refresh, session restore, 401 refresh-once. `./gradlew test` **32 passed**; `assembleDebug` SUCCESS. No emulator. Phase 9 remains in progress and is **not** complete.
- **2026-08-20:** Phase 9.4 Android app shell + Home UI **COMPLETE**. Four-tab shell, Home preview, Light/Dark, Profile theme + sign out. `./gradlew test` **50 passed**; `assembleDebug` SUCCESS. No emulator. Phase 9 remains in progress and is **not** complete.
- **2026-08-20:** Phase 9.5 Android Commitments product UI **COMPLETE**. List/create/detail actions against backend APIs. `./gradlew test` **71 passed**; `assembleDebug` SUCCESS. No emulator. Phase 9 remains in progress and is **not** complete.
- **2026-08-20:** Phase 9.6 Android Goals product UI **COMPLETE**. List/create/detail/check-in/lifecycle against backend APIs. `./gradlew test` **90 passed**; `assembleDebug` SUCCESS. No emulator. Phase 9 remains in progress and is **not** complete.
- **2026-08-22:** Phase 9.7 Android Home Real Data Integration **COMPLETE**. Replaced preview home data with live `CommitmentRepository` (`GET /api/v1/commitments/`) and `GoalRepository` (`GET /api/v1/goals/`) via `HomeRepositoryImpl`. Mapped overdue, today, and upcoming commitments using user's timezone; mapped active goals to practices with backend-derived progress and streaks; handled partial section failures and pull-to-refresh; connected row navigation to Commitment and Goal details. `./gradlew test` **205 passed**; `assembleDebug` SUCCESS. Phase 9 remains in progress and is **not** complete.
- **2026-08-22:** Phase 9.8 Android Completion + Production Readiness **COMPLETE**. Real Register UI connected to `POST /api/v1/auth/register/` with validation, duplicate email handling, rate limiting (429), and session transition; Profile secondary `POST /api/v1/auth/logout-all/` with confirmation dialog and haptics; Production API configuration supporting debug local/LAN/emulator and release fail-safe validation (never falling back to localhost); deep links verified (`promise://home`, `promise://commitment/{id}`, `promise://goal/{id}`); accessibility, theme, and haptics review completed. `./gradlew test` **208 passed**; `assembleDebug` SUCCESS. Phase 9 remains in progress and is **not** complete.
- **2026-08-22:** Phase 10.1 Promise Notifications & Reminders Design **COMPLETE**. Locked design recorded in `docs/NOTIFICATION_DESIGN.md`: commitment/goal taxonomy, snooze semantics strictly preserving `due_at` via `POST /api/v1/commitments/{id}/snooze/`, quiet hours with timezone respect, durable server-side preferences, durable reminder identity/idempotency keys, and Notification Scheduler/Worker architecture. No code or backend changes implemented.
- **2026-08-22:** Phase 10.2 Backend Notification Architecture **COMPLETE**. Created `apps.notifications` with `UserNotificationPreferences` (OneToOne with User, quiet hours, category controls) and `Reminder` (durable identity_key uniqueness constraint, lifecycle statuses, due/entity indexes). Implemented lifecycle helper services (`schedule_reminder` with `select_for_update` idempotency, `cancel_reminders_for_entity`, `mark_reminder_dispatched`/`suppressed`/`failed`). Migration `notifications.0001_initial` applied. Backend pytest **435 passed**. No FCM, Android, or Kafka changes.
- **2026-08-22:** Phase 10.3 FCM Push Dispatch **COMPLETE**. Added `UserDevice` and `NotificationDelivery` models in `apps.notifications` with migration `0002`. Implemented device registration REST API (`POST`/`DELETE /api/v1/notifications/devices/`) with token rotation and single-logout/logout-all device deactivation. Implemented FCM client protocol with `MockFcmClient` for testing/local dev. Implemented lease-based Reminder claim and `dispatch_due_reminders` with quiet hours deferral, resolved state pre-checks, per-device delivery retry/state machine (success on one device does not suppress retry on failed device; Reminder transitions to DISPATCHED only when all deliverable attempts resolve; token invalidation deactivates only unregistered devices). Backend pytest **449 passed**. Android gradlew test **208 passed**.
- **2026-08-22:** Phase 10.4 Android FCM Notification Receiver & Channels **COMPLETE**. Added `DeviceApi` and `DeviceRegistrationRepositoryImpl` with token sync on `login`, `register`, `restoreSession`, `onNewToken`, and unregistration on `logout`/`logout-all`. Configured 4 separate notification channels (`channel_commitment_alerts`, `channel_commitment_reminders`, `channel_goal_reminders`, `channel_system`). Implemented `PromiseNotificationManager` with stable FNV-1a 32-bit notification IDs, `NotificationPayloadParser`, `NotificationActionReceiver` (dismisses shade immediately), and WorkManager `NotificationActionWorker` (executes `CommitmentRepository` / `GoalRepository` actions using authenticated Retrofit and 401 refresh-once flow). Android gradlew test **214 passed**; `assembleDebug` SUCCESS; backend pytest **449 passed**.
- **2026-08-22:** Phase 10.5 Notification Preferences UI **COMPLETE**. Added `NotificationPreferencesApi` (`GET`/`PATCH /api/v1/notifications/preferences/`), domain models, and `NotificationPreferencesRepositoryImpl`. Implemented `NotificationPreferencesSection` inside `ProfileScreen` with animated sub-sections, independent OS notification permission warning banner with `ACTION_APP_NOTIFICATION_SETTINGS` handoff, Material 3 time dialog for quiet hours pair selection, and `ProfileViewModel` with immediate optimistic update and rollback on failure. Android gradlew test **216 passed**; `assembleDebug` SUCCESS; backend pytest **449 passed**.
- **2026-08-22:** Phase 10.6 End-to-End Notification Verification **COMPLETE**. Verified end-to-end notification architecture, models, device tracking, lease claiming, retry state machines, mock FCM dispatcher, quiet-hours suppression, resolved-state suppression, WorkManager direct action execution, and preference syncing across both test suites. Android gradlew test **216 passed**; `assembleDebug` SUCCESS; backend pytest **449 passed** (6 skipped live Redis). Physical Pixel 9 device was not attached to adb at verification time and is documented accurately as unattached. Phase 10 (10.1–10.6) **COMPLETE**.
- **2026-08-22:** Phase 11.1 Shared Goals & Participant Chat Design **LOCKED**. Defined canonical `GoalParticipant` membership model (`OWNER`/`PARTICIPANT`, `ACTIVE`/`INVITED`/`DECLINED`/`LEFT`/`REMOVED`) used across auth, check-in attribution (`GoalCheckIn.participant`), shared progress aggregation, and chat access. Formulated non-gamified shared progress semantics for BINARY and COUNT tracking. Designed scoped text/emoji chat (`ChatMessage`, `GoalChatReadState` with strictly monotonic forward cursor and `(created_at, id)` deterministic ordering), privacy rules (404 for non-participants and personal goals), outbox events (`goal.chat.message_created.v1` with minimal payload), and Android navigation flow. No code or migrations implemented.
- **2026-08-22:** Phase 11.2 Shared Goal Models & Migrations **COMPLETE**. Added `is_shared` on `Goal`, created `ChatMessage` with `(goal, created_at, id)` ordering index, and `GoalChatReadState` with `(goal, user)` uniqueness constraint. Verified `GoalParticipant` canonical membership model (`OWNER`/`PARTICIPANT`, `ACTIVE`/`INVITED`/`DECLINED`/`LEFT`/`REMOVED`) and `GoalCheckIn.participant` attribution. Migration `goals.0007_goal_is_shared_chatmessage_goalchatreadstate_and_more` applied with data backfill. Backend pytest **455 passed**; Android gradlew test **216 passed**.
- **2026-08-22:** Phase 11.3 Shared Goal Services & Authorization **COMPLETE**. Implemented canonical membership helpers (`get_goal_membership`, `can_view_goal`, `can_edit_goal`, `can_manage_members`, `can_check_in`, `can_view_chat`, `can_send_chat`, `can_update_read_state`). Added `create_shared_goal`, `calculate_collective_progress` (supporting BINARY and COUNT), and full chat services (`send_chat_message`, `list_chat_messages`, `mark_chat_read` with monotonic forward cursor, `get_chat_summary` excluding self unread count). Emitted transactional outbox event `goal.chat.message_created` with minimal payload. Backend pytest **465 passed**; Android gradlew test **216 passed**.
- **2026-08-22:** Phase 11.4 Shared Goal REST APIs **COMPLETE**. Implemented REST endpoints for shared goals, invitation lifecycle (`invite` with 201 first / 200 duplicate / 409 active, `accept`, `decline`, `leave`, and `DELETE /participants/{participant_id}/`), participant check-ins with private note protection across participants, and scoped chat API (`GET`/`POST /chat/messages/`, `POST /chat/read/`, `GET /chat/summary/`). Enforced object-hiding 404 for unrelated users, non-participants, and personal goal chat access. Added `test_shared_goal_api.py`. Backend pytest **472 passed**; Android gradlew test **216 passed**.
- **2026-08-22:** Phase 11.5.1 Shared Goal + Chat Android UI Design **LOCKED**. Formulated Android interaction and visual specifications for Shared Goals: strict reuse of existing Promise theme tokens and typography; restrained visual separation of individual progress/streak vs collective progress; structural icons from Material icon system (emojis restricted to message bodies only); `PromiseHaptics` (`confirm()` on check-in, `light()` on chat send); cursor-based reverse layout pagination with stable scroll positioning; optimistic chat sending with local temporary ID reconciliation; and private room chat experience without gamification or public discovery. No code or migrations implemented.
- **2026-08-22:** Phase 11.5.2 Android Shared Goal + Chat Implementation **COMPLETE**. Extended domain models (`ChatMessage`, `GoalChatSummary`, `GoalChatReadState`, `isShared`), DTOs, mappers, and `GoalApi` endpoints (`listChatMessages`, `sendChatMessage`, `markChatRead`, `getChatSummary`). Implemented `GoalChatScreen` with reverse layout cursor pagination, optimistic message dispatch, error retry, and date/sender bubble styling conforming to the Promise design system. Connected deep-links `promise://goal/{goalId}` and `promise://goal/{goalId}/chat` with iOS-like slide motion and `PromiseHaptics`. Added `GoalChatViewModelTest`. Backend pytest **472 passed**; Android gradlew test **221 passed**; `assembleDebug` SUCCESS.
- **2026-08-22:** Phase 11.5.3 Android Shared Goal Participants & Invitations **COMPLETE**. Completed participant and invitation experience using pre-existing `UserRepository.lookupByEmail` without backend modifications. Updated `ParticipantsSheet` to cleanly segment Active Members and Pending Invitations with Owner indicator chips and removal confirmations. Integrated `GoalDetailViewModel` state reconciliation on invite, remove, leave, accept, and decline with immediate roster cache invalidation, `HomeFreshness.markDirty()`, and `PromiseHaptics`. Added test cases in `GoalDetailViewModelTest`. Android gradlew test **225 passed**; `assembleDebug` SUCCESS; backend pytest **472 passed**.
- **2026-08-22:** Phase 11.5.4 Shared Goal Chat Push Integration **COMPLETE**. Connected `goal.chat.message_created` from Kafka `promise.goal.v1` to the notification pipeline. Added `consume_goal_events` management command and `handle_goal_event` consumer to query active goal participants (excluding sender) and schedule idempotent, deterministic `Reminder` records with identity key `{user_id}:GOAL:{goal_id}:goal.chat.message_created:{message_id}`. Extended `dispatch_due_reminders` to render `{sender_name}: {message_body}` with deep link `promise://goal/{goal_id}/chat`, respecting quiet hours, user preferences, and participant membership. Updated Android `NotificationPayloadParser` to route chat pushes to `CHANNEL_GOAL_REMINDERS`. Backend pytest **488 passed** (10 new tests in `test_goal_chat_notifications.py`), Django `check` clean; Android gradlew test **225 passed**; `assembleDebug` and `installDebug` on 2 physical devices (Pixel 9 & 25028RN03I) SUCCESS.
- **2026-08-22:** Phase 11.5.5 Real-Time Shared Goal Chat **COMPLETE**. Implemented full WebSocket real-time chat stack: Django Channels + `daphne` ASGI routing + `channels_redis` group fanout (`goal_chat_{goal_id}`). Implemented `ChatConsumer` with strict `Authorization: Bearer <access_token>` handshake header validation, active membership check, personal/invited/left/removed rejection, and Redis presence tracking (`promise:chat_presence:{goal_id}:{user_id}`). Hooked `transaction.on_commit` broadcast in `send_chat_message` and revocation disconnects in `remove_participant` and `leave_goal`. Extended `dispatch_due_reminders` with foreground chat presence suppression to prevent duplicate push notifications. Implemented Android `GoalChatRealtimeClient` with OkHttp WebSocket, exponential backoff reconnect loop, and 4401 token refresh integration via `SessionRefresher`. Integrated `GoalChatViewModel` for live message stream consumption, optimistic temporary ID reconciliation, and REST catch-up after reconnects. Backend pytest **497 passed** (9 new tests in `test_websocket_chat.py`); Django `check` clean; Android gradlew test **229 passed** (4 new tests in `GoalChatRealtimeClientTest` and `GoalChatViewModelTest`); `assembleDebug` and `installDebug` on 2 physical devices (Pixel 9 & 25028RN03I) SUCCESS.
- **2026-08-22:** Batch 13 Final UX Simplification + AI UI Wiring **COMPLETE**. Streamlined Android authentication UI to a single calm Google-only authentication page; simplified Profile screen by removing password/email change and security history sections while retaining active session management and account deletion; added 10-topic animated FAQ accordion section; wired existing AI features into Android UI (`GoalAiBuilderSheet` in goal creation, `CommitmentRefinerSheet` in commitment creation, `ThoughtParserSheet` and `WeeklyInsightsCard` on HomeScreen, and `ChatSummarySheet` in GoalChatScreen); configured release signing via `promise-release.jks` with alias `promise`.
- **2026-08-23:** Batch 14C Visual Hierarchy, Gesture Sync, and UI Polish **COMPLETE**. Implemented finger-synced `HorizontalPager` in `MainShell.kt` with 1:1 gesture tracking, fast flings, smooth cancellation, and reduced-motion fallback; deepened dark theme palette to layered surfaces (`#0A0A0C`, `#111114`, `#18181C`); established 7 curated high-contrast session accents; refined typography tokens across Display, Headline, Title, Body, Label; created reusable `PromiseSkeleton` system; restructured HomeScreen into a clean dashboard; integrated Android Credential Manager `GetGoogleIdOption` with Web Client ID (`547289698615-huc17692on1292athsn3ph80fiaagm9p.apps.googleusercontent.com`) to launch native Google account picker and submit ID tokens to `POST /api/v1/auth/google/`; eliminated obsolete email/password error strings; verified clean release build with `./gradlew assembleRelease`.
- **2026-08-23:** Batch 15 AI Reliability + Daily Motivation + Weekly Insights Dashboard **COMPLETE**. Refactored `GeminiKeyScheduler` into fair thread-safe round-robin scheduler ($K_1 \rightarrow K_2 \rightarrow K_3 \rightarrow K_1$) with health-aware failover and 60-second cooldown; made Weekly Insights fully authoritative and resilient by calculating PostgreSQL facts first and generating deterministic factual fallback insights on Gemini failures (guaranteeing HTTP 200); built compact Weekly Insights dashboard card on HomeScreen with 3-metric top row, time-of-day distribution bars, and skeleton loading; added `DailyMotivationQuote` model with atomic date uniqueness, generation outside transactions, race-safe insert, and date-based curated fallback (`/api/v1/ai/motivation/today/`); added Today's Thought section on HomeScreen; eliminated `"Request cancelled by Promise"` messages by catching `CancellationException` silently across ViewModels and AI sheets; verified `./gradlew test` and signed production release build.
- **2026-08-23:** Batch 16 Notification Settings + Feature FAQ + Tech Signature **COMPLETE**. Aligned Android notification DTOs (`goals_daily_reminder`, `goals_daily_reminder_time`, `goals_evening_reminder`, `goals_evening_reminder_time`, `quiet_hours_start`, `quiet_hours_end`) with backend `UserNotificationPreferencesSerializer`; revamped `NotificationPreferencesSection` on Profile into 6 clear visual sections (Master toggle, Reminders with inline Morning/Evening TimePicker dialogs, Shared Goals, Weekly Digest, Quiet Hours range picker, and Paginated History cards); added layout-preserving skeleton loader during async fetch; updated `PROMISE_FAQS` to 12 concise, benefit-focused, feature-selling questions; added monospace developer signature footer (`PROMISE CORE v${BuildConfig.VERSION_NAME} // BUILD 3042 // ARCHITECTED & CRAFTED BY ARJUN VATS // SYSTEM STATUS: OPERATIONAL`); verified `./gradlew test` and signed production release build with `./gradlew assembleRelease`.
- **2026-08-24:** Batch 17 Profile Avatars + Goal Shared Conversion + Firebase Updates **COMPLETE**. Implemented user profile avatar support with local storage (`AvatarStorage`), backend sync (`POST/DELETE /api/v1/users/me/avatar/`), and Compose UI (`ProfileAvatar`, `AvatarPickerBottomSheet`); added personal goal conversion to shared status (`POST /api/v1/goals/{id}/convert-shared/`); integrated in-app release update checks via Firebase App Distribution (`updateIfNewReleaseAvailable`) on `onResume` with `REQUEST_INSTALL_PACKAGES` permission; updated Gemini default reasoning model to `3.5-flash` and fast model to `3.5-flash-lite`; standardized default timezone to `Asia/Kolkata`; enhanced network resilience thresholds and silent cancellation handling in AI sheets; verified `./gradlew test` and signed production release build.
- **2026-08-29:** Batch 18 Modern Redesign, Interactive Glance Widgets, Interactive Notifications, & Streamlined UX **COMPLETE**. Redesigned atmospheric Login Screen with ambient lighting, Compose Canvas 4-color Google vector, and 3 bento feature cards; redesigned Bottom Navigation Bar with 64dp elevated container, 24dp icons, `.navigationBarsPadding()`, and labels; built autonomous Glance Home Screen Widgets (`GoalsGlanceWidget` & `CommitmentsGlanceWidget`) with scrollable `LazyColumn`, in-widget 1-tap check-in/completion, `🔄` live sync button, and direct deep-linking; overhauled interactive notification system with deterministic entity deduplication, 1-tap actions (`✓ Complete`, `⏰ Snooze 1h`, `✓ Check In`), inline `RemoteInput` direct replies for shared goal chats, and immediate widget refresh dispatch; streamlined commitment list filters to 3 clean categories (`All Open`, `Overdue`, `Completed`) with 0ms tab switching and shimmer skeletons; verified `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (35 tasks passed) and backend Python `py_compile` (0 errors).
- **2026-08-29:** Batch 19 Voice Quick Capture, Natural Language AI Temporal Engine, & Floating Mic Glance Widget **COMPLETE**. Built end-to-end voice capture with 4-stage state machine (Acoustic Recording $\rightarrow$ User Review/Retake $\rightarrow$ AI Processing $\rightarrow$ Finalize); scrubbed technical AI copy; engineered compound sentence decomposition (`_split_compound_thoughts`) breaking continuous speech into individual task clauses; implemented universal natural language date/time parsing across Android client (`NaturalLanguageDateParser.kt`) and backend (`thought_parser.py`) supporting relative offsets (*"after 3 days"*, *"3 days after"*, *"in 2 weeks"*), named intervals (*"in afternoon"*, *"morning"*, *"evening"*, *"night"*), calendar dates (*"1st of September"*), and automatic modal verb/date title cleaning; built floating 1x1 aura disc and 2x1 pill Glance widget (`VoiceMicGlanceWidget.kt`) launching translucent `VoiceQuickCaptureActivity.kt` with live theme and user accent synchronization; resolved coroutine cancellation activity teardown bug using suspend-and-await creation; fixed Room duplicate entity collision via online/offline outbox isolation; verified 100% backend pytest (`9/9 passed`), Android unit tests (`11/11 passed`), and release build `:app:assembleRelease` with 0 errors and 0 warnings.

