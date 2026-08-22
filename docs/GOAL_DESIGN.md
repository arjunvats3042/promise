# Promise — Goal Design

**Status:** Design complete. Phase 6 Goal Domain is **COMPLETE** (models, services, REST APIs, GoalEvent + OutboxEvent, publisher routing to `promise.goal.v1`, live consume/dedup). Notifications, AI, Android, shared goals, and a dedicated Goal consumer command are not.

| Label | Meaning |
|---|---|
| **DECIDED** | Locked. Do not silently change. |
| **DEFERRED** | Designed enough to avoid a rewrite; not part of the first goal implementation. |
| **OPEN** | Needs a product/engineering choice before that slice is built. |
| **IMPLEMENTED** | Models, services, REST APIs, and outbox/Kafka routing exist. See `docs/BUILD_PROGRESS.md` for what is running. |

This document is the source of truth for the goal domain. It supersedes the `goal_schedules` / `check_ins` sketch in `docs/DEVELOPMENT.md` Phase 6, the nested “schedule as a child object” sketch in `docs/ARCHITECTURE.md` §12, and PROJECT_PLAN’s six tracking modes as a single enum.

It does **not** replace `docs/COMMITMENT_DESIGN.md`. Recurrence, streaks, and period scoring belong here. Finite one-shot promises stay Commitments.

Do not treat this file as a substitute for `docs/BUILD_PROGRESS.md`. §16 is the running Goal HTTP contract.

Authentication is already implemented (Phase 4). Goal APIs use `request.user` from `JWTAccessAuthentication`. Do not change authentication to ship goals.

The transactional outbox, Kafka publisher, and generic consumer already exist (Phase 5). Goal implementation reuses them. Do not add a second outbox or publish Kafka from the request path.

---

## 1. What a Goal is

**DECIDED:** A **Goal** is a personal practice the user wants to do **consistently** across repeating local periods. It has a recurrence rule, optional numeric target, and check-ins that record what happened on specific local dates. It is not a one-shot promise, not a period row, and not a time-boxed group program.

| Concept | Meaning | Example | Domain |
|---|---|---|---|
| **Goal** | Recurring practice. Schedule + target live on one row. | “Study DSA 5 days every week.” | This document. |
| **Commitment** | One finite accountable promise. Optional deadline. No recurrence. | “I will solve 3 graph problems tonight.” | `COMMITMENT_DESIGN.md`. |
| **Check-in** | What the user recorded for **one goal period** (a local date). | “Today: completed.” / “20 pages.” | `GoalCheckIn` in this document. |
| **Challenge** | Time-boxed program with membership. | “30-day gym challenge.” | Future aggregate. May *link* goals; not a Goal subtype. |
| **Task** | Generic to-do without Promise accountability, recurrence, or check-ins. | “Buy milk.” | **Not a domain entity.** Do not add a `Task` table. |

### Why Goal is its own domain

1. **Repeating vs finite.** Streaks, missed periods, and “5× this week” are period math. Putting `rrule` on `Commitment` would mix two products (`COMMITMENT_DESIGN.md` §2).
2. **Check-ins are historical truth.** A Tuesday completion recorded on Wednesday is still Tuesday. Commitments have one lifecycle, not one row per day.
3. **Android UI.** Home “today’s goals / YES-NO” is a check-in flow. Commitment complete is a one-time action. Mixing them in one list model produces the wrong empty states.
4. **Notifications.** Daily check-in reminders are goal events. Due/overdue are commitment events.
5. **Sharing later.** Challenges wrap goals (or commitments). They must not require mutating Goal into a one-shot promise.

**DECIDED:** “I will send Rahul the proposal tomorrow” is a **Commitment**, even if typed into a goal composer. The goal MVP does not auto-convert that text. Classification is a later AI concern.

**DECIDED:** one `Goal` type. Sharing is a future membership/challenge concern, not a second goal model in MVP.

---

## 2. Lifecycle

**DECIDED:** Stored statuses are **ACTIVE**, **PAUSED**, **COMPLETED**, **CANCELLED**.

**DECIDED:** Completion is **explicit** (user action), not derived from check-in rate or `end_date`.

**DECIDED:** `COMPLETED` and `CANCELLED` are terminal.

PROJECT_PLAN lists “Archive.” That is a **list filter** over terminal goals (hide `COMPLETED`/`CANCELLED` by default), not a fifth stored status.

### Stored vs derived

| Value | Stored? | Meaning |
|---|---|---|
| `ACTIVE` | Yes | Open. Periods are expected. Check-ins allowed. |
| `PAUSED` | Yes | User stopped tracking temporarily. Periods while paused are **not** expected (not misses). Check-ins rejected until resume. |
| `COMPLETED` | Yes | Terminal success. User ended the practice on purpose. |
| `CANCELLED` | Yes | Terminal abandoned. |
| `is_ended` | **Derived** | `end_date` is set AND today’s local date (goal timezone) is after `end_date`. Status may still be `ACTIVE`. |
| Progress / streak | **Derived** | From recurrence + check-ins. Never canonical on `Goal`. |
| Missed period | **Derived** | Expected local date with no successful check-in. No `MISSED` row in MVP. |

### Transitions

```text
                    create
                      │
                      v
                +-----------+
                |  ACTIVE   |◄── resume
                +-----+-----+
               /      |      \
              v       v       v
         PAUSED   COMPLETED  CANCELLED
            │        (T)        (T)
            ├──► ACTIVE
            ├──► COMPLETED
            └──► CANCELLED
```

`T` = terminal.

### Allowed

| From | To | How |
|---|---|---|
| *(new)* | `ACTIVE` | Create |
| `ACTIVE` | `PAUSED` | Pause |
| `ACTIVE` | `COMPLETED` | Complete |
| `ACTIVE` | `CANCELLED` | Cancel |
| `PAUSED` | `ACTIVE` | Resume |
| `PAUSED` | `COMPLETED` | Complete |
| `PAUSED` | `CANCELLED` | Cancel |

### Forbidden

- Any transition **from** `COMPLETED` or `CANCELLED` (no uncomplete / uncancel in MVP).
- `PATCH` of `status` (use action endpoints, same as commitments).
- Check-in while `PAUSED`, `COMPLETED`, or `CANCELLED`.
- Pause when already `PAUSED` is a **no-op** (200, no second event). Resume when already `ACTIVE` is a no-op.

