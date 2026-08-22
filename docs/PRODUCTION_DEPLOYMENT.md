# Promise — Production Deployment Guide & Runbook

This document provides the exhaustive production deployment specifications, architecture, environment configurations, and operational runbooks for **Promise**.

---

## 1. Target Infrastructure Overview

Promise is hosted on **Railway** with managed data and messaging tiers across **Railway** and **Aiven**:

```
+-----------------------------------------------------------------------------------+
|                                  CLIENT LAYER                                     |
|    Android Client (app.promise.android) — HTTPS REST API + WSS Chat WebSocket    |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v  (HTTPS / WSS via Cloud Load Balancer)
+-----------------------------------------------------------------------------------+
|                                 RAILWAY SERVICES                                  |
|                                                                                   |
|  +------------------------------------+  +-------------------------------------+  |
|  | Service 1: promise-web             |  | Service 2: promise-outbox-worker    |  |
|  | Daphne ASGI Server                 |  | Transactional Outbox Publisher      |  |
|  | daphne -b 0.0.0.0 -p $PORT         |  | python manage.py publish_outbox     |  |
|  |   config.asgi:application          |  |   --continuous                      |  |
|  +------------------------------------+  +-------------------------------------+  |
|                                                                                   |
|  +------------------------------------+  +-------------------------------------+  |
|  | Service 3: promise-goal-worker     |  | Service 4: promise-notification-wkr |  |
|  | Kafka Goal Consumer Group          |  | FCM Reminder Dispatcher             |  |
|  | python manage.py                   |  | python manage.py                    |  |
|  |   consume_goal_events              |  |   run_notification_dispatcher       |  |
|  |                                    |  |   --continuous                      |  |
|  +------------------------------------+  +-------------------------------------+  |
+-----------------------------------------+-----------------------------------------+
                                          |
        +---------------------------------+---------------------------------+
        |                                 |                                 |
        v                                 v                                 v
+-----------------------+     +-----------------------+     +-----------------------+
|  Railway PostgreSQL   |     |  Railway Redis (KV)   |     |      Aiven Kafka      |
|  - PostgreSQL 17      |     |  - Redis 8 (TLS)      |     |  - Managed Cluster    |
|  - ACID Domain State  |     |  - Channels Layer     |     |  - SASL_SSL / SCRAM   |
|  - Outbox Events      |     |  - Rate Limits        |     |  - Topics:            |
|  - Processed Events   |     |  - Session Denylist   |     |    * promise.goal.v1  |
|  - Reminders & Auth   |     |  - Chat Presence      |     |    * promise.commit...|
+-----------------------+     +-----------------------+     +-----------------------+
```

### Railway Service Definitions
Every backend service uses the same repository and same Dockerfile build context (`backend/Dockerfile` with `rootDirectory = "backend"`), sharing the same environment variables.

| Railway Service Name | Start Command | Type / Ports | Healthcheck Path |
| :--- | :--- | :--- | :--- |
| `promise-web` | `daphne -b 0.0.0.0 -p $PORT config.asgi:application` | Web (HTTP / WSS) | `/api/v1/health/ready/` |
| `promise-outbox-worker` | `python manage.py publish_outbox --continuous` | Background Worker | N/A (Process Monitoring) |
| `promise-goal-worker` | `python manage.py consume_goal_events` | Background Worker | N/A (Process Monitoring) |
| `promise-notification-worker` | `python manage.py run_notification_dispatcher --continuous` | Background Worker | N/A (Process Monitoring) |

> [!IMPORTANT]
> **Network Security & Isolation**:
> - Railway PostgreSQL and Redis must **NOT** have public TCP proxies enabled.
> - All service-to-database communication occurs strictly over Railway's private internal network (`postgres.railway.internal`, `redis.railway.internal`).
> - Only `promise-web` exposes a public HTTPS/WSS domain. The 3 background workers have no public ports.

---

## 2. Environment Variables Reference

All secrets must be configured as environment variables in Railway. Never commit or hardcode production credentials.

### Core Django
| Variable | Required | Description / Format | Example Value |
| :--- | :--- | :--- | :--- |
| `DJANGO_SETTINGS_MODULE` | Yes | Django settings module | `config.settings.production` |
| `DJANGO_SECRET_KEY` | Yes | 50+ char random secret key | `secrets.token_urlsafe(50)` |
| `DJANGO_DEBUG` | Yes | Debug flag (Must be False) | `false` |
| `DJANGO_ALLOWED_HOSTS` | Yes | Comma-separated domain allowlist | `api.promise.app,promise-web.up.railway.app` |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated CORS allowed origins | `https://app.promise.app` |
| `CSRF_TRUSTED_ORIGINS` | No | Comma-separated CSRF trusted origins | `https://api.promise.app` |

