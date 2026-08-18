# Promise — Authentication Design

**Status:** Design complete. Implementation has not started.

| Label | Meaning |
|---|---|
| **DECIDED** | Locked for future implementation. Do not silently change. |
| **DEFERRED** | Designed enough to avoid a rewrite, not part of the first auth implementation. |
| **NOT IMPLEMENTED** | No code, packages, models, migrations, or APIs exist yet. |

This document is the source of truth for authentication. It supersedes the placeholder in `docs/ARCHITECTURE.md` §18 (“the exact token strategy can be selected during backend implementation”).

Do not treat this file as an implementation checklist that has already been built. Every mechanism below is **NOT IMPLEMENTED** until a later Phase 4 task says otherwise.

---

## 1. Authentication goals

Promise is an Android-first accountability product. Authentication must be strong enough for a Play Store app, private user data, and later iOS/web clients, without forcing a replacement of `users.User`.

**DECIDED** goals:

1. Email + password sign-up and sign-in first.
2. Short-lived JWT access tokens.
3. Refresh tokens tracked as server-side sessions.
4. Refresh-token rotation on every successful refresh.
5. Refresh-token reuse detection with session revocation.
6. Revoke one session/device.
7. Revoke all sessions for a user.
8. Passwords stored only via Django’s password hashing API (`set_password` / `check_password`).
9. Identity model that can later add Google and Apple without duplicating users or adding `google_id` / `apple_id` columns on `User`.
10. Authentication (“who are you?”) stays separate from authorization (“what may you do?”).
11. PostgreSQL remains the durable source of truth for users and sessions.
12. Redis is not the session database.
13. Kafka is not on the login request path.

**Out of scope for the first implementation** (**DEFERRED**): social login, email-sending, password-reset delivery, rate-limit enforcement, Kafka publication, Android code.

---

## 2. Recommended architecture

**DECIDED:** Option B — JWT access tokens + server-side rotating refresh sessions.

```text
Android
  │  HTTPS JSON
  │  Authorization: Bearer <access JWT>
  ▼
Django + DRF
  ├── verify JWT signature, iss, aud, exp, typ=access
  ├── Redis denylist lookup by sid when Redis is up (revocation hint)
  ├── request.user = users.User
  └── request.auth = session id
        │
        ├── PostgreSQL     users, auth sessions, future identities
        ├── Redis          rate limits, refresh lock, access denylist
        └── Kafka          later: user.created and security events via outbox
```

### Why this shape

| Piece | Role |
|---|---|
| Access JWT | Proves authentication for ~15 minutes without a PostgreSQL hit on every API call |
| Opaque refresh token | Bound to one `AuthSession` row; rotated; reusable-theft detectable |
| `AuthSession` in PostgreSQL | Source of truth for “this device is still allowed to mint access tokens” |
| Redis denylist of `sid` | Makes logout take effect for access tokens before `exp` |
| Django password hashers | Password storage; never our own crypto |

### What we will not do

- Stateless refresh JWTs with no server session (cannot revoke a device reliably).
- Opaque access tokens that must hit PostgreSQL on every request (unnecessary latency for a modular monolith that already has short-lived JWTs).
- Cookie/session authentication for Android (CSRF surface, poor fit for Retrofit).
- `djangorestframework-simplejwt` as the session model (its refresh/blacklist model is not rotation + reuse detection + per-device sessions). Issuing JWTs with PyJWT (or equivalent) plus our own session table is the intended path.

**NOT IMPLEMENTED.** No JWT library is installed today (`backend/pyproject.toml` has Django, DRF, django-environ, psycopg only).

---

## 3. Token lifecycle

### 3.1 Access token

**DECIDED**

| Property | Value |
|---|---|
| Format | Signed JWT (see §14) |
| Lifetime | **15 minutes** (`exp = iat + 900`) |
| Transport | `Authorization: Bearer <token>` |
| Persistence on Android | **Not persisted.** Memory only. |
| Server storage | Not stored. Optionally `sid` is denylisted in Redis on revoke. |
| Use | All protected APIs |

**Why short-lived:** a stolen access token is a bearer credential. Fifteen minutes bounds the damage if the phone is compromised, logs leak a header, or logout cannot reach every replica instantly. It is also short enough that “logout” plus a Redis `sid` denylist is effective in practice.

**Why not 5 minutes:** Android process death, flaky networks, and parallel API calls would refresh constantly. More refresh traffic means more rotation races.

**Why not 60 minutes:** logout and “stolen phone” would leave a working API credential for too long unless every request checked PostgreSQL (which is Option C).

Clock skew: validators should allow **30 seconds** of leeway.

### 3.2 Refresh token

**DECIDED**

| Property | Value |
|---|---|
| Format | Opaque: `{session_id}.{secret}` |
| `session_id` | UUID of `AuthSession` |
| `secret` | ≥ 256 bits, cryptographically random, URL-safe |
| Server representation | HMAC-SHA256 of `secret` with a server pepper; **never** the raw secret |
| Lifetime | **30 days sliding** from last successful refresh |
| Absolute cap | **90 days** from session `created_at` |
| Rotation | Every successful refresh issues a new secret; old secret becomes invalid |
| Transport | JSON body only, never `Authorization` (that header is for access tokens) |
| Persistence on Android | Keystore-backed encrypted storage (see §8) |