### Pause vs commitment snooze

Snooze keeps a commitment due date and hides overdue. Pause **stops expecting periods**. Original `start_date` / `end_date` / recurrence are not rewritten.

---

## 3. Core entity — `Goal`

**DECIDED:** `apps.goals` models inherit `apps.core.models.BaseModel` (UUID v4 `id`, timezone-aware `created_at` / `updated_at`). Django stores UTC (`TIME_ZONE=UTC`, `USE_TZ=True`).

Table name: `goals` (`Meta.db_table`).

**DECIDED:** owner is `created_by` (FK `users.User`). No `user_id` column name. No `GoalParticipant` table in MVP.

### Fields

| Field | Canonical? | On Goal? | Why |
|---|---|---|---|
| `id` | Canonical | Yes (BaseModel) | Public identifier. **Not authorization.** |
| `created_by` | Canonical | Yes, FK `users.User` | Owner. Set from `request.user`. Immutable after insert. |
| `title` | Canonical | Yes | The practice statement. Required. Max 255. Maps to PROJECT_PLAN `name`. Named `title` to match Commitment. |
| `description` | Canonical | Yes | Optional context. `TextField`, blank default. |
| `status` | Canonical **stored** | Yes | §2. Services own transitions. |
| `timezone` | Canonical | Yes | IANA name used for **this goal’s** local dates and period boundaries. Copied from `User.timezone` at create if omitted. See §10. |
| `start_date` | Canonical | Yes, `DateField` | First local calendar date periods may exist. Not a timestamptz. |
| `end_date` | Canonical | Yes, nullable `DateField` | Last local date periods are expected. Null = open-ended. |
| `recurrence_kind` | Canonical | Yes | `DAILY` \| `WEEKLY_DAYS` \| `N_PER_PERIOD`. See §4. |
| `weekdays` | Canonical | Yes, JSON list | ISO weekdays `1–7` (Mon–Sun). Empty unless `WEEKLY_DAYS`. |
| `period_unit` | Canonical | Yes, nullable | MVP: `WEEK` when `N_PER_PERIOD`; else null. |
| `times_per_period` | Canonical | Yes, nullable int | Required count when `N_PER_PERIOD` (MVP 1–7 for `WEEK`). Else null. |
| `tracking_kind` | Canonical | Yes | `BINARY` \| `COUNT`. What a check-in measures. Orthogonal to recurrence. |
| `target_value` | Canonical | Yes, nullable int | Required positive int if `COUNT`. Null if `BINARY`. |
| `target_unit` | Display | Yes, blank string | Optional label (`pages`, `minutes`). Not used in math. |
| `source` | Canonical | Yes | Origin enum. Server-set `MANUAL` on HTTP create. Same idea as commitments. |
| `paused_at` | Canonical | Yes, nullable timestamptz | Set iff `status=PAUSED`. Cleared on resume/complete/cancel. |
| `completed_at` | Canonical | Yes, nullable timestamptz | Set iff `status=COMPLETED`. |
| `cancelled_at` | Canonical | Yes, nullable timestamptz | Set iff `status=CANCELLED`. |
| `created_at` / `updated_at` | Canonical | Yes (BaseModel) | Audit timestamps. |
| `is_ended` | **Derived** | Serializer / query | Today (goal TZ) > `end_date` when `end_date` is set. |
| Progress / streak / missed | **Derived** | Serializer | From rule + `goal_check_ins`. |
| `reminder_time` | — | **No** (DEFERRED) | Notifications phase. |
| `user_id` (duplicate owner) | — | **No** | `created_by` is the owner. |
| `challenge_id` | — | **No** on MVP Goal | Future Challenge aggregate. Nullable FK later does not break this schema. |
| Recurrence as cron / RRULE string | — | **No** as source of truth | Columns above are canonical. Export RRULE later if needed. |
| Stored streak / percent | — | **No** | Derived. |

`source` values (aligned with commitments, no `SHARED`): `MANUAL`, `AI_TEXT`, `AI_SCREENSHOT`, `IMPORT`, `SYSTEM`.

### Invariants

- `created_by` is immutable.
- Status changes go through domain services, not `PATCH status`.
- `end_date` is null or `>= start_date`.
- Recurrence/tracking columns match §4 / §5 constraints (service + serializer; DB checks where practical).
- Completing sets `completed_at` and clears `paused_at`.
- Cancelling sets `cancelled_at` and clears `paused_at`.

### Rejected fields

| Candidate | Decision |
|---|---|
| `target` as a single overloaded field | **No.** Split `tracking_kind` + `target_value` + `target_unit`. |
| `User` timezone as the only clock | **No.** Snapshot `Goal.timezone` so history does not move. |
| `start_at` / `end_at` timestamptz | **No.** Goals are calendar-date concepts. |
| Per-day Goal rows | **No.** One Goal; many check-ins. |

---

## 4. Frequency / recurrence

**DECIDED:** The recurrence **rule on the Goal row** is the source of truth. Do not create one Goal per day. Do not run a cron engine to spawn instances. Do not add a `goal_schedules` table in MVP (a 1:1 child adds a join without a second schedule).

**DECIDED:** Recurrence and tracking are orthogonal.

| User intent | `recurrence_kind` | Other columns |
|---|---|---|
| Every local day | `DAILY` | `weekdays=[]`, `period_unit` null, `times_per_period` null |
| Every weekday (Mon–Fri) | `WEEKLY_DAYS` | `weekdays=[1,2,3,4,5]` |
| Selected weekdays | `WEEKLY_DAYS` | `weekdays` = that subset (non-empty, unique, sorted) |
| N times per week (any days) | `N_PER_PERIOD` | `period_unit=WEEK`, `times_per_period=N` (1–7) |
| N times per month | — | **DEFERRED** (`period_unit=MONTH`) |

### Period definition (deterministic)

**DECIDED:** Goal weekly periods use **ISO-8601 weeks** in `Goal.timezone`. This is the single project-wide definition for Goal weekly periods. All server progress and streak calculations use Monday-start ISO weeks. Do not use Sunday-start weeks, locale week-start, or the device calendar for server math.

| Rule | Value |
|---|---|
| Week standard | ISO-8601 |
| Monday | `1` |
| Sunday | `7` |
| Week bounds | Monday 00:00 – Sunday 23:59:59.999 in `Goal.timezone` |
| Progress / streaks | Monday-start ISO weeks only |

