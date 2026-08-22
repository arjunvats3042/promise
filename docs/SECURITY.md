# Promise — Security Architecture & Threat Mitigation Guide

This document outlines the security controls, cryptographic standards, authorization models, and threat defenses implemented across the Promise backend and Android applications.

---

## 1. Authentication & Token Lifecycle

### 1.1 Dual-Token Architecture
- **Short-Lived Access JWT**:
  - Expiry: 15 minutes.
  - Signed via HS256 using dedicated `JWT_SIGNING_KEY` (supports zero-downtime key rotation via `JWT_KEY_ID` and `JWT_KEY_ID_PREVIOUS`).
  - Contains standard claims: `sub` (User UUID), `sid` (Session UUID), `jti` (Token UUID), `iss` (`promise-api`), `aud` (`promise-client`).
- **Cryptographic Refresh Token**:
  - Expiry: 30 days.
  - Structure: High-entropy random token (`secrets.token_urlsafe(64)`).
  - Storage: Stored only as an HMAC-SHA256 hash using a dedicated `AUTH_REFRESH_TOKEN_PEPPER`. Plaintext tokens are never stored in the database.
  - Active Token Ciphertext: Protected via 32-byte Fernet symmetric encryption for safe 30-second concurrency grace recovery.

### 1.2 Multi-Factor & Federated Identity
- **Google OAuth2 Sign-In**: Validates Google ID tokens via official Google token verification endpoints, extracting verified email and sub identifiers.
- **Email Verification**: Cryptographically signed 6-digit numeric codes with 15-minute TTL and rate-limited attempts.
- **Password Reset**: Cryptographically secure, single-use reset tokens with strict invalidation upon successful completion.

---

## 2. Session & Device Management

- **Multi-Device Tracking**: Every user login generates an explicit `UserSession` tracking IP address, user agent, device model, and last active timestamp.
- **Instant Revocation**: Users can revoke individual sessions or invoke "Log out all other devices".
- **Redis Session Denylist**: When a session is revoked, its `sid` is immediately pushed to Redis (`promise:auth:denylist:sid:<sid>`) with a TTL matching the access token lifetime. Daphne WebSocket and REST endpoints check the denylist on every protected action.
- **Account Deletion**: Full GDPR/CCPA compliant hard deletion removing all personal identity, session records, push tokens, and revoking all active authentication credentials immediately.

---

## 3. Authorization & Insecure Direct Object Reference (IDOR) Mitigation

- **Object-Level Authorization**: Every database query filters by user ownership or explicit goal participation (`apps.goals.permissions` and `apps.commitments.permissions`).
- **UUID Primary Keys**: All entities (Users, Goals, Commitments, Check-ins, Messages) use non-sequential UUIDv4 primary keys to prevent enumeration attacks.
- **Role Hierarchy in Shared Goals**:
  - `OWNER`: Full administrative control, member removal, goal termination, ownership transfer.
  - `MEMBER`: Check-in creation, chat participation, progress viewing.
  - Non-members receive strict `404 Not Found` (rather than `403 Forbidden`) to avoid leaking the existence of private entities.

---

## 4. Rate Limiting & Abuse Defense

Promise enforces atomic Redis token-bucket and sliding-window rate limiters on all sensitive endpoints:

| Endpoint / Operation | Rate Limit Ceiling | Key Structure / Scope |
| :--- | :--- | :--- |
| `POST /api/v1/auth/login/` | 5 req / min | IP + Hashed Email |
| `POST /api/v1/auth/register/` | 3 req / hour | IP Address |
| `POST /api/v1/auth/password/reset/` | 3 req / hour | Hashed Email |
| `POST /api/v1/ai/*` | 20 req / min | User UUID |
| `GET /api/v1/users/lookup/` | 10 req / min | User UUID |
| Global Unauthenticated API | 60 req / min | IP Address |

---

## 5. WebSocket & Real-Time Security

- **Handshake Authentication**: Daphne ASGI inspects standard `Authorization: Bearer <jwt>` headers during HTTP upgrade handshake.
- **Continuous Presence & Membership Validation**: WebSocket connections verify active participation on connection. If a user is removed or leaves a goal, their active socket is closed immediately with a policy violation code (`4403`).
- **Presence Sanitization**: Ephemeral chat presence is tracked in Redis with a 60-second sliding TTL, preventing zombie presence indicators.

---

## 6. Logging Sanitization & Zero-Leak Guarantee

- **Automated Parameter Filtering**: Custom logging filters sanitize incoming query strings, request payloads, and exception contexts.
- **Protected Fields**: `password`, `token`, `secret`, `authorization`, `gemini_api_key`, `credentials_json`, `refresh_token`, `pepper`, and `encryption_key` are masked across all stdout streams.
- **Safe Configuration Audit**: The `check_production` management command audits system health reporting only `CONFIGURED`/`MISSING` status without printing secret strings.
