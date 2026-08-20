# Promise — Redis Product Layer Design

**Status:** Phase 7 product Redis layer **COMPLETE** (7.5 verified). Live: denylist (4.7), Lua counters (7.2), auth throttles (7.3), Goal/Commitment/check-in write throttles (7.4). Domain caches and new locks are **not** built. Authentication `sid` denylist is **not** rebuilt here.

| Label | Meaning |
|---|---|
| **DECIDED** | Locked for the first product Redis slice. Do not silently change. |
| **DEFERRED** | Designed enough to avoid a rewrite; not part of the first product Redis implementation. |
| **OPEN** | Needs a choice before that later slice is built. |
| **IMPLEMENTED** | Already running. Documented here so Phase 7 does not duplicate it. |

This document is the source of truth for Redis product keys and remaining Redis work (optional domain caches, locks, short-lived coordination). Implemented throttles and the denylist are recorded here so later slices do not invent a second convention.

It fills in the rate-limit **numbers and extra buckets** that `docs/AUTHENTICATION_DESIGN.md` §15 named but left unwired. It supersedes the “cache today’s dashboard / active goals / AI rate limits first” sketch in `docs/DEVELOPMENT.md` Phase 7 and the example keys in `docs/ARCHITECTURE.md` §9 / `docs/PROJECT_PLAN.md` §12.

It does **not** replace:

- `docs/AUTHENTICATION_DESIGN.md` §15 — denylist, fail-open Redis, refresh lock deferred
- `docs/GOAL_DESIGN.md` — progress/streak derived; **do not cache progress in Redis for MVP**
- `docs/COMMITMENT_DESIGN.md` — PostgreSQL source of truth for commitments
- `docs/OUTBOX_DESIGN.md` — Kafka is the async path; Redis is not an outbox

Do not treat caches or locks as built. Auth and mutation rate limits exist; domain caches and new locks do not until a later Phase 7 task says otherwise.

Do not modify the authentication denylist, Kafka, Android, or Docker memory settings to ship this design.

---

## 1. Redis responsibilities

**DECIDED:** Redis has four product jobs. Denylist and rate limits are live; caches and locks are not.

| Job | Status | Role |
|---|---|---|
| Access-session denylist | **IMPLEMENTED** (Phase 4.7) | Fast reject of `sid` after logout. Hint only. |
| Rate limiting | **IMPLEMENTED** (7.2 primitive; 7.3 auth; 7.4 mutations) | Fixed-window counters. Forgot/AI/notification buckets deferred. |
| Domain caching | **DEFERRED** | No Goal/Commitment/home cache in the first product slice. |
| Distributed locks | **DEFERRED** | PostgreSQL `SELECT FOR UPDATE` already protects Goal check-ins, Commitment transitions, and refresh rotation. |
| Short-lived processing state | **DEFERRED** | Capture/AI status when those APIs exist. |

**DECIDED:** Phase 7 product implementation starts with **auth + mutation rate limits** and an atomic counter helper. It does not start with a cache layer.

### Why not cache lists now

Owner-scoped Goal and Commitment lists are cheap PostgreSQL queries with `prefetch_related` for derived Goal fields. There is no home/dashboard endpoint yet. `GOAL_DESIGN.md` forbids Redis-cached streaks in MVP. Caching would add invalidation surface (every check-in, pause, complete) without a measured hot path.

### Already implemented (do not rebuild)

| Item | Value |
|---|---|
| Client | `apps.core.redis.get_redis_client()` — `REDIS_URL`, `decode_responses=True`, 200ms timeouts, lazy connect |
| Counter primitive | `incr_with_ttl(key, ttl_seconds)` — **not safe enough for security throttles** (see §7) |
| Denylist key | `promise:auth:denylist:sid:{session_id}` |
| Denylist value | `"1"` (not a JWT or secret) |
| Denylist TTL | `JWT_ACCESS_TTL_SECONDS + JWT_LEEWAY_SECONDS` = 930s |
| Denylist fallback | Redis down → skip hint; PostgreSQL `AuthSession.is_active()` still runs |
| Refresh lock | **DEFERRED** — Postgres row lock is enough (`AUTHENTICATION_DESIGN.md`) |