Android may label weekday names for display. Identity, expected periods, `week_progress`, N×/week quotas, and streaks stay ISO Monday.

| Kind | A “period” for identity | Expected cadence |
|---|---|---|
| `DAILY` | Local date | Every date in `[start_date, end_or_today]` while `ACTIVE` and not paused |
| `WEEKLY_DAYS` | Local date | Those dates whose ISO weekday is in `weekdays` |
| `N_PER_PERIOD` | Local date still (the day the user checks in) | Quota is per ISO week: `times_per_period` successful days. Any local dates in that week may fill slots. Extra successful days beyond `N` are allowed (`completed` may exceed `required`) |

**DECIDED:** Check-in identity is always a **local date**, including for “5×/week”. The week quota is derived from those dates. Two check-ins on the same local date are the same check-in (see §6).

### Queryability

Services compute “today”, “this ISO week”, and “expected dates in a range” from `(timezone, start_date, end_date, recurrence_kind, weekdays, period_unit, times_per_period, status, paused_at)`. PostgreSQL stores the rule; it does not expand a calendar table.

### Extensibility

Adding `MONTH` or “every N days” is a new `recurrence_kind` / `period_unit` value plus validation. Existing rows keep working. Do not encode future kinds as free-text cron.

### Android shape

Create/PATCH send the same columns the API stores. Example “gym 4×/week”:

```json
{
  "recurrence_kind": "N_PER_PERIOD",
  "weekdays": [],
  "period_unit": "WEEK",
  "times_per_period": 4
}
```

Example “study weekdays”:

```json
{
  "recurrence_kind": "WEEKLY_DAYS",
  "weekdays": [1, 2, 3, 4, 5],
  "period_unit": null,
  "times_per_period": null
}
```

**DECIDED:** Recurrence (and `tracking_kind` / `target_value`) is **immutable after the first check-in**. Before that, PATCH may change the rule. After that, 409 `GOAL_SCHEDULE_LOCKED`. Title/description/`end_date` remain patchable.

---

## 5. Check-in model — `GoalCheckIn`

**DECIDED:** Table `goal_check_ins`. Model `GoalCheckIn`. Inherits `BaseModel`.

### MVP tracking recommendation

PROJECT_PLAN lists binary, count, duration, frequency, scheduled, reduction.

**DECIDED for MVP:**

| Mode | Support | How |
|---|---|---|
| **A. Binary completion** | **Yes** | `tracking_kind=BINARY`. Check-in `value` must be null. Success = `status=COMPLETED`. |
| **B. Numeric measurements** | **Yes** | `tracking_kind=COUNT`. Check-in `value` required `>= 0`. Period success = `status=COMPLETED` AND `value >= goal.target_value`. |
| Duration | **DEFERRED** | Use `COUNT` + `target_unit="minutes"` if needed without a third kind. |
| Frequency / scheduled | Recurrence (§4), not a tracking kind | |
| Reduction | **DEFERRED** | Different success math (baseline → target). |
| `PARTIAL` stored status | **DEFERRED** | COUNT under target is still a stored `COMPLETED` log with `value`; success flag is derived. UI may show “10 / 20”. |

Binary is the 30–60 second YES/NO flow. Count covers “20 pages/day” without a second product.

**DECIDED:** stored check-in statuses are **COMPLETED** and **SKIPPED** only.

| Status | Meaning |
|---|---|
| `COMPLETED` | User logged the period (binary yes, or a numeric value). |
| `SKIPPED` | User explicitly declined the date. Occupies `(goal, period_date)`. Not a success. |

`MISSED` is **not** stored (§9). `PARTIAL` is not stored.

### Fields

| Field | Canonical? | Why |
|---|---|---|
| `id` | Yes | UUID PK. Not authorization. |
| `goal` | Yes | FK `goals.id` **CASCADE**. |
| `created_by` | Yes | FK `users.User` **PROTECT**. Who recorded it (`request.user`). Prepares shared goals without a rewrite. |
| `period_date` | Yes | Local calendar date **in `Goal.timezone`**. Identity. Not a timestamp. |
| `status` | Yes | `COMPLETED` \| `SKIPPED`. |
| `value` | Yes, nullable int | Null for `BINARY` and for `SKIPPED`. Required for `COUNT` + `COMPLETED`. |
| `note` | Yes, blank text | Optional. Not uniqueness. |
| `checked_at` | Yes, timestamptz | Last time this period was written. UTC. **Not** uniqueness. |
| `created_at` / `updated_at` | Yes (BaseModel) | First insert vs last row change. |

No `user` duplicate of `created_by`. No `period` timestamptz.

### Validation (service)

- Goal must be `ACTIVE`.
- `period_date >= start_date`.
- `period_date <=` today in `Goal.timezone` (no future-dated check-ins).
- If `end_date` set: `period_date <= end_date`.
- `WEEKLY_DAYS`: `period_date` weekday must be in `weekdays` (cannot check in Saturday for a weekday goal).
- `DAILY` / `N_PER_PERIOD`: any date in range.
- `SKIPPED`: `value` must be null.
- `BINARY` + `COMPLETED`: `value` must be null.
- `COUNT` + `COMPLETED`: `value` required, integer `>= 0`.

---

## 6. Check-in identity / duplicates

**DECIDED:** uniqueness is **`(goal_id, period_date)`**.

Not unique on `checked_at`. Not unique on `created_at`. Two retries with the same local date are the same check-in.

Mobile retries (timeout, double-tap) must not insert a second row. Enforce with a PostgreSQL unique constraint. Race: `select_for_update` on the Goal, then get-or-create the check-in.

### Duplicate behavior

| Incoming vs existing | HTTP | Row | Event |
|---|---|---|---|
| Identical `status`, `value`, `note` | **200** current representation | Unchanged (`checked_at` unchanged) | **None** |
| Same `period_date`, different `status` / `value` / `note` | **200** | Update in place; `checked_at` = now | `CHECKIN_UPDATED` + outbox |
| New `period_date` | **201** | Insert | `CHECKIN_RECORDED` + outbox |

`POST` is therefore **upsert-by-period**, not append-only. That is the idempotency mechanism. `Idempotency-Key` header **DEFERRED** (same as commitments).

`created_at` stays the first insert. `period_date` never moves. A late Tuesday completion does not become Wednesday.

---

## 7. Progress