### Managed Databases & Caches (Private Network)
| Variable | Required | Description / Format | Example Value |
| :--- | :--- | :--- | :--- |
| `DATABASE_URL` | Yes | Internal PostgreSQL connection URI | `postgresql://user:pass@postgres.railway.internal:5432/railway?sslmode=require` |
| `REDIS_URL` | Yes | Internal Redis connection URI | `rediss://default:pass@redis.railway.internal:6379/0` |

### Aiven Kafka
| Variable | Required | Description / Format | Default in Prod |
| :--- | :--- | :--- | :--- |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | Comma-separated broker endpoints | `kafka-inst.aivencloud.com:19096` |
| `KAFKA_SECURITY_PROTOCOL` | Yes | Security protocol | `SASL_SSL` |
| `KAFKA_SASL_MECHANISM` | Yes | SASL mechanism | `SCRAM-SHA-256` |
| `KAFKA_SASL_USERNAME` | Yes | Aiven Kafka user username | `avnadmin` |
| `KAFKA_SASL_PASSWORD` | Yes | Aiven Kafka user password | `SecretPassword123` |
| `KAFKA_SSL_CA_LOCATION` | No | CA cert path if custom CA is used | `""` (Uses system CAs) |

### Cryptography & Authentication
| Variable | Required | Description / Generation |
| :--- | :--- | :--- |
| `AUTH_REFRESH_TOKEN_PEPPER` | Yes | Dedicated HMAC secret for refresh token pepper hashing. |
| `AUTH_REFRESH_TOKEN_ENCRYPTION_KEY` | Yes | Dedicated 32-byte Fernet key for active refresh-secret ciphertext: `python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"` |
| `JWT_SIGNING_KEY` | Yes | Dedicated HS256 secret for access JWT signatures. |
| `JWT_KEY_ID` | Yes | Active Key Identifier (e.g. `prod-v1`). |
| `JWT_ISSUER` | Yes | Token Issuer (`promise-api`). |
| `JWT_AUDIENCE` | Yes | Token Audience (`promise-client`). |

### Gemini AI (Backend Only)
| Variable | Required | Description |
| :--- | :--- | :--- |
| `GEMINI_API_KEY_1` | Yes | Primary Gemini API Key |
| `GEMINI_API_KEY_2` | No | Secondary Gemini API Key (Fallback key 2) |
| `GEMINI_API_KEY_3` | No | Tertiary Gemini API Key (Fallback key 3) |
| `GEMINI_DEFAULT_MODEL` | No | Default model (Default: `gemini-3.6-flash`) |
| `GEMINI_FAST_MODEL` | No | Fast model (Default: `gemini-3.6-flash`) |
| `GEMINI_TIMEOUT_SECONDS`| No | Timeout in seconds (Default: `15`) |
| `GEMINI_MAX_OUTPUT_TOKENS` | No | Output token limit (Default: `1024`) |

### Push Notifications & Email
| Variable | Required | Description |
| :--- | :--- | :--- |
| `FCM_ENABLED` | Yes | `true` when Firebase is active, `false` for mock dispatcher. |
| `FIREBASE_PROJECT_ID` | If FCM | Firebase Project ID. |
| `FIREBASE_CREDENTIALS_JSON`| If FCM | JSON service account credentials string or base64. |
| `DJANGO_EMAIL_BACKEND` | No | `django.core.mail.backends.smtp.EmailBackend` |
| `EMAIL_HOST` | If Email | SMTP Host (e.g. `smtp.sendgrid.net`). |
| `EMAIL_PORT` | If Email | SMTP Port (`587`). |
| `EMAIL_HOST_USER` | If Email | SMTP Username (`apikey`). |
| `EMAIL_HOST_PASSWORD` | If Email | SMTP Password / API Key. |
| `EMAIL_USE_TLS` | No | `true` |
| `DEFAULT_FROM_EMAIL` | No | `Promise <noreply@promise.app>` |

---

## 3. Database Migration & Safety

### Migration Command
```bash
python manage.py migrate
```

### Migration Safety Rules
- All migrations are forward-compatible and non-destructive.
- No table drops, no column removals on active columns.
- Pre-deployment check: `python manage.py makemigrations --check` must return 0 changes.
- Rollback consideration: In case of deployment rollback, previous schema migrations maintain backward compatibility with existing columns.

---

## 4. Redis Architecture

