# Promise — Android Foundation Design

**Status:** Design complete. Phases 9.2–9.6 exist under `android/` (bootstrap, auth/networking, shell + Home, Commitments product UI, Goals product UI). Shared Goals / FCM / Room are **not** implemented. Backend Phases 4–7 remain the API source of truth.

| Label | Meaning |
|---|---|
| **DECIDED** | Locked for the first Android slice. Do not silently change. |
| **DEFERRED** | Designed enough to avoid a rewrite; not part of Android foundation implementation. |
| **OPEN** | Needs a choice before that later slice is built. |
| **IMPLEMENTED** | Already running on the backend. Android must consume it, not reinvent it. |

This document is the source of truth for the first Android app: stack, architecture, auth storage, API client, navigation shell, design system, and MVP UX.

It fills in `docs/DEVELOPMENT.md` Phase 9 (Android Foundation) and `docs/ARCHITECTURE.md` §16–17. It does **not** replace:

- `docs/AUTHENTICATION_DESIGN.md` — Option B tokens, Keystore refresh, memory-only access
- `docs/COMMITMENT_DESIGN.md` — stored statuses, derived overdue, action APIs
- `docs/GOAL_DESIGN.md` — recurrence, check-ins, derived progress/streaks
- `docs/REDIS_DESIGN.md` — 429 `RATE_LIMITED` + `Retry-After` (client must honor, not re-tune)

Phase 9.2 added the single `:app` Gradle module, theme tokens, navigation graphs, and `LoadState` / `ActionState`. Phase 9.3 added Retrofit/OkHttp, Keystore refresh storage, login, and session restore. Phase 9.4 added the four-tab Main shell, Home with local preview data, and Light/Dark themes. Phase 9.5 added Commitments list/create/detail against backend APIs. Phase 9.6 added Goals list/create/detail/check-in/lifecycle against backend APIs. Shared Goals screens still do not exist.

Do not modify backend APIs, Docker, Redis, or Kafka to ship this design.

---

## 1. Product visual direction

**DECIDED:** Promise on Android should feel like a quiet desk: paper, ink, and one restrained accent. The product is accountability, not a dashboard and not a game.

### Qualities

Minimal, modern, elegant, calm, smooth, highly readable, uncluttered, premium without flash.

### Avoid

Excessive cards stacked in cards, heavy gradients, decorative illustration as chrome, a rainbow of semantic chips, dense stat grids, drop shadows used for hierarchy, skeuomorphic trophies, confetti on every check-in.

### How that shows up

| Surface | Direction |
|---|---|
| Home | A short “today” reading list, not charts. |
| Lists | One primary line + one supporting line. Status is type and a small color, not a badge farm. |
| Actions | One obvious primary (Complete / Check in). Secondary actions in a sheet or overflow. |
| Empty | One sentence + one action. No mascot. |
| Motion | Short, ease-out, no bounce. Completing a promise is a quiet confirmation, not a celebration loop. |

**DECIDED:** Material 3 is the implementation substrate (color roles, ripples, insets). Visual identity is **not** the default Material purple/tonal-spot theme. Theme tokens in this document override defaults.

---

## 2. Android stack

**DECIDED** for the first app module.

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin | Project plan and DEVELOPMENT.md. |
| UI | Jetpack Compose | Single UI toolkit; no XML layouts for product screens. |
| Min SDK | **26** | Keystore AES-GCM is practical; covers current Play devices. |
| Compile / target SDK | Current stable at implementation time | Do not freeze a number in this design. |
| JDK | **17** | Matches `docs/DEVELOPMENT.md` Phase 0. |
| Navigation | Navigation Compose, nested graphs | Auth graph vs main graph. Type-safe routes when the library in use supports them. |
| Presentation state | `ViewModel` + `StateFlow` | One stream per screen. No `LiveData` for new code. |
| Async | Kotlin Coroutines + `viewModelScope` / `SupervisorJob` at app scope | |
| HTTP | **OkHttp** + **Retrofit** | Interceptors, timeouts, cancellation. DEVELOPMENT.md. |
| JSON | **kotlinx.serialization** | First-party, no reflection Gson. Retrofit converter. |
| DI | **Hilt** | Official Android DI; ViewModel inject; test replacements. Not Koin for MVP. |
| Local DB | **None in foundation** | Backend is source of truth. No Room until a measured offline need. |
| Encrypted refresh | Keystore-backed AES-GCM blob (see §4) | `AUTHENTICATION_DESIGN.md` §8. |
| Images | Not required for MVP | No Coil unless avatars exist. |
| Logging | Timber or `android.util.Log` behind a debug flag | Never log tokens, passwords, or full auth headers. |