**DECIDED:** progress is **derived**. Do not store percent, counts, or “this week” on `Goal`.

Let `successful(check-in)` mean:

- `BINARY`: `status=COMPLETED`
- `COUNT`: `status=COMPLETED` AND `value >= target_value`

Paused dates are **excluded** from expected sets.

### Current period (API)

| Kind | `required` | `completed` |
|---|---|---|
| `DAILY` | 1 if today is expected | 1 if today’s check-in is successful, else 0 |
| `WEEKLY_DAYS` | 1 if today is an expected weekday | 1 if today’s check-in is successful, else 0 |
| `N_PER_PERIOD` | `times_per_period` for the current ISO week | count of successful check-ins with `period_date` in that week |

COUNT also returns `value` / `target_value` for **today** when a check-in exists.

### Windows (detail)

| Name | Rule |
|---|---|
| `week_progress` | Same as current ISO week for `N_PER_PERIOD`; for daily/weekday, successful expected dates this week / expected dates this week so far (Mon…today). |
| `consistency_percent` | Successful expected periods / expected periods in `[max(start_date, today-27 days), yesterday]` (rolling ~4 weeks, excluding paused). Integer 0–100. Today omitted so an unfinished today does not punish. |

Monthly rollups and recovery copy **DEFERRED**.

Do not cache progress in Redis for MVP.

---

## 8. Streaks

**DECIDED:** streaks are **derived** at read time. Not stored on `Goal`. Not Redis-cached in MVP.

Product principle: consistency and recovery matter more than fragile streaks (`PROJECT_PLAN.md`). Late check-ins **can repair** a streak because they write historical `period_date`.

### Unit (do not mix)

| `recurrence_kind` | Streak unit | Increment | Break |
|---|---|---|---|
| `DAILY` | Local dates | Consecutive expected dates successful, walking backward from **yesterday** (or today if today already successful) | Missing expected date, or `SKIPPED` |
| `WEEKLY_DAYS` | Expected local dates only | Same walk, skipping dates not in `weekdays` | Miss or skip on an expected weekday |
| `N_PER_PERIOD` | ISO weeks | Consecutive weeks with `completed >= times_per_period`, walking backward from last **finished** week (week containing yesterday) | A finished week under quota |

### Pause, cancel, complete

| Situation | Streak |
|---|---|
| `PAUSED` | **Freeze.** Days/weeks while paused are not expected; they do not increment or break. After resume, the next expected period continues the frozen count if successful. |
| `CANCELLED` / `COMPLETED` | Derived from history still; UI may hide. No new increments. |
| Late check-in filling a miss | Recompute from rows; streak can increase. |
| Future `period_date` | Rejected; cannot inflate streak. |

`SKIPPED` breaks a daily/weekday streak (explicit non-success). For `N_PER_PERIOD` it does not add a weekly slot; the week can still succeed on other days.

---

## 9. Missed periods

**DECIDED (MVP):** **infer from absence.** Do not insert `MISSED` rows.

| Option | MVP |
|---|---|
| No row | **Yes.** Expected date + no successful check-in = missed (or still in-progress if the period is not finished — e.g. week still open). |
| Explicit `MISSED` row | **DEFERRED.** Needs a scheduler; conflicts with late check-ins. |
| `SKIPPED` row | **Yes**, only when the user skips. |

Why absence:

- Goal CRUD and check-ins work without a worker.
- Late completion stays a write to `(goal, period_date)`, not a fight with a `MISSED` row.
- Notifications can later emit `goal.checkin.missed` from a scheduler that **reads** expected dates minus check-ins, without making miss canonical.

**DECIDED:** for `N_PER_PERIOD`, a day without a row is not a miss by itself. The week is incomplete only when the ISO week has ended and `completed < times_per_period`.

---

## 10. Timezone semantics

**DECIDED:** period math uses **`Goal.timezone`**, not “whatever `User.timezone` is right now.”

| Question | Rule |
|---|---|
| What timezone belongs to a Goal? | IANA string on the Goal (`America/Kolkata`). |
| Default at creation | `request.user.timezone` if client omits `timezone`. Validate IANA (`zoneinfo`). Invalid → 400. |
| `User.timezone` changes later | **Does not** rewrite `Goal.timezone`, `start_date`, `end_date`, or `period_date`. Existing check-ins keep meaning. |
| PATCH `timezone` | Allowed only if **zero** check-ins; otherwise 409 `GOAL_TIMEZONE_LOCKED`. |
| Local date / “today” | `now` converted to `Goal.timezone`, then `.date()`. |
| ISO week | ISO-8601 Monday-start (`1` = Monday, `7` = Sunday). Monday 00:00–Sunday 23:59:59.999 in `Goal.timezone`. |
| UTC timestamps | `created_at`, `updated_at`, `checked_at`, `paused_at`, `completed_at`, `cancelled_at` are timestamptz (UTC in DB). They do **not** define the period. |
| `period_date` | Naive date whose meaning is “that calendar day in `Goal.timezone`.” |

Avoid changing historical period meaning: never backfill `period_date` after a timezone change. Lock timezone after the first check-in.

---

## 11. Commitment ↔ Goal

**DEFERRED.** Do not add `goal_id` (or a contribution table) to `Commitment` in this phase. Do not modify commitment models.

Example that is **not** MVP: completing “Solve graph problems tonight” auto-completes today’s DSA goal check-in.

Why defer:

- Phase 5 commitment schema is complete and must not move for Goals.
- Contribution rules (does one commitment fill a binary day, a count, or neither?) are a separate product.
- Personal goals are useful with manual check-ins alone.

A later nullable `commitments.goal_id` or `goal_contributions` join does not require changing recurrence or check-in identity.

**DECIDED:** Android and APIs must not imply a live link. Copy in the UI may mention both concepts; the backend will not join them yet.

---

## 12. Recurring goals (source of truth)

**DECIDED:** one Goal row for the life of the practice. Recurrence instances are **check-ins**, not cloned Goals.

Canonical schedule fields: `timezone`, `start_date`, `end_date`, `recurrence_kind`, `weekdays`, `period_unit`, `times_per_period`.

Do not pre-insert empty check-ins for future dates.

---

## 13. Authorization

Goals are **personal** in MVP.

**DECIDED:** JWT `request.user`. Visibility and all mutations = `created_by_id == request.user.id`. Anyone else, including a guessed UUID, gets **404** `GOAL_NOT_FOUND` (same pattern as commitments: do not leak existence).

