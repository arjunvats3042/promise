# Promise — Commitment Design

**Status:** Design complete. Personal commitments, REST APIs, outbox, publisher, and generic consumer are implemented (Phase 5). Shared invitations, notifications, AI, and Android are not.

| Label | Meaning |
|---|---|
| **DECIDED** | Locked for future implementation. Do not silently change. |
| **DEFERRED** | Designed enough to avoid a rewrite, not part of the first commitment implementation. |
| **OPEN** | Needs a product/engineering choice before that slice is built. |
| **IMPLEMENTED** | Models, services, REST APIs, and the event backbone exist. See `docs/BUILD_PROGRESS.md` for what is running. |

This document is the source of truth for the commitment domain. It supersedes the placeholder state machine in `docs/ARCHITECTURE.md` §11 (OVERDUE as a stored state) and the `user_id` + `person_id` sketch in `docs/DEVELOPMENT.md` Phase 5.

Do not treat this file as a substitute for `docs/BUILD_PROGRESS.md`. Where this design names `WAITING_SET` / `WAITING_CLEARED` / `OVERDUE` events, HTTP `/unwait/`, server-only `source` on create, or automatic snooze expiry, the running code differs — those are deferred product follow-ups, not a rewrite of this document.

Authentication is already implemented (Phase 4). Commitment APIs use `request.user` from `JWTAccessAuthentication`. Do not change authentication to ship commitments.

---

## 1. Product context

Promise is an accountability platform. It remembers what you promised others and what you promised yourself.

A commitment is a **discrete, finite promise** — something someone is accountable for doing, usually once, optionally by a deadline.

Examples the model must eventually express:

| Example | Shape |
|---|---|
| “I will study DSA tonight.” | Personal. Creator = responsible. |
| “I will send Rahul the credentials by 6 PM.” | Creator is responsible. Rahul is the recipient (may not be a Promise user yet). |
| “Rahul will send Arjun the credentials by 6 PM.” | Arjun creates it. Rahul is responsible. Arjun is the recipient. |
| “Rahul sends credentials → Arjun integrates them.” | Two commitments, or one commitment in `WAITING` until the dependency lands. Not a recurring goal. |

The first implementation is personal commitments for the authenticated user. The schema must not force a rewrite when we add:

- shared commitments
- accountability partners
- group challenges
- AI-created (user-confirmed) commitments
- notification-driven accountability

**DECIDED:** one `Commitment` type. Sharing is membership (`CommitmentParticipant`), not a second model. Recurrence belongs to Goals, not Commitments.

---

## 2. What a Commitment is

**DECIDED:** A **Commitment** is a single accountable promise: a statement of what will be done, who is responsible, who it is for, and optionally when it is due. It has a lifecycle (open → done, cancelled, or waiting). It is not a habit, not a period record, and not a time-boxed group program.

| Concept | Meaning | Relationship |
|---|---|---|
| **Commitment** | One finite promise. “Send the credentials by 6 PM.” | This domain. |
| **Goal** | Something the user wants to do **consistently**. “Study DSA every weekday.” | Separate domain (Phase 6). Has schedule, tracking mode, check-ins. |
| **Check-in** | What happened for a **goal** in a period (completed / missed / partial / skipped). | Belongs to Goal. Preserves historical truth for that date. |
| **Challenge** | A time-boxed **goal** (or group of goals) with membership. “30-day gym challenge.” | Separate future aggregate. May *create* or *link* commitments/goals; it is not a commitment subtype. |
| **Task** | Generic to-do without Promise’s accountability graph (responsible/recipient, snooze/overdue, AI capture, notifications). | Not a domain entity. Do not add a `Task` table. |

### Why Commitment is its own domain

1. **Accountability, not a list.** A commitment has a responsible party, a lifecycle, and later witnesses. A task app’s “todo row owned by user_id” cannot represent “Rahul will send me credentials.”
2. **Finite vs repeating.** Recurrence, streaks, and period scoring are Goal/Check-in problems. Putting `rrule` on Commitment would mix two products.
3. **AI capture.** Screenshots and chat extracts produce candidate *promises*, not habits. Confirmation creates a Commitment.
4. **Notifications.** Due / overdue / completed are commitment events. Daily check-in reminders are goal events.
5. **Sharing.** Participants and roles attach here. Challenges reuse membership patterns later; they should not require mutating Goal to look like a one-shot promise.

**DECIDED:** “Study every weekday” is a **Goal**, even if the user types it into a commitment composer. The commitment MVP does not auto-convert that text. Classification is a later AI concern.

---

## 3. Core entity — `Commitment`

**DECIDED:** `apps.commitments` models inherit `apps.core.models.BaseModel` (UUID v4 `id`, timezone-aware `created_at` / `updated_at`). Django stores UTC (`TIME_ZONE=UTC`, `USE_TZ=True`). User timezone is `users.User.timezone` and does not change stored values.