**Why longer-lived:** the user should not type a password every day. A habit/accountability app is opened in bursts, including after a week away. Thirty days idle matches that. Ninety days absolute prevents a forgotten stolen session from lasting forever.

**Why not a JWT refresh token:** reuse detection needs a stable session id plus a rotatable secret. An opaque token with `session_id` in the prefix lets the server load the row even when the secret is wrong (the reuse case). A signed refresh JWT is possible later; it is not required.

**Why not store the raw refresh token:** a database dump would become a bag of valid logins. HMAC with a pepper means the dump is not sufficient without the pepper.

### 3.3 Authentication session / device

One login (or register-and-login) creates one `AuthSession`. That row is the device/session. Access tokens carry `sid`. Refresh tokens name the same id.

Recommended defaults vs trade-offs:

| Setting | Default | If shorter | If longer |
|---|---|---|---|
| Access TTL | 15 min | More refreshes, more rotation races | Larger stolen-access window |
| Refresh idle TTL | 30 days | Vacation / infrequent users re-login | Stolen refresh lives longer (rotation still helps) |
| Absolute session cap | 90 days | More password prompts | Long-lived device sessions |
| Reuse grace | 30 seconds | Legitimate Android retries look like theft | Replay window after rotation |

---

## 4. Session lifecycle

**DECIDED** conceptual model (not created yet).

`AuthSession` should inherit `apps.core.models.BaseModel` (UUID `id`, `created_at`, `updated_at`). `users.User` stays as it is and does **not** inherit `BaseModel`.

```text
AuthSession
├── id                    UUID  (session id, also JWT sid)
├── user                  FK users.User
├── refresh_token_hmac    current secret’s HMAC (not plaintext)
├── previous_token_hmac   nullable, for retry grace only
├── previous_rotated_at   nullable
├── created_at
├── last_used_at
├── expires_at            sliding idle expiry
├── absolute_expires_at   created_at + 90 days
├── revoked_at            null = active
├── revoked_reason        logout | logout_all | reuse | password_change | admin | expired
├── device_name           client-supplied, untrusted, length-capped
├── device_id             optional install id, untrusted
├── platform              android | ios | web | unknown
├── app_version           optional, untrusted
├── ip_address            optional last-seen
└── user_agent            optional last-seen
```

IP and User-Agent are telemetry for later “new device” UX and abuse investigation. They are **not** authorization factors. Clients can spoof them.

**Active session** means:

```text
revoked_at IS NULL
AND now < expires_at
AND now < absolute_expires_at
AND user.is_active is True
```

**NOT IMPLEMENTED.** Do not add this model or a migration until Phase 4.

### Session state machine

```text
                  login / register
                         │
                         v
                    +----------+
                    |  ACTIVE  |
                    +----+-----+
                         |
         refresh (rotate hmac, extend expires_at)
                         |
          +--------------+------------------+
          |              |                  |
          v              v                  v
       LOGOUT      LOGOUT_ALL         REUSE DETECTED
     (this row)   (all user rows)     (this family/row)
          |              |                  |
          v              v                  v
                    +----------+
                    | REVOKED  |
                    +----------+

PASSWORD_CHANGE / ACCOUNT_DISABLE → all sessions REVOKED
IDLE/ABSOLUTE EXPIRY → unusable (treat as revoked on refresh)
```

---

## 5. Refresh-token rotation

**DECIDED**

On `POST /api/v1/auth/refresh/`:

```text
1. Parse {session_id}.{secret}. Reject malformed tokens as TOKEN_INVALID.
2. Load AuthSession by session_id. If missing → TOKEN_INVALID.
3. If session not active → SESSION_REVOKED or TOKEN_EXPIRED (see §9).
4. HMAC(secret) and compare to refresh_token_hmac (constant time).
5. If match:
     - generate new_secret
     - previous_token_hmac ← old hmac
     - previous_rotated_at ← now
     - refresh_token_hmac ← HMAC(new_secret)
     - last_used_at ← now
     - expires_at ← now + 30 days (do not exceed absolute_expires_at)
     - persist in one PostgreSQL transaction
     - return new access JWT + new refresh token
6. If no match → reuse / retry path (§6)
```

The client **must** replace both tokens. An access token is never used to mint another access token.

Hold a **per-session refresh lock** in Redis (`promise:lock:auth:refresh:{session_id}`) during steps 4–5 so two parallel refreshes cannot both succeed and then each other’s result look like reuse. Android must also serialize refresh in-process (single OkHttp Authenticator / mutex).

If Redis is down, the lock is skipped and the grace window in §6 remains the safety net. PostgreSQL is still the source of truth.

---

## 6. Reuse detection

**DECIDED**

A stolen refresh token is usually used **after** the legitimate client has already rotated. The thief presents a secret that is no longer `refresh_token_hmac`.

```text
HMAC(secret) != current hmac
        │
        ├── matches previous_token_hmac
        │     AND now - previous_rotated_at <= 30 seconds
        │           → treat as retry, return the already-issued current tokens
        │              (do not rotate again)
        │
        ├── matches previous_token_hmac but grace expired
        │           → REUSE
        │
        └── matches neither
                  → REUSE (unknown secret for a known session_id)
```