Rejected for MVP:

- Multi-module Gradle split (`:app`, `:core`, `:feature-auth`, …) — one `:app` until the app is large.
- Cookie/session auth — Android stays `Authorization: Bearer`.
- GraphQL / Kafka on device — CRUD is HTTPS to Django.
- Datastore plaintext for secrets.

**DECIDED:** API contracts on the backend are source of truth. Android DTOs map the JSON envelope; they do not invent fields the API does not return. Derived `is_overdue`, `progress`, and `current_streak` come from the server.

---

## 3. App architecture

**DECIDED:** one Gradle module `:app`. Packages, not a dozen modules.

```text
android/
└── app/
    └── src/main/java/app/promise/android/
        ├── PromiseApp.kt
        ├── MainActivity.kt
        ├── di/
        ├── core/          # result types, time, logging, config
        ├── domain/        # models + repository interfaces
        ├── data/
        │   ├── remote/    # Retrofit, interceptors, DTO mappers
        │   └── local/     # refresh-token store only (MVP)
        ├── ui/
        │   ├── theme/
        │   ├── components/
        │   ├── navigation/
        │   ├── auth/
        │   ├── home/
        │   ├── commitments/
        │   ├── goals/
        │   └── profile/
        └── notifications/ # empty façade for later FCM (see §17)
```

Application id: **`app.promise.android`**. Change only before Play listing if the id is taken.

### Boundaries

| Layer | May depend on | Must not |
|---|---|---|
| `ui` | `domain`, `ui.theme`, Hilt ViewModels | Retrofit, OkHttp, Keystore APIs |
| `domain` | Kotlin only | Android framework, Retrofit |
| `data` | `domain`, Android where storage requires it | Compose |
| `di` | wires the rest | business rules |

```text
Screen (Compose)
  ↓
ViewModel (UI state)
  ↓
Repository interface (domain)
  ↓
Repository impl (data)
  ├── Api (Retrofit)
  └── RefreshStore (Keystore)
```

**DECIDED:** no “use case” class per endpoint. Repositories are the use-case surface until duplication appears.

**DEFERRED:** Room cache, work manager sync queue, feature modules.

---

## 4. Authentication storage

**DECIDED** (locked with `AUTHENTICATION_DESIGN.md` §8):

| Secret | Where | Survives process death? |
|---|---|---|
| Access JWT | Process memory (`AuthSession` / interceptor) | **No** |
| Refresh token `{sid}.{secret}` | Keystore-wrapped ciphertext on disk | **Yes** |
| Password | Never stored | — |

**Do not** put tokens in plaintext `SharedPreferences`, unencrypted DataStore, Room, logs, or crash reports.

### Refresh store

- Generate/use an AES-GCM key in the Android Keystore (TEE / StrongBox when available).
- Encrypt the refresh token string; persist only the ciphertext + IV.
- Implementation may use EncryptedFile, EncryptedSharedPreferences, or a small custom blob. The requirement is **Keystore-backed AES-GCM**, not a library name.
- **DEFERRED:** biometric gate before reading the refresh token.

### Startup restoration

```text
Cold start
  → splash (no branded animation longer than ~400ms)
  → if no refresh blob → Auth graph (login)
  → if refresh blob → POST /api/v1/auth/refresh/
       → success: put access in memory, replace refresh blob, Main graph
       → TOKEN_EXPIRED / SESSION_REVOKED / TOKEN_INVALID: wipe store, Auth graph
       → network failure: stay on splash/retry; do not wipe the blob
```

