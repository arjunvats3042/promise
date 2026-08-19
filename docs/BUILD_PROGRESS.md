# Promise — Build Progress

This file records **actual** implementation progress.

It does **not** replace:

- `docs/PROJECT_PLAN.md` — product and plan
- `docs/ARCHITECTURE.md` — intended architecture
- `docs/DEVELOPMENT.md` — intended roadmap

Update this file after every meaningful, verified development result. Never store secrets.

---

## Current Status

- **Current phase:** Phase 5 — Commitment Domain
- **Current step:** Phase 4.8 — Final authentication verification **COMPLETE**. Phase 4 Authentication is **COMPLETE**.
- **Overall status:** Backend authentication (Option B) is implemented and automated-verified: register, login, JWT `/me/`, refresh rotation + 30s grace, logout / logout-all, Redis `sid` denylist with PostgreSQL fallback. `users.User` unchanged. Manual cURL against a live server was **not** executed in this step.
- **Last completed milestone:** Phase 4.8 final authentication verification (2026-08-20)
- **Immediate next step:** Phase 5 — Commitment domain (`commitments`, `commitment_events`). Deferred by design: login/register/refresh rate limits, password reset, email verification, social login, Kafka user events, Android.

Phase checklist:

- Phase 1 Repository Setup: **COMPLETE**
- Phase 2 Local Infrastructure: **COMPLETE**
- Phase 3 Django Backend Foundation: **COMPLETE**
- Phase 4 Authentication: **COMPLETE** (automated verification; manual cURL pending)

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
- **Git commit:** not committed yet

---

## Current Work

Phase 4 Authentication is complete (automated verification; manual cURL pending). Phase 5 Commitment Domain has not started.

Uncommitted (Phase 4.1–4.8):

- `backend/apps/authentication/`
- `backend/apps/core/redis.py`
- `backend/tests/test_auth_session.py`
- `backend/tests/test_access_tokens.py`
- `backend/tests/test_auth_register_login.py`
- `backend/tests/test_auth_jwt_me.py`
- `backend/tests/test_auth_refresh.py`
- `backend/tests/test_auth_logout.py`
- `backend/tests/test_auth_redis.py`
- `backend/tests/test_redis_client.py`
- `backend/tests/conftest.py`
- `backend/config/settings/base.py`
- `backend/config/exceptions.py`
- `backend/config/urls.py`
- `backend/config/views.py`
- `backend/pyproject.toml` (`PyJWT>=2.8`, `argon2-cffi`, `cryptography`, `redis`; dev `fakeredis`)
- `.env.example` (pepper + Fernet encryption-key placeholder + JWT placeholders)
- `docs/BUILD_PROGRESS.md`
- `docs/AUTHENTICATION_DESIGN.md`
- `docs/ARCHITECTURE.md`

---

## Next Steps

1. Phase 5 — Commitment domain (`commitments`, `commitment_events`).
2. Deferred by design: login/register/refresh rate limits (`incr_with_ttl` exists but is unwired), password reset, email verification, social login, Kafka user events, Android.

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
| **Auth Option B:** JWT access + server-side rotating refresh sessions | Session table **implemented** (4.1), access JWT issue/verify **implemented** (4.2), register/login **implemented** (4.3), DRF JWT auth + `/me/` **implemented** (4.4), refresh rotation + 30s grace **implemented** (4.5), logout / logout-all **implemented** (4.6), Redis `sid` denylist **implemented** (4.7). Phase 4.8 **verified**. Rate limits remain unwired (deferred). | 2026-08-18 / design; 2026-08-19–20 / Phase 4.1–4.8 |
| Access JWT 15 min; refresh 30d sliding / 90d cap; HS256 with dedicated key | Short stolen-access window; mobile-friendly re-auth; key rotation via `kid` | 2026-08-18 / design |
| Opaque refresh `{sid}.{secret}` stored as HMAC; current secret also Fernet ciphertext for 30s grace | HMAC cannot reconstruct the secret; ciphertext is grace recovery only; previous secrets HMAC-only; plaintext never stored | 2026-08-18 / design; 2026-08-19 / Phase 4.5 |
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
| 2026-08-19 | Phase 4.1 AuthSession migrate + tests | **Pass** — `authentication.0001_initial` applied; table `authentication_authsession`; `check` clean; `makemigrations --check` no changes; `pytest -v` **27 passed** in 3.59s |
| 2026-08-19 | Phase 4.2 access JWT infrastructure | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `pytest -v` **41 passed** in 6.08s |
| 2026-08-19 | Phase 4.3 register/login APIs | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `pytest -v` **65 passed** in 2.81s |
| 2026-08-19 | Phase 4.4 DRF JWT auth + `/auth/me/` | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `pytest -v` **88 passed** in 3.62s |
| 2026-08-19 | Phase 4.5 refresh-token rotation | **Pass** — `authentication.0002_authsession_current_refresh_secret_ciphertext` applied; `check` clean; `makemigrations --check` no changes; `pytest -v` **109 passed** in 4.93s |
| 2026-08-20 | Phase 4.6 logout / logout-all | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **127 passed** in 6.89s |
| 2026-08-20 | Phase 4.7 Redis auth denylist | **Pass** — no new migration; `check` clean; `makemigrations --check` no changes; `git diff --check` clean; `pytest -v` **138 passed** in 8.19s |
| 2026-08-20 | Phase 4.8 final authentication verification | **Pass** — `manage.py check` 0 issues; `makemigrations --check` no changes; `authentication` 0001+0002 applied; `.env` gitignored; `git diff --check` clean; `pytest -v` **138 passed** in 7.93s. Manual cURL **pending**. |