Because the token contains `session_id`, the server can always load the family/row even when the secret is wrong.

**On REUSE:**

1. Set `revoked_at` on **that session** immediately.
2. Add `sid` to the Redis access-token denylist with TTL ≥ remaining access TTL (15 minutes).
3. Log a security event (session id, user id, not the token).
4. Return the same client-visible error as an invalid refresh (`AUTHENTICATION_FAILED` / `TOKEN_INVALID`). Do **not** return `TOKEN_REUSE_DETECTED` to the client (it teaches attackers).
5. **DEFERRED:** revoke all of the user’s sessions, email the user, or publish Kafka. First implementation revokes the compromised session. Escalating to logout-all is a product/security choice once notifications exist.

**Why a 30-second grace:** Android may refresh, the server rotates, then the client never stores the new token (process kill, 5xx after commit, timeout). The next retry would look identical to theft. Grace + storing `previous_token_hmac` makes refresh **idempotent** for that window.

**NOT IMPLEMENTED.**

---

## 7. Logout behavior

**DECIDED**

### Logout this device — `POST /api/v1/auth/logout/`

Identifies the session from the **refresh token** in the body (works even if the access token already expired).

```text
validate refresh token → load session
  → revoked_at = now, reason = logout
  → denylist sid in Redis (TTL ≥ access TTL)
  → 204 No Content
```

If the session is already revoked or unknown, still return **204**. Logout is idempotent. Do not leak whether a token was valid.

### Logout all devices — `POST /api/v1/auth/logout-all/`

Requires a valid **access** token (user is authenticated).

```text
UPDATE AuthSession
   SET revoked_at = now, revoked_reason = logout_all
 WHERE user_id = request.user.id
   AND revoked_at IS NULL
→ denylist every affected sid
→ 204 No Content
```

Access tokens for other devices remain cryptographically valid until `exp` **unless** those `sid` values are denylisted. That is why access TTL is 15 minutes and why logout writes Redis.

### Password change / reset / account disable

Revoke **all** sessions in the same PostgreSQL transaction as the password update. Same denylist fan-out. The user signs in again and gets a new session.

### Access token after logout

Without a denylist, a stolen access token works until `exp`. With denylist-on-revoke, subsequent API calls fail with `UNAUTHENTICATED` as soon as Redis is checked. If Redis is unavailable, verification **fails open** for access tokens (valid JWT still accepted until `exp`). Refresh always consults PostgreSQL.

This matches the platform rule: Redis loss must not take down core tracking. A 15-minute residual access window in a Redis outage is the accepted trade-off.

---

## 8. Android token storage

**NOT IMPLEMENTED.** No Android auth code in this phase.

**DECIDED** storage policy:

| Secret | Where | Persist? |
|---|---|---|
| Access token | Process memory (auth repository / OkHttp interceptor) | **No** |
| Refresh token | Keystore-backed encrypted storage | **Yes** |
| Password | Never stored | — |

**Do not** put tokens in plaintext `SharedPreferences`, unencrypted DataStore, Room, logs, crash reports, or screenshots of debug UIs.

### Three layers (what they actually are)

**App memory**

- The access token lives in RAM associated with the app process.
- It disappears on process death (common on Android).
- It is not written to disk, so disk inspection and backup do not capture it.
- Cold start: read the refresh token from encrypted storage, call `/auth/refresh/`, put the new access token in memory.

**Encrypted local storage**

- A ciphertext blob on disk (EncryptedSharedPreferences, EncryptedFile, or equivalent).
- Useful for the refresh token so the user stays signed in across process death and reboots.
- Encryption at rest is only as strong as the key that wraps it.

**Android Keystore-backed protection**

- The wrapping key is created in the Android Keystore (TEE / StrongBox when available).
- The raw key material is not supposed to leave secure hardware.
- The app uses that key to AES-GCM encrypt the refresh token blob.
- This is what “do not use SharedPreferences for secrets” is pointing at: platform keystore wrapping, not an XML preferences file of bearer tokens.

At implementation time, use the current Jetpack-recommended Keystore + AES-GCM approach. EncryptedSharedPreferences has been the usual wrapper; libraries deprecate. The requirement is **Keystore-backed AES-GCM**, not a specific artifact name.

**DEFERRED:** biometric unlock before reading the refresh token. Right for a banking app; extra friction for a daily check-in app. Revisit if we add a high-sensitivity vault.

**Future iOS:** Keychain, not UserDefaults. Same split: access in memory, refresh in Keychain.

**Future web:** do **not** put refresh tokens in `localStorage`. That client needs a separate cookie/BFF decision. Android remains header-based Bearer.

---

## 9. API contracts

**NOT IMPLEMENTED.** Paths are reserved. Register them **before** the `/api/v1/<path:resource>` catch-all.

All APIs under `/api/v1/`. Trailing slashes required (Django/`health/` convention).

Shared success fields:

```json
{
  "user": {
    "id": "uuid",
    "email": "alex@example.com",
    "name": "Alex",
    "timezone": "UTC",
    "created_at": "2026-08-18T18:00:00Z"
  },
  "tokens": {
    "access_token": "<jwt>",
    "refresh_token": "<session_id>.<secret>",
    "token_type": "Bearer",
    "expires_in": 900
  }
}
```

