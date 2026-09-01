# Promise — Artificial Intelligence Architecture & Integration Guide

Promise leverages **Google Gemini** to provide intelligent cognitive assistance across personal and shared goals. The AI subsystem is designed with strict architectural principles prioritizing user privacy, deterministic validation, structured output contracts, fair key scheduling, and failure isolation.

---

## 1. Core Architectural Tenet: AI Is NOT the Source of Truth

> [!IMPORTANT]
> **Fundamental Design Rules**:
> - **AI is an advisory assistant, never the system of record.**
> - The AI subsystem **CANNOT directly mutate Promise domain data** (Goals, Commitments, Check-ins, Participants, or Chat messages).
> - All AI operations return structured proposals or advisory insights that **require explicit user confirmation** or client-side review before any database record is created or updated.
> - **Weekly Insights** and **Daily Motivation** are resilient: if Gemini is down or rate-limited, deterministic factual fallback generation guarantees HTTP 200 with zero broken or empty experiences.

---

## 2. Gemini Provider & Key Routing

The AI layer communicates exclusively through the backend provider abstraction [`apps.ai.providers.gemini.GeminiProvider`](file:///Users/arjunvats/Desktop/promise/backend/apps/ai/providers/gemini.py).

### 2.1 Model Routing Strategy
- **Default General Model**: `gemini-3.5-flash` (Structured goal planning, weekly insights, reflections, summaries, daily quotes).
- **Fast Interactive Model**: `gemini-3.5-flash-lite` (Thought parsing, commitment refining, natural language command routing).

### 2.2 Fair Round-Robin Key Scheduler with Health-Aware Failover
To ensure fair load distribution and high availability across multiple Google Gemini API keys, the backend uses a thread-safe `GeminiKeyScheduler`:

```
Request 1 ──► [Key 1]
Request 2 ──► [Key 2]
Request 3 ──► [Key 3]
Request 4 ──► [Key 1]
...
```

- **Fair Rotation**: Keys rotate evenly across all requests ($K_1 \rightarrow K_2 \rightarrow K_3 \rightarrow K_1 \dots$) without resetting per request.
- **Health-Aware Failover**:
  - **Key Authentication Errors** (HTTP 401/403 or invalid key): Failing key is placed in cooldown and the scheduler immediately retries on the next candidate key.
  - **Transient Failures** (HTTP 429 rate limits, HTTP 5xx server errors, network timeouts): Key is placed into temporary cooldown (default 60s) and fails over to the next candidate key in rotation.
  - **Malformed Request / Client Errors** (HTTP 400, 422, schema failures) & **Content Safety Refusals**: Non-retryable permanent errors fail-fast immediately (`AiBadRequestError`, `AiRefusalError`) without burning fallback keys.
- **Cooldown Recovery**: Keys in cooldown rejoin rotation automatically after expiration. A successful response immediately marks a key healthy and resets failure counters.
- **Strict Key Redaction**: API keys are securely stripped and masked from all logs, exceptions, and metrics.

---

## 3. Environment Variables Reference

All Gemini configuration is managed strictly on the backend:

| Variable | Required | Default | Purpose |
| :--- | :---: | :---: | :--- |
| `GEMINI_API_KEY_1` | Yes | — | Primary Google Gemini API Key |
| `GEMINI_API_KEY_2` | No | `""` | Secondary round-robin / fallback key |
| `GEMINI_API_KEY_3` | No | `""` | Tertiary round-robin / fallback key |
| `GEMINI_DEFAULT_MODEL`| No | `gemini-3.7-flash` | Standard reasoning model |
| `GEMINI_FAST_MODEL` | No | `gemini-3.7-flash` | Low-latency parsing model |
| `GEMINI_TIMEOUT_SECONDS`| No | `12` | Request timeout ceiling |
| `GEMINI_MAX_OUTPUT_TOKENS`| No | `1024` | Maximum tokens per response |

> [!CAUTION]
> **Mobile Client Safety**:
> Gemini API keys **NEVER** exist on Android devices or in client build configurations. All AI interactions occur via authenticated backend REST endpoints.

---

## 4. Structured Output & Contract Validation

The AI layer utilizes structured JSON schema definitions. All raw LLM text outputs are parsed through strict Pydantic / Python dataclass schemas before reaching the client:

1. **Commitment Refiner** (`apps.ai.services.commitment_refiner`):
   - Categorizes input into `READY`, `NEEDS_CLARIFICATION`, or `INVALID`.
   - Extracts structured `refined_title`, `due_date_suggestion`, `category`, and clarifying questions.
2. **Thought Parser & Compound Decomposition** (`apps.ai.services.thought_parser`):
   - **Compound Thought Decomposition (`_split_compound_thoughts`)**: Intelligently decomposes continuous speech streams and run-on sentences with coordinating conjunctions (`"and I have to"`, `"and send"`, `"and call"`, `"also I need to"`) into distinct, individual promise items.
   - **Universal Temporal Grounding**: Dynamically resolves arbitrary human timelines relative to the user's timezone (`Asia/Kolkata`) and UTC reference time:
     - Relative prefix offsets: `"after 3 days"`, `"in 2 weeks"`, `"in 2 hours"`
     - Relative suffix offsets: `"3 days after"`, `"2 weeks later"`, `"5 hours from now"`
     - Named intervals & periods: `"in afternoon"` (14:00), `"morning"` (09:00), `"evening"` (18:00), `"at night"` (21:00), `"noon"` (12:00)
     - Calendar dates: `"1st of September"`, `"Sep 15th"`, `"October 20"`
     - Weekend & day keywords: `"this weekend"`, `"next Monday"`, `"tomorrow"`, `"day after tomorrow"`
   - **Automatic Title Cleaning**: Automatically strips redundant temporal suffixes and modal verbs (`"I have to..."`, `"and I need to..."`) leaving concise, clean action titles.
   - **Dual-Layer Fallback**: Full on-device Kotlin safety net (`NaturalLanguageDateParser.kt`) mirrors backend Python heuristic parsing when offline.
3. **Weekly Insights** (`apps.ai.services.insights`):
   - Calculates authoritative backend facts from PostgreSQL (`commitments_total`, `commitments_completed`, `commitments_overdue`, `commitment_completion_rate`, `completion_time_slots`, `active_goals_count`, `check_ins_past_7_days`).
   - If Gemini succeeds: returns validated structured summary, observed patterns, and constructive suggestion.
   - If Gemini fails: generates deterministic factual fallback summary and patterns. **Always returns HTTP 200.**
4. **Daily Motivation Quote** (`apps.ai.services.motivation`):
   - Stored in `DailyMotivationQuote` model (`date` unique index).
   - Generates candidate quote outside transaction using Gemini; validates 8–20 words, 1 sentence, calm reflection without attribution or product mention.
   - Short atomic transaction insert catching `IntegrityError` on race conditions; deterministic date-based fallback list `FALLBACK_QUOTES[date.toordinal() % len(FALLBACK_QUOTES)]`.
   - **Canonical Endpoint**: `GET /api/v1/ai/motivation/today/`.

---

## 5. Voice Quick Capture & Natural Language Intent Architecture

1. **Zero-Jargon UI**: The voice experience removes all technical AI jargon in favor of clear human outcomes (*"Setting up deadlines and schedules"*, *"Done Speaking"*, *"Retake"*, *"Continue"*).
2. **Multilingual & Hinglish Ingestion**:
   - Full support for Hindi, Hinglish, and mixed regional phrasing (e.g. *"kal subah 8 baje mummy ko call karna hai aur roz gym jana hai"*).
   - Ingests Hindi temporal lexicon (`aaj`, `kal`, `parso`, `narso`, `subah`, `dopahar`, `shaam`, `raat`, `baje`, `roz`, `somwar`..`ravivar`).
   - Automatically translates intentions into clean English titles (`"Call Mom"`, `"Gym Workout"`).
3. **Strict Title Purification**:
   - Automatically removes scheduling prepositions, adverbs, and spoken noise (e.g. *"and set it for..."*, *"first of October 11 am"*) leaving pristine English action titles with due dates parsed into structured UTC ISO-8601 timestamps.
4. **Suspend-and-Await Creation**: The voice sheet awaits database insertion and event bus dispatch before dismissing, preventing coroutine cancellation and premature activity teardown.
5. **Glance Widget Integration**: 1-tap floating mic widget (`VoiceMicGlanceWidget`) launches translucent `VoiceQuickCaptureActivity` directly from the home screen with theme and accent color synchronization.

---

## 6. Promise Support Concierge Chatbot & Knowledge Engine

Promise includes an interactive, intelligent **Concierge Guide** (`POST /api/v1/ai/support/ask/`) that answers user questions regarding the application:

1. **Domain Grounding & Knowledge Injection**:
   - Full context grounding in `PROMISE_SUPPORT_BOT_PROMPT_V1`:
     - **App Overview**: Calm, local-first productivity system balancing one-time commitments and recurring goal practices.
     - **Commitments vs Goals**: One-time tasks with precision deadlines vs recurring habit practices.
     - **Personal Goals vs Shared Goals**: Private 1-person habits vs collaborative multi-member group habits with shared streaks and room chat.
     - **Teammate Privacy Guarantee**: Strict architectural isolation — teammates in a Shared Goal can **never** see private commitments, personal habits, or other goals.
     - **Goal Lifecycle**: Creating, pausing (retaining past streaks/history), and deleting/leaving goals.
     - **Uninstallation & Account Deletion**: Guiding users to wipe local and cloud data from Profile → Account Management before uninstalling.
     - **Group Chat AI Summary**: 1-tap `✨ AI Summary` inside shared room chat to catch up on member discussions.
     - **AI Privacy & Zero Training**: Ephemeral inference guarantee with zero model training.
     - **Notifications & Quiet Hours**: 15-minute advance alerts, morning briefs, evening streak protection, and customizable rest windows.
2. **MacBook-Style Exact-Origin Zoom Window**:
   - The support window dynamically blooms outward directly from the floating orb's screen coordinates `(originX, originY)` using bouncy spring physics (`Spring.DampingRatioLowBouncy`, `Spring.StiffnessMediumLow`), scaling from `0.12f` to `1.0f` with frosted scrim fade.
   - On dismissal (close button, backdrop tap, or Android system back button), the sheet smoothly shrinks and collapses back into the orb.
3. **1-Tap Action Deep-Link Badges (`SupportActionChip`)**:
   - Structured action badges returned in `action_chips` (`CREATE_GOAL`, `CREATE_COMMITMENT`, `OPEN_THEME`, `OPEN_SHARED_GOALS`, `OPEN_PROFILE`, `OPEN_VOICE_CAPTURE`, `OPEN_NOTIFICATIONS`) render below responses.
   - 1 tap smoothly closes the chat window and navigates directly to the designated tab or creation sheet.
4. **0ms Instant Local-First Resolution**:
   - Frequently asked questions and follow-up suggestion chips (*"What is Promise?"*, *"Can teammates see my private commitments?"*, *"Goal vs. Commitment difference"*, *"How do Shared Goals work?"*, *"How to quit a goal?"*, etc.) resolve **instantly in 0ms** from an in-memory knowledge store in `PromiseSupportViewModel`, bypassing network latency and eliminating loading spinners.
5. **Smooth Typewriter Stream Rendering**:
   - Text streams in naturally character-by-character with calibrated cadence and tap-to-skip support.
6. **Strict Guardrails for Off-Topic Queries**:
   - Truly off-topic questions (e.g. trivia, homework, general coding, or profanities) set `is_off_topic = true` and politely redirect the user back to Promise capabilities with recommended exploration chips.
7. **Floating Concierge Orb (`FloatingPromiseConciergeOrb.kt`)**:
   - Globally accessible floating assistant circle with spring-snapping physics (snaps to screen edges with natural damping), ambient radial breathing glow, live status micro-dot, and tactile haptic feedback.

---

## 7. Security, Privacy & Safety Guardrails

- **Zero User Data for Model Training (Absolute Guarantee)**: User inputs and commitments processed through AI services are ephemeral and strictly used for runtime inference. They are **never stored by third-party model providers or used to train public foundation models**.
- **Pseudonymous Context Only**: Prompts receive only necessary entity metadata (e.g. title, target frequency, completion percentage). User identity, email addresses, phone numbers, and cryptographic keys are never sent to the LLM.
- **Prompt Injection Defense**: User-supplied input is wrapped in isolated, delimited text blocks with explicit system instructions prohibiting override of output schemas or system roles.
- **Failure Isolation & Graceful Fallbacks**: If Gemini is unreachable or rate-limited, fallback services take over seamlessly to guarantee continuous service availability.

---

## 8. Interactive Notifications & Quiet Hours Architecture

1. **Interactive Notification Actions**:
   - **✓ Complete**: Directly completes a commitment from the notification shade, dismisses the alert, cancels remaining local exact alarms, updates home screen widgets, and triggers instant UI Toast feedback.
   - **⏰ Snooze**: Postpones commitment by 60 minutes, reschedules local reminder alarm, and provides instant Toast confirmation.
   - **✓ Check In**: Logs daily goal completion, preserves active streak, and updates Glance widgets.
   - **💬 Quick Reply**: RemoteInput inline chat reply inside Shared Goal message notifications.
2. **Quiet Hours (Do Not Disturb Window)**:
   - Full configuration restored in **Profile → Notifications & Reminders**.
   - Allows users to specify start and end rest times (e.g., 10:00 PM – 8:00 AM) to mute non-urgent notifications during sleep.
   - Device notification test trigger cleanly removed from the profile interface for a clutter-free production surface.