Promise uses Redis for five critical distributed mechanisms:
1. **Django Channels Channel Layer**: Coordinates WebSocket real-time messages across ASGI worker processes.
2. **Rate Limiting Engine**: High-throughput atomic token buckets (`incr_with_ttl`, `increment_rate_limit`) with fail-closed security for auth endpoints.
3. **Real-time Chat Presence**: Ephemeral keys `promise:chat_presence:<goal_id>:<user_id>` with 60-second TTL refreshed via ping.
4. **Session Denylist**: Immediate invalidation of revoked JWT sessions (`promise:auth:denylist:sid:<session_id>`).
5. **Worker Coordination**: Ephemeral locks and lease records for reminder dispatching.

---

## 5. Aiven Kafka & Event Streaming

### Topic Provisioning
Ensure the following topics are created on Aiven Kafka prior to launching worker services:

| Topic Name | Partitions | Replication Factor | Cleanup Policy | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `promise.goal.v1` | 3 | 3 (or cluster default) | `delete` | Goal lifecycle, participant, check-in, and chat events |
| `promise.commitment.v1` | 3 | 3 (or cluster default) | `delete` | Commitment state transitions, snoozes, completions |

### Consumer Groups
- `promise-goal-events` (Handled by `promise-goal-worker`)
- `promise-commitment-events` (Handled by commitment consumer)

### SASL/SSL Authentication Configuration
The `apps.outbox.kafka` module automatically applies:
- `security.protocol = "SASL_SSL"`
- `sasl.mechanism = "SCRAM-SHA-256"`
- `sasl.username` & `sasl.password`

---

## 6. Background Workers & Graceful Shutdown

All three worker services are designed for zero-data-loss and graceful shutdown:

### Worker 1: Outbox Publisher
- **Command**: `python manage.py publish_outbox --continuous`
- **Behavior**: Scans `outbox_events` table for unpublished events, publishes to Kafka with delivery callbacks, updates `published_at` timestamp.
- **Shutdown**: Handles `SIGTERM`/`SIGINT`, finishes publishing the current batch, and terminates cleanly.

### Worker 2: Goal Event Consumer
- **Command**: `python manage.py consume_goal_events`
- **Behavior**: Subscribes to `promise.goal.v1`, records processed event IDs in PostgreSQL `processed_events` for strict idempotency, commits Kafka offsets post-transaction.
- **Shutdown**: Handles `SIGTERM`/`SIGINT`, closes consumer connection, and cleanly commits offset.

### Worker 3: Notification Dispatcher
- **Command**: `python manage.py run_notification_dispatcher --continuous`
- **Behavior**: Claims due reminders from PostgreSQL using transactional row locking and lease expiration, checks quiet hours and user notification preferences, delivers pushes via FCM.
- **Shutdown**: Handles `SIGTERM`/`SIGINT`, completes active batch dispatch, and exits without leaving orphaned claimed leases.

---

## 7. Push Notifications & Firebase FCM

1. **Package / Application ID**: `app.promise.android`
2. **Device Registration**: Client registers FCM tokens via `POST /api/v1/notifications/devices/`.
3. **Deep Links**:
   - Goal Detail / Chat: `promise://goals/{goal_id}`
   - Commitment Detail: `promise://commitments/{commitment_id}`
4. **Token Invalidation**: Server automatically marks device tokens inactive on `UnregisteredError` or `SenderIdMismatchError` from FCM.

---

## 8. Gemini AI Production Architecture

1. **Keys & Fallback**: Backend utilizes a 3-key fallback chain (`GEMINI_API_KEY_1` -> `GEMINI_API_KEY_2` -> `GEMINI_API_KEY_3`). If Key 1 hits quota or rate limit (HTTP 429/503), the client automatically fails over to Key 2, then Key 3.
2. **Model Routing**:
   - Default: `gemini-3.6-flash`
   - Fast Model: `gemini-3.6-flash`
3. **Client Security**: Android client contains **ZERO** Gemini API keys. All AI interactions go through authenticated backend endpoints (`/api/v1/ai/...`).
4. **Privacy**: Raw AI prompts and outputs containing user goal content are never written to general application logs.

---

## 9. HTTPS, WSS & Proxy Configuration

1. **Reverse Proxy SSL Termination**: Railway terminates TLS and forwards traffic with `X-Forwarded-Proto: https`.
2. **Django SSL Header**: `SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")`.
3. **WebSocket Route**: `wss://<production-domain>/ws/goals/<goal_id>/chat/`.
4. **WebSocket Authentication**: Handshake uses the standard HTTP `Authorization: Bearer <jwt_access_token>` header.

---

