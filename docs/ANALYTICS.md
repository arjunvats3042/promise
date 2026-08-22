# Promise — Product Analytics & Telemetry Architecture

Promise implements an event-driven, privacy-preserving product analytics subsystem designed to understand user engagement, feature adoption, and retention without compromising personal privacy.

---

## 1. Privacy Boundaries & Principles

1. **Pseudonymous Identity**:
   - Analytics events are linked to a hashed, rotating pseudonymous identifier or randomized installation token.
   - Real email addresses, full legal names, phone numbers, and payment information are **strictly prohibited** from the analytics pipeline.
2. **What Is NEVER Collected**:
   - Plaintext chat messages or private shared goal conversations.
   - Commitment notes or personal reflection journal entries.
   - Device location (GPS coordinates, Wi-Fi SSIDs, IP addresses).
   - Keystroke dynamics or raw clipboard content.
3. **Data Minimization**:
   - Only approved property keys from the official taxonomy are ingested.
   - Any unwhitelisted properties or suspicious keys (e.g. `password`, `token`, `secret`, `email`) are automatically stripped and dropped at the API serializer boundary.

---

## 2. Event Taxonomy (Approved Catalog)

| Event Name | Source | Description |
| :--- | :--- | :--- |
| `goal_created` | Server / Client | Triggered when a new personal or shared goal is initialized. |
| `goal_completed` | Server | Triggered when a goal reaches 100% completion or is marked completed. |
| `goal_paused` / `goal_resumed` | Server | User pauses or resumes goal tracking. |
| `commitment_created` | Server / Client | Commitment item created. |
| `commitment_completed` | Server | Commitment marked as fulfilled. |
| `shared_goal_invited` | Server | An invitation is dispatched to a participant. |
| `shared_goal_joined` | Server | Participant accepts an invite and joins. |
| `ai_refiner_invoked` | Client | User requests AI commitment refinement. |
| `ai_insight_viewed` | Client | User opens weekly AI performance insights. |
| `chat_message_sent` | Server | Metadata counter of chat volume (message content excluded). |
| `screen_view` | Client | User navigates to a primary application screen. |

---

## 3. Architecture & Ingestion Pipeline

```
Android Client (Batched Delivery)
       │
       ▼  (POST /api/v1/analytics/events/ — Gzip & Buffered)
API Gateway / Serializer
  ├── Whitelist Validation (Strict Taxonomy)
  ├── Forbidden Keyword Stripping (PII Scrubbing)
  └── Schema Contract Check
       │
       ▼
PostgreSQL Analytics Tier (`AnalyticsEvent` table)
       │
       ▼
Aggregator Engine (Periodic Rollups)
```

### 3.1 Android Client Batching
- Events are buffered locally in an encrypted Room SQLite queue.
- Dispatched in batches of up to 50 events every 60 seconds or upon app backgrounding to conserve battery and cellular data.
- Failed transmissions back off exponentially with zero impact on core app functionality.

### 3.2 Server-Side Authoritative Events
- Critical lifecycle milestones (e.g. goal completion, member joins, billing changes) are recorded server-side during database transaction execution to guarantee 100% accuracy independent of client connectivity.

---

## 4. Retention & Lifecycle Policy

- **90-Day Raw Event Retention**: Raw, fine-grained event logs (`AnalyticsEvent`) are automatically pruned after 90 days.
- **Long-Term Aggregate Retention**: Daily and weekly aggregated statistics (e.g. daily active users, feature adoption percentages) are computed by backend rollup tasks and retained indefinitely for trend analysis.
- **Admin Access**: Aggregated reporting is accessible exclusively via Django Admin with audited staff permissions.