Table name: `commitments` (`Meta.db_table`).

### Fields

| Field | Canonical? | On Commitment? | Why |
|---|---|---|---|
| `id` | Canonical | Yes (BaseModel) | Public identifier. Not authorization. |
| `created_by` | Canonical | Yes, FK `users.User` | Who created the row. Survives even if they are not responsible. Never taken from the client as a writable owner id — set from `request.user`. |
| `title` | Canonical | Yes | The promise statement. Maps to PROJECT_PLAN `action`. Named `title` because the statement is not always the creator’s own action (“Rahul will send credentials”). Required. Max 255. |
| `description` | Canonical | Yes | Optional longer context. `TextField`, blank default. |
| `status` | Canonical **stored** | Yes | Open lifecycle. Values in §4. **Does not include OVERDUE.** |
| `due_at` | Canonical | Yes, nullable timestamptz | Instant used for overdue and scheduling. Null = no deadline. |
| `due_precision` | Canonical | Yes | `none` \| `date` \| `datetime`. How the user specified the deadline. Constraint: `none` iff `due_at` is null. |
| `source` | Canonical | Yes | Origin enum (§8). Not a status. |
| `snoozed_until` | Canonical | Yes, nullable timestamptz | Set iff `status=SNOOZED`. Cleared on unsnooze. |
| `completed_at` | Canonical | Yes, nullable timestamptz | Set iff `status=COMPLETED`. |
| `cancelled_at` | Canonical | Yes, nullable timestamptz | Set iff `status=CANCELLED`. |
| `created_at` / `updated_at` | Canonical | Yes (BaseModel) | Audit timestamps. |
| `is_overdue` | **Derived** | Serializer / query only | `status ∈ {PENDING, WAITING}` AND `due_at` is not null AND `now >= due_at`. False while `SNOOZED`. |
| `user_id` (sole owner) | — | **No** | Replaced by `created_by` + participants. |
| `person_id` | — | **No** (DEFERRED) | Contacts who are not users belong to a future People domain. MVP title may include a name. |
| `capture_id` | — | **No** on MVP Commitment | Optional FK later when Capture exists. `source` is enough for v1. |
| `challenge_id` | — | **No** on MVP Commitment | Future Challenge aggregate. Adding a nullable FK later does not break this schema. |
| Participant roles | Canonical | **Participant table** | Not duplicated as columns on Commitment. |
| Recurrence rule | — | **No** | Goal domain. |

### Invariants

- `created_by` is immutable after insert.
- Status changes go through domain services, not arbitrary `PATCH status`.
- Completing sets `completed_at` and clears `snoozed_until`.
- Cancelling sets `cancelled_at` and clears `snoozed_until`.
- At least one `RESPONSIBLE` participant exists (service-enforced). MVP: exactly one, and that user is `created_by`.

---

## 4. Status state machine

**DECIDED:** Stored statuses are **PENDING**, **WAITING**, **SNOOZED**, **COMPLETED**, **CANCELLED**.

**DECIDED:** **OVERDUE is derived**, never persisted as `status`. See §13.

### Stored vs derived

| Value | Stored? | Meaning |
|---|---|---|
| `PENDING` | Yes | Open, not waiting, not snoozed. May also be overdue. |
| `WAITING` | Yes | Blocked / waiting on a dependency or another person. May also be overdue. |
| `SNOOZED` | Yes | Reminders paused until `snoozed_until`. |
| `COMPLETED` | Yes | Terminal success. |
| `CANCELLED` | Yes | Terminal abandoned. |
| `OVERDUE` | No | Display/query flag `is_overdue`. |

### Transitions

```text
                         create
                            │
                            v
                      +-----------+
                      |  PENDING  |◄── unsnooze / snooze expiry
                      +-----+-----+
                     /    |    |   \
                    /     |    |    \
                   v      v    v     v
            COMPLETED  WAITING SNOOZED  CANCELLED
               (T)       │       │         (T)
                         │       │
                         │       ├──► PENDING
                         │       ├──► COMPLETED
                         │       └──► CANCELLED
                         ├──► PENDING   (unwait)
                         ├──► COMPLETED
                         └──► CANCELLED
```

`T` = terminal.

### Allowed

| From | To | How |
|---|---|---|
| *(new)* | `PENDING` | Create |
| `PENDING` | `COMPLETED` | Complete |
| `PENDING` | `SNOOZED` | Snooze |
| `PENDING` | `WAITING` | Wait |
| `PENDING` | `CANCELLED` | Cancel |
| `SNOOZED` | `PENDING` | Unsnooze, or `now >= snoozed_until` (lazy + worker) |
| `SNOOZED` | `COMPLETED` | Complete while snoozed |
| `SNOOZED` | `CANCELLED` | Cancel while snoozed |
| `WAITING` | `PENDING` | Unwait |
| `WAITING` | `COMPLETED` | Complete |
| `WAITING` | `CANCELLED` | Cancel |

