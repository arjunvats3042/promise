# Promise — Artificial Intelligence Architecture & Integration Guide

Promise leverages **Google Gemini** to provide intelligent cognitive assistance across personal and shared goals. The AI subsystem is designed with strict architectural principles prioritizing user privacy, deterministic validation, structured output contracts, and failure isolation.

---

## 1. Core Architectural Tenet: AI Is NOT the Source of Truth

> [!IMPORTANT]
> **Fundamental Design Rule**:
> - **AI is an advisory assistant, never the system of record.**
> - The AI subsystem **CANNOT directly mutate Promise domain data** (Goals, Commitments, Check-ins, Participants, or Chat messages).
> - All AI operations return structured proposals or advisory insights that **require explicit user confirmation** or client-side review before any database record is created or updated.

---

## 2. Gemini Provider & Model Routing

The AI layer communicates exclusively through the backend provider abstraction [`apps.ai.providers.gemini.GeminiProvider`](file:///Users/arjunvats/Desktop/promise/backend/apps/ai/providers/gemini.py).

### 2.1 Model Routing Strategy
- **Default General Model**: `gemini-3.6-flash` (Structured goal planning, weekly insights, reflections, summaries).
- **Fast Interactive Model**: `gemini-3.6-flash` (Thought parsing, commitment refining, natural language command routing).

### 2.2 3-Key Automatic Fallback Chain
To ensure maximum availability under production load or individual project quota exhaustion, the provider implements a resilient 3-tier key fallback mechanism:

```
Request Dispatch
       │
       ▼
[Key 1 (GEMINI_API_KEY_1)] ──(HTTP 429 / 503 / 403)──► [Key 2 (GEMINI_API_KEY_2)] ──(HTTP 429 / 503 / 403)──► [Key 3 (GEMINI_API_KEY_3)]
       │                                                      │                                                      │
    Success                                                Success                                                Success
       ▼                                                      ▼                                                      ▼
  Return JSON                                            Return JSON                                            Return JSON
                                                                                                                     │
                                                                                                              (All Keys Exhausted)
                                                                                                                     ▼
                                                                                                           Raise AiUnavailableError (503)
```

- **Fail-Fast on Client Errors**: Non-retryable request validation errors (e.g. invalid arguments, malformed syntax) fail immediately and **do not** burn subsequent fallback keys.
- **Strict Key Redaction**: API keys are securely stripped and masked from all exception messages, stack traces, and application logs.

---

## 3. Environment Variables Reference

All Gemini configuration is managed strictly on the backend:

| Variable | Required | Default | Purpose |
| :--- | :---: | :---: | :--- |
| `GEMINI_API_KEY_1` | Yes | — | Primary Google Gemini API Key |
| `GEMINI_API_KEY_2` | No | `""` | Secondary fallback key |
| `GEMINI_API_KEY_3` | No | `""` | Tertiary fallback key |
| `GEMINI_DEFAULT_MODEL`| No | `gemini-3.6-flash` | Standard reasoning model |
| `GEMINI_FAST_MODEL` | No | `gemini-3.6-flash` | Low-latency parsing model |
| `GEMINI_TIMEOUT_SECONDS`| No | `15` | Request timeout ceiling |
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
3. **Weekly Insights** (`apps.ai.services.insights`):
   - Grounded strictly in calculated database facts (completion rate, active streaks, overdue volume).
   - Generates constructive patterns without hallucinations or synthetic numbers.
4. **Command Center** (`apps.ai.services.command_parser`):
   - Interprets natural language queries into deterministic filter parameters (`status`, `is_overdue`, `tag`).

---

## 5. Security, Privacy & Safety Guardrails

- **Pseudonymous Context Only**: Prompts receive only necessary entity metadata (e.g. title, target frequency, completion percentage). User identity, email addresses, phone numbers, and cryptographic keys are never sent to the LLM.
- **Prompt Injection Defense**: User-supplied input is wrapped in isolated, delimited text blocks with explicit system instructions prohibiting override of output schemas or system roles.
- **Failure Isolation**: If the Gemini API is unreachable, rate-limited, or responds with malformed JSON, the backend returns clean `503 Service Unavailable` or `400 Bad Request` API error envelopes with localized user-friendly error codes (`AI_SERVICE_UNAVAILABLE`). The core Promise app remains 100% operational.

---

## 6. Real AI Integration Testing

The real integration test suite validates all 15 feature contracts against live Gemini servers.

To execute the suite:
```bash
cd backend
RUN_REAL_AI_TESTS=1 GEMINI_API_KEY_1="<your_api_key>" ./.venv/bin/pytest tests/test_real_ai_integration.py -v
```