Access JWT expiry is 15 minutes. Cold start always refreshes rather than guessing remaining JWT life from a persisted access token (access is never persisted).

### In-process refresh mutex

**DECIDED:** one app-wide `Mutex` (or single-thread dispatcher) around `/auth/refresh/`. Parallel 401s wait for the same in-flight refresh. Matches `AUTHENTICATION_DESIGN.md` (Android must serialize refresh; Redis refresh lock is deferred on the server).

After a successful refresh, replace **both** tokens. Never use an access token to mint another access token.

Honor the server’s 30-second reuse grace by retrying the **same** refresh token once if the first refresh appears to succeed on the server but the client did not persist the new token (timeout after commit). Do not invent a second grace window.

### Logout

| Action | Client | Server |
|---|---|---|
| Logout this device | `POST /api/v1/auth/logout/` with Bearer access + refresh body; then wipe memory + Keystore | Revokes session; denylist `sid` |
| Logout all devices | `POST /api/v1/auth/logout-all/` with Bearer access; then wipe local | Revokes all of this user’s sessions |

If logout HTTP fails (offline), **still wipe local tokens** and send the user to login. Stale server sessions expire; do not leave a refresh blob on a device the user asked to sign out of.

### Expired / revoked session

Protected `401 UNAUTHENTICATED` after a failed refresh → session ended. Clear store, navigate to login. Do not show stack traces or “sid denylist”.

---

## 5. API client

**DECIDED:** one Retrofit instance, `baseUrl` from build config.

| Environment | Base URL |
|---|---|
| Emulator debug | `http://10.0.2.2:8000/api/v1/` |
| Physical device debug | machine LAN URL in local `gradle.properties` (not committed) |
| Release | **OPEN** (HTTPS production host; not chosen in this phase) |

Trailing slashes match the backend.

### Timeouts

| Timeout | Value |
|---|---|
| Connect | 10s |
| Read / write | 20s |
| Call | 30s |

### Headers

- Authenticated calls: `Authorization: Bearer <access>` from memory.
- Do not attach Bearer to `/auth/login/`, `/auth/register/`, `/auth/refresh/`.
- `Accept: application/json`. JSON bodies `Content-Type: application/json`.

### 401 handling

```text
401 on a protected call
  → if already retried after refresh → fail to session-ended
  → if no refresh blob → session-ended
  → mutex: POST /auth/refresh/
       → 200: store tokens, retry original once
       → 401/400 TOKEN_* : wipe, session-ended
       → 429: do not loop; surface rate limit
       → network error: fail the original call; keep refresh blob
```

**DECIDED:** at most **one** refresh-and-retry per call. No recursive refresh. Refresh endpoint 401 never triggers another refresh.

### Cancellation

Compose leaves cancel the ViewModel’s in-flight `Job` via `viewModelScope`. OkHttp calls are cancelled with the coroutine. Do not swallow `CancellationException`.

### Error envelope

Every error body is:

```json
{ "error": { "code": "...", "message": "...", "details": {} } }
```

Map `code` to a sealed `ApiError`. User-visible copy uses a small table (§14), not `message` when it might leak internals. `INTERNAL_SERVER_ERROR` always shows a generic retry string.

### Rate limits

`429 RATE_LIMITED` + `Retry-After`: show a calm snackbar (“Try again in a moment”). Do not busy-loop. Login/register/refresh and mutations can all 429 (`REDIS_DESIGN.md`). GETs are not throttled on the server today; still handle 429 if it appears.

### Idempotency

Do not send `Idempotency-Key` until the backend supports it (**DEFERRED**). Rely on existing idempotent actions (complete, check-in upsert by `period_date`). Disable the primary button while a mutation is in flight.

---

## 6. Navigation

**DECIDED:** two root graphs.

```text
Auth
  ├── Login
  └── Register

Main (bottom bar)
  ├── Home
  ├── Commitments
  ├── Goals
  └── Profile
```

Back stack: Auth never sits under Main. After login, replace the graph. Logout pops to Auth and clears Main.

**DECIDED:** bottom navigation has four destinations. Home is the default.

