# Promise — Transactional Outbox and Domain Event Design

**Status:** Design complete. Persistence (5.7), publisher (5.8), generic consumer + `processed_events` (5.9), Goal topic routing, and Phase 6 live Goal publish/consume verification are implemented. Product consumers (notifications, AI, analytics) and a dedicated Goal consumer command are not.

| Label | Meaning |
|---|---|
| **DECIDED** | Locked for future implementation. Do not silently change. |
| **DEFERRED** | Designed enough to avoid a rewrite; not part of the first outbox slice. |
| **OPEN** | Needs a choice before that slice is built. |
| **IMPLEMENTED** | Table, publisher, and generic commitment consumer exist in Django. Product workers are still deferred. |

This document is the source of truth for the transactional outbox and commitment integration events. It refines `docs/ARCHITECTURE.md` §6–8, `docs/PROJECT_PLAN.md` §13–14, `docs/COMMITMENT_DESIGN.md` §16, and `docs/AUTHENTICATION_DESIGN.md` §16.

It does **not** replace `CommitmentEvent`. Audit history stays on `commitment_events`. The outbox is a delivery mechanism.

Do not treat this file as a substitute for `docs/BUILD_PROGRESS.md`. Models, migrations, publisher, and the generic consumer exist; see BUILD_PROGRESS for what is actually running.

---

## 1. Problem

Django/DRF writes commitment state in PostgreSQL and returns HTTP. Notifications, AI, analytics, and activity feeds must react **after** the write, without putting Kafka on the Android CRUD path.

This sequence is not reliable:

```text
UPDATE commitments
     ↓
publish to Kafka
```

If Kafka is down, the HTTP request fails after the row is already committed, or the event is lost. Promise already rejected that pattern (`ARCHITECTURE.md` §6).

**DECIDED:** every event-producing domain mutation writes application state and an outbox row in the **same PostgreSQL transaction**. A publisher process later sends unpublished rows to Kafka and marks them published. Consumers are idempotent. Delivery is **at-least-once**, not exactly-once.

---

## 2. CommitmentEvent vs OutboxEvent

**DECIDED:** two tables, two jobs. Do not merge them. Do not publish `CommitmentEvent` rows to Kafka as-is.

| | `CommitmentEvent` | `OutboxEvent` |
|---|---|---|
| Table | `commitment_events` | `outbox_events` |
| Job | Product/business audit history | Asynchronous integration transport |
| Audience | Domain, future UI history, support | Publisher → Kafka → workers |
| Mutability | Append-only. Product code does not update or delete. | Publisher updates `published_at`, `attempts`, `next_attempt_at`, `last_error`. |
| Contents | `event_type` enum (`CREATED`, …), `actor`, `metadata` | Stable Kafka `event_type` (`commitment.completed`), envelope fields, JSON payload |
| Retention | Long-term audit (until account-deletion policy) | Short for published rows; unpublished until sent or parked |
| Kafka | Never | Yes, via publisher |

`CommitmentEvent` answers “what happened to this promise, and who did it?”. `OutboxEvent` answers “has this integration event been offered to Kafka yet?”

Idempotent service no-ops (complete when already `COMPLETED`, cancel when already `CANCELLED`, unsnooze when already `PENDING`, snooze with the same `snoozed_until`) write **neither** row. The API layer must not invent a second event.

There is **no FK** from `OutboxEvent` to `CommitmentEvent`. Correlation is `payload.commitment_event_id`. Outbox retention must not cascade-delete audit history, and audit retention must not block outbox deletes.

---

## 3. Outbox schema

**DECIDED:** one table `outbox_events` (`Meta.db_table = "outbox_events"`), model `OutboxEvent`, inheriting `BaseModel` (UUID `id`, `created_at`, `updated_at`). Home: `apps.outbox` so user/goal/capture events can share it. **IMPLEMENTED.**

Django internal tables stay Django-named. Application tables keep explicit `db_table` names.

### Fields