Never return `password`, password hashes, `is_superuser`, or HMAC values.

`email_verified` is **not** in the first `/me/` or register/login bodies. Add it only when `email_verified_at` exists on `User`.

Optional device object on register/login (untrusted, length-capped):

```json
{
  "device": {
    "name": "Pixel 8",
    "platform": "android",
    "app_version": "0.1.0",
    "device_id": "<install-scoped id>"
  }
}
```

Error envelope (already implemented in `config.exceptions`):

```json
{
  "error": {
    "code": "SOME_ERROR_CODE",
    "message": "Human readable message"
  }
}
```

Validation:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {}
  }
}
```

Existing codes to reuse: `VALIDATION_ERROR`, `INVALID_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `RATE_LIMITED`, `INTERNAL_SERVER_ERROR`.

Auth-specific codes:

| Code | When |
|---|---|
| `AUTHENTICATION_FAILED` | Login credentials wrong, or refresh token rejected (generic) |
| `EMAIL_ALREADY_EXISTS` | Register and the email is taken |
| `ACCOUNT_DISABLED` | Password was correct but `is_active=False` |
| `TOKEN_INVALID` | Malformed/forged access or refresh token |
| `TOKEN_EXPIRED` | Refresh session idle or absolute expiry |
| `SESSION_REVOKED` | Session revoked; client must re-authenticate |

`UNAUTHENTICATED` remains “no/invalid **access** credentials on a protected route.” Do not use it as the login-failure code.

---

### `POST /api/v1/auth/register/`

| | |
|---|---|
| **Purpose** | Create a user with email + password and start a session (auto-login). |
| **Auth** | None |
| **Success** | `201 Created` — `user` + `tokens` |
| **Request** | `{ "email", "password", "name", "timezone?", "device?" }` |

`timezone` defaults to `"UTC"` (already the User default).

| Failure | HTTP | Code |
|---|---|---|
| Missing/invalid fields, weak password | 400 | `VALIDATION_ERROR` |
| Duplicate email | 409 | `EMAIL_ALREADY_EXISTS` |
| Too many attempts | 429 | `RATE_LIMITED` |

Emails are stored normalized: strip, lowercase entire address (stronger than Django’s domain-only `normalize_email`) so `Alex@x.com` and `alex@x.com` are the same account. Duplicate races: unique constraint + `IntegrityError` → 409.

**DECIDED:** 409 on duplicate email. That is account enumeration on the register path. Mitigation is rate limiting, not pretending registration succeeded. Login and password-reset still do not reveal account existence.

---

### `POST /api/v1/auth/login/`

| | |
|---|---|
| **Purpose** | Create a new session for an existing user. |
| **Auth** | None |
| **Success** | `200 OK` — `user` + `tokens` |
| **Request** | `{ "email", "password", "device?" }` |

Always run a password hash check, including when the email is unknown (dummy hash) so timing does not enumerate accounts.

| Failure | HTTP | Code |
|---|---|---|
| Unknown email or wrong password | 401 | `AUTHENTICATION_FAILED` — message: `"Invalid email or password."` |
| Correct password, `is_active=False` | 403 | `ACCOUNT_DISABLED` (only after a successful password check) |
| Validation | 400 | `VALIDATION_ERROR` |
| Too many attempts | 429 | `RATE_LIMITED` |

Do not say “email not found” or “wrong password” separately.

---

### `POST /api/v1/auth/refresh/`

| | |
|---|---|
| **Purpose** | Rotate refresh token; mint a new access token. |
| **Auth** | None (refresh token in body) |
| **Success** | `200 OK` — `{ "tokens": { ... } }` only. No `user` object. |
| **Request** | `{ "refresh_token": "<session_id>.<secret>" }` |

| Failure | HTTP | Code |
|---|---|---|
| Malformed token, unknown `session_id`, hmac mismatch, or reuse after internal revoke | 401 | `TOKEN_INVALID` |
| Idle or absolute expiry | 401 | `TOKEN_EXPIRED` |
| Session revoked (logout) | 401 | `SESSION_REVOKED` |
| Validation | 400 | `VALIDATION_ERROR` |
| Rate limit | 429 | `RATE_LIMITED` |

On reuse, revoke first, then return `TOKEN_INVALID`. Login is the only endpoint that uses `AUTHENTICATION_FAILED`.

---

### `POST /api/v1/auth/logout/`

| | |
|---|---|
| **Purpose** | Revoke this device/session. |
| **Auth** | Refresh token in body; access token not required |
| **Success** | `204 No Content` empty body |
| **Request** | `{ "refresh_token": "..." }` |

Always 204 if the body is syntactically valid. Invalid JSON still 400 `INVALID_REQUEST` / `VALIDATION_ERROR`.

---

### `POST /api/v1/auth/logout-all/`

| | |
|---|---|
| **Purpose** | Revoke every session for the authenticated user. |
| **Auth** | Required: `Authorization: Bearer <access>` |
| **Success** | `204 No Content` |
| **Request** | Empty object `{}` |

| Failure | HTTP | Code |
|---|---|---|
| Missing/invalid/expired access | 401 | `UNAUTHENTICATED` / `TOKEN_INVALID` / `TOKEN_EXPIRED` |