**DECIDED:** this document does **not** freeze pixel layouts, list filters, or copy decks. Later UX slices (DEVELOPMENT Phases 10–11) refine screens. Foundation implements the graph and empty shells.

Deep links (later): `promise://commitment/{id}`, `promise://goal/{id}`, `promise://home`. Register the same routes now as navigation IDs so FCM can land without renaming.

---

## 7. State management

**DECIDED:** screens do not explode booleans. Each screen ViewModel exposes one `StateFlow<UiModel>`.

```text
sealed interface LoadState<out T> {
  data object Loading : LoadState<Nothing>
  data class Ready<T>(val value: T, val isRefreshing: Boolean = false) : LoadState<T>
  data object Empty : LoadState<Nothing>
  data class Error(val kind: ErrorKind, val canRetry: Boolean) : LoadState<Nothing>
}
```

Mutations use a separate `ActionState`: `Idle` | `InFlight` | `Failed(kind)`. Success updates `LoadState.Ready` from the response body (actions return the current resource).

**Stale:** MVP is online-first. There is no Room snapshot, so “stale” is not a persisted age. Pull-to-refresh sets `isRefreshing` on `Ready` without replacing the list with a spinner. After process death, Home reloads from the network.

**Empty vs error:** empty is a successful zero-item list. Error is a failed request. Do not show both.

---

## 8. Design system

**DECIDED:** a small token set. No third-party component library.

### Spacing

4 dp base: **4, 8, 12, 16, 24, 32, 48**. Screen horizontal inset **20**. Section gap **24**.

### Radius

| Token | dp | Use |
|---|---|---|
| `r-sm` | 8 | Inputs, chips |
| `r-md` | 12 | Sheets, dialogs |
| `r-lg` | 16 | Rare large panels |

Lists are **not** each wrapped in a raised card. Dividers or simple vertical rhythm.

### Elevation

**DECIDED:** almost none. Default screens are flat on `background`. Bottom sheets and dialogs may use a 1 dp hairline or scrim, not a heavy shadow. No stacked card decks.

### Typography

One humanist sans. Scale: Display 28 semibold, Title 20 semibold, Body 16 regular (~1.35 line height), Meta 13 regular. Exact font file **OPEN** (see §19).

### Iconography

Material Symbols (outlined), 24 dp, 2 dp stroke feel. No filled color icons except a single accent on the selected nav item.

### Buttons

| Role | Style |
|---|---|
| Primary | Filled, accent, 48 dp min height |
| Secondary | Text or outline, same height |
| Destructive | Text in error color (Cancel commitment / logout) |
| Tertiary | Quiet text |

One primary per screen.

### Inputs

Single-line filled-soft (surface + hairline), 16 dp padding, label above not floating chaos. Error text under the field. Email keyboard on email; password `PasswordVisualTransformation`.

### Cards

Used sparingly for **Home attention items** only (one list of rows, not nested cards). Commitment/goal lists are rows.

### Bottom sheets

Snooze, wait confirmation, check-in, create-commitment (short form), overflow actions. Drag handle, 16 dp top radius, scrim.

### Dialogs

Rare: logout confirm, discard create. Title + body + two actions. No three-button dialogs.

### Snackbars

One line, 4 s, action “Retry” when `canRetry`. Host at scaffold. Do not stack.

### Empty states

Title (one line) + supporting sentence + optional primary action (“Add a commitment”). Centered in the list area, not a full-bleed illustration.

---

## 9. Color

**DECIDED:** restrained warm-neutral palette. Color marks **state**, not decoration. Dark theme is **DEFERRED** (foundation ships light only; tokens should still be named so dark can map later).

| Token | Hex | Role |
|---|---|---|
| `background` | `#F6F3EE` | App chrome, home |
| `surface` | `#FFFcf8` | Sheets, inputs |
| `primary` | `#24352C` | Ink / primary buttons / selected nav |
| `secondary` | `#6E7A73` | Icons at rest, secondary text companion |
| `success` | `#2F6A4A` | Completed, successful check-in |
| `warning` | `#8A6A2A` | Overdue, needs attention |
| `error` | `#8B3A32` | Errors, destructive |
| `textPrimary` | `#1C1B19` | Titles, body |
| `textSecondary` | `#6B6862` | Meta, timestamps |
| `outline` | `#E4DFD6` | Hairlines |