Snoozing an already-`SNOOZED` row **updates** `snoozed_until` and stays `SNOOZED` (re-snooze). That is not a status change.

### Invalid

- Any transition out of `COMPLETED` or `CANCELLED` (no undo in MVP).
- `WAITING` → `SNOOZED` or `SNOOZED` → `WAITING` (unsnooze/unwait first).
- Client `PATCH` of `status`.
- Writing `status=OVERDUE`.
- Complete/cancel/snooze/wait on a row the user cannot see (404).

### After OVERDUE

Nothing special happens to stored status. The row stays `PENDING` or `WAITING`. The user may still complete, snooze, wait, unwait, or cancel. Snooze hides overdue in the UI until `snoozed_until` (`is_overdue` false while snoozed). After unsnooze, if `due_at` is still in the past, `is_overdue` is true again. Original `due_at` is not rewritten by snooze.

---

## 5. Participants / shared promises

**DECIDED:** `CommitmentParticipant` is the membership graph. Authorization for shared promises is this table plus `created_by`, never UUID secrecy.

**DECIDED:** `created_by` is **not** a participant role. Creator is a fact on `Commitment`. A creator who is also responsible or recipient gets a participant row for that role.

### Roles

| Role | Meaning | Typical example |
|---|---|---|
| `RESPONSIBLE` | The person who must do the work. Completing is their act (and, in MVP, the only user’s act). | Rahul in “Rahul will send credentials.” Arjun in “I will send Rahul credentials.” |
| `RECIPIENT` | The person the promise is *for*. They should see it when sharing exists. They do not automatically get complete rights. | Arjun in “Rahul will send me credentials.” Rahul in “I will send Rahul credentials.” |
| `OBSERVER` | Accountability partner / witness. View (and later nudge). No complete/edit. | Future partner. |

There is no `CREATOR` role on this table.

### Two promises that look similar

**“I will send Rahul credentials.”** (Arjun creates)

| Fact | Value |
|---|---|
| `created_by` | Arjun |
| `RESPONSIBLE` | Arjun |
| `RECIPIENT` | Rahul (future; MVP omitted) |

**“Rahul will send me credentials.”** (Arjun creates)

| Fact | Value |
|---|---|
| `created_by` | Arjun |
| `RESPONSIBLE` | Rahul |
| `RECIPIENT` | Arjun |

Same `Commitment` type. Different participant rows. The title is the statement; responsibility is not inferred from English.

### Uniqueness

**DECIDED:** `UNIQUE (commitment_id, user_id)` where `user_id` is not null.

One Promise user has **one** row per commitment and **one** primary `role`. They are not duplicated as CREATOR+RECIPIENT rows. If Arjun is creator and recipient, he is `created_by` + participant `RECIPIENT`. If Arjun is creator and responsible, he is `created_by` + participant `RESPONSIBLE`.

**DECIDED:** Role does **not** participate in uniqueness. Two different users may both be `RESPONSIBLE` (future group work). MVP service requires **exactly one** `RESPONSIBLE`.

### Participant fields (designed)

| Field | MVP? | Why |
|---|---|---|
| `id` | Yes | BaseModel UUID. |
| `commitment` | Yes | FK, cascade delete with the commitment. |
| `user` | Yes | FK `users.User`. MVP always set. Nullable later only if we invite by email before signup (**DEFERRED**, prefer not to null this; use People or invite tokens instead). |
| `role` | Yes | `RESPONSIBLE` \| `RECIPIENT` \| `OBSERVER`. |
| `status` | Yes (write `ACTIVE` only) | Enum designed: `INVITED`, `ACTIVE`, `DECLINED`, `LEFT`. MVP inserts `ACTIVE`. |
| `joined_at` | Yes | Canonical. MVP = `created_at` of the row. Later: when an invite is accepted. |
| `completed_at` | **No** (DEFERRED) | Per-person completion for group challenges. MVP completion is on `Commitment.completed_at` only. |

### People who are not users

**DEFERRED:** `people` / contacts from PROJECT_PLAN. Do not add `person_id` on Commitment in the first schema. Recipients without accounts are a sharing/invite problem, not an MVP column.

---

## 6. Personal commitments

**DECIDED:** A personal commitment is **not** a separate model.

On create (MVP):

1. Insert `Commitment` with `created_by = request.user`, `status=PENDING`.
2. Insert `CommitmentParticipant` (`user=request.user`, `role=RESPONSIBLE`, `status=ACTIVE`).

“To myself” means the authenticated user is both creator and responsible. Recipients/observers are absent.

