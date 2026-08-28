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
2. **Thought Parser** (`apps.ai.services.thought_parser`):
   - Deconstructs stream-of-consciousness thoughts into discrete, deduplicated `COMMITMENT` and `GOAL` items.
   - **Temporal Reference Grounding**: Automatically injects dynamic server context (`Reference current time (UTC): {now_utc_iso}` + `User local timezone: {timezone}`) so relative phrases like *"tomorrow at 11pm"* or *"next Monday morning"* map deterministically to accurate ISO 8601 UTC timestamps.
   - Robust Python normalization parses and validates dates before passing items to the client.
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

## 5. Security, Privacy & Safety Guardrails

- **Pseudonymous Context Only**: Prompts receive only necessary entity metadata (e.g. title, target frequency, completion percentage). User identity, email addresses, phone numbers, and cryptographic keys are never sent to the LLM.
- **Prompt Injection Defense**: User-supplied input is wrapped in isolated, delimited text blocks with explicit system instructions prohibiting override of output schemas or system roles.
- **Failure Isolation & Graceful Fallbacks**: If Gemini is unreachable or rate-limited, fallback services take over seamlessly to guarantee continuous service availability.