Contrast: `textPrimary` on `background` and `primary` on white buttons must meet WCAG AA for body text. Do not put `warning` text on `background` at small sizes without checking contrast; overdue can use `warning` as a 8 dp mark plus `textPrimary` label.

No gradient fills. No colored list backgrounds.

---

## 10. Motion

**DECIDED:** subtle, 180–220 ms, `FastOutSlowIn`. No spring bounce. No looping loaders besides a small indeterminate indicator.

| Event | Motion |
|---|---|
| Screen enter (push) | Shared axis X, 200 ms |
| Bottom sheet | Slide from bottom, 220 ms |
| List item insert | Fade + 8 dp rise, 180 ms |
| Complete commitment | Row checkmark fade; optional strike of title; no confetti |
| Check-in | Control settles to success color |
| Progress / streak | Number crossfade; no counting animation past 300 ms |
| Pull-to-refresh | Platform indicator, accent color muted |

`AccessibilityManager` / `rememberReduceMotion`: replace translation with fade (or instant).

**DEFERRED:** custom completion animation polish (DEVELOPMENT Phase 12).

---

## 11. Home screen

**DECIDED:** Home answers four questions in this order. It is not a statistics dashboard.

1. **What do I need to do today?** — Expected goal check-ins for local today (from goal timezone / user timezone as returned by the API). Binary: Yes / Skip. Count: value + save.
2. **What promises are due?** — Open commitments with a due date today (and overdue), then soon without dumping the whole list.
3. **How are my goals progressing?** — At most one line per active goal: title + server `progress` / streak. No weekly bar chart in MVP.
4. **What requires attention?** — Overdue commitments (`is_overdue`) and paused goals only if they block today’s check-in. Short section, not a third dashboard.

Layout sketch (not pixel-final):

```text
Good morning, {name}
─────────────────────
Needs attention     (0–N rows; hidden if empty)
Today’s practices   (check-in controls)
Open promises       (due / overdue first)
```

Do not show consistency percent as a hero ring. Do not mix commitment Complete and goal Yes into one undifferentiated checklist (`GOAL_DESIGN.md`: different empty states and semantics).

FAB: none on Home. Creation lives on Commitments / Goals tabs (less noise).

---

## 12. Commitment UX

**DECIDED:** follow `COMMITMENT_DESIGN.md`. Stored statuses: `PENDING`, `WAITING`, `SNOOZED`, `COMPLETED`, `CANCELLED`. **OVERDUE is derived** (`is_overdue`). Do not send `status` on PATCH. Do not invent `OVERDUE` as a write.

### List

The commitment collection is **not** hidden-terminal by default on the server (unlike goals). Android’s default list **requests open work**: show `PENDING`, `WAITING`, and `SNOOZED` (client filter and/or repeated `status` queries). Completed/cancelled live behind a filter. Row: title, due (if any), overdue mark, snoozed-until if snoozed. Primary tap → detail.

### Create

Bottom sheet: title (required), optional due datetime, optional description expander. Maps to `POST /api/v1/commitments/`. `source` is server-set `MANUAL` — do not send it if the running API ignores/forbids client `source`.

### Detail

Title, description, due, stored status, overdue. Primary: **Complete** when allowed. Secondary (sheet): Snooze, Unsnooze, Wait, Cancel, Edit (PATCH title/description/due).

| Action | API | UI |
|---|---|---|
| Complete | `POST .../complete/` | Primary; idempotent 200 |
| Snooze | `POST .../snooze/` `{ snoozed_until }` | Sheet: datetime in the future, max 30 days |
| Unsnooze | `POST .../unsnooze/` | Shown when `SNOOZED` |
| Wait | `POST .../wait/` | “Waiting on someone / something” |
| Unwait | `POST .../unwait/` | **DEFERRED** until the backend route exists; do not fake it with PATCH status |
| Cancel | `POST .../cancel/` | Confirm dialog; destructive |
| Edit | `PATCH .../` | 409 if terminal |