Shared promises later add rows; they do not migrate personal rows onto another table.

---

## 7. Event / history model — `CommitmentEvent`

**DECIDED:** Append-only history in `commitment_events`. Never update or delete events in product code (user data-deletion is a later compliance path). Logout-style “don’t delete audit rows” applies: completing/cancelling does not remove history.

### Event types

| Type | MVP? | When |
|---|---|---|
| `CREATED` | Yes | Insert. |
| `UPDATED` | Yes | Title/description/due change via PATCH. |
| `COMPLETED` | Yes | Complete. |
| `SNOOZED` | Yes | Snooze or re-snooze. |
| `UNSNOOZED` | Yes | Explicit unsnooze or snooze expiry transition to `PENDING`. |
| `CANCELLED` | Yes | Cancel. |
| `WAITING_SET` | Yes | Enter `WAITING`. |
| `WAITING_CLEARED` | Yes | Return to `PENDING` from `WAITING`. |
| `OVERDUE` | Yes (worker/lazy first detection) | First time this `due_at` is observed overdue while open. Not a stored status. |
| `PARTICIPANT_ADDED` | Deferred | Sharing. |
| `PARTICIPANT_REMOVED` | Deferred | Sharing. |
| `RESPONSIBILITY_CHANGED` | Deferred | Sharing. |

### Row shape

| Field | Notes |
|---|---|
| `id` | BaseModel UUID. |
| `commitment` | FK. |
| `actor` | FK `users.User`, **nullable**. Null = `SYSTEM` (overdue scanner, snooze expiry job). |
| `event_type` | Enum above. |
| `created_at` | Event time (BaseModel). No separate timestamp column. |
| `metadata` | JSON object, default `{}`. No secrets, no access/refresh tokens, no password hashes. |

Example metadata:

- `UPDATED`: `{ "fields": ["title", "due_at"] }`
- `SNOOZED`: `{ "snoozed_until": "<iso8601>" }`
- `OVERDUE`: `{ "due_at": "<iso8601>" }` so a later due-date edit can emit a new overdue once.

### Why events exist

| Use | How |
|---|---|
| Audit | Who completed/cancelled/snoozed, when. |
| Analytics | Time-to-complete, snooze counts, overdue rate. |
| AI insights | Load, follow-through, broken-promise patterns (later). |
| Notifications | Workers subscribe to domain events rather than polling random columns only. |
| Shared commitments | Recipients see a trustworthy history, not a silently edited row. |

`CommitmentEvent` is **not** the Kafka outbox. Outbox rows are a transport copy (§16).

---

## 8. Source

**DECIDED:** `source` is origin only. It never changes because the commitment was snoozed, shared, or completed.

| Value | Meaning |
|---|---|
| `MANUAL` | Typed in the app. MVP default. |
| `AI_TEXT` | Confirmed from a text capture. |
| `AI_SCREENSHOT` | Confirmed from a screenshot/OCR capture. |
| `IMPORT` | Imported from another system. |
| `SYSTEM` | Created by Promise itself (future automations). |

**DECIDED:** There is **no** `SHARED` source. Sharing is participants. A manual commitment that later gains Rahul is still `MANUAL`.

After AI confirmation the row is a **normal Commitment**. `source` stays `AI_TEXT` or `AI_SCREENSHOT` forever. No “proposed” status on Commitment. Proposals live on Capture until confirm (§9).

---

## 9. AI integration

**DECIDED:** AI does not own the commitment model. AI must not insert an important commitment without user confirmation (`docs/ARCHITECTURE.md` invariant 4).

```text
Capture
  ↓
AI extraction (async worker)
  ↓
Proposed commitment (DTO on Capture, not a Commitment row)
  ↓
User confirmation
  ↓
Commitment  (source=AI_TEXT | AI_SCREENSHOT, normal lifecycle)
```

| Piece | MVP commitments? | Notes |
|---|---|---|
| Separate **Capture** entity | **DEFERRED** (AI phase) | Already in ARCHITECTURE. Fields later: raw payload ref, status, error. |
| Extraction metadata | On Capture | Model, prompt version, OCR flag. Not on Commitment. |
| Confidence score | On Capture / proposal | Gate confirmation UX. Not copied onto Commitment. |
| Source reference | Later nullable `capture_id` | Add when Capture ships. |

Core tracking works if AI is down: `POST /commitments/` with `source=MANUAL` still works.

---

## 10. Dates and timezones

**DECIDED:**

| User intent | Storage |
|---|---|
| Exact due time (“6:00 PM”) | `due_at` = that instant in UTC; `due_precision=datetime` |
| Date only (“Friday”) | `due_at` = **end of that local calendar date** in `request.user.timezone` (23:59:59), converted to UTC; `due_precision=date` |
| No deadline | `due_at` null; `due_precision=none` |