| Action | Who (MVP) |
|---|---|
| View list/detail, check-in list | Owner |
| Create, PATCH, pause, resume, complete, cancel | Owner |
| Create/upsert check-in | Owner |

Shared goals / challenge membership **DEFERRED**. Do not invent observer roles now.

UUIDs are identifiers, not credentials.

---

## 14. Events / outbox

**DECIDED (when Goal is implemented):** same dual-write as commitments.

```text
BEGIN
  mutate Goal / GoalCheckIn
  INSERT goal_events
  INSERT outbox_events   -- aggregate_type = 'goal'
COMMIT
```

Then existing `publish_outbox` → Kafka → consumers using `processed_events`.

**DECIDED:** audit table `goal_events` (do not publish it directly). Correlation: `payload.goal_event_id`. No FK from `outbox_events` to `goal_events`.

**DECIDED:** Kafka topic **`promise.goal.v1`**. Key = `Goal.id` (`aggregate_id`). Check-in events use the **same** topic and key so period order is preserved. `ARCHITECTURE.md`’s `promise.checkin.v1` is **not** used in MVP (split later if needed).

The outbox publisher selects the topic from `aggregate_type`: `commitment` → `promise.commitment.v1`, `goal` → `promise.goal.v1`. Unknown types fail without producing. A dedicated `consume_goal_events` command remains **DEFERRED**. The generic `handle_record` path accepts the Goal `event_type`s so the existing consumer can process `promise.goal.v1` when pointed at that topic.

Idempotent no-ops write **neither** `GoalEvent` nor `OutboxEvent`.

### Event types

| `GoalEvent.event_type` | Kafka `event_type` | MVP when implementing APIs? |
|---|---|---|
| `CREATED` | `goal.created` | **Yes** |
| `UPDATED` | `goal.updated` | **Yes** (PATCH that changes fields) |
| `PAUSED` | `goal.paused` | **Yes** |
| `RESUMED` | `goal.resumed` | **Yes** |
| `COMPLETED` | `goal.completed` | **Yes** |
| `CANCELLED` | `goal.cancelled` | **Yes** |
| `CHECKIN_RECORDED` | `goal.checkin.created` | **Yes** |
| `CHECKIN_UPDATED` | `goal.checkin.updated` | **Yes** |
| — | `goal.checkin.missed` | **DEFERRED** (scheduler) |

Payloads: compact JSON, **no titles, notes, passwords, JWTs, or keys**. Include ids, status, `period_date`, `tracking_kind`, `value` as needed. `event_version = 1`.

Generic consumer: reuse `run_consumer` / `handle_record` / `processed_events`. Verification used group `promise-goal-events-verify-6-5` against `promise.goal.v1`. Product workers (FCM, AI, analytics) and a `consume_goal_events` command stay deferred.

---

## 15. Notifications

**DEFERRED.** Do not implement FCM, reminder columns, or schedulers in Phase 6.1–6.x models/APIs unless a later task says so.

Future triggers (consumers / scheduled work, not request-path):

| Trigger | Likely event / clock |
|---|---|
| Check-in reminder | Scheduler in `Goal.timezone` (reminder time **DEFERRED** on model) |
| Missed check-in | After expected period ends; `goal.checkin.missed` |
| Streak milestone | Derived; emit from worker, not stored streak |
| Goal completed | `goal.completed` |
| Period ending soon | Scheduler (`N_PER_PERIOD` week almost over and quota unmet) |

Quiet hours and per-goal reminder times stay in the notifications phase.

---

## 16. API design

**DECIDED:** trailing-slash REST under `/api/v1/goals/`. JWT required. 201 on create and on **new** check-in insert; **200** if a check-in for that `period_date` already existed, and on PATCH and actions (body = current Goal or check-in). Pagination: page size 20, max 100 (same as commitments).

**IMPLEMENTED** (Phase 6.4). This section is the running contract.

### Collection

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| `POST` | `/api/v1/goals/` | Create personal goal | **201** Goal | 400, 401 |
| `GET` | `/api/v1/goals/` | List **own** goals | **200** paginated | 401 |

`POST` body (MVP):

```json
{
  "title": "Study DSA 5 days every week",
  "description": "",
  "timezone": "Asia/Kolkata",
  "start_date": "2026-08-20",
  "end_date": null,
  "recurrence_kind": "N_PER_PERIOD",
  "weekdays": [],
  "period_unit": "WEEK",
  "times_per_period": 5,
  "tracking_kind": "BINARY",
  "target_value": null,
  "target_unit": ""
}
```

- `timezone` optional → `User.timezone`.
- `start_date` optional → today in the resolved timezone.
- Do not accept `created_by`, `status`, `source`, timestamps from the client. `source=MANUAL` server-side.

`GET` query: `status`, `recurrence_kind`, `tracking_kind`. Default list **excludes** `COMPLETED` and `CANCELLED` unless `status` is set. Order: `-created_at`.

List and detail use the **same** Goal serializer: stored fields + derived `is_ended`, `progress` (`current_period`, `week_progress`, `consistency_percent`), and `current_streak`. Progress and streak are not stored.

### Detail / PATCH

| Method | Path | Success | Errors |
|---|---|---|---|
| `GET` | `/api/v1/goals/{id}/` | **200** Goal (same fields as list, including `progress` and `current_streak`) | 401, 404 |
| `PATCH` | `/api/v1/goals/{id}/` | **200** | 401, 404, 400, 409 terminal or locked schedule/timezone |

PATCH may include `title`, `description`, `end_date`. Recurrence/tracking/`timezone`/`start_date` only if no check-ins. Empty PATCH is a no-op **200**, no event.

### Actions

| Method | Path | Success | Idempotency | Errors |
|---|---|---|---|---|
| `POST` | `/api/v1/goals/{id}/pause/` | **200** | Already `PAUSED` → 200, no event | 401, 404, 409 if terminal |
| `POST` | `/api/v1/goals/{id}/resume/` | **200** | Already `ACTIVE` → 200, no event | 401, 404, 409 if terminal |
| `POST` | `/api/v1/goals/{id}/complete/` | **200** | Already `COMPLETED` → 200, no event | 401, 404, 409 if `CANCELLED` |
| `POST` | `/api/v1/goals/{id}/cancel/` | **200** | Already `CANCELLED` → 200, no event | 401, 404, 409 if `COMPLETED` |