| Field | Store? | Canonical? | Why |
|---|---|---|---|
| `id` | **Yes** | Yes. This **is** Kafka `event_id`. | UUID v4 PK. Deduplication key for consumers. Immutable. |
| `aggregate_type` | **Yes** | Yes | Stable name (`commitment`, later `user`). Not a Django app label. |
| `aggregate_id` | **Yes** | Yes | UUID of the aggregate (`Commitment.id`). Partition key for commitment events. |
| `event_type` | **Yes** | Yes | Dotted integration name, e.g. `commitment.completed`. Not `CommitmentEvent.EventType`. |
| `event_version` | **Yes** | Yes | Integer schema version of **this event type**. MVP = `1`. |
| `payload` | **Yes** | Yes (snapshot at write time) | JSON object. Inner payload only; the publisher wraps the envelope. Immutable after insert. |
| `occurred_at` | **Yes** | Yes | Business time. Copy `CommitmentEvent.created_at` (UTC). Immutable. |
| `published_at` | **Yes** | Transport only | `NULL` = not successfully marked published. Set after Kafka accept. |
| `attempts` | **Yes** | Transport only | How many publish attempts have finished (success or fail). Starts at `0`. |
| `next_attempt_at` | **Yes** | Transport only | When the publisher may try again. Set to `now` on insert. `NULL` = **parked** (poison). |
| `last_error` | **Yes** | Transport only | Truncated public-safe error (`max_length` 2048). Empty string when none. No payloads, tokens, or stack traces. |
| `created_at` | **Yes** | Row insert time | BaseModel. Usually ≈ `occurred_at`. |
| `updated_at` | **Yes** | Row maintenance | BaseModel. Changes when the publisher updates transport fields. |

### Rejected fields (MVP)

| Field | Why not |
|---|---|
| `status` enum | Derived: published (`published_at` set), pending (`published_at` null and `next_attempt_at` set), parked (`published_at` null and `next_attempt_at` null). |
| `locked_by` / `locked_at` | `SELECT … FOR UPDATE SKIP LOCKED` is enough for concurrent publishers. |
| `topic` / `partition` | Derived from `aggregate_type` + Kafka metadata at publish time. |
| FK to `CommitmentEvent` | Couples retention. Use `payload.commitment_event_id`. |
| `correlation_id` / HTTP request id | Useful later; not required to ship the table. **DEFERRED.** |
| Kafka offset / `broker_timestamp` | Broker metadata, not source of truth. |

### Retry / idempotency role

- **`id`:** consumers store `(consumer_group, event_id)` and skip duplicates.
- **`attempts` / `next_attempt_at`:** publisher backoff. Not visible to consumers.
- **`published_at`:** publisher progress. A crash after Kafka ack and before this write causes a **retry publish** of the same `event_id` (at-least-once).
- **Payload and envelope fields:** never rewritten on retry. The Kafka record is the same logical event.

### Indexes

**DECIDED (MVP, one index):**

```text
outbox_unpublished_due (next_attempt_at, created_at)
  WHERE published_at IS NULL AND next_attempt_at IS NOT NULL
```

That is the publisher poll: due, unpublished, not parked, oldest first.

**DEFERRED:** `(aggregate_type, aggregate_id)` for replay-by-commitment; `(event_type)`; index on `published_at` for retention deletes (add when the deleter exists if sequential scans hurt).

Do not index `occurred_at` until a query needs it.

---

## 4. Event types

**DECIDED:** Kafka `event_type` is a stable dotted name. It is **not** the Django enum (`COMPLETED`) and **not** a model class name (`CommitmentEvent`).

### Mapping from current services

| `CommitmentEvent.event_type` | Kafka `event_type` | MVP outbox? |
|---|---|---|
| `CREATED` | `commitment.created` | **Yes** |
| `UPDATED` | `commitment.updated` | **Yes** (includes `clear_waiting`, which already writes `UPDATED`) |
| `COMPLETED` | `commitment.completed` | **Yes** |
| `SNOOZED` | `commitment.snoozed` | **Yes** |
| `UNSNOOZED` | `commitment.unsnoozed` | **Yes** |
| `WAITING` | `commitment.waiting` | **Yes** (`set_waiting` only) |
| `CANCELLED` | `commitment.cancelled` | **Yes** |

No-ops write no `CommitmentEvent` and therefore no `OutboxEvent`.

### Not MVP