If access is expired, the client refreshes (or logs in) then calls logout-all.

---

### `GET /api/v1/auth/me/`

| | |
|---|---|
| **Purpose** | Return the current user. |
| **Auth** | Required: access token |
| **Success** | `200 OK` — `{ "user": { ... } }` no tokens |
| **Request** | Empty |

| Failure | HTTP | Code |
|---|---|---|
| No/invalid access | 401 | `UNAUTHENTICATED` |
| User deactivated after token issued | 401 | `UNAUTHENTICATED` (treat as unauthenticated, not 403, so the client clears tokens) |

---

### Future password APIs (**DEFERRED**, designed)

#### `POST /api/v1/auth/password/forgot/`

| | |
|---|---|
| **Purpose** | Start a reset. Always looks successful. |
| **Auth** | None |
| **Success** | `202 Accepted` — `{ "message": "If an account exists for this email, password reset instructions will be sent." }` |
| **Request** | `{ "email": "..." }` |

Do not reveal whether the email exists. Send mail only when a user exists. Rate-limit per IP and per email hash.

Reset token: random ≥ 256-bit secret, **HMAC stored in PostgreSQL** with `expires_at` ~ **1 hour**, single use. Redis is only for rate limits, not the token itself (Redis flush must not create confusion about whether a token existed; Postgres expiry is enough).

#### `POST /api/v1/auth/password/reset/`

| | |
|---|---|
| **Purpose** | Set a new password given a one-time token. |
| **Auth** | None (the reset token is the capability) |
| **Success** | `204 No Content` — no session issued; client must login |
| **Request** | `{ "token": "...", "password": "..." }` |

Invalid/expired/used token: `400` `PASSWORD_RESET_INVALID` with a generic message. Same message for all failure reasons except `VALIDATION_ERROR` on a weak new password.

On success: hash new password, revoke **all** sessions, consume token.

**NOT IMPLEMENTED.** No email provider, no reset table.

---

## 10. Authorization model

**DECIDED**

Authentication answers **who are you?**  
Authorization answers **what are you allowed to do?**

```text
Valid access JWT
    → request.user is set
    → NOT enough to read or mutate a resource

Protected resource
    → load by id
    → 404 if not visible to this user (do not 403 on other users’ objects)
    → require ownership OR an explicit membership/permission row
```

Rules:

1. Never trust client `user_id`, `owner_id`, or a UUID in the path as proof of access.
2. UUID v4 primary keys reduce casual enumeration; they are **not** an authorization mechanism (`docs/BUILD_PROGRESS.md` already states this).
3. Default DRF permission for product APIs: `IsAuthenticated`. `GET /api/v1/health/` stays public.
4. Querysets for list endpoints are filtered to resources the user may see **before** serialization.
5. Shared promises, challenges, and accountability relationships use explicit membership (future tables), not “they guessed the UUID.”
6. Staff/superuser (`PermissionsMixin` on `User`) is for Django admin later, not for Android feature flags. Do not put roles in the JWT.
7. Permissions are resolved **server-side at request time**. Sharing graphs change; embedding ACLs in a 15-minute JWT would be stale and forgeable from the client’s point of view if we ever trusted those claims without a check.

Future object rule (commitments, goals, notifications, etc.):

```text
if resource.user_id != request.user.id
   and not has_explicit_grant(request.user, resource):
       raise NotFound    # same as missing — no existence leak
```

**NOT IMPLEMENTED** for domain objects (they do not exist yet).

---

## 11. Password security

**DECIDED**

- Passwords are set with `User.set_password` and checked with `check_password` / `authenticate`.
- Hashes live only in `users_user.password` (Django’s hasher string).
- APIs never return hashes.
- Prefer **Argon2id** as the first hasher when auth is implemented (Django supports it; add the extra when coding, not now). Keep PBKDF2 in `PASSWORD_HASHERS` so existing hashes still verify.
- Enable Django `AUTH_PASSWORD_VALIDATORS`: minimum length 8, not a common password, not entirely numeric, not too similar to email/name.
- No composition theater (forced uppercase/symbols). Length + breached-common list is the NIST-aligned default.
- Login failures: `"Invalid email or password."` only.
- Registration duplicates: 409 `EMAIL_ALREADY_EXISTS`.
- Password reset: no account-existence leak (§9).
- Dummy password verify when the user does not exist (timing).
- After password change/reset: revoke all sessions.

**DEFERRED:** HaveIBeenPwned / password-breach API.

**NOT IMPLEMENTED.** `AUTH_PASSWORD_VALIDATORS` is not in `config.settings.base` today. Do not add it in this design task.

---

## 12. Email verification recommendation

**DECIDED (policy):** **Soft verification.** Do not block MVP onboarding on a clicked email link.

| Concern | How soft verification treats it |
|---|---|
| Security | Email is not a proven inbox until verified; password reset still only helps someone who can read the inbox |
| Onboarding friction | User can create commitments/goals immediately after register |
| Notifications | Primary channel is FCM, not email |
| Password recovery | Reset email **is** inbox proof; does not require a prior verification flag |
| Fake accounts | Rate limits + later abuse tools; email verify is a weak bot deterrent by itself |

**DECIDED fields/behavior for later (no migration now):**