---

## 2. Source of truth

**DECIDED**

| Store | Holds |
|---|---|
| **PostgreSQL** | Users, sessions, commitments, goals, check-ins, events, outbox, processed events |
| **Redis** | Derived hints, counters, future caches, future locks |
| **Kafka** | Integration events after PostgreSQL commit (outbox) |

Rules:

1. A Redis **cache miss** never means the Goal/Commitment/user is missing. Read PostgreSQL.
2. A Redis **outage** must not corrupt or roll back domain rows.
3. Redis is not the session database, not the check-in store, and not the outbox.
4. Auth denylist is a hint. Revocation truth is `AuthSession.revoked_at`.

```text
Request
  │
  ├─ rate-limit counter (Redis, if up)
  ├─ JWT + denylist hint (Redis, if up)
  └─ domain read/write (PostgreSQL always)
```

---

## 3. Caching candidates

**DECIDED:** evaluate each candidate. Cache only high-read / expensive derived data. Do not cache everything.

| Candidate | Cost today | Invalidation churn | Worth adding now? |
|---|---|---|---|
| Goal list `GET /api/v1/goals/` | Indexed owner query + prefetch | Every create/patch/lifecycle/check-in | **No.** Cheap; high write coupling. |
| Commitment list `GET /api/v1/commitments/` | Indexed owner query | Every create/patch/action | **No.** Same. |
| Per-goal progress / streak | In-process over prefetched check-ins | Every check-in; late repair must be visible | **No.** Forbidden by `GOAL_DESIGN.md` MVP. Revisit if list of many long-history goals is measured slow. |
| User dashboard / home summary | **No API exists** | Would couple Goal + Commitment | **No.** Design a home endpoint first, then consider cache. |

### If added later (not MVP)

| Cache | Key | TTL | Invalidate on | Stale tolerance |
|---|---|---|---|---|
| Goal list | `promise:cache:goals:user:{user_id}:list` | 30s | Goal create/update/pause/resume/complete/cancel; check-in write | Low. Check-in should show on next GET. Prefer delete-on-write over TTL-only. |
| Goal progress | `promise:cache:goals:goal:{goal_id}:progress` | 30s | Check-in create/update; goal schedule/timezone/end_date patch; pause/resume | **None** for the acting user after their own write. Invalidate immediately. |
| Commitment list | `promise:cache:commitments:user:{user_id}:list` | 30s | Commitment create/update/complete/snooze/unsnooze/wait/cancel | Low. Delete-on-write. |
| Home summary | `promise:cache:home:user:{user_id}:summary` | 30s | Any Goal or Commitment mutation above | Low. Delete-on-write. |

**DECIDED (MVP):** none of the rows above are implemented. TTL-only caches are **not** acceptable for progress/streak if that cache is added later.

Cache miss → PostgreSQL → (optional) fill Redis → return. Never return “not found” from a cache miss.

---

## 4. Cache key convention

**DECIDED:** namespaced, Promise-only prefixes. Colon-separated. No secrets in keys.

```text
promise:{family}:{resource}:{id-type}:{id}:{facet}
```

| Family | Purpose | Examples |
|---|---|---|
| `auth` | Auth hints | `promise:auth:denylist:sid:{session_id}` **IMPLEMENTED** |
| `ratelimit` | Counters | `promise:ratelimit:login:ip:{ip}` |
| `cache` | Derived reads | `promise:cache:goals:user:{user_id}:list` **DEFERRED** |
| `lock` | Short exclusive work | `promise:lock:notification:dispatch:{user_id}:{kind}` **DEFERRED** |
| `processing` | Capture/job status | `promise:processing:capture:{capture_id}` **DEFERRED** |

Rules:

1. Prefix is always `promise:`. Do not use bare `{user_id}` keys.
2. IDs are UUIDs as canonical strings.
3. Emails in keys are `{email_hash}` = SHA-256 hex of the **normalized** email (strip + lowercase). Never plaintext (`AUTHENTICATION_DESIGN.md`).
4. **DECIDED:** do not embed environment (`prod`/`staging`) in keys. Each environment has its own Redis (`REDIS_URL`). Collisions across env are an ops bug, not a key-format problem.
5. **DEFERRED:** optional `REDIS_KEY_PREFIX` if two apps ever share one Redis. Not needed for Compose/local.

Existing denylist keys stay exactly as implemented. Do not rename them.

---

## 5. Invalidation

**DECIDED:** if a domain cache is added later, **delete on write**. Do not rely only on TTL when the user just mutated the data.

```text
Goal check-in write
  → DEL promise:cache:goals:goal:{goal_id}:progress
  → DEL promise:cache:goals:user:{user_id}:list
  → DEL promise:cache:home:user:{user_id}:summary

Goal pause / resume / complete / cancel / PATCH
  → DEL list + progress (if schedule/end_date/status changed) + home

Commitment complete / cancel / snooze / wait / PATCH
  → DEL promise:cache:commitments:user:{user_id}:list
  → DEL home
```

Invalidation is best-effort. If Redis is down, skip `DEL`; PostgreSQL still has the new state; the next read is a miss or a stale TTL expiry. Stale cache after a failed `DEL` is bounded by TTL (30s if those caches exist).

**MVP:** no domain cache keys, so no invalidation wiring.

Rate-limit keys expire by TTL. Do not delete them on successful login (fixed window).

Denylist keys expire by access-token TTL. Logout writes them; they are not invalidated on login.

---

## 6. Rate limiting

**DECIDED:** Redis fixed-window counters. HTTP **429** `RATE_LIMITED` via `RateLimitedError`. Envelope `{ "error": { "code": "RATE_LIMITED", "message": "Too many requests. Please try again later." } }`. Include `Retry-After: <seconds>` from the exceeded bucket’s TTL (use the window if TTL is missing). Do not name which bucket fired. Do not log email, password, or tokens.

**IMPLEMENTED key convention (canonical):** `promise:ratelimit:{bucket}:{subject-type}:{subject}`. No environment names in keys. Emails are SHA-256 of `canonicalize_email`, never plaintext. User buckets use authenticated `user_id` (UUID), never email or IP.

Windows are **fixed**, not sliding, so Lua INCR+EXPIRE matches the counter. Burst at window start is accepted.

### Why these numbers

| Bucket | Threat | Reasoning |
|---|---|---|
| Login | Credential stuffing | Argon2id already slows each attempt. 10 tries / 15 min per email is ~one guess per 90s — enough for typos, not stuffing. 20 / 15 min per IP allows a NAT/café without opening a botnet. |
| Register | Account farms + 409 email enumeration | 5 creates / hour / IP. Enumeration still exists (409); throttle slows harvesting. 10 / hour / email-hash leaves room for validation retries (weak password) without opening a farm. |
| Refresh | Token spraying / tight retry loops | Access TTL is 15 minutes. Legitimate Android refresh is ~1/session/15 min plus a few retries. 30 / 15 min per session and 120 / 15 min per user leave room for multi-device and grace retries. |
| Commitment mutations | Accidental client loops, not password guessing | Authenticated. 60 / min / user is above human UI, below a tight script. Domain idempotency still applies. |
| Goal check-in | Same | 30 / min / user. Identical retries are service no-ops; this bounds request cost. |
| Future notification APIs | Abuse when they exist | Same shape as mutations until measured. |
| Future AI | Cost | Separate, stricter bucket when AI exists. |

### Buckets

Auth (Phase 7.3 **IMPLEMENTED**):

