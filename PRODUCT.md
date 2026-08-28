# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Primary users are individuals seeking deliberate, calm daily habit accountability, personal commitment tracking, and shared achievement groups without the noise, anxiety, and gamification fatigue of typical habit apps.

## Product Purpose

Promise is a personal commitment tracking, daily habit accountability, and shared achievement platform that integrates Gemini AI insights and interactive Android home screen Glance widgets. It exists to turn intentions into reliable daily follow-through with minimal friction.

## Positioning

A quiet desk: paper, ink, and one restrained accent. Accountability as a mindful, disciplined practice rather than a noisy, badge-farming casino with gratuitous streaks, confetti loops, and excessive gamified cards.

## Operating Context

- Primary interaction surface: Android smartphones (Android 12+ / Material You dynamic theming and layered dark mode).
- At-a-glance routines: Interactive Android Glance home screen widgets with full scrollable lists and 1-tap in-widget check-ins.
- Lock screen & notification shade: Rich interactive push notifications with 1-tap actions (`Complete`, `Check In`, `Snooze`) and inline direct reply for shared goal chat.
- In-app workflows: 4-tab shell with finger-synced horizontal pager (Today reading list, Commitments, Goals / Habits, Profile & Settings), Shared Goal community rooms with real-time WebSocket chat, and Gemini AI behavioral digests.

## Capabilities and Constraints

- **Mobile Client:** Native Android built with Kotlin, Jetpack Compose, Glance AppWidgets, Material 3 foundation with custom design tokens, Google Credential Manager authentication, Coroutines & Flow, Hilt, Retrofit, and OkHttp WebSockets.
- **Backend & Infrastructure:** Python 3.12, Django 6.1 (Daphne ASGI), PostgreSQL 17, Redis 8 (rate limiting, session denylist, presence, Channels fanout), Apache Kafka transactional outbox.
- **AI Assistant:** Google Gemini 3.5 API with round-robin key routing, structured outputs, weekly behavioral digests, and goal/commitment refiners.
- **Key Terminology:**
  - *Commitments*: Discrete tasks/promises categorized into `All Open`, `Overdue`, and `Completed`.
  - *Goals*: Recurring habits with frequencies, streaks, and check-in logs.
  - *Shared Goals*: Community accountability rooms with group progress and real-time chat.
  - *Check-Ins*: Proof of daily habit completion.

## Brand Commitments

- **Name:** Promise
- **Tone & Voice:** Calm, disciplined, clear, supportive, minimal, and respectful of the user's attention.
- **Visual Aesthetic:** Minimalist, modern, elegant, quiet desk feel. Restrained typography, subtle layered dark themes, custom Promise Indigo accent, and purposeful micro-interactions without flash or distraction.

## Evidence on Hand

- Native Android source tree under `android/` (`app.promise.android`)
- Backend Django ASGI service and Kafka/FCM workers under `backend/`
- Exhaustive architecture and design specifications under `docs/` (`ANDROID_DESIGN.md`, `GOAL_DESIGN.md`, `COMMITMENT_DESIGN.md`, `AUTHENTICATION_DESIGN.md`, `NOTIFICATION_DESIGN.md`, `ARCHITECTURE.md`)

## Product Principles

1. **Clarity Over Clutter:** Present essential information first (Today reading list) with clean typography hierarchy and zero cognitive overload.
2. **Frictionless Action:** Enable immediate action directly from notifications and home screen Glance widgets without requiring full app launch.
3. **Calm Reliability:** Foster steady habit formation and accountability through deliberate follow-through, avoiding anxious or manipulative mechanics.
4. **Deep Native Craft:** Adhere strictly to modern Android Material 3 guidelines (insets, touch targets, dynamic color, predictive back, spring physics).

## Accessibility & Inclusion

- Adhere to Android Material 3 accessibility standards:
  - 48×48 dp minimum touch targets with at least 8 dp spacing.
  - Scalable typography using `sp` units respecting system font scaling up to 130%.
  - High-contrast color roles for light and first-class layered dark themes.
  - Descriptive semantic content descriptions for screen readers across all interactive elements.