- Add nullable `email_verified_at` on `User` (or a boolean derived from it).
- Send a verification message after register once email sending exists.
- `GET /me/` includes `email_verified` only after that field exists.
- Google/Apple verified emails can set `email_verified_at` when those providers are added.

**DEFERRED hard requirement:** require verification before emailing other people (shared promises, accountability invites). Personal tracking does not wait.

**NOT IMPLEMENTED.**

---

## 13. Social identity architecture

**DECIDED:** add a separate `UserIdentity` table when social login is built. Do **not** put `google_id` or `apple_id` on `User`.

```text
UserIdentity  (inherits BaseModel when implemented)
├── user              FK users.User
├── provider          google | apple | ...
├── provider_user_id  stable subject from the provider
├── created_at
└── updated_at

UNIQUE (provider, provider_user_id)
UNIQUE (user, provider)   # one Google, one Apple per user
```

### Why not columns on User

- Each new provider is a migration and a pile of nullable columns.
- A user can link more than one provider.
- Provider subject and email are different identifiers; email can change.
- Account linking is a row insert, not a User rewrite.
- Password-only users, social-only users (`set_unusable_password`), and both, stay one `User`.

### Linking algorithm (future)

```text
Verify provider ID token (signature, iss, aud, exp, nonce for Apple)
  → lookup UserIdentity(provider, sub)
      → found: issue AuthSession for that user
  → else lookup User by verified provider email
      → found: only auto-link if the provider asserts verified email
               otherwise require authenticated link flow (takeover prevention)
      → missing: create User with unusable password + UserIdentity
```

Do not create a second User for the same person.

**NOT IMPLEMENTED.** Do not add `UserIdentity` now.

---

## 14. JWT claims

**DECIDED** access token payload (no PII):

| Claim | Required | Value |
|---|---|---|
| `sub` | yes | User UUID string |
| `sid` | yes | AuthSession UUID |
| `jti` | yes | Unique id per access token, for logs and correlation only. Revocation uses `sid`, not `jti`. |
| `iat` | yes | Issued at |
| `exp` | yes | `iat + 900` |
| `iss` | yes | `promise-api` |
| `aud` | yes | `promise-client` |
| `typ` | yes | `access` |

Header: `alg`, `kid` (key id for rotation).

**Do not put in the JWT:** email, name, timezone, password, roles, permissions, phone, device IP.

**Roles/permissions:** **server-side only.** `is_staff` is not a product ACL. Sharing/membership will change faster than token TTL, and clients must not be trusted to honor embedded roles.

**Algorithm (first implementation):** **HS256** with a dedicated `JWT_SIGNING_KEY` (not `DJANGO_SECRET_KEY`), plus `kid`. Keep current and previous keys in env so rotation does not log everyone out for 15 minutes.

**DEFERRED:** RS256/JWKS when a second service must verify tokens without the signing secret.

**Key rotation procedure:**

1. Introduce `JWT_SIGNING_KEY_NEW` with a new `kid`.
2. Sign new tokens with the new kid; verify accepting both kids.
3. After > 15 minutes, drop the old key.

Refresh tokens are **not** JWTs.

**NOT IMPLEMENTED.** No keys, no `kid`, no PyJWT.

---

## 15. Redis usage

**DECIDED**

| Use | Redis? | Source of truth |
|---|---|---|
| AuthSession, users, identities | **No** | PostgreSQL |
| Login / register / refresh / forgot rate limits | **Yes** | Counters with TTL |
| Per-session refresh lock | **Yes** | Lock only; session still in Postgres |
| Access `sid` denylist after logout | **Yes** | Hint; Postgres `revoked_at` is truth for refresh |
| Password reset tokens | **No** | PostgreSQL hashed token + expiry |
| Email verification tokens | **No** (when built) | PostgreSQL |
| Replay detection of refresh | **No** | Postgres hmac + previous hmac |
| Caching `/me/` | **No** (not worth it) | — |

Keys (follow existing `promise:` prefix):

```text
promise:ratelimit:ip:{ip}:auth:login
promise:ratelimit:email:{email_hash}:auth:login
promise:ratelimit:ip:{ip}:auth:register
promise:ratelimit:ip:{ip}:auth:forgot
promise:lock:auth:refresh:{session_id}
promise:auth:denylist:sid:{session_id}    TTL ≥ access TTL
```

Hash emails in rate-limit keys so Redis is not a plaintext email directory.

If Redis is down: skip rate limits and denylist (**fail open** for availability in local/dev and for core APIs); **never** skip PostgreSQL session checks on refresh. Do not over-engineer local dev: missing Redis must not block `runserver` for health checks, as today.

**DEFERRED:** actually wiring a Redis client and throttles.

---

## 16. Kafka events

**DECIDED** when the outbox exists: publish **user lifecycle** events, not every login.

| Event | Publish? | Why |
|---|---|---|
| `user.created` | **Yes** (name already in `PROJECT_PLAN.md`) | Welcome, analytics, provision defaults |
| `user.email_verified` | **Yes**, when verification exists | Unlock later sharing features |
| `user.password_changed` | **Yes** | Security notification worker |
| `user.logged_in` | **No** for MVP | High volume, IP/device in payload is sensitive; use metrics |
| `user.logged_out` | **No** for MVP | Same |
| Refresh / reuse | **No** Kafka | Logs + metrics; optional later security notify |