| Endpoint class | Key | Window | Limit | Redis down |
|---|---|---|---|---|
| `POST /api/v1/auth/login/` | `promise:ratelimit:login:ip:{ip}` | 15 min | 20 | **Fail open** (allow). Argon2 + generic 401 still apply. |
| `POST /api/v1/auth/login/` | `promise:ratelimit:login:email:{email_hash}` | 15 min | 10 | Fail open |
| `POST /api/v1/auth/register/` | `promise:ratelimit:register:ip:{ip}` | 1 hour | 5 | Fail open |
| `POST /api/v1/auth/register/` | `promise:ratelimit:register:email:{email_hash}` | 1 hour | 10 | Fail open |
| `POST /api/v1/auth/refresh/` | `promise:ratelimit:refresh:session:{session_id}` | 15 min | 30 | Fail open |
| `POST /api/v1/auth/refresh/` | `promise:ratelimit:refresh:user:{user_id}` | 15 min | 120 | Fail open |
| `POST /api/v1/auth/refresh/` (malformed / unknown session) | `promise:ratelimit:refresh:ip:{ip}` | 15 min | 30 | Fail open. Do not create per-session keys for unknown UUIDs. |

Mutations (Phase 7.4 **IMPLEMENTED**). Authenticate first; bucket is the **caller’s** `user_id`. Do not rate-limit GET/list/detail. Check-in POST uses its own bucket (not the goal-write bucket).

| Endpoint class | Key | Window | Limit | Redis down |
|---|---|---|---|---|
| Commitment writes: `POST /api/v1/commitments/`, `PATCH /{id}/`, `POST` complete/snooze/unsnooze/wait/cancel | `promise:ratelimit:commitment:user:{user_id}` | 1 min | 60 | Fail open |
| Goal writes: `POST /api/v1/goals/`, `PATCH /{id}/`, `POST` pause/resume/complete/cancel | `promise:ratelimit:goal:user:{user_id}` | 1 min | 60 | Fail open |
| Goal check-in `POST /api/v1/goals/{id}/check-ins/` | `promise:ratelimit:goal_checkin:user:{user_id}` | 1 min | 30 | Fail open |
| `POST /api/v1/auth/forgot/` (when built) | `promise:ratelimit:forgot:ip:{ip}` | 1 hour | 5 | Fail open — **DEFERRED** with password reset |
| Future notification writes | `promise:ratelimit:notification:user:{user_id}` | 1 min | 30 | Fail open — **DEFERRED** |
| Future AI | `promise:ratelimit:ai:user:{user_id}` | 1 hour | 20 | Fail open — **DEFERRED**; tighten with cost data |

Auth: either matching bucket over limit → 429. Count **every POST attempt** (success, wrong password, unknown email), not only 401s. Count **before** Argon2 so stuffing dies on Redis. If the request is under the cap, login still runs the real or dummy password hasher (`AUTHENTICATION_DESIGN.md` — do not skip hashing for unknown emails).

Mutations: JWT authentication → identify user → increment that user’s bucket → authorization/domain service → PostgreSQL. Unauthenticated writes are 401 and do not increment mutation keys. Another authenticated user consumes **their** bucket; 429 must not reveal the resource owner.

**DECIDED:** fail-open on Redis for rate limits matches `AUTHENTICATION_DESIGN.md` §15. Taking login down when Redis is down is worse than a window of unthrottled stuffing; hashes still run. **Never** skip PostgreSQL session checks.

**DECIDED:** no new rate-limit package. Use `apps.core.redis` + Lua. Tests use existing `fakeredis` (`tests/test_redis_client.py`, `tests/test_auth_redis.py`). Do not change denylist tests to ship throttles.

**DECIDED:** implementation order: (1) login/register/refresh, (2) Goal/Commitment write buckets. Do not ship AI/notification/forgot throttles until those APIs exist.

Client IP: implemented auth throttles use `REMOTE_ADDR` only. First `X-Forwarded-For` hop behind a trusted proxy remains **OPEN** / **DEFERRED**.

---

## 7. Atomic counters

**DECIDED:** security and product throttles must increment and set TTL in **one Redis round-trip**.

`incr_with_ttl` today:

```text
INCR key
if count == 1:
    EXPIRE key ttl
```