APIs accept and return timezone-aware ISO-8601 timestamps. The client may send an offset; the server persists UTC.

`users.User.timezone` is a preference (`CharField`, default `UTC`). Changing it does **not** rewrite existing `due_at` values.

### Later notifications

A notification worker (not this phase):

1. Load open commitments with `due_at`.
2. Convert `due_at` to the responsible user’s `timezone`.
3. Schedule `commitment.due_soon` / FCM using local clock (exact time for `datetime`; a default local reminder hour for `date` is **OPEN**, notification phase).
4. Overdue: `now >= due_at` in UTC (same as `is_overdue`). No extra timezone conversion required for the overdue predicate.

---

## 11. Repeating commitments

**DECIDED: defer.** Recurring “study every weekday” is a **Goal** (`docs/PROJECT_PLAN.md` §2.2, `docs/DEVELOPMENT.md` Phase 6).

Why not on Commitment now:

- Recurrence needs schedule, skip/miss, and period identity (check-ins). That is a different state machine.
- Snooze/overdue/complete-once semantics break if one row means “every weekday.”
- Putting `rrule` on Commitment would force a later split anyway.

MVP may store a one-shot “Study DSA tonight.” It must not invent a hidden series.

---

## 12. Snooze

**DECIDED:**

```text
PENDING  →  SNOOZED  →  PENDING
```

- `snoozed_until` is **stored**.
- Each snooze writes `SNOOZED` (including re-snooze).
- Multiple snoozes are allowed while `PENDING` or `SNOOZED`.
- Body: `{ "snoozed_until": "<aware datetime>" }`. Must be strictly in the future. Maximum **30 days** from `now`.
- Snooze does **not** change `due_at`. It pauses nagging; it does not rewrite the original promise instant.
- While `SNOOZED` and `now < snoozed_until`, `is_overdue` is false even if `due_at` is past.
- When `now >= snoozed_until`, persist `PENDING`, clear `snoozed_until`, write `UNSNOOZED`. Do this on **write** paths and a later worker. List/detail may persist the same transition so stored status does not lag the UI. If `due_at` is still past, `is_overdue` becomes true immediately.
- Complete/cancel from `SNOOZED` is allowed.
- Wait from `SNOOZED` is not allowed.

**DEFERRED:** snooze presets (“1 hour”, “tomorrow morning”) as API sugar. Client can send an absolute `snoozed_until`.

---

## 13. Overdue

**DECIDED: derived**, not a stored status.

```text
is_overdue =
  status in (PENDING, WAITING)
  and due_at is not null
  and now >= due_at
```

| Approach | Trade-off |
|---|---|
| **Derived (chosen)** | Clock movement needs no writer. Complete-at-the-deadline has no race with an overdue worker. Queries use `status` + `due_at`. |
| Stored `OVERDUE` | Needs a job to flip rows; job lag lies; complete vs overdue races; snooze must flip back. |

Notifications still detect overdue reliably:

- **Query:** index-friendly filter on stored `status` + `due_at <= now`.
- **Event:** at most one `OVERDUE` `CommitmentEvent` per `(commitment_id, due_at)` while still open, emitted by a worker (and optionally on read if missing). That event feeds Kafka/`commitment.overdue` later.
- Snoozed rows are excluded from `is_overdue` until unsnoozed.

---

## 14. Authorization

**DECIDED:** Follow `docs/AUTHENTICATION_DESIGN.md` §10.

- Authenticate with Phase 4 JWT. Never trust client `user_id` or path UUID as proof.
- Load the commitment, then authorize. If the user may not see it, **404** `COMMITMENT_NOT_FOUND` (same as missing). Do not 403 other people’s ids.
- List querysets are filtered **before** serialization.

### Visibility (designed for sharing; MVP is the personal subset)

A user **may view** iff they are `created_by` **or** have an `ACTIVE` (later also `INVITED`, product choice) `CommitmentParticipant` row.

### Mutations (MVP = personal: creator = responsible)

| Action | MVP | Future shared |
|---|---|---|
| View | Creator or participant | Unchanged |
| PATCH title/description/due | Creator | Creator; maybe responsible. Not observer. |
| Complete | Creator / responsible (same user) | **RESPONSIBLE** only. Creator who is only recipient cannot complete for Rahul. |
| Snooze / unsnooze | Same as complete | Responsible (and maybe creator). **OPEN** whether recipient may snooze. |
| Wait / unwait | Same | Responsible or creator. |
| Cancel | Same | Creator, or responsible. **OPEN** whether recipient may cancel. |
| Add/remove participants | N/A | Creator. **DEFERRED** APIs. |

Staff/superuser is Django admin later, not Android bypass.

---

## 15. Notifications (not implemented)

