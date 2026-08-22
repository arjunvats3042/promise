# Promise — Testing Strategy & Execution Guide

This document outlines the testing architecture, categories, execution commands, and environment requirements for the Promise backend and Android applications.

---

## 1. Test Categories

All test cases across the Promise platform are structured into discrete validation categories:

| Category | Scope & Purpose | Execution Trigger | Dependencies Required |
| :--- | :--- | :--- | :--- |
| **UNIT** | Fast domain logic, models, serializers, utility helpers, and pure functions. | Local dev, pre-commit, CI | SQLite / in-memory |
| **INTEGRATION** | Multi-component orchestration: API views, PostgreSQL transactions, Redis rate limits, transactional outbox, Channels WebSocket handlers. | Local dev, CI | PostgreSQL, Redis |
| **REGRESSION** | Edge cases discovered during development (e.g. concurrency races, idempotency deduplication, timezone boundaries, token revocation). | Local dev, CI | PostgreSQL, Redis |
| **SECURITY** | Authentication flows, JWT rotation, IDOR cross-user boundaries, rate limiting, SQL injection protection, cryptographic key separation, log sanitization. | Local dev, CI | PostgreSQL, Redis |
| **REAL_EXTERNAL_INTEGRATION** | Live Google Gemini API validation across 15 structured feature contracts, multi-key fallback chains, and network failure modes. | Explicit manual execution (`RUN_REAL_AI_TESTS=1`) | Live Internet, Gemini API keys |
| **PHYSICAL_MANUAL_QA** | Two-device real Android phone testing: push notifications, WebSocket chat presence, background sync, network transitions. | Release qualification | Physical Android devices, ADB |

---

## 2. Backend Testing Commands

All backend commands run inside the virtual environment located at `backend/.venv/`.

### 2.1 Standard Automated Test Suite (Ordinary CI / Local Dev)
Runs all unit, integration, regression, and security tests. External services (Gemini, FCM) are mocked or gated.

```bash
cd backend
./.venv/bin/pytest -v
```

### 2.2 Focused Test Execution
Target specific subsystems or batches for rapid iteration:

```bash
# Security & Hardening
./.venv/bin/pytest tests/test_batch_11_hardening.py -v

# Real-Time WebSocket & Chat
./.venv/bin/pytest tests/test_websocket_chat.py -v

# Outbox Publisher & Kafka Serialization
./.venv/bin/pytest tests/test_outbox_publisher.py tests/test_outbox_consumer.py -v

# Rate Limiting & Redis Atomicity
./.venv/bin/pytest tests/test_rate_limit_redis.py -v

# Deployment & Configuration Sanity
./.venv/bin/pytest tests/test_batch_14a_deployment.py -v
```

### 2.3 Real Gemini External Integration Suite
> [!WARNING]
> **External API & Quota Notice**:
> These tests interact directly with Google Gemini production API endpoints.
> - Requires `RUN_REAL_AI_TESTS=1` and `GEMINI_API_KEY_1` set in the environment.
> - May incur API quota and token usage.
> - **Never** executed during ordinary CI or automated pre-commit runs.

```bash
cd backend
RUN_REAL_AI_TESTS=1 GEMINI_API_KEY_1="AIzaSy..." ./.venv/bin/pytest tests/test_real_ai_integration.py -v
```

### 2.4 Django System & Migration Checks

```bash
cd backend
# Validate Django system integrity and settings
./.venv/bin/python manage.py check

# Validate database migrations are complete with zero ungenerated changes
./.venv/bin/python manage.py makemigrations --check

# Validate production environment configuration (Safe audit - zero secrets logged)
DJANGO_SETTINGS_MODULE=config.settings.production ./.venv/bin/python manage.py check_production
```

---

## 3. Android Testing Commands

All Android commands run from the `android/` directory using Gradle and JDK 17.

### 3.1 Local Unit Tests (JVM)
Runs all Kotlin unit tests, ViewModel tests, repository tests, and JSON serialization contracts:

```bash
cd android
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew test
```

### 3.2 Debug Build & APK Assembly
Assembles the debug APK and verifies full compilation, Compose UI tree, and Hilt dependency injection graphs:

```bash
cd android
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew assembleDebug
```

---

## 4. Physical Multi-Device QA Commands

Used during physical two-device validation for real-time chat, FCM push delivery, and synchronization:

```bash
# Check connected physical hardware
adb devices

# Install debug build onto all attached target devices
cd android
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew installDebug

# Capture live logcat filtered for Promise
adb logcat -s PromiseApp:V PromiseFCM:V PromiseSync:V
```

---

## 5. Command Dependency & Prerequisite Matrix

| Command | Database (PostgreSQL) | Cache (Redis) | Kafka Broker | Gemini API Keys | Physical Device | Can Incur Cost / Quota |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `pytest` | **Required** | **Required** | Mocked / Local | No | No | No |
| `pytest test_real_ai_integration.py` | **Required** | No | No | **Required** | No | **Yes (Gemini Tokens)** |
| `python manage.py check` | No | No | No | No | No | No |
| `python manage.py check_production` | **Required** | **Required** | No | Optional | No | No |
| `gradlew test` | No | No | No | No | No | No |
| `gradlew assembleDebug` | No | No | No | No | No | No |
| `gradlew installDebug` | No | No | No | No | **Required** | No |