Empty JSON `{}`. **200** not 204 so Android gets new `status` without a follow-up GET.

### Check-ins

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/api/v1/goals/{id}/check-ins/` | **201** insert / **200** if that `period_date` already existed | 401, 404, 400, 409 if not `ACTIVE` |
| `GET` | `/api/v1/goals/{id}/check-ins/` | **200** paginated | 401, 404 |

`POST` body:

```json
{
  "period_date": "2026-08-20",
  "status": "COMPLETED",
  "value": null,
  "note": ""
}
```

`period_date` optional → today in `Goal.timezone`. The view always calls `record_check_in()`. Duplicate semantics §6.

`GET` query: `start_date`, `end_date` (inclusive `period_date` range), `status`, page. Order: `-period_date`, `-created_at`.

PROJECT_PLAN’s `GET .../history` is this list. Do not add a second history resource.

### Error codes

| Code | HTTP | When |
|---|---|---|
| `GOAL_NOT_FOUND` | 404 | Missing or not owner |
| `GOAL_INVALID_TRANSITION` | 409 | Illegal status change; check-in on non-ACTIVE |
| `GOAL_SCHEDULE_LOCKED` | 409 | Recurrence/tracking/`start_date` change after first check-in |
| `GOAL_TIMEZONE_LOCKED` | 409 | Timezone change after first check-in |
| `GOAL_INVALID_CHECKIN` | 400 | Date out of range, future date, weekday mismatch, value rules |
| `VALIDATION_ERROR` | 400 | Serializer |
| `UNAUTHENTICATED` | 401 | Existing auth handler |

---

## 17. Database design

PostgreSQL remains source of truth. Redis unused for goal CRUD. Kafka unused on the request path.

Django app (future): `backend/apps/goals/`. Services own transitions and check-in upsert; views stay thin.

### `goals`

- PK: `id` UUID
- FK: `created_by_id` → `users.id` **PROTECT** (same account-deletion OPEN as commitments)
- Indexes:
  - `(created_by_id, status)`
  - `(status)` only if list scans need it; owner+status is enough for MVP
- Checks (where practical): status enum; recurrence/tracking nullability; `end_date >= start_date`; `paused_at` null unless `PAUSED`

### `goal_check_ins`

- PK: `id` UUID
- FK: `goal_id` → `goals.id` **CASCADE**
- FK: `created_by_id` → `users.id` **PROTECT**
- **UNIQUE (`goal_id`, `period_date`)**
- Index: `(goal_id, period_date DESC)` covered by unique; `(created_by_id, period_date)` not required in MVP
- Checks: status enum; `value` null vs required per tracking (enforce in service if CHECK is awkward)

### `goal_events`

- PK: `id` UUID
- FK: `goal_id` → `goals.id` **CASCADE**
- FK: `actor_id` → `users.id` **SET NULL** (nullable)
- Index: `(goal_id, created_at)`
- Append-only. No unique on `event_type`.

`outbox_events` / `processed_events` already exist. Goal rows set `aggregate_type='goal'`, `aggregate_id=Goal.id`.

No `goal_schedules` table. No `check_ins` name (too generic; application tables are explicit: `goal_check_ins`).

---

## 18. MVP vs future

### MVP (first implementation after this document)

- Personal goals only (`created_by` = sole actor)
- Recurrence: daily, selected weekdays, N×/week
- Tracking: binary + count
- Check-ins upsert by `(goal, period_date)`
- Derived progress + streak (no stored counters)
- Pause / resume / complete / cancel
- `GoalEvent` + `OutboxEvent` in the same transaction
- APIs in §16
- Tests: create validation, timezone today, unique check-in, identical retry, value update, pause rejects check-in, terminal 409, other user 404, no-op pause, progress/streak fixtures, no auth bypass via UUID

### Deliberately deferred

- Shared goals, participants, Challenges
- Commitment → Goal contribution
- AI-generated goals, captures
- Duration/reduction tracking, stored `PARTIAL` / `MISSED`
- `period_unit=MONTH`, adaptive targets
- Reminders, FCM, missed-event scheduler, streak-milestone worker
- Analytics, Redis goal cache
- `Idempotency-Key`, Android UI
- Changing authentication, Commitment models, Kafka topology beyond routing `goal` to `promise.goal.v1`
- 14-day outbox deletion, DLT

---

## 19. Final design

### Final Goal Model

**DECIDED.** `goals`: BaseModel + `created_by`, `title`, `description`, `status` (`ACTIVE|PAUSED|COMPLETED|CANCELLED`), `timezone`, `start_date`, `end_date`, `recurrence_kind`, `weekdays`, `period_unit`, `times_per_period`, `tracking_kind`, `target_value`, `target_unit`, `source`, `paused_at`, `completed_at`, `cancelled_at`. `is_ended`, progress, and streak derived.

### Final GoalCheckIn Model

**DECIDED.** `goal_check_ins`: BaseModel + `goal`, `created_by`, `period_date`, `status` (`COMPLETED|SKIPPED`), `value`, `note`, `checked_at`. Unique `(goal, period_date)`.

### Goal State Machine

**DECIDED.** Four stored statuses. Terminal: `COMPLETED`, `CANCELLED`. Pause ↔ active. Complete/cancel from active or paused. Completion explicit. Archive = default list filter, not a status.

### Recurrence Model

**DECIDED.** Columns on Goal, not a schedule table, not cron, not per-day Goal rows. `DAILY` | `WEEKLY_DAYS` | `N_PER_PERIOD`+`WEEK`. ISO-8601 Monday-start weeks in `Goal.timezone` (`1` = Monday, `7` = Sunday). Locked after first check-in.

### Check-in Identity

**DECIDED.** `UNIQUE (goal_id, period_date)`. Identical retry: 200, no event. Different payload: in-place update + `CHECKIN_UPDATED`. Timestamps are not identity.

### Progress Calculation

**DECIDED.** Derived. Binary success = `COMPLETED`. Count success = `COMPLETED` and `value >= target_value`. `N_PER_PERIOD` week = successful dates in ISO week / `times_per_period`. No stored percent.

### Streak Calculation

**DECIDED.** Derived. Daily/weekday = consecutive expected dates; N×/week = consecutive successful ISO weeks. Pause freezes. Skip breaks daily/weekday. Late check-ins may repair. No cache.

### Timezone Rules

**DECIDED.** `Goal.timezone` snapshot. User timezone change does not move history. `period_date` is local date. `checked_at` is UTC. Timezone PATCH locked after first check-in.

### Commitment Relationship

**DEFERRED.** No FK. Do not modify commitments.

### Authorization Rules

**DECIDED.** JWT. Owner only. Non-owner 404 `GOAL_NOT_FOUND`. UUID is not authorization.

### Event / Outbox Plan

**DECIDED** path. Publisher routes `goal` → `promise.goal.v1` (key = `Goal.id`). Generic `handle_record` accepts Goal `event_type`s. No `consume_goal_events` command. `goal_events` + existing `outbox_events`. MVP Kafka types: created/updated/paused/resumed/completed/cancelled/checkin.created/checkin.updated. Missed **DEFERRED**. Compact payloads, no secrets.

### API Contract

**DECIDED.** `/api/v1/goals/` CRUD-ish + `pause` / `resume` / `complete` / `cancel` + nested `check-ins`. 201 create/new check-in; 200 otherwise. No `/history/` alias.

### Database Schema

**DECIDED.** `goals`, `goal_check_ins`, `goal_events`. UUID PKs. Unique `(goal_id, period_date)`. Owner `PROTECT`; goal delete **CASCADE** children.

### MVP vs Future

**DECIDED.** §18. Personal recurring goals + check-ins first; sharing, AI, notifications, commitment links, challenges later.

### Open Questions

| Item | Status | Notes |
|---|---|---|
| Locale week-start (Sunday) vs ISO Monday | **DECIDED** | ISO-8601 Monday-start (`1` = Monday, `7` = Sunday). All server progress/streak math uses this. Android may label days for display without changing identity. |
| COUNT `value=0` as a log vs “leave absent” | **DECIDED** | Allowed. Logged as a check-in; not success unless `value >= target_value`. |
| May owner shorten `end_date` behind existing check-ins? | **DECIDED** | **No.** Rejected if `end_date` is before an existing `period_date`. |
| Account deletion vs `PROTECT` on `created_by` | **OPEN** | Same bucket as commitments. |
| Publish titles/notes on Kafka | **DECIDED** | Outbox/Kafka payloads omit titles, descriptions, and notes. |
| Whether resume should require catching up missed days | **OPEN** | MVP: no; freeze and continue. |

---

## Decision index

| Decision | Label |
|---|---|
| Goal ≠ Commitment ≠ Check-in ≠ Challenge ≠ Task | **DECIDED** |
| Stored statuses ACTIVE/PAUSED/COMPLETED/CANCELLED; completion explicit | **DECIDED** |
| Recurrence columns on Goal; no `goal_schedules`; no per-day Goal | **DECIDED** |
| MVP tracking BINARY + COUNT | **DECIDED** |
| Unique `(goal, period_date)` upsert | **DECIDED** |
| Missed = absence; SKIPPED explicit | **DECIDED** |
| Progress and streak derived | **DECIDED** |
| `Goal.timezone` snapshot; ISO-8601 Monday=1 / Sunday=7 weeks | **DECIDED** |
| Commitment link | **DEFERRED** |
| Personal owner-only; 404 for others | **DECIDED** |
| `goal_events` + existing outbox; topic `promise.goal.v1` | **DECIDED** (publisher routes `goal`; generic `handle_record` accepts Goal types; no `consume_goal_events` command) |
| Notifications / AI / Android / challenges | **DEFERRED** |
| Models and services | **IMPLEMENTED** (6.2–6.3) |
| HTTP APIs | **IMPLEMENTED** (6.4) |
| Shared Goals & Chat Design | **DECIDED / LOCKED** (Phase 11.1) |

---

## 20. Shared Goals & Chat Architecture (Phase 11.1 Locked Design)

### 20.1. Canonical Membership Model: `GoalParticipant`
A single canonical membership model governs authorization, check-ins, shared progress aggregation, chat access, and notifications:

```python
class GoalParticipant(BaseModel):
    class Role(models.TextChoices):
        OWNER = "OWNER", "Owner"
        PARTICIPANT = "PARTICIPANT", "Participant"

    class Status(models.TextChoices):
        INVITED = "INVITED", "Invited"
        ACTIVE = "ACTIVE", "Active"
        DECLINED = "DECLINED", "Declined"
        LEFT = "LEFT", "Left"
        REMOVED = "REMOVED", "Removed"

    goal = models.ForeignKey("Goal", on_delete=models.CASCADE, related_name="participants")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="goal_participations")
    role = models.CharField(max_length=16, choices=Role.choices)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.ACTIVE)
    invited_at = models.DateTimeField(null=True, blank=True)
    joined_at = models.DateTimeField(null=True, blank=True)
    left_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "goal_participants"
        constraints = [
            models.UniqueConstraint(fields=["goal", "user"], name="uniq_goal_participant_user"),
        ]