Identify now; do not build FCM or `notifications` rows in this phase.

| Name | Kind | Trigger |
|---|---|---|
| `commitment.created` | Domain | Insert |
| `commitment.updated` | Domain | PATCH |
| `commitment.completed` | Domain | Complete |
| `commitment.cancelled` | Domain | Cancel |
| `commitment.snoozed` | Domain | Snooze |
| `commitment.unsnoozed` | Domain | Unsnooze / expiry |
| `commitment.waiting` | Domain | Wait / unwait (type in payload) |
| `commitment.overdue` | Domain **and** scheduled detector | First overdue observation for this `due_at` |
| `commitment.due_soon` | **Scheduled** notification, not a commitment state change | Worker: `due_at` approaching; no column flip |

`due_soon` must not be a stored commitment status. It is a notification scheduler concern (PROJECT_PLAN `commitment_due`).

---

## 16. Kafka / outbox

**DECIDED (architecture):** mutations that matter write PostgreSQL state and an outbox row in the **same transaction**. A publisher later sends to Kafka. Kafka is not on the Android CRUD path.

**DEFERRED:** `outbox_events` table, publisher process, and Kafka topics. Phase 5 implementation writes `commitment_events` in the same transaction as the commitment. Mapping onto outbox happens in the event-backbone phase so this slice stays small.

```text
DB transaction
  ├── Commitment (+ participant on create)
  ├── CommitmentEvent
  └── (later) OutboxEvent
       ↓
HTTP response
       ↓
Outbox publisher
       ↓
Kafka  promise.commitment.v1
```

| Kafka event | From |
|---|---|
| `commitment.created` | `CREATED` |
| `commitment.completed` | `COMPLETED` |
| `commitment.cancelled` | `CANCELLED` |
| `commitment.snoozed` | `SNOOZED` |
| `commitment.overdue` | `OVERDUE` event |

Envelope should match ARCHITECTURE (event id, type, timestamp, payload with commitment id and user ids). Consumers are idempotent on event id.

Do not publish raw title/description to a broadly shared topic without a privacy pass later. Payload can be ids + type until analytics needs more.

---

## 17. API design

**NOT IMPLEMENTED.** Trailing slashes match existing `/api/v1/auth/` routes. All endpoints: `IsAuthenticated`. Querysets scoped per §14.

Default DRF error envelope: `{ "error": { "code", "message", "details?" } }`.

### Collection

| Method | Path | Purpose | Auth | Success | Errors |
|---|---|---|---|---|---|
| `POST` | `/api/v1/commitments/` | Create personal commitment + responsible participant | JWT | **201** body | 400 validation, 401 |
| `GET` | `/api/v1/commitments/` | List visible commitments | JWT | **200** paginated | 401 |
| `GET` | `/api/v1/commitments/{id}/` | Detail | JWT | **200** | 401, 404 |
| `PATCH` | `/api/v1/commitments/{id}/` | Edit title, description, due_at, due_precision | JWT | **200** | 401, 404, 400, **409** `COMMITMENT_INVALID_TRANSITION` if terminal |

`POST` body (MVP):

```json
{
  "title": "Study DSA tonight",
  "description": "",
  "due_at": "2026-08-20T18:30:00+05:30",
  "due_precision": "datetime"
}
```

`source` is server-set to `MANUAL` for this endpoint. Do not accept `created_by`, `status`, or participant lists from the client in MVP.

`GET` list query (MVP): `status`, `is_overdue` (true/false), `due_before`, `due_after`. Default order: `due_at` nulls last, then `created_at` desc. Pagination: DRF page number, default 20, max 100.

Response includes stored `status`, derived `is_overdue`, `created_by`, participants (MVP: one responsible), timestamps. Never password hashes or auth tokens.

### Actions

| Method | Path | Success | Idempotency | Errors |
|---|---|---|---|---|
| `POST` | `/api/v1/commitments/{id}/complete/` | **200** current representation | Yes — already completed returns 200, no second event | 401, 404, 409 if cancelled |
| `POST` | `/api/v1/commitments/{id}/snooze/` | **200** | Re-snooze allowed; `Idempotency-Key` **DEFERRED**. Duplicate in-flight retries may apply the later `snoozed_until` | 401, 404, 400 (not future / >30d), 409 if terminal or `WAITING` |
| `POST` | `/api/v1/commitments/{id}/unsnooze/` | **200** | Already `PENDING` is a no-op 200 (retry-safe; includes never-snoozed) | 401, 404, 409 if `WAITING` or terminal |
| `POST` | `/api/v1/commitments/{id}/cancel/` | **200** | Yes — already cancelled returns 200, no second event | 401, 404, 409 if completed |
| `POST` | `/api/v1/commitments/{id}/wait/` | **200** | Already `WAITING` is a no-op 200 | 401, 404, 409 if terminal or snoozed |
| `POST` | `/api/v1/commitments/{id}/unwait/` | **200** | Already `PENDING` is a no-op 200 | 401, 404, 409 if snoozed or terminal |

