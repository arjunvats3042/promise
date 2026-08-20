# Android UI Follow-up Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or subagent-driven-development. Steps use checkbox syntax.

**Goal:** Ship shared date/clock pickers, quiet filter switching, persisted theme, Profile polish, and stronger greetings on Android.

**Architecture:** Pure helpers for clock/theme resolution + Compose pickers; sheets consume LocalDate/LocalTime; list VMs load filters silently when Ready; ThemeController persists via SharedPreferences.

**Tech Stack:** Kotlin, Compose Material3, Hilt, JUnit, SharedPreferences.

## Global Constraints

- Android UI only; no backend/API/auth/token/repo contract changes.
- Blend with existing Promise paper/ink style.
- Do not commit unless the user asks.
- Verify with `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

## Tasks

### Task 1: DateTimeFormat helpers + tests
### Task 2: Quiet filter switching (VMs + chips + remove Goals AnimatedContent)
### Task 3: Theme persistence (SharedPreferences, system until user sets)
### Task 4: PromiseDatePicker + PromiseClockPicker + sheet wiring
### Task 5: Profile redesign + shared greeting text

Spec: `docs/superpowers/specs/2026-08-21-android-ui-followup-polish-design.md`