```

- **Check-in Attribution**: `GoalCheckIn.participant` is the canonical reference linking the check-in to membership. `GoalCheckIn.created_by` remains the user author (`participant.user`).
- **Personal Goals**: When a personal goal is created, the creator is automatically assigned a `GoalParticipant` record (`role=OWNER`, `status=ACTIVE`).

### 20.2. Shared Progress & Streak Semantics (Non-Gamified)
Shared goal progress clearly separates individual accountability from collective group progress:

1. **Individual Participant Progress**:
   - Evaluated exactly as personal goals using that participant's own check-ins (`current_period`, `week_progress`, `consistency_percent`, `current_streak`).
2. **Collective Shared Goal Progress (`collective_progress`)**:
   - `BINARY`:
     - `DAILY` / `WEEKLY_DAYS`:
       - `current_period`: `{ "required": N_active, "completed": N_active_completed }`
       - `week_progress`: `{ "required": total_required_for_active_members, "completed": total_completed_by_active_members }`
     - `N_PER_PERIOD`:
       - `current_period`: `{ "required": times_per_period * N_active, "completed": sum_of_active_member_successful_days }`
       - `week_progress`: identical to `current_period` for weekly recurrence.
   - `COUNT`:
     - `DAILY` / `WEEKLY_DAYS`:
       - `current_period`: `{ "required": target_value * N_active, "completed": sum_of_successful_participant_values, "value_sum": sum_of_successful_participant_values, "target_sum": target_value * N_active }`
       - `week_progress`: `{ "required": target_value * total_expected_occurrences, "completed": sum_of_successful_values_for_week, "target_sum": target_value * total_expected_occurrences }`
     - `N_PER_PERIOD`:
       - `current_period`: `{ "required": target_value * times_per_period * N_active, "completed": sum_of_successful_values_for_period }`
3. **Streak Semantics**:
   - **Individual Streak**: Canonical per participant (encourages personal consistency without penalizing individual users for others' missed days).
   - **Group Consistency**: In MVP, collective progress is represented by period completion rates without artificial gamification.

### 20.3. Scoped Shared Goal Chat
Chat is private text-only messaging strictly scoped to an active Shared Goal.

```python
class ChatMessage(BaseModel):
    goal = models.ForeignKey("Goal", on_delete=models.CASCADE, related_name="chat_messages")
    sender = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="sent_goal_messages")
    body = models.TextField(max_length=2000)

    class Meta:
        db_table = "goal_chat_messages"
        indexes = [
            models.Index(fields=["goal", "created_at", "id"], name="goal_chat_msg_order_idx"),
        ]