**DECIDED:** snooze presets (“1 hour”, “tomorrow morning”) are client-computed absolute `snoozed_until` (**DEFERRED** as API sugar; allowed in UI).

Illegal transitions: map `409 COMMITMENT_INVALID_TRANSITION` to “That action isn’t available anymore” and refresh the row.

404: leave the screen, snackbar “Not found”. Same as unauthorized (backend collapses them).

---

## 13. Goal UX

**DECIDED:** follow `GOAL_DESIGN.md`. Stored statuses: `ACTIVE`, `PAUSED`, `COMPLETED`, `CANCELLED`. Completion is **explicit**, not derived from streaks. Progress and streak are **read from the API**, never computed as a second source of truth on device.

### Recurrence (create)

MVP kinds the API accepts: `DAILY`, `WEEKLY_DAYS`, `N_PER_PERIOD`. Tracking: `BINARY` or `COUNT`.

Android may label weekdays in the user’s locale. **Server math is ISO-8601 Monday weeks.** Do not send Sunday-start weeks as identity.

Timezone: default `User.timezone` from `/auth/me/`. Do not PATCH timezone after the first check-in (409 `GOAL_TIMEZONE_LOCKED`). Recurrence locks after first check-in (`GOAL_SCHEDULE_LOCKED`).

### List

Default excludes `COMPLETED` and `CANCELLED`. Row: title, one progress line, pause mark if paused.

### Create

**Full screen** (more fields than a commitment). Title, kind, tracking, optional target, optional end date. Keep the form short: start with Daily + Binary; reveal weekday chips / N-per-week only when selected.

### Detail

Progress + streak as returned. History: `GET .../check-ins/`. Actions: Pause / Resume / Complete / Cancel (confirm complete/cancel). Check-in for a date: `POST .../check-ins/` `{ status, period_date?, value?, note? }`.

| Check-in status (API) | UI |
|---|---|
| `COMPLETED` | Yes / done (binary) or value ≥ target (count) |
| `SKIPPED` | Skip today (not a miss row) |

Do **not** send `PARTIAL` or `MISSED` — not stored in MVP. Missed periods are the absence of a successful check-in.

201 on first insert for a `period_date`; 200 on upsert. Treat both as success.

Paused: hide check-in; show Resume. `is_ended` (derived): still show history; creating new check-ins follows server rules (reject after end).

---

## 14. Error UX

**DECIDED:** never show Django traces, Redis, SQL, or raw JSON to the user.

| HTTP / code | User-facing | Behavior |
|---|---|---|
| 401 `UNAUTHENTICATED` / `TOKEN_*` / `SESSION_REVOKED` | Signed out or “Session ended” | Refresh mutex then Auth graph |
| 401 `AUTHENTICATION_FAILED` | “Invalid email or password.” | Stay on login (generic; no enumeration) |
| 400 `VALIDATION_ERROR` | Field errors from `details` | Inline on form |
| 404 `COMMITMENT_NOT_FOUND` / `GOAL_NOT_FOUND` / `NOT_FOUND` | “Not found” | Pop back |
| 409 `EMAIL_ALREADY_EXISTS` | “An account with that email already exists.” | Register |
| 409 `COMMITMENT_INVALID_TRANSITION` / `GOAL_INVALID_TRANSITION` / `GOAL_SCHEDULE_LOCKED` / `GOAL_TIMEZONE_LOCKED` / `GOAL_INVALID_CHECKIN` | “That can’t be done in the current state.” | Refresh resource |
| 429 `RATE_LIMITED` | “Too many tries. Wait a moment.” | Honor `Retry-After`; disable submit briefly |
| 500 `INTERNAL_SERVER_ERROR` | “Something went wrong. Try again.” | Retry |
| Timeout / no network | “You’re offline” / “Connection timed out” | Retry; keep refresh blob |

Do not distinguish 403 vs 404 for other people’s ids (backend uses 404).

---

