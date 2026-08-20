# Promise Android UI Follow-up Polish — Design Spec

**Date:** 2026-08-21  
**Status:** Approved in conversation; awaiting user review of this written spec before implementation planning  
**Scope:** Android UI only. No backend, API, auth contract, token store, or repository behavior changes.

## 1. Problem

After Phase 9.6 token/polish work, several product surfaces still feel unfinished:

1. Date and time are entered as raw `YYYY-MM-DD` / `HH:mm` text fields (create commitment, snooze custom, goal end date). Values are hard to read and not modern.
2. Commitments/Goals filter chips flash white on press and show a green progress/refresh indicator when switching filters.
3. Profile reads as a sparse settings stub rather than an editorial identity screen.
4. Theme always starts as Light because `ThemeController` hardcodes `PromiseThemeMode.Light` and does not persist.
5. Greetings on Login and Home need stronger shared hierarchy and presence.

## 2. Goals

- Replace every user-entered date/time control with shared Promise pickers.
- Make filter switching quiet (no flash, no spinner).
- Persist Light/Dark; first launch follows system until the user chooses.
- Redesign Profile to match Home’s paper/ink language.
- Unify and improve greeting presentation on Login and Home.

## 3. Non-goals

- Backend, Redis, Kafka, FCM, Room, Home networking API, Goals product rules.
- Changing due-date semantics, snooze validation, or goal end-date API payloads (only the input UI).
- Making Plus Jakarta Sans the default (still gated on Pixel font QA from Phase 9.6).
- Screenshot golden infrastructure.
- Free-scrolling tab pager / carousel.

## 4. Decisions (locked)

| Topic | Decision |
| --- | --- |
| Date/time coverage | **Everywhere** the user enters date or time: create commitment, snooze custom, create goal end date |
| Picker approach | Shared custom Promise pickers (not Material dialogs) |
| Clock interaction | Analog clock with rotatable hands + AM/PM; large digital readout for clarity |
| Filter loader | No green progress indicator on filter change; pull-to-refresh may still show progress |
| Filter press | No white/ripple flash on chip press |
| Theme | Remember last Light/Dark choice; if never chosen, follow system |
| Profile | Identity header + calmer appearance control + clear sign-out |
| Greetings | Shared stronger visual treatment on Login + Home |

## 5. Architecture

### 5.1 Shared components (`ui/components/`)

**`PromiseDatePicker`**
- Inputs: `LocalDate` value, `onDateChange`, optional `enabled`, optional min/max.
- UI: month title, prev/next month controls, weekday headers, day grid.
- Selected day: restrained green fill or ink text + quiet tonal chip; unselected days use primary/secondary ink.
- Accessibility: each day is a 48dp target; selected date announced.

**`PromiseClockPicker`**
- Inputs: `LocalTime` (or hour/minute), `onTimeChange`, `enabled`.
- UI:
  - Large digital readout (e.g. `6:00`) with clear AM/PM segment control.
  - Analog face with **two-step mode**: Hour first, then Minute. Tapping the hour or minute digits in the readout switches which hand is active; dragging on the face rotates only the active hand.
  - AM/PM toggles remap 12-hour face to 24-hour `LocalTime` for callers.
- Visibility: readout and AM/PM must meet AA contrast on sheet surface in Light and Dark.

**`PromiseDateField` / `PromiseTimeField` (thin wrappers)**
- Summary row showing formatted value (`EEE, d MMM` / `h:mm a` locale-aware).
- Tap expands inline picker in the sheet (preferred) or reveals it below the row — no opaque typed text fields for these values.
- Emits `LocalDate` / `LocalTime` (or ISO strings only at the sheet’s existing parse boundary).

### 5.2 Sheet wiring

| Surface | Date | Time |
| --- | --- | --- |
| `CreateCommitmentSheet` Date mode | Yes | No |
| `CreateCommitmentSheet` Date & time | Yes | Yes |
| `SnoozeSheet` custom | Yes | Yes |
| `CreateGoalSheet` end date | Yes (optional empty) | No |

Existing `parseDue` / snooze Instant / goal end-date string conversion stays at the sheet boundary. Pickers feed typed values; sheets still produce the same API strings.