| Event | Reason |
|---|---|
| `commitment.overdue` | No `OVERDUE` `CommitmentEvent` is written today. Detector/worker is **DEFERRED**. |
| `commitment.due_soon` | Scheduler/notification concern, not a commitment mutation. Must not be a stored status. **DEFERRED.** |
| `commitment.waiting_cleared` | Current domain uses `UPDATED` with `{from, to}`. Do not invent a second Kafka type until the domain enum exists. |
| Participant added/removed/responsibility changed | Invite APIs **DEFERRED**. |
| `user.created` / `user.password_changed` / `user.email_verified` | Designed in `AUTHENTICATION_DESIGN.md` §16. Same outbox table later. **DEFERRED** for this slice. |
| Goals, check-ins, captures, challenges, `notification.requested` | Other phases. |

---

## 5. Event envelope

**DECIDED:** the Kafka value is JSON with this envelope. Publisher builds it from columns + `payload`. The table does **not** store a second copy of the envelope.

```json
{
  "event_id": "3d2e0c6a-7b1f-4c9e-9a2b-1f0e8d7c6b5a",
  "event_type": "commitment.completed",
  "event_version": 1,
  "occurred_at": "2026-08-20T18:00:00Z",
  "aggregate_type": "commitment",
  "aggregate_id": "9a1b2c3d-4e5f-6789-abcd-ef0123456789",
  "payload": {}
}
```

| Field | Mandatory | Immutable | Notes |
|---|---|---|---|
| `event_id` | Yes | Yes | `OutboxEvent.id` |
| `event_type` | Yes | Yes | Dotted name |
| `event_version` | Yes | Yes | Integer. This **is** ARCHITECTURE’s `version`. Prefer the explicit name. |
| `occurred_at` | Yes | Yes | UTC ISO-8601 |
| `aggregate_type` | Yes | Yes | |
| `aggregate_id` | Yes | Yes | |
| `payload` | Yes | Yes | Object, may be empty `{}` |

**DECIDED:** no envelope-level `user_id` for commitment events. Shared promises have several users; system actors can be null. `ARCHITECTURE.md` §7 `user_id` remains valid for **user-lifecycle** events. Commitment payloads carry `actor_user_id` (nullable) and `created_by_user_id`.

### Versioning

- `event_version` belongs **in the envelope** (and the column). Consumers branch on `(event_type, event_version)`.
- **Non-breaking:** add optional payload keys; old consumers ignore them. Keep `event_version = 1`.
- **Breaking:** remove/rename keys or change meaning. Publish `event_version = 2` (same `event_type`) **or** a new `event_type`. Do not mutate already-written outbox rows.
- Schema registry: **DEFERRED**.

---

## 6. Payload rules

**DECIDED:** event-specific **compact snapshot**, not the full ORM row, not ids-only.

Workers in this modular monolith **may** load PostgreSQL by `aggregate_id`. The payload still carries enough to notify or log without a second read, and remains correct if title/description later change.

### Always (version 1)

| Key | Meaning |
|---|---|
| `commitment_event_id` | `CommitmentEvent.id` for correlation |
| `created_by_user_id` | Commitment creator |
| `actor_user_id` | Acting user; `null` for future system jobs |
| `status` | Stored status **after** the mutation |

### Event-specific additions

| `event_type` | Extra keys |
|---|---|
| `commitment.created` | `due_at`, `due_precision`, `source` |
| `commitment.updated` | `fields` (list of changed names, same as `CommitmentEvent.metadata`) |
| `commitment.completed` | `completed_at` |
| `commitment.snoozed` | `snoozed_until` |
| `commitment.unsnoozed` | none beyond common |
| `commitment.waiting` | none beyond common |
| `commitment.cancelled` | `cancelled_at` |

### Never publish

- passwords, password hashes
- JWTs, refresh tokens, HMAC/ciphertext, session secrets
- `AuthSession` rows
- `title`, `description` in MVP (privacy). Notifications use ids + type; in-process workers may read Postgres under authorization.
- participant lists, emails, names, timezones
- unbounded user text, screenshots, capture blobs
- stack traces, SQL, internal exception messages