## 15. Offline / network

**DECIDED:** MVP is **online-first**, not offline-first. Do not promise a sync queue.

| Situation | Behavior |
|---|---|
| No network on read | Error state + retry. Empty if never loaded. |
| No network on mutation | Keep the form; snackbar; do not pretend it saved |
| Timeout | Same as failure; user may retry. Check-in/complete are idempotent on the server |
| Process death mid-mutation | Next launch refreshes session; list reload from GET |
| Stale data | Pull-to-refresh. No TTL cache in Room |

**DEFERRED:** Room cache, outbox of mutations, conflict UI.

Architecture seam: repositories already return `Result`; a future local data source can be added under `data/local/` without changing Compose.

---

## 16. Accessibility

**DECIDED** for foundation:

- Min touch target **48 dp**.
- Font scale: use `sp`; do not cap below 1.0; layouts must not clip at 1.3.
- Contrast AA for body text (`textPrimary` / `textSecondary` on `background` and `surface`).
- Every icon button has a content description (Complete, Snooze, Check in).
- Nav items labeled.
- Reduce motion: §10.
- Do not rely on color alone for overdue (mark + text).
- TalkBack order: title, status, primary action.

**DEFERRED:** full large-display / foldable layout; TalkBack traversal audit as a named QA pass.

---

## 17. Future notifications

**DECIDED:** do **not** implement FCM, notification channels, or reminder UX in foundation.

Leave seams so later work does not restructure the app:

| Seam | What to put in foundation |
|---|---|
| `notifications/` package | Empty `NotificationRouter` interface; no-op impl |
| Deep links | Nav routes for home, commitment id, goal id |
| Application class | `onCreate` hook comment / empty `Firebase` not added |
| Auth | After login, a later slice can `POST` a device token; field already optional on login (`device?`) |

Notification **actions** (Complete from the shade) will call the same repositories as the UI. Do not duplicate HTTP in a `BroadcastReceiver` later.

Quiet hours, FCM, and scheduler stay the notifications phase.

---

## 18. MVP screen map

**DECIDED:** minimum set for Android foundation + first UX slices. Foundation (9.x) may ship shells; later phases fill them.

| Screen | Mode | Notes |
|---|---|---|
| Splash / session restore | Full | Short; no marketing |
| Login | Full (Auth) | Email + password |
| Register | Full (Auth) | Email, password, name; timezone default from device zone id if valid IANA |
| Home | Full (tab) | §11 |
| Commitments list | Full (tab) | |
| Commitment detail | Full | |
| Create commitment | **Sheet** | |
| Edit commitment | Sheet or full if due picker needs it | PATCH |
| Snooze | **Sheet** | |
| Goals list | Full (tab) | |
| Goal detail | Full | Includes check-in history |
| Create goal | **Full** | Recurrence fields |
| Check-in | **Sheet** or inline on Home | Same API |
| Pause / resume / complete / cancel goal | Detail actions + confirm for terminal | |
| Profile / settings | Full (tab) | Name, email (read), logout, logout all |

Modals, not screens: snooze, create commitment, check-in, confirm destructive, logout confirm.

**Out of MVP screens:** shared invites, challenges, AI capture, password reset, social login, notification settings, dark theme, onboarding carousel.

---

## 19. Final decisions

### Android Stack

**DECIDED.** Kotlin, Jetpack Compose, Navigation Compose, ViewModel, StateFlow, Coroutines, OkHttp + Retrofit, kotlinx.serialization, Hilt, minSdk 26, JDK 17. No Room in foundation. No Koin. Backend `/api/v1/` is the contract.

### Architecture

**DECIDED.** Single `:app` module. Packages: `ui` / `domain` / `data` / `di` / `core`. Repositories, not a use-case class per call. Application id `app.promise.android`.

### Navigation

**DECIDED.** Auth graph vs Main graph. Bottom bar: Home, Commitments, Goals, Profile. Screen chrome not frozen beyond this graph.

### Auth Storage

**DECIDED.** Access JWT memory-only. Refresh token Keystore AES-GCM. No plaintext prefs. Startup refresh. Process-wide refresh mutex. Logout wipes local even if HTTP fails. Biometric unlock **DEFERRED**.