---

## Known Issues

- **Compose `restart: "no"`.** Containers do not restart themselves after Docker Desktop or an explicit stop. PostgreSQL was up for the 2026-08-18 migrate and 2026-08-19 final review.
- **Python version mismatch on PATH.** Project target is 3.12; default `python3` is 3.14.6. Backend work uses `backend/.venv` (3.12.14).
- **Empty placeholder directories.** `android/` and `infrastructure/{postgres,redis,kafka}` exist but have no app code. Compose lives at the repo root. `backend/` has the Django foundation.
- **Health test does not require a database query.** The health endpoint still returns `{"status": "ok"}` without hitting application tables. PostgreSQL is used for applied Django migrations.
- **Custom `AUTH_USER_MODEL` applied after a local DB reset.** The empty local `promise` database was dropped and recreated (not the Docker volume). `users_user` is the user table; `auth_user` is gone.
- **Manual authentication cURL is pending.** Phase 4.8 automated verification passed; live HTTP against `runserver` was not executed.
- **Deferred auth work (non-blocking).** Login/register/refresh rate limits are not wired. Password reset, email verification, social login, Kafka user events, and Android were out of Phase 4.8 scope. `docs/DEVELOPMENT.md` still lists Phase 4 as the next checkpoint.

No open infrastructure defects from the Phase 2 verification.

---

## Git Checkpoints

| Commit | Date | Meaning |
|---|---|---|
| `6b9cb00` | 2026-08-18 | Initialize repo, `.gitignore`, `README.md` |
| `5284662` | 2026-08-18 | Add plan, architecture, and development docs |
| `c4fac57` | 2026-08-18 | Add local Docker infrastructure |
| `d8e351e` | 2026-08-19 | Bootstrap Django backend (Phase 3 complete) |

`main` tracks `origin/main` at `d8e351e`.

Not in Git yet (Phase 4.1–4.8):

- `backend/apps/authentication/`
- `backend/apps/core/redis.py`
- `backend/tests/test_auth_session.py`
- `backend/tests/test_access_tokens.py`
- `backend/tests/test_auth_register_login.py`
- `backend/tests/test_auth_jwt_me.py`
- `backend/tests/test_auth_refresh.py`
- `backend/tests/test_auth_logout.py`
- `backend/tests/test_auth_redis.py`
- `backend/tests/test_redis_client.py`
- `backend/tests/conftest.py`
- `backend/config/settings/base.py`
- `backend/config/exceptions.py`
- `backend/config/urls.py`
- `backend/config/views.py`
- `backend/pyproject.toml` (`PyJWT>=2.8`, `argon2-cffi`, `cryptography`, `redis`)
- `.env.example` JWT + Fernet placeholders
- `docs/BUILD_PROGRESS.md`
- `docs/AUTHENTICATION_DESIGN.md`
- `docs/ARCHITECTURE.md`

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