`COMMITMENT_DESIGN.md` left “titles on Kafka vs ids-only” **OPEN**. This document **DECIDES** MVP = no titles/descriptions on the topic. Revisit when analytics/out-of-process consumers cannot hit PostgreSQL.

### Size / validation

**DECIDED:** payload JSON serialized size ≤ **16 KiB**. Reject insert (and roll back the domain transaction) if over the limit. `payload` must be a JSON object. Keys listed above are UUID/string/datetime/list-of-small-strings only.

---

## 7. Transaction boundary

**DECIDED** for every event-producing commitment mutation (create, patch-that-changes, complete, snooze, unsnooze, wait, cancel):

```text
BEGIN
    lock commitment (existing select_for_update on mutations)
    apply domain state
    INSERT commitment_events          -- skipped on service no-op
    INSERT outbox_events              -- skipped if no CommitmentEvent
COMMIT

HTTP response

(later, other process)
    SELECT unpublished due rows FOR UPDATE SKIP LOCKED
    produce to Kafka
    SET published_at = now()
```

All three writes succeed or all three roll back. **No Kafka client in the request transaction or the view.**

Create already inserts `Commitment` + `CommitmentParticipant` + `CommitmentEvent` in one `atomic()`. Outbox is a fourth insert in that same `atomic()`.

---

## 8. Publisher design

**DEFERRED** implementation. **DECIDED** behavior.

The publisher is a long-running process (management command or worker), not Celery-on-the-request, not `save()` hooks.

```text
loop:
  SELECT * FROM outbox_events
   WHERE published_at IS NULL
     AND next_attempt_at IS NOT NULL
     AND next_attempt_at <= now()
   ORDER BY next_attempt_at, created_at
   FOR UPDATE SKIP LOCKED
   LIMIT 50

  for each row:
    attempts += 1
    produce envelope to topic selected by aggregate_type
      commitment → promise.commitment.v1
      goal → promise.goal.v1
      key = aggregate_id
    on success: published_at = now(); last_error = ""
    on failure: last_error = truncated; next_attempt_at = now() + backoff(attempts)
                if attempts >= max_attempts: next_attempt_at = NULL  -- park
  COMMIT
  sleep briefly if batch empty
```

| Concern | MVP rule |
|---|---|
| Batching | 50 rows per transaction. |
| Claiming | `SKIP LOCKED` only. No ownership column. |
| Concurrent workers | Allowed. Each row locked by one worker. |
| Kafka produce | Sync ack for that produce (wait for broker). Then mark published in the **same** DB transaction as the lock. |
| Ordering in a batch | Process in `created_at` order; still no cross-partition Kafka order. |

Do not implement this process in Phase 5.6.

---

## 9. Retry strategy

**DECIDED:**

| Parameter | Value |
|---|---|
| Initial `next_attempt_at` | `now` (eligible immediately after commit) |
| Backoff | Exponential, capped: 5s, 15s, 45s, 2 min, 5 min, 15 min, then 30 min |
| `max_attempts` | 8 |
| After max | **Park:** `next_attempt_at = NULL`, `published_at` remains `NULL` |
| Poison | Parked rows. Ops can reset `next_attempt_at` and optionally `attempts` after a fix |
| Dead-letter Kafka topic | **DEFERRED** |
| Parking-lot table | **DEFERRED** (park in place) |

Retries republish the **same** `event_id` and payload. Consumers must treat that as a duplicate, not a new business event.

---

## 10. Consumer idempotency

**DECIDED:** Kafka delivery is **at-least-once**. Promise does **not** claim exactly-once (no Kafka transactions + consumer EOS in MVP, and the publish-then-mark crash exists by design).

Consumers **must** tolerate `event X` then `event X` again.

**Dedup key:** `event_id` (`OutboxEvent.id`).

**DEFERRED table** (ARCHITECTURE `processed_events`), when the first consumer exists:

```text
processed_events
  consumer_group  text
  event_id        uuid
  processed_at    timestamptz
  PRIMARY KEY (consumer_group, event_id)
```

Each consumer group records processed ids independently (notifications vs analytics).

Also use **business keys** where natural (e.g. “overdue already recorded for this `due_at`”) so a logical duplicate with a new `event_id` does not double-notify. That is in addition to `event_id`, not a replacement.