### API Client

**DECIDED.** Bearer injection except auth endpoints. One refresh-and-retry on 401. No refresh loops. Timeouts 10/20/30s. Envelope mapping. Honor 429 `Retry-After`. Emulator base URL `http://10.0.2.2:8000/api/v1/`. Production host **OPEN**.

### State Management

**DECIDED.** Sealed `LoadState` + `ActionState`. Pull-to-refresh via `isRefreshing`. No boolean explosion. No optimistic mutations in MVP.

### Design System

**DECIDED.** Spacing 4–48, radius 8/12/16, flat elevation, outlined Material Symbols, one primary button per screen, sheets for short tasks, sparse cards.

### Color

**DECIDED.** Warm paper + ink palette in §9. Light theme only for foundation. Dark theme **DEFERRED**.

### Typography

**DECIDED.** One sans for UI (humanist, high x-height). Exact font file **OPEN** (Plus Jakarta Sans vs Manrope at asset import). Scale: Display 28 semibold, Title 20 semibold, Body 16 regular, Meta 13 regular. No decorative serif in MVP.

### Motion

**DECIDED.** 180–220 ms ease-out, reduce-motion fades. No confetti. Custom polish **DEFERRED** (Phase 12).

### Home UX

**DECIDED.** Today’s practices, due/overdue promises, light progress lines, attention first. Not a stats dashboard. No Home FAB.

### Commitment UX

**DECIDED.** Matches commitment APIs and derived overdue. Create in a sheet. Complete primary. Snooze sheet with future `snoozed_until` (max 30 days). Wait uses `POST /wait/`. Unwait **DEFERRED** until backend `/unwait/` exists. No client-side stored `OVERDUE`.

### Goal UX

**DECIDED.** Matches goal APIs. Check-in `COMPLETED` / `SKIPPED` only. ISO Monday weeks on the server. Progress/streak from GET. Pause stops check-in. Create goal full screen. No PARTIAL/MISSED writes.

### Accessibility

**DECIDED.** 48 dp targets, `sp` type, AA contrast, content descriptions, reduce motion, overdue not color-only.

### MVP Screens

**DECIDED.** Splash, login, register, home, commitment list/detail/create, goal list/detail/create, profile. Sheets: create commitment, snooze, check-in, confirms.

### Deferred

**DEFERRED.** Room/offline-first, FCM, dark theme, biometric refresh gate, social login, password reset, shared commitments, challenges, AI capture, `Idempotency-Key`, custom completion animation, production base URL, unwait UI (blocked on API), feature Gradle modules, optimistic UI.

### Open Questions

| Item | Status | Notes |
|---|---|---|
| Production HTTPS API host | **OPEN** | Choose at release; debug URLs are decided. |
| Play application id collision | **OPEN** | Default `app.promise.android`; rename before listing if needed. |
| Exact font file (Jakarta vs Manrope) | **OPEN** | Same role; pick at asset import. |
| Home “due today” vs “next 7 days” cutoff | **OPEN** | Product slice; API can filter `is_overdue` / list. |
| Whether register timezone is device zone or picker | **OPEN** | Default device IANA if valid; else `UTC`. |
| Fail-closed vs retry forever on splash refresh when offline | **OPEN** | Lean: retry control, keep blob (do not wipe). |

---

## 20. Decision index

| Decision | Label |
|---|---|
| Visual: quiet paper, not dashboard | **DECIDED** |
| Compose + Hilt + Retrofit + serialization | **DECIDED** |
| Single app module | **DECIDED** |
| Access memory / refresh Keystore | **DECIDED** |
| Online-first; no Room | **DECIDED** |
| Sealed UI state | **DECIDED** |
| Four-tab main graph | **DECIDED** |
| Domain rules stay on the backend | **DECIDED** |
| FCM / dark theme / offline sync | **DEFERRED** |
| Android implementation | **9.2–9.6 IMPLEMENTED** (through Goals UI); Shared Goals / FCM / Room **NOT** |