## 10. Health Checks & Monitoring

### Liveness Probe
- **Endpoint**: `GET /api/v1/health/`
- **Response**: `{"status": "ok"}` (HTTP 200)

### Readiness Probe
- **Endpoint**: `GET /api/v1/health/ready/`
- **Checks**:
  - PostgreSQL connectivity (`connection.ensure_connection()`)
  - Redis connectivity (`redis.ping()`)
- **Success Response (HTTP 200)**:
  ```json
  {
    "status": "ok",
    "db": "healthy",
    "redis": "healthy"
  }
  ```
- **Degraded Response (HTTP 503)**:
  ```json
  {
    "status": "degraded",
    "db": "unhealthy",
    "redis": "healthy"
  }
  ```
*Note: Readiness endpoint never returns database credentials or connection strings.*

---

## 11. Production Configuration Checker Command

To validate all production environment variables and downstream service health in a container or CLI without printing secrets:

```bash
DJANGO_SETTINGS_MODULE=config.settings.production python manage.py check_production
```

Output example:
```
==================================================
PROMISE PRODUCTION CONFIGURATION AUDIT
==================================================
[OK] DEBUG = False
[OK] ALLOWED_HOSTS configured (2 host(s))
[OK] DJANGO_SECRET_KEY is configured
[OK] JWT_SIGNING_KEY is configured
[OK] Refresh token pepper & encryption key configured
[OK] PostgreSQL connection HEALTHY
[OK] Redis connection HEALTHY
[OK] Kafka bootstrap servers CONFIGURED (protocol=SASL_SSL)
[OK] Gemini AI keys CONFIGURED (3 fallback key(s))
[INFO] Firebase FCM is DISABLED (using mock dispatcher)
[OK] SECURE_PROXY_SSL_HEADER configured for HTTPS reverse proxy
==================================================
RESULT: PRODUCTION CONFIGURATION VALID
```

---

## 12. Android Release Build & Signing

### Build Configuration
The Android application dynamically selects the production API endpoint when building the `release` variant.

- **Set Release URL**:
  Add `promise.prodApiBaseUrl` to `local.properties` or set environment variable `PROMISE_PROD_API_BASE_URL`:
  ```bash
  export PROMISE_PROD_API_BASE_URL="https://api.promise.app/api/v1/"
  ```
- **Build Release APK / Bundle**:
  ```bash
  JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew assembleRelease
  ```
- **Safety Safeguard**: The Gradle build task `validateReleaseApiConfig` automatically prevents building a release APK if the URL is empty or points to `localhost`/`10.0.2.2`.

---

## 13. Backup & Disaster Recovery

1. **PostgreSQL Automated Backups**: Enable Railway daily automated backups with 7-day retention.
2. **Manual Snapshot Command**:
   ```bash
   pg_dump "$DATABASE_URL" -Fc -f "promise_backup_$(date +%Y%m%d_%H%M%S).dump"
   ```
3. **Restore Verification Procedure**:
   ```bash
   pg_restore --clean --if-exists -d "$RESTORE_DATABASE_URL" promise_backup_*.dump
   ```

---

## 14. Pre-Deployment Verification Checklist

Before deploying to live infrastructure:

- [ ] Execute test suite: `pytest -v` (Must pass 100%)
- [ ] Django system check: `python manage.py check --deploy`
- [ ] Migrations check: `python manage.py makemigrations --check`
- [ ] Android tests: `./gradlew test` (Must pass 100%)
- [ ] Android debug assemble: `./gradlew assembleDebug`
- [ ] Build Docker backend container: `docker build -t promise-backend:latest ./backend`
- [ ] Run `python manage.py check_production` under production settings
- [ ] Provision Aiven Kafka topics (`promise.goal.v1`, `promise.commitment.v1`)
- [ ] Provision Railway services (`promise-web`, `promise-outbox-worker`, `promise-goal-worker`, `promise-notification-worker`)
- [ ] Configure environment variables in Railway dashboard

---

## 15. Incident Playbook & Rollback Strategy

1. **Degraded Healthcheck (HTTP 503)**:
   - Check `/api/v1/health/ready/` response to identify whether PostgreSQL or Redis is down.
   - Inspect Railway PostgreSQL and Redis memory / connection metrics.
2. **Outbox Queue Backlog**:
   - Check `promise-outbox-worker` logs.
   - Verify Aiven Kafka broker connectivity and SASL credentials.
3. **Immediate Service Rollback**:
   - In Railway dashboard, select the previous successful deployment and click **Redeploy**.
   - Workers and web services roll back synchronously to the previous container image.
