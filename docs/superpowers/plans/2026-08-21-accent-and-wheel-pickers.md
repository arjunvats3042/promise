# Random accent + wheel date/time pickers

**Goal:** Pick a new app accent each process start (loaders/progress/tabs use it), and replace calendar/clock with horizontal rows of vertical wheels.

## Plan

1. **Accent session** — curated muted palette (light+dark pairs); `AccentSession` Hilt singleton picks one at process start; `PromiseTheme` applies it to `colorScheme.primary` + `extended.accent` (+ success stays productive green OR follows accent for progress consistency — follow accent for primary/accent only; progress indicators already use `colors.accent`).
2. **Wheel pickers** — replace `PromiseDatePicker` / `PromiseClockPicker` with snap `LazyColumn` wheels: Date = day | month | year; Time = hour | minute | AM/PM.
3. **Verify** — unit tests for palette pick + wheel bound helpers; `assembleDebug`.

No backend changes.