Action endpoints use empty JSON `{}` except snooze:

```json
{ "snoozed_until": "2026-08-21T09:00:00+05:30" }
```

**DECIDED:** 200 not 204 on actions so clients get the new `status` / `is_overdue` without a follow-up GET. (Logout remains 204; that is auth.)

### Participants

**DEFERRED** for MVP:

```text
POST   /api/v1/commitments/{id}/participants/
DELETE /api/v1/commitments/{id}/participants/{participant_id}/
```

### Error codes (commitment)

| Code | HTTP | When |
|---|---|---|
| `COMMITMENT_NOT_FOUND` | 404 | Missing or not authorized |
| `COMMITMENT_INVALID_TRANSITION` | 409 | Illegal state change |
| `VALIDATION_ERROR` | 400 | Serializer |
| `UNAUTHENTICATED` | 401 | Existing auth handler |

---

## 18. Idempotency

Mobile retries are expected (timeouts, double-taps).

| Mutation | Behavior |
|---|---|
| Create | **Not** idempotent without a key. Two POSTs → two commitments. `Idempotency-Key` **DEFERRED**. |
| PATCH | Last write wins. No key in MVP. |
| Complete | Idempotent. Second call 200, same `completed_at`, no duplicate `COMPLETED` event. |
| Cancel | Idempotent analogously. |
| Wait / unwait / unsnooze | Idempotent if already in the target stored status (200, no extra event). |
| Snooze | **Not** status-idempotent: a new `snoozed_until` is a new snooze. Same body retry should converge (same `snoozed_until` → 200, skip extra event if unchanged). |
| Participant add/remove | **DEFERRED.** Add: unique `(commitment, user)` → second add is 200/409 conflict, not a duplicate row. |

**DECIDED:** no `Idempotency-Key` header in commitment MVP. Complete/cancel are safe retries by construction. Create double-submit is an accepted MVP risk (user can cancel extras).

---

## 19. Database design

PostgreSQL remains source of truth. Redis/Kafka unused for commitment CRUD.

### `commitments`

- PK: `id` UUID
- FK: `created_by_id` → `users_user.id` (protect/restrict delete: do not cascade-delete a user’s commitments silently without a deletion policy — **DECIDED** `PROTECT` until account deletion is designed)
- Indexes:
  - `(created_by_id, status, due_at)`
  - `(status, due_at)` for overdue scans (`PENDING`/`WAITING` and non-null `due_at`)
- Checks: status enum; `due_precision=none` iff `due_at` is null; completed/cancelled/snoozed timestamp alignment (service + DB checks where practical)

### `commitment_participants`

- PK: `id` UUID
- FK: `commitment_id` → `commitments.id` **CASCADE**
- FK: `user_id` → `users_user.id` **PROTECT**
- **UNIQUE (`commitment_id`, `user_id`)**
- Index: `(user_id, status)` for “my commitments”
- Check: role enum; status enum

### `commitment_events`

- PK: `id` UUID
- FK: `commitment_id` → `commitments.id` **CASCADE**
- FK: `actor_id` → `users_user.id` **SET NULL** (nullable system actor)
- Index: `(commitment_id, created_at)`
- No unique on `event_type` (many `SNOOZED` / `UPDATED`). Overdue de-dupe is service-level on `(commitment, due_at)` in metadata.

Django app: `backend/apps/commitments/`. Services own transitions; views stay thin (same layering as authentication).

---

## 20. Future group challenges

This schema does **not** implement Challenges. It must not block them.

| Future product | How this design holds |
|---|---|
| Two-person shared promise | `RESPONSIBLE` + `RECIPIENT` rows; APIs later. |
| Accountability partner | `OBSERVER` (or a future Partner aggregate that *adds* observers). |
| Multi-person challenge | Separate `Challenge` + membership. May attach many goals or many commitments. Multiple `RESPONSIBLE` rows are already allowed by uniqueness. Per-person `completed_at` on participants can be added later. |
| Group progress | Derived from participant/check-in rows, not a status on `Commitment` today. |

Do not encode challenge progress as `Commitment.status`.

---

## 21. MVP vs later

### MVP (first implementation after this document)

- Django `commitments` app, models, migrations, services, DRF APIs above
- Personal commitments only (creator = sole `RESPONSIBLE`)
- Stored statuses + derived `is_overdue`
- Snooze, wait, complete, cancel, unsnooze, unwait
- `CommitmentEvent` written in the same DB transaction
- Authorization via JWT + created_by/participant (personal)
- Tests: create, list scope, 404 other user, transitions, overdue derivation, snooze, idempotent complete/cancel, events, no auth bypass via UUID