### 5.3 Filter chips + loading

**Visual**
- Chip `indication = null` (or equivalent) so press does not flash white.
- Keep 48dp min height and selected accent color.

**State**
- `selectFilter` must not set `isRefreshing = true` and must not replace a Ready list with full-screen `Loading` when a Ready list already exists.
- Preferred: keep current items visible, swap filter, load silently in background; on success replace items. Optional quiet error toast/text if load fails — do not blank the list for filter taps.
- `PullToRefreshBox` `isRefreshing` only for explicit pull-to-refresh (and initial cold load may still use the existing Loading column).
- Goals: remove `AnimatedContent` fade tied to filter change (the “white fade on the button/list” sensation).

### 5.4 Theme persistence

Extend `ThemeController` (or a small `ThemePreferences` store it owns):

- Storage: DataStore Preferences (preferred) or SharedPreferences if DataStore is not already in the module — match existing Android deps if possible.
- Keys:
  - `theme_user_set` (boolean) or nullable stored mode.
  - `theme_mode` = `light` | `dark` when user has chosen.
- Resolution:
  1. If user has chosen → that mode.
  2. Else → system dark/light (`isSystemInDarkTheme()` / `UiModeManager`) at composition or controller init.
- Profile Light/Dark continues to call `setMode`, which persists and sets `user_set`.
- No backend sync in this pass.

### 5.5 Profile redesign

Composition (top → bottom):

1. Screen title “Profile” (title large) or rely on identity as hero — prefer identity-first: muted greeting optional, **name as display**, email as meta.
2. Avatar circle (48–56dp), surfaceMuted, outline, initials — same language as Home.
3. Quiet surfaceMuted identity band optional (no elevation, no card shadow).
4. Appearance: segmented Light / Dark control (full-width 48dp targets), not bare Material radios.
5. Sign out: destructive text action with breathing room; confirm haptic remains after logout succeeds (existing SessionViewModel behavior).

### 5.6 Greetings

- Shared presentation helper/composable for greeting text: Meta 13sp medium, secondary ink, `0.2sp` tracking.
- Login: keep typewriter reducer; semantic string is full greeting; improve spacing under brand.
- Home: same visual weight as Login greeting; optional short opacity settle (respect reduced motion).
- Do not add decorative looping animation.

## 6. Testing

- Unit: date/time formatting helpers; 12h clock ↔ `LocalTime` mapping including noon/midnight edges; theme resolution (unset→system, set→persisted).
- Unit: filter select does not emit refreshing when already Ready (ViewModel assertion on state transitions).
- Unit: existing commitment/snooze/goal submit still produce correct ISO / precision when fed picker values.
- No new backend tests required.
- Pixel QA checklist: pickers Light/Dark, filter tap quiet, theme survives process death, Profile hierarchy, greetings.

## 7. Risks

- Analog clock gesture vs sheet drag: consume pointer only on the clock face; keep sheet drag handle usable.
- Filter silent reload: brief stale list under a new filter is acceptable; avoid spinner flash.
- Theme flash on cold start: read persisted preference as early as practical (Application/`ThemeController` init) before first frame if feasible.

## 8. File touch list (expected)

- New: `ui/components/PromiseDatePicker.kt`, `PromiseClockPicker.kt` (and thin field wrappers if needed)
- `CreateCommitmentSheet.kt`, `SnoozeSheet.kt`, `CreateGoalSheet.kt`
- `CommitmentsListScreen.kt`, `CommitmentsListViewModel.kt`
- `GoalsListScreen.kt`, `GoalsListViewModel.kt`
- `ThemeController.kt` (+ prefs helper/module as needed)
- `ProfileScreen.kt`
- `LoginScreen.kt` / greeting helpers, `HomeScreen.kt`
- Matching unit tests under `android/app/src/test/...`

## 9. Success criteria

- No typed YYYY-MM-DD / HH:mm fields remain for user date/time entry on the listed sheets.
- Clock time is obviously readable (digital + AM/PM) in Light and Dark.
- Filter taps: no white flash, no green progress indicator.
- Relaunch remembers last theme; first install follows system until Profile choice.
- Profile and greetings feel consistent with the quiet editorial Home language.