Ignore unknown `event_type` / higher `event_version` (log + skip or park per consumer policy). Do not crash the group on one new field.

---

## 11. Kafka topic strategy

**DECIDED (MVP):** one topic per **domain**, not per event type.

```text
promise.commitment.v1
promise.goal.v1
```

Matches `ARCHITECTURE.md` §7. Later: `promise.user.v1`, etc.

| Approach | Verdict |
|---|---|
| One topic per event type (`commitment.completed`, …) | Rejected for MVP. More partitions/config; consumers that want the whole lifecycle must join streams. |
| One topic for the whole product | Rejected. Unrelated domains share lag and ACLs. |
| `promise.commitment.v1` | **Chosen.** All commitment integration events, discriminated by `event_type`. |
| `promise.goal.v1` | **Chosen.** All goal integration events (including check-ins), discriminated by `event_type`. Key = `Goal.id`. |

**Partition key:** `aggregate_id` (commitment or goal UUID).

The publisher maps `aggregate_type` → topic. Unknown types fail without producing. There is no `consume_goal_events` command yet. The generic `handle_record` path accepts Goal `event_type`s so the existing consumer can process `promise.goal.v1` when pointed at that topic. Product Goal workers remain deferred.

ARCHITECTURE’s `partition_key = user_id` applies to user-scoped lifecycle events. For commitments, per-aggregate order matters more: `created` before `completed` for the same promise. Different commitments of one user **may** interleave across partitions; that is accepted.

Topic name includes `v1` as a **topic** generation, independent of `event_version`. A breaking envelope-wide change may introduce `promise.commitment.v2` later (**DEFERRED**).

---

## 12. Failure handling

| Scenario | Expected behavior |
|---|---|
| **A. DB transaction fails** | No commitment change, no `CommitmentEvent`, no `OutboxEvent`. HTTP error from existing handlers. Kafka unchanged. |
| **B. Kafka unavailable** | HTTP already succeeded. Publisher fails produce, increments `attempts`, sets backoff `next_attempt_at`. Retries until success or park. |
| **C. Crash after Kafka ack, before `published_at`** | Row still unpublished. Retry publishes the **same** `event_id`. Consumer dedups. |
| **D. Publisher retries** | Same envelope. `attempts` increases. No second `CommitmentEvent`. |
| **E. Duplicate Kafka delivery** | Consumer sees same `event_id` (or broker redelivery). Skip if `(consumer_group, event_id)` exists. |
| **F. Invalid payload** | Must not be inserted. Validate in the domain transaction; if validation fails, **rollback** the mutation. Already-published poison is a bug: park and fix in code. |
| **G. Poison repeatedly fails** | After `max_attempts`, park (`next_attempt_at` null). Alert via logs/metrics (**DEFERRED** wiring). Do not block the unpublished queue. Manual unpark after fix. |

---

## 13. Security / privacy

**DECIDED:** outbox rows and Kafka values are **sensitive application data**. Same access rules as PostgreSQL: not public, not logged in full, not copied to analytics without a privacy pass.

- No secrets in payload (see §6).
- `last_error` is truncated and sanitized.
- Publisher/consumer logs: `event_id`, `event_type`, `aggregate_id`, `attempts` — not payload dumps.
- Topic ACL in production: produce = publisher; consume = known worker groups. **DEFERRED** ops work.
- Payload size cap 16 KiB.
- Do not put untrusted multi-kilobyte user text on the bus in MVP.

---

## 14. Retention

**DECIDED (keep MVP simple):**

| Store | Retention |
|---|---|
| `commitment_events` | Long-term audit. Not deleted by outbox jobs. Account deletion **OPEN** (existing commitment design). |
| Unpublished / parked `outbox_events` | Keep until published or manually removed. Do not auto-delete unpublished. |
| Published `outbox_events` | Delete after **14 days**. No archive in MVP. |
| Kafka topic | Broker retention **OPEN** (ops). Consumers must not assume the topic is the audit log. |

**DECIDED:** `CommitmentEvent` remains the long-term audit history. Kafka is not the system of record.

---

## 15. MVP vs future

### MVP (first implementation slice — not this design task)