Payloads must not contain passwords, tokens, or hashes. `user_id` is the partition key (existing convention).

Publication path: **transactional outbox**, never “save user then emit to Kafka” in the request thread.

**NOT IMPLEMENTED.** No outbox, no Kafka client in Django.

---

## 17. Security considerations

**DECIDED** requirements. Enforcement is **DEFERRED** except as noted.

| Topic | Design |
|---|---|
| Brute-force login | Redis throttle per IP and per email hash; generic 401 |
| Rate limiting | Same; `429 RATE_LIMITED` already mapped in `config.exceptions` |
| Credential stuffing | Throttles + common-password validator; breach list later |
| Refresh theft | HMAC at rest, rotation, reuse revoke, Keystore on device |
| Refresh replay | Rotation + grace; second use outside grace revokes |
| Account enumeration | Generic login/reset; register 409 is accepted and throttled |
| Password-reset abuse | Throttle forgot; 1-hour single-use tokens; generic 202 |
| Token expiration | 15 min access; 30d / 90d session |
| Session revocation | Postgres + Redis denylist |
| HTTPS | **Required in production.** Local emulator HTTP is allowed. |
| Secrets | Env / secret manager. Never Git. Dedicated JWT key and refresh pepper. |
| JWT key rotation | Dual `kid` HS256 keys (§14) |
| CORS | Irrelevant to Android. Future web: explicit origin allowlist, never `*` with credentials. |
| CSRF | Android uses Bearer headers, not cookies → CSRF does not apply. If a cookie web client appears, that client needs SameSite + CSRF; do not switch Android to cookies. |
| `DEBUG` | Must be false in production; tokens must not appear in error bodies (existing handler already hides internals). |

Local development: HTTP, `DEBUG=true`, no Redis rate limits required. Do not build a local IdP.

---

## 18. Threat model

| Threat | Attack | Mitigation |
|---|---|---|
| Stolen password | Credential stuffing, phishing | Hashing, throttles, later 2FA **DEFERRED** |
| Stolen access JWT | Log leak, rooted device, proxy | 15 min TTL, HTTPS, memory-only on Android, denylist on logout |
| Stolen refresh token | Backup extraction, malware | Keystore wrapping, HMAC in DB, rotation, reuse revoke |
| Refresh race / double submit | Parallel 401 handlers | Android mutex + Redis lock + 30s grace |
| Session fixation | Attacker sets a token | Server generates all secrets; clients never choose `sid` |
| Account takeover via social email | Unverified social email matches victim | Auto-link only with provider-verified email (**DEFERRED** social) |
| IDOR on promises/goals | Guess UUID | Server-side ownership; 404 not 403 |
| Enumeration | Login/forgot timing and messages | Dummy hash, generic copy |
| CSRF | Browser cookie APIs | Not used for Android Bearer |
| MITM | HTTP in production | HTTPS only in production |
| DB backup leak | Read `refresh_token_hmac` | Peppered HMAC; still rotate pepper as a secret |
| Insider / log leak | Tokens in logs | Log `sid` / `user_id` / `jti`, never raw tokens |
| XSS on future web | Steal localStorage refresh | Do not use this token model on the web without a BFF/cookie design |

---

## 19. Alternatives considered

### Option A — JWT access + stateless refresh tokens

Refresh is also a JWT (or a long-lived JWT). Server stores little or nothing.

- **Pros:** Simple, easy to scale, no session table.
- **Cons:** Cannot revoke one device without a denylist that grows into a session store anyway. Reuse detection needs state. Logout-all is “wait until expiry” or a user-wide `token_version` that invalidates all devices at once only (no per-device revoke). **Rejected.**

### Option B — JWT access + server-side rotating refresh sessions

**Recommended and DECIDED.**

- **Pros:** Short-lived stateless-ish API auth; per-device revoke; rotation + reuse; fine for Android, iOS, and a future token-based web API; PostgreSQL-sized session table is tiny compared with commitments/check-ins.
- **Cons:** Refresh hits the database; must handle rotation races; slightly more code than Option A.
- **Fits Promise:** modular monolith, Redis already planned, Play Store security bar, no rewrite when adding devices.

### Option C — Opaque access + opaque server-side sessions

Every API call looks up a session in Redis or PostgreSQL.

- **Pros:** Instant revoke with no denylist; tokens are random blobs.
- **Cons:** PostgreSQL/Redis on the hot path for every check-in tap; harder to verify tokens in future workers without a round trip; reinventing Django sessions for a mobile API.
- **Rejected for now.** Revisit only if we need immediate access revoke without Redis and without 15-minute residual risk (e.g. a much higher-sensitivity product).

### Other rejects

| Idea | Why not |
|---|---|
| Django session cookie | CSRF, cookie jar on Android, not a clean Retrofit fit |
| `simplejwt` as the architecture | Blacklist ≠ rotating server sessions with reuse grace |
| Firebase Auth as source of truth | Splits identity from PostgreSQL User; harder authorization joins |
| Embed roles in JWT | Stale ACL, client-spoof temptation, sharing model will be relational |

---

## 20. Final decisions