Concurrent first hits are fine (`INCR` is atomic; only `count == 1` sets TTL). The failure mode is **crash or timeout between INCR and EXPIRE** on the first hit: the key has no TTL and the bucket never resets.

**DECIDED:** rate-limit implementation uses a Lua script:

```text
local n = redis.call('INCR', KEYS[1])
if n == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
local ttl = redis.call('TTL', KEYS[1])
return {n, ttl}
```

`increment_rate_limit` (Phase 7.2 **IMPLEMENTED**) returns `{"count": int, "ttl": int}`. It does not decide allow/deny. HTTP layers compare `count` to the bucket limit and set `Retry-After` from `ttl`.

EVAL is atomic on the key. No separate `INCR` then `EXPIRE` from Python.

Alternative considered: `SET key 1 NX EX ttl` then `INCR` if the SET failed. A tiny race remains if the key expires between a failed SET and INCR (INCR would recreate without TTL). Lua is simpler. **Not chosen:** always EXPIRE after INCR (that makes a sliding window and resets the window on every hit).

Do not change `incr_with_ttl` or the denylist for throttles. Security throttles must call `increment_rate_limit`, not `incr_with_ttl`.

Denylist uses `SET` with `ex=` (already atomic). No counter.

---

## 8. Distributed locks

**DECIDED:** do **not** add Redis locks where PostgreSQL row locking already provides correctness.

| Operation | Lock today | Redis lock? |
|---|---|---|
| Goal check-in upsert | `select_for_update` on Goal | **No** |
| Goal/Commitment lifecycle | `select_for_update` on the row | **No** |
| Refresh rotation | `select_for_update` on `AuthSession` | **No** (`promise:lock:auth:refresh:{session_id}` remains **DEFERRED**) |
| Outbox publish | `SKIP LOCKED` on `outbox_events` | **No** |
| Cache stampede | N/A (no cache) | **No** for MVP |
| Notification dispatch | No worker yet | **DEFERRED** |
| Duplicate reminder | No scheduler yet | **DEFERRED** |

### Future lock (notifications)

When a reminder worker exists:

| Field | Value |
|---|---|
| Key | `promise:lock:notification:dispatch:{user_id}:{kind}:{period_date}` |
| Value | random token (not a secret from auth) |
| TTL | 120s (crash recovery: lock expires; job may retry; idempotency is `processed_events` / “already sent” row) |
| Acquire | `SET key token NX EX 120` |
| Release | Lua: delete only if value == token (do not delete another worker’s lock) |
| Redis down | **Skip this dispatch tick** (fail safe: may delay a reminder, must not double-send without the lock **and** without an idempotency row). Prefer “no reminder” over “two pushes” unless a durable sent-row exists. |

**DECIDED (when built):** reminder sending needs **both** a short Redis lock (avoid two workers racing) **and** a durable idempotency record (Postgres or `processed_events`). Lock alone is not enough.

---

## 9. Cache stampede

**DECIDED:** no single-flight lock for Goal progress/streak in MVP. Calculation is in-process over already-fetched rows. Concurrent GETs recomputing the same streak is acceptable.

**DEFERRED:** if progress is cached later and p99 compute becomes expensive, add:

```text
promise:lock:cache:goals:goal:{goal_id}:progress
```

TTL 2s, `SET NX EX 2`. Holders compute and fill cache; waiters miss to PostgreSQL (or wait briefly). Do not block HTTP on a lock wait longer than ~50ms; fall through to PostgreSQL.

---

## 10. Redis failure

**DECIDED** per feature:

| Feature | Redis up | Redis down |
|---|---|---|
| Denylist read | Deny if key exists, then still check Postgres | Treat as not denied; Postgres still rejects revoked sessions |
| Denylist write | SET hint after Postgres revoke | Log; logout still 204; Postgres revoked |
| Rate limit | INCR; 429 over cap | **Allow** the request (fail open) |
| Domain cache (future) | HIT/MISS | Miss → PostgreSQL; skip fill |
| Cache invalidation (future) | DEL | Skip; TTL bounds staleness |
| Notification lock (future) | SET NX | Do not dispatch this tick |
| Health / runserver | Unused | Must still start (`AUTHENTICATION_DESIGN.md`) |