- `outbox_events` table + `OutboxEvent` model
- Envelope and `event_version = 1`
- Map the seven commitment mutation events in §4
- Insert outbox row in the same transaction as `CommitmentEvent`
- No Kafka client, no publisher process yet (rows accumulate unpublished; that is acceptable until the publisher ships)

### Future / DEFERRED

- Outbox publisher loop
- Kafka produce to `promise.commitment.v1`
- `processed_events` and first consumers: notification worker, AI worker, analytics, activity feed
- `commitment.overdue`, `commitment.due_soon`
- User-lifecycle events on the same table
- Schema registry
- Dead-letter topic, parking-lot table, claim columns
- Titles/descriptions on the bus
- Celery vs dedicated publisher process choice
- Concrete Kafka client library

### Consumers (identify only)

| Consumer | Interest |
|---|---|
| Notification worker | completed, cancelled, snoozed, overdue/due_soon later |
| AI worker | created/completed patterns later |
| Analytics | all commitment types |
| Activity feed | created/completed/cancelled |

Do not implement them now.

---

## 16. Final decisions

| Decision | Label |
|---|---|
| Separate `CommitmentEvent` (audit) and `OutboxEvent` (transport) | **DECIDED** |
| Same PostgreSQL transaction: state + audit + outbox; no Kafka in request | **DECIDED** |
| Table `outbox_events`; UUID `id` = `event_id` | **DECIDED** |
| Transport fields: `published_at`, `attempts`, `next_attempt_at`, `last_error` | **DECIDED** |
| No status/lock/FK/topic columns in MVP | **DECIDED** |
| Kafka names `commitment.*`; map from existing `CommitmentEvent` types | **DECIDED** |
| Envelope with `event_id`, `event_type`, `event_version`, `occurred_at`, `aggregate_type`, `aggregate_id`, `payload` | **DECIDED** |
| Compact payload; no titles/secrets/tokens | **DECIDED** |
| Topic `promise.commitment.v1` / `promise.goal.v1`; key = `aggregate_id` | **DECIDED** |
| At-least-once; consumer dedup on `event_id` | **DECIDED** |
| Exponential backoff; park after 8 attempts | **DECIDED** |
| Publisher `SKIP LOCKED` batches of 50 | **DECIDED** |
| Published outbox rows deleted after 14 days; audit kept | **DECIDED** |
| Partial index for unpublished due rows | **DECIDED** |
| Publisher, Kafka client, generic consumer | **IMPLEMENTED** (5.8–5.9). Product consumers, DLT, schema registry **DEFERRED** |
| `commitment.overdue` / `due_soon` / user events / participant events | **DEFERRED** |
| Models, migrations, publisher, generic consumer | **IMPLEMENTED** |

---

## 17. Open questions

| Item | Status | Notes |
|---|---|---|
| Production Kafka topic retention and ACLs | **OPEN** | Ops, not domain. |
| Whether an out-of-process analytics pipeline later needs titles | **OPEN** | MVP omits titles. Would be `event_version` bump + privacy review. |
| `commitment.waiting_cleared` as its own type | **OPEN** | Only if domain adds a distinct `CommitmentEvent` type. |
| Account deletion vs leftover outbox rows | **OPEN** | Same bucket as `PROTECT` on `created_by`. |
| Publisher as `manage.py` loop vs separate container | **DECIDED** | One-shot `publish_outbox`; optional `--loop` not added. Cron/process supervisor can rerun. |
| Kafka client library | **DECIDED** | `confluent-kafka`. |

---

## Decision index

| Decision | Label |
|---|---|
| Dual events: audit vs outbox | **DECIDED** |
| Transaction = mutation + `CommitmentEvent` + `OutboxEvent` | **DECIDED** |
| `promise.commitment.v1` / `promise.goal.v1` + key `aggregate_id` | **DECIDED** |
| At-least-once + `event_id` dedup | **DECIDED** |
| Park poison in place | **DECIDED** |
| No titles on Kafka in MVP | **DECIDED** |
| Publisher / generic consumer / Kafka client | **IMPLEMENTED** (product consumers **DEFERRED**) |
| Implementation | **IMPLEMENTED** for persistence, publisher, generic consumer |