class GoalChatReadState(BaseModel):
    goal = models.ForeignKey("Goal", on_delete=models.CASCADE, related_name="chat_read_states")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="goal_chat_read_states")
    last_read_message = models.ForeignKey("ChatMessage", on_delete=models.SET_NULL, null=True, blank=True)
    last_read_at = models.DateTimeField()

    class Meta:
        db_table = "goal_chat_read_states"
        constraints = [
            models.UniqueConstraint(fields=["goal", "user"], name="uniq_goal_chat_user_read"),
        ]
```

- **Scope**: Plain text and Unicode emojis only. No media, voice notes, files, GIFs, or social discovery.
- **Immutability**: Messages are immutable in MVP (no editing or deletion).
- **Ordering & Read Cursor**:
  - Deterministic message ordering: `(created_at, id)` ascending.
  - `last_read_at` / `last_read_message` moves strictly monotonically forward (never backward).
  - `unread_count`: count of messages with `created_at > last_read_at` excluding the user's own sent messages.

### 20.4. Privacy & Authorization Rules
- **Active Participants Only**: Access to chat history, sending, and read markers requires `GoalParticipant.status == ACTIVE`.
- **Left / Removed Users**: Instantly lose access to chat endpoints (returns 404).
- **Personal Goals**: Do not expose chat endpoints (returns 404).
- **Unrelated Users**: Return 404 Not Found (zero information leakage).

### 20.5. Outbox & Kafka Event Backbone
- On message creation:
  - `event_type`: `goal.chat.message_created.v1`
  - `topic`: `promise.goal.v1`
  - `aggregate_id`: `<goal_id>`
  - `payload`:
    ```json
    {
      "message_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "goal_id": "7b2e1f40-8b1a-4d22-90ab-5c328901f44a",
      "sender_id": "11111111-1111-1111-1111-111111111111",
      "created_at": "2026-08-22T02:15:00Z"
    }
    ```
  - Note: `sender_name` is omitted from the raw event payload; notification consumers resolve user details if needed.

### 20.6. REST API Surface
- `GET /api/v1/goals/{id}/chat/messages/`: Paginated message list (`created_at, id` ascending or cursor).
- `POST /api/v1/goals/{id}/chat/messages/`: Send message `{ "body": "..." }`.
- `POST /api/v1/goals/{id}/chat/read/`: Update read cursor `{ "last_read_message_id": "..." }`.
- `GET /api/v1/goals/{id}/chat/summary/`: Unread message count and latest snippet.

### 20.7. Android UI & Chat Interaction Architecture
1. **Design System Integration**:
   - Reuses existing Promise theme tokens (`AppColors`, `PromiseTheme`), typography, and surface elevations. No custom color palettes or ad-hoc emoji structural icons.
2. **Progress Hierarchy**:
   - Distinct, restrained presentation of *My Progress / Streak* and *Collective Progress* without gamification, ranking, or dashboard clutter.
3. **Chat Engine**:
   - Private room scoped to the Shared Goal.
   - Text and Unicode emojis in message content only.
   - Cursor-based reverse layout pagination preserving scroll offset without jumping.
   - Optimistic message sending with temporary local IDs, transitioning to authoritative server records on success or recoverable failure state on error.
   - Integrated `PromiseHaptics` (light on send, confirm on check-in, no passive haptics).

### 20.8. Real-Time Shared Goal Chat Architecture (WebSocket + REST Hybrid)
1. **Transport & Routing**:
   - **REST**: Initial history, pagination, message creation (`POST /chat/messages/`), and read markers (`POST /chat/read/`).
   - **WebSocket**: Live broadcast channel (`ws://<host>/ws/goals/{goal_id}/chat/`) backed by Django ASGI/Channels with Redis channel layer (`channels_redis`).
2. **Handshake Authentication & Security**:
   - Authenticated via HTTP header `Authorization: Bearer <access_token>` during the WebSocket upgrade handshake. Tokens are **never** passed in the query string and **never** logged.
   - Token expiry / rejection: Android client reuses the existing `SessionRefresher` / process-wide refresh mutex (refresh once, reconnect once).
3. **Transaction Invariant & Event Fanout**:
   - Strict ordering: `PostgreSQL ChatMessage + OutboxEvent` $\rightarrow$ `transaction.on_commit(...)` $\rightarrow$ WebSocket broadcast to Redis channel group `goal_chat_{goal_id}`.
   - Never broadcast a message before the DB transaction commits.
4. **Storage & Resilience Hierarchy**:
   - **PostgreSQL**: Single authoritative source of truth for messages and membership.
   - **Kafka**: Durable integration event propagation.
   - **Redis**: Ephemeral coordination and cross-worker broadcast only. Redis outages or reconnects do not lose messages; clients automatically recover missed messages via REST.
5. **Presence & FCM Notification Optimization**:
   - Ephemeral presence key in Redis (`promise:chat_presence:{goal_id}:{user_id}`) with short TTL + heartbeat.
   - Used solely as an optimization to suppress redundant FCM push notifications when the recipient is actively in the chat room. Stale presence never compromises message delivery correctness.
6. **Membership Revocation & Disconnect**:
   - When a participant becomes `LEFT` or `REMOVED`, the server immediately closes all active WebSockets for that user/goal session and rejects future handshakes.