Redis outage **must not**:

- insert/update/delete Goal, Commitment, or User rows incorrectly
- skip `AuthSession` checks
- publish Kafka from the request path
- store or return secrets

---

## 11. Data that must never be cached

**DECIDED:** Redis must never store:

- passwords or password hashes
- JWT signing keys or previous signing keys
- refresh token secrets, HMAC peppers, or Fernet encryption keys
- access or refresh tokens
- denylist values other than `"1"`
- full request bodies

Allowed: `sid` UUIDs, user UUIDs, SHA-256 email hex, IP strings, integer counters, `"1"` markers, future compact JSON **without** titles/notes if a cache is added (same privacy bar as outbox: prefer ids and counts).

Authentication denylist behavior remains Phase 4.7 as designed.

---

## 12. Memory / eviction

**DECIDED:** keep MVP simple. Do **not** change `docker-compose.yml` memory or `maxmemory`.

| Kind | TTL | Volume (local/MVP) | Persistence |
|---|---|---|---|
| Denylist | 930s | Logged-out sessions in the last ~15 min | AOF is on in Compose; loss is OK (Postgres still revokes) |
| Rate limits | 60s–1h | IPs + users active in the window | Loss is OK (windows reset) |
| Future caches | 30s | One key per user/list or per goal | Not required |
| Future locks | 2–120s | Few keys per worker | Not required |

All product keys have TTLs. Accept Redis eviction of cache/ratelimit/denylist keys: cache → miss; denylist → Postgres; rate limit → fail open.

Do not create Redis keys without TTL except by accident (the Lua script exists to prevent that).

---

## 13. Observability

**DECIDED:** use logger `promise`. Identifiers only: `user_id`, `session_id`, `ip` (or hash), `key family`, `event_id`. Never log tokens, hashes of passwords, HMAC material, or Lua scripts’ user payloads.

| Signal | When |
|---|---|
| Rate limited | INFO: bucket name, user_id or ip. Do not log email. Email hash in the Redis key is not the email; still prefer bucket name over dumping the full key. |
| RedisError on throttle/denylist | WARNING: operation name, no exception details that might include URL passwords |
| Cache hit/miss | DEBUG when caches exist |
| Lock not acquired | INFO when notification locks exist |
| Redis latency | DEFERRED metrics backend; logs are enough for MVP |

Do not add a metrics vendor in this slice.

---

## 14. MVP vs future

### MVP (first product Redis implementation after this document)

- Document existing denylist (already live)
- Atomic Lua INCR+EXPIRE helper for throttles **IMPLEMENTED** (7.2)
- Wire **login / register / refresh** buckets **IMPLEMENTED** (7.3)
- Wire **Goal write / Goal check-in / Commitment write** buckets **IMPLEMENTED** (7.4)
- Tests with fakeredis (no Docker Redis required), including Redis-down fail-open
- No domain caches
- No new locks
- No Compose/auth denylist/Kafka/Android changes

### Future

- Home/dashboard cache after that API exists
- Goal progress cache only if measured slow; then delete-on-write + optional 2s single-flight lock
- Cache warming
- Notification dispatch locks + durable sent-row
- AI rate limits (`promise:ratelimit:ai:user:{user_id}`)
- Forgot-password throttle
- Distributed scheduling
- Analytics / insights cache
- `REDIS_KEY_PREFIX` for shared Redis
- Sliding-window or token-bucket algorithms

---

## 15. Final design

### Redis Responsibilities

**DECIDED.** PostgreSQL is source of truth. Redis is denylist hint (**IMPLEMENTED**), rate-limit counters (**MVP**), caches/locks/processing (**DEFERRED**). A miss is not missing domain data.

### Key Naming Convention

