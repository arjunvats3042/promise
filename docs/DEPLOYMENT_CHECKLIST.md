# Promise — Production Deployment Checklist

Use this checklist during live deployment to ensure all pre-flight verifications, provisioning steps, and smoke tests are completed systematically.

---

## 1. PRE-DEPLOYMENT QUALIFICATION

### Automated Code & Test Checks
- [ ] Backend test suite passes 100%: `./.venv/bin/pytest -v` (651 tests passing).
- [ ] Django system check passes cleanly: `python manage.py check`.
- [ ] No unapplied database migrations: `python manage.py makemigrations --check`.
- [ ] Production configuration audit passes: `python manage.py check_production`.
- [ ] Docker container builds cleanly: `docker build -t promise-backend:prod ./backend`.
- [ ] Git workspace clean with zero whitespace errors: `git diff --check`.
- [ ] Android JVM unit tests pass: `./gradlew test`.
- [ ] Android debug compilation succeeds: `./gradlew assembleDebug`.

### Infrastructure & Secrets Provisioning
- [ ] Railway project created.
- [ ] Railway PostgreSQL instance provisioned (Internal networking only; public TCP proxy disabled).
- [ ] Railway Redis instance provisioned (Internal networking only; public TCP proxy disabled).
- [ ] Aiven Kafka cluster provisioned with SASL_SSL SCRAM-SHA-256 user.
- [ ] Kafka topics created on Aiven: `promise.goal.v1` and `promise.commitment.v1`.
- [ ] Firebase project created with FCM push credentials (`FIREBASE_CREDENTIALS_JSON`).
- [ ] Google Gemini API keys provisioned (`GEMINI_API_KEY_1`, `GEMINI_API_KEY_2`, `GEMINI_API_KEY_3`).
- [ ] Dedicated cryptographic keys generated (`DJANGO_SECRET_KEY`, `AUTH_REFRESH_TOKEN_PEPPER`, `AUTH_REFRESH_TOKEN_ENCRYPTION_KEY`, `JWT_SIGNING_KEY`).
- [ ] Transactional SMTP email credentials configured (`EMAIL_HOST_USER`, `EMAIL_HOST_PASSWORD`).
- [ ] Railway environment variables populated across all 4 services.

---

## 2. DEPLOYMENT EXECUTION

### Service Initialization
- [ ] 4 Railway services linked to repository with build context `backend/`:
  - [ ] `promise-web` (`daphne -b 0.0.0.0 -p $PORT config.asgi:application`)
  - [ ] `promise-outbox-worker` (`python manage.py publish_outbox --continuous`)
  - [ ] `promise-goal-worker` (`python manage.py consume_goal_events`)
  - [ ] `promise-notification-worker` (`python manage.py run_notification_dispatcher --continuous`)
- [ ] Run initial database migrations: `python manage.py migrate`.
- [ ] Deploy services in Railway.

### Health & Connectivity Verification
- [ ] `promise-web` Liveness probe responds HTTP 200: `GET https://<domain>/api/v1/health/`.
- [ ] `promise-web` Readiness probe responds HTTP 200: `GET https://<domain>/api/v1/health/ready/` (`db: healthy`, `redis: healthy`).
- [ ] `promise-outbox-worker` logs indicate active polling loop with 0 unhandled exceptions.
- [ ] `promise-goal-worker` logs confirm successful Kafka consumer group assignment on `promise.goal.v1`.
- [ ] `promise-notification-worker` logs confirm reminder dispatcher running.
- [ ] HTTPS redirect active; HSTS headers verified.
- [ ] WSS WebSocket connection handshakes cleanly on `wss://<domain>/ws/goals/<id>/chat/`.

---

## 3. POST-DEPLOYMENT SMOKE TESTS

- [ ] **Authentication**: Register a new user account, verify email, log in, refresh token, and fetch `/api/v1/auth/me/`.
- [ ] **Personal Goal**: Create a goal, log a check-in, verify streak calculation and progress updates.
- [ ] **Personal Commitment**: Create a commitment with due date, fulfill it, verify event recording.
- [ ] **Shared Goal**: Create a shared goal, invite a second user, accept invitation, and verify member capacity limits.
- [ ] **Real-Time Chat**: Connect two users via WebSocket to the shared goal, send messages, and verify real-time broadcast and presence indicators.
- [ ] **Push Notification**: Register a device token, trigger a reminder, verify FCM push delivery.
- [ ] **Search & Discovery**: Search for existing goals and commitments via `/api/v1/search/`.
- [ ] **AI Assistant**: Test AI commitment refiner or thought parser proposal, confirm structured output and verify domain data is not mutated without user approval.
- [ ] **Product Analytics**: Trigger client events, verify batched ingestion at `/api/v1/analytics/events/` and ensure PII is scrubbed.
- [ ] **Two-Device Physical QA**: Execute end-to-end sync between two physical Android devices over real cellular/Wi-Fi networks.