### Deliberately deferred

- Participant invite APIs, `RECIPIENT` / `OBSERVER` writes
- `people` contacts, email invites, `person_id`
- Capture entity, AI extraction, `capture_id`, confidence
- Recurrence / Goals / check-ins / Challenges
- Kafka publisher, `outbox_events`, FCM, `due_soon` scheduler
- `Idempotency-Key`, undo complete, cancel reasons
- Per-participant `completed_at`
- Rate limits, Android UI
- Changing authentication

---

## 22. Final design

### Final Commitment Model

**DECIDED.** `commitments`: BaseModel + `created_by`, `title`, `description`, `status` (`PENDING|WAITING|SNOOZED|COMPLETED|CANCELLED`), `due_at`, `due_precision`, `source`, `snoozed_until`, `completed_at`, `cancelled_at`. `is_overdue` derived.

### Final Participant Model

**DECIDED.** `commitment_participants`: BaseModel + `commitment`, `user`, `role` (`RESPONSIBLE|RECIPIENT|OBSERVER`), `status` (MVP `ACTIVE`), `joined_at`. Unique `(commitment, user)`. Creator is `Commitment.created_by`, not a role.

### Final Commitment Event Model

**DECIDED.** `commitment_events`: BaseModel + `commitment`, nullable `actor`, `event_type`, `metadata`. Append-only. MVP types: `CREATED`, `UPDATED`, `COMPLETED`, `SNOOZED`, `UNSNOOZED`, `CANCELLED`, `WAITING_SET`, `WAITING_CLEARED`, `OVERDUE`.

### State Machine

**DECIDED.** Stored five statuses. OVERDUE derived. Terminal: `COMPLETED`, `CANCELLED`. Snooze ↔ pending. Wait ↔ pending. No wait↔snooze. After overdue: still complete/snooze/wait/cancel.

### Authorization Rules

**DECIDED.** JWT `request.user`. Visibility = creator or participant. 404 if not visible. MVP mutations: that user only. Future: complete = responsible; edit/cancel = creator and/or responsible; observers read-only.

### API Contract

**DECIDED.** Trailing-slash REST under `/api/v1/commitments/`. CRUD + `complete`, `snooze`, `unsnooze`, `cancel`, `wait`, `unwait`. Participant HTTP **DEFERRED**. 201 create; 200 otherwise; 401/400/404/409 as above.

### Database Schema

**DECIDED.** Three tables, UUID PKs, FKs and indexes in §19. `UNIQUE (commitment_id, user_id)`. Role not in the unique key.

### Kafka/Outbox Integration Plan

**DECIDED** path; **DEFERRED** implementation. Same transaction as state + `CommitmentEvent`; later copy to `outbox_events` → `promise.commitment.v1`. Topics: created, completed, cancelled, snoozed, overdue. `due_soon` is a scheduler event, not a status.

### MVP vs Future

**DECIDED.** §21. Personal finite commitments first; sharing, AI, goals, challenges, Kafka, notifications later.

### Open Questions

| Item | Status | Notes |
|---|---|---|
| May a **recipient** snooze or cancel a shared promise? | **OPEN** | Not needed until participant APIs. |
| Invite-before-signup (nullable `user_id` vs People vs invite token) | **OPEN** | Do not null `user_id` in MVP. |
| Default local hour for **date-only** due reminders | **OPEN** | Notification phase. Overdue still uses UTC `due_at` (end of local day at create). |
| Undo complete / uncancel | **DEFERRED** | Terminal in MVP. |
| Whether `WAITING` should suppress `due_soon` but not overdue | **OPEN** | Notification policy. `is_overdue` still true while waiting past `due_at`. |
| Account deletion vs `PROTECT` on `created_by` | **OPEN** | GDPR/deletion later. |
| Publish titles on Kafka vs ids-only | **OPEN** | Privacy vs analytics. Start ids-only. |

---

## Decision index

| Decision | Label |
|---|---|
| Single Commitment type; sharing via participants | **DECIDED** |
| Personal = creator + one RESPONSIBLE (same user) | **DECIDED** |
| `title` not `action`; no `user_id` owner column | **DECIDED** |
| OVERDUE derived | **DECIDED** |
| SNOOZED stored; `snoozed_until` stored; due_at unchanged | **DECIDED** |
| WAITING stored | **DECIDED** |
| Recurrence is Goals | **DECIDED** |
| Source enum without SHARED | **DECIDED** |
| AI confirms into a normal Commitment | **DECIDED** |
| Capture / confidence / Kafka publisher / participant APIs | **DEFERRED** |
| Recipient mutation rights; date-only reminder hour; Kafka payload privacy | **OPEN** |
| Models, migrations, APIs | **NOT IMPLEMENTED** |