**DECIDED.** `promise:{family}:...` with families `auth`, `ratelimit`, `cache`, `lock`, `processing`. Rate-limit keys are `promise:ratelimit:{bucket}:{subject-type}:{subject}` (Phase 7.3/7.4). Hash emails. Dedicated Redis per environment. Do not rename the existing denylist key.

### Cache Strategy

**DECIDED.** No Goal list, Commitment list, progress/streak, or home cache in MVP. Revisit progress cache only with measurements; honor `GOAL_DESIGN.md`.

### Invalidation Strategy

**DECIDED.** Future caches: delete-on-write for the acting user’s keys; TTL is a backstop. MVP: N/A.

### Rate Limits

**DECIDED / IMPLEMENTED (7.3–7.4).** Fixed windows. Canonical keys `promise:ratelimit:{bucket}:{subject-type}:{subject}`. Login 20/15m/IP and 10/15m/email-hash. Register 5/h/IP and 10/h/email-hash. Refresh 30/15m/session and 120/15m/user (unknown refresh → IP fallback 30/15m). Commitment writes 60/min/user. Goal writes 60/min/user. Check-ins 30/min/user. GET exempt. 429 `RATE_LIMITED` + `Retry-After`. Lua counters. Fail open if Redis is down. Count every login attempt. Mutation buckets use the authenticated caller, not the resource owner.

### Lock Strategy

**DECIDED.** No new Redis locks in MVP. Postgres row locks remain. Notification `SET NX EX` + token compare-and-delete **DEFERRED**. Refresh Redis lock stays **DEFERRED**.

### Failure / Fallback

**DECIDED.** Cache down → PostgreSQL. Rate limiter down → allow. Denylist down → Postgres session check. Future notification lock down → skip that tick. Never corrupt domain state.

### Security

**DECIDED.** No credentials, JWTs, refresh secrets, signing keys, encryption keys, or HMACs in Redis. Denylist value `"1"` only.

### Observability

**DECIDED.** Log throttle hits and Redis errors without secrets. Cache hit/miss when caches exist.

### MVP vs Future

**DECIDED.** MVP = throttles + Lua helper. Future = caches, notification locks, AI limits, warming, scheduling.

### Open Questions

| Item | Status | Notes |
|---|---|---|
| Production client IP behind a proxy | **OPEN** | Trusted `X-Forwarded-For` vs `REMOTE_ADDR`. Do not throttle the proxy IP. |
| Fail-closed login if Redis is down in production | **OPEN** | Design inherits fail-open. Revisit if stuffing is observed while Redis is down. |
| Ship mutation throttles in the same slice as auth throttles | **DECIDED** | Separate slices: 7.3 auth, 7.4 mutations. Both implemented. |
| Progress-cache trigger (p99 / goal count) | **OPEN** | No cache until measured. |
| AI hourly cap | **OPEN** | 20/hour is a placeholder until cost is known. |
| `Retry-After` on 429 | **IMPLEMENTED** | Seconds from the exceeded bucket TTL. Same generic envelope; do not name the bucket. |

---

## 16. Decision index

| Decision | Label |
|---|---|
| PostgreSQL source of truth; Redis derived/temporary | **DECIDED** |
| Do not rebuild auth denylist | **IMPLEMENTED** / leave as-is |
| MVP Redis product work = rate limits, not caches | **DECIDED** |
| No Goal progress/streak Redis cache in MVP | **DECIDED** (also `GOAL_DESIGN.md`) |
| `promise:{family}:` keys; hash emails; no env in key | **DECIDED** |
| Lua INCR+EXPIRE for throttles | **DECIDED** |
| Fail-open rate limits if Redis is down | **DECIDED** |
| No new distributed locks in MVP | **DECIDED** |
| Cache stampede lock | **DEFERRED** |
| Notification / reminder locks | **DEFERRED** |
| Refresh Redis lock | **DEFERRED** |
| Change Compose memory / AOF | **DEFERRED** (do not) |
| Auth + mutation HTTP throttles (7.3–7.4) | **IMPLEMENTED** |
| Domain caches / new locks / AI / notification / forgot throttles | **NOT IMPLEMENTED** |