| Topic | Decision | Label |
|---|---|---|
| Architecture | Option B | **DECIDED** |
| Access token | JWT, 15 min, memory on Android | **DECIDED** |
| Refresh token | Opaque `{sid}.{secret}`, HMAC in Postgres, 30d sliding / 90d cap | **DECIDED** |
| Rotation | Every refresh | **DECIDED** |
| Reuse | Revoke session; generic client error; 30s retry grace | **DECIDED** |
| Logout | Refresh body → one session; access → all sessions | **DECIDED** |
| Signing | HS256, dedicated key, `kid` rotation | **DECIDED** |
| RS256/JWKS | When a second verifier appears | **DEFERRED** |
| Permissions in JWT | No | **DECIDED** |
| Authorization | Ownership or explicit grant; UUIDs are not capability tokens | **DECIDED** |
| Password hashing | Django API; Argon2id preferred at implement time | **DECIDED** |
| Email verification | Soft; do not block MVP | **DECIDED** |
| Social | `UserIdentity` table later; no provider ids on User | **DECIDED** |
| Redis | Limits, locks, denylist only | **DECIDED** |
| Kafka | `user.created` / `user.email_verified` / `user.password_changed`; not login/logout | **DECIDED** |
| CSRF | N/A for Bearer Android client | **DECIDED** |
| HTTPS | Production required | **DECIDED** |
| Rate limits, email, reset, social, Android storage code | After this design | **DEFERRED** / **NOT IMPLEMENTED** |
| This whole system in code | — | **NOT IMPLEMENTED** |

`users.User` remains: UUID pk, email `USERNAME_FIELD`, Django password field, timezone, `is_active`, `is_staff`, timestamps. Future additive columns: `email_verified_at` only when verification is built.

---

## 21. Future implementation sequence

Do **not** start this sequence in the design task. When Phase 4 begins, implement in this order so tests can gate each slice.

1. Settings: `AUTH_PASSWORD_VALIDATORS`, JWT key/pepper env placeholders, Argon2 hasher extra.
2. `AuthSession` model + migration (no APIs yet).
3. Token services: random refresh secret, HMAC, JWT issue/verify with `kid`.
4. `POST /register/` and `POST /login/` + tests (including duplicate email, generic login failure, no hash in JSON).
5. DRF authentication class; `IsAuthenticated` default; `health/` stays `AllowAny`.
6. `POST /refresh/` with rotation, grace, reuse revoke, Android-parallel test cases.
7. `POST /logout/` and `POST /logout-all/` + Redis denylist helper (skip if Redis down).
8. `GET /me/`.
9. Rate limiting on login/register/refresh (**DEFERRED** until Redis client exists, but keep tests’ 429 mapping).
10. Password forgot/reset once email exists.
11. `email_verified_at` + verification APIs.
12. `UserIdentity` + Google/Apple.
13. Outbox `user.created` / `user.password_changed`.
14. Android: Keystore refresh storage, in-memory access, OkHttp Authenticator mutex.

Each step stays inside `apps.users` until the module is large enough to split. Do not add Celery, Firebase Auth, or a new microservice.

---

## Appendix A — Request / response flows

### Register

```text
Android
  POST /api/v1/auth/register/  {email, password, name, timezone?, device?}
        │
        ▼
  validate → create User.set_password → create AuthSession
        │
        ▼
  201 { user, tokens.access_token, tokens.refresh_token }
        │
        ▼
  Android: refresh → Keystore; access → memory
```

### Login

```text
POST /api/v1/auth/login/  {email, password, device?}
  → dummy or real check_password
  → 401 AUTHENTICATION_FAILED  or  200 { user, tokens }
```

### Authenticated API call

```text
GET /api/v1/...
Authorization: Bearer <access>
  → verify JWT (sig, iss, aud, exp, typ)
  → if Redis denylist has sid → 401
  → request.user loaded by sub
  → permission/ownership check
```

### Refresh

```text
POST /api/v1/auth/refresh/  { refresh_token }
  → lock sid
  → verify hmac / grace / reuse
  → rotate
  → 200 { tokens }
Android replaces both tokens
```

### Logout this device

```text
POST /api/v1/auth/logout/  { refresh_token }
  → revoke session, denylist sid
  → 204
Android deletes Keystore refresh + memory access
```

### Logout all

```text
POST /api/v1/auth/logout-all/
Authorization: Bearer <access>
  → revoke all user sessions, denylist all sids
  → 204
```

---

## Appendix B — Mapping to current code (read-only)

| Current | Auth design impact |
|---|---|
| `users.User` | Keep; do not replace; additive fields later |
| `UserManager.set_password` | Already correct; keep using it |
| `REST_FRAMEWORK` empty authentication | Will become JWT class in Phase 4 |
| `AllowAny` default | Will become `IsAuthenticated` except health + auth entrypoints |
| Error handler | Reuse; add auth codes as they are raised |
| `/api/v1/health/` | Stays public, unchanged |
| `REDIS_URL` / `KAFKA_BOOTSTRAP_SERVERS` | Loaded, unused; auth will use Redis later, not Kafka on the request path |
| No JWT in `pyproject.toml` | Add only at implementation |

---

*End of authentication design. No authentication code is implemented by this document.*
