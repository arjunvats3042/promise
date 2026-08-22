# Promise — Notification & Reminder System Design

**Status:** Phase 10 Notifications & Reminders (10.1–10.6) **COMPLETE** (Architecture, backend models, dispatch engine, multi-device tracking, Android channels & receiver, direct actions via WorkManager, preferences UI).

| Label | Meaning |
|---|---|
| **DECIDED** | Locked for future implementation. Do not silently change. |
| **DEFERRED** | Designed enough to avoid a rewrite; not part of the initial notification slice. |
| **OPEN** | Specific implementation detail to be finalized in future phases. |
| **IMPLEMENTED** | Underlying domain APIs, outbox, Kafka event topics, notification preferences, reminders table, device registration API, FCM dispatcher, Android notification receiver, WorkManager direct action execution, Profile notification preferences UI, and Android navigation exist. |

This document is the source of truth for notifications and reminders in Promise. It complements:
- `docs/COMMITMENT_DESIGN.md` — commitment domain & action APIs
- `docs/GOAL_DESIGN.md` — goal recurrence, progress, and check-ins
- `docs/OUTBOX_DESIGN.md` — transactional outbox & integration events
- `docs/ANDROID_DESIGN.md` — Android architecture & deep linking

---

## 1. Product & UX Principles

Promise notifications exist to help users keep commitments and maintain consistency.

1. **Calm & Dignified**: Notifications never scold, nag, guilt-trip, or employ manipulative urgency. They read like a trusted personal assistant delivering a quiet, timely reminder.
2. **Context-Aware & Actionable**: Each notification delivers enough clarity so the user knows what is due and can take an immediate one-tap action (Complete, Snooze, Check in) directly from the lock screen/shade.
3. **Never Remind About the Resolved**: If an item has been completed, snoozed, cancelled, paused, or already checked in, pending or delivered notifications for that item must be cancelled or dismissed.
4. **Resolute Respect for Sleep & Focus**: Quiet hours and local timezones are hard boundaries. Non-critical reminders are automatically held until the user's active day begins.
5. **Zero Spam / Zero Fatigue**: Low default frequency. No celebratory confetti spam, no generic engagement pings.

---

## 2. Notification Taxonomy

| Category | Type | Purpose | Urgency / Priority |
|---|---|---|---|
| **Commitment** | `commitment.due_soon` | Early reminder before a deadline | Low / Default (Quiet) |
| **Commitment** | `commitment.due_now` | Exact-time alert for timed commitments | Medium / High (Alerting) |
| **Commitment** | `commitment.overdue` | Gentle morning check on a missed promise | Low / Passive |
| **Commitment** | `commitment.snooze_expired` | Alert when a snoozed promise re-enters active state | Medium / Alerting |
| **Commitment** | `commitment.waiting_followup` | Periodic check on commitments marked waiting | Low / Passive |
| **Goal / Practice** | `goal.today_practice` | Daily morning reminder for active practices | Low / Passive |
| **Goal / Practice** | `goal.checkin_reminder` | Evening check-in reminder for incomplete practices | Low / Passive |
| **Goal / Practice** | `goal.at_risk_consistency` | Heads-up before a period expires without target | Low / Passive |
| **Goal / Practice** | `goal.streak_protection` | Evening gentle prompt when a streak is active and unchecked | Low / Passive |
| **Goal / Practice** | `goal.lifecycle` | Notice when a goal is completed or paused | Low / Informational |
| **System** | `auth.new_device_login` | Security alert when account is signed in on a new device | High / Immediate |

---

## 3. Commitment Notification Flows

### 3.1. Commitment: Due Soon (`commitment.due_soon`)
- **Trigger**: 1 hour before `due_at` for `DATETIME` commitments, or morning of `due_at` (09:00 local) for `DATE` commitments.
- **Content**: 
  - *Title*: Due soon
  - *Body*: "Send revised proposal to Elena" (due at 14:00)
- **Actions**: `[Complete]` · `[Snooze 1h]` · `[Open]`
- **Deep Link**: `promise://commitment/{id}`
- **Action Behavior**:
  - *Complete*: Calls `POST /api/v1/commitments/{id}/complete/` in background, dismisses notification, triggers confirmation haptic.
  - *Snooze 1h*: Calls `POST /api/v1/commitments/{id}/snooze/` with `{"snoozed_until": "<now + 1h>"}`. **`due_at` is never modified by snooze.**
- **Resolved Behavior**: Suppressed if already completed, snoozed, or cancelled.

### 3.2. Commitment: Due Now (`commitment.due_now`)
- **Trigger**: At exact `due_at` timestamp (for precision = `DATETIME`).
- **Content**:
  - *Title*: Due now
  - *Body*: "Team weekly sync preparation"
- **Actions**: `[Complete]` · `[Snooze 30m]` · `[Open]`
- **Deep Link**: `promise://commitment/{id}`
- **Action Behavior**:
  - *Complete*: Marks complete, clears notification.
  - *Snooze 30m*: Calls `POST /api/v1/commitments/{id}/snooze/` with `{"snoozed_until": "<now + 30m>"}`.
- **Resolved Behavior**: Suppressed if completed before deadline.

### 3.3. Commitment: Overdue (`commitment.overdue`)
- **Trigger**: Morning review after deadline (09:15 local time), delivered once per overdue item.
- **Content**:
  - *Title*: Overdue promise
  - *Body*: "Review contract draft was due yesterday. Would you like to reschedule or complete it?"
- **Actions**: `[Complete]` · `[Snooze to Tomorrow]` · `[Open]`
- **Deep Link**: `promise://commitment/{id}`
- **Action Behavior**: `[Snooze to Tomorrow]` calls `POST /api/v1/commitments/{id}/snooze/` with tomorrow's start timestamp. `due_at` remains intact.

### 3.4. Commitment: Snooze Expired (`commitment.snooze_expired`)
- **Trigger**: At `snoozed_until` timestamp.
- **Content**:
  - *Title*: Reminder
  - *Body*: "Call Dr. Vance (snoozed earlier)"
- **Actions**: `[Complete]` · `[Snooze 1h]` · `[Open]`
- **Deep Link**: `promise://commitment/{id}`

---

## 4. Goal / Practice Notification Flows

### 4.1. Goal: Today's Practice Reminder (`goal.today_practice`)
- **Trigger**: Morning anchor time (default 08:30 local) on scheduled recurrence days.
- **Content**:
  - *Title*: Today’s practice
  - *Body*: "Morning 20m meditation · 4-day streak"
- **Actions**: `[Check in]` · `[Open]`
- **Deep Link**: `promise://goal/{id}`
- **Action Behavior**: For `BINARY` goals, records `COMPLETED` for today's period date. For `COUNT` goals, opens quick entry sheet.
- **Resolved Behavior**: Suppressed if user already checked in earlier today.

### 4.2. Goal: Evening Check-in & Streak Protection (`goal.streak_protection`)
- **Trigger**: Evening anchor time (default 20:30 local) if active goal has `needsCheckInToday == true`.
- **Content**:
  - *Title*: Practice check-in
  - *Body*: "Read 15 pages · Check in to keep your 12-day streak active"
- **Actions**: `[Done today]` · `[Open]`
- **Deep Link**: `promise://goal/{id}`

### 4.3. Goal: At-Risk Consistency Reminder (`goal.at_risk_consistency`)
- **Trigger**: Saturday morning for weekly goals if `completed < required` with remaining days equal to shortfall.
- **Content**:
  - *Title*: Weekly consistency
  - *Body*: "Strength training: 2 of 3 sessions completed this week."
- **Actions**: `[Check in]` · `[Open]`
- **Deep Link**: `promise://goal/{id}`

---

## 5. Notification Actions & Direct Action Semantics

- **Commitment Actions**:
  - `Complete`: Invokes `POST /api/v1/commitments/{id}/complete/`.
  - `Snooze`: Invokes `POST /api/v1/commitments/{id}/snooze/` with `snoozed_until`. **`due_at` is never changed by snooze.**
- **Goal Actions**:
  - `Done` / `Check in`: Invokes `POST /api/v1/goals/{id}/check-ins/` (`status: "COMPLETED"`).
  - `Skip`: Invokes `POST /api/v1/goals/{id}/check-ins/` (`status: "SKIPPED"`).

---

## 6. Timing & Quiet Hours

- **Quiet Hours**: `22:00` to `08:00` in user's local timezone.
- Non-critical reminders scheduled during quiet hours are held and delivered at quiet hours exit (08:05 local).
- Exact `DATETIME` user commitments scheduled inside quiet hours are posted silently without audio interruption.
- All scheduled times respect `User.timezone` and adapt automatically across timezone/DST changes.

---

## 7. Durable Notification Preferences

User preferences are stored as durable server-side state (in PostgreSQL `user_notification_preferences`) so settings synchronize across multiple devices:

```json
{
  "enabled": true,
  "commitments_due_soon": true,
  "commitments_due_now": true,
  "commitments_overdue": true,
  "goals_daily_reminder": true,
  "goals_daily_reminder_time": "08:30:00",
  "goals_evening_reminder": true,
  "goals_evening_reminder_time": "20:30:00",
  "quiet_hours_enabled": true,
  "quiet_hours_start": "22:00:00",
  "quiet_hours_end": "08:00:00"
}
```

---

## 8. Durable Idempotency & Deduplication

- Kafka `processed_events` prevents re-processing outbox events.
- In addition, scheduled reminder delivery enforces its own **durable reminder identity**:
  - Deterministic Reminder Key: `{user_id}:{entity_type}:{entity_id}:{event_type}:{target_timestamp_or_period}`
  - Example: `usr_123:commitment:com_456:due_soon:2026-08-22T14:00:00Z`
  - Example: `usr_123:goal:gol_789:daily_checkin:2026-08-22`
- Delivered/sent reminders are durably tracked so restarts or duplicate scheduled triggers never emit duplicate notifications.

---

## 9. Architecture Boundary & Scheduling Strategy

```
[ PostgreSQL Mutation ] ──(Atomic Tx)──► [ outbox_events ]
                                                │
                                                ▼
                                     [ publish_outbox ]
                                                │
                                                ▼
                                        [ Kafka Topics ]
                                                │
                                                ▼
                                [ Notification Consumer / Worker ]
                                                │
                          ┌─────────────────────┴─────────────────────┐
                          ▼                                           ▼
                 [ FCM Push Service ]                     [ Durable Reminder State ]
                          │
                          ▼
               [ Android App Client ]
           (Notification Shade & Actions)
```

- **Notification Scheduler/Worker**: Responsible for background event consumption and time-based reminder checks. Concrete scheduling mechanism (cron, worker process, Celery, or Redis timer) will be finalized in Phase 10.2 based on existing infrastructure inspection.

---

## 10. Phase 10.3 — FCM Push Dispatch Architecture

### 10.1. Device Registration Model: `UserDevice`
```python
class UserDevice(BaseModel):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="devices")
    fcm_token = models.CharField(max_length=512, unique=True)
    device_id = models.CharField(max_length=128, db_index=True)
    device_name = models.CharField(max_length=128, blank=True, default="")
    platform = models.CharField(max_length=32, default="ANDROID")
    app_version = models.CharField(max_length=32, blank=True, default="")
    is_active = models.BooleanField(default=True)
    last_seen_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "user_devices"
        constraints = [
            models.UniqueConstraint(fields=["user", "device_id"], name="unique_user_device_id"),
        ]
        indexes = [
            models.Index(fields=["user", "is_active"], name="user_devices_active_idx"),
            models.Index(fields=["device_id", "user"], name="user_devices_id_idx"),
        ]
```

- **Token Rotation**: `POST /api/v1/notifications/devices/` updates `fcm_token` and `last_seen_at` for the unique `(user, device_id)` pair. If the `fcm_token` was previously registered to another user, ownership transfers to current user.
- **Logout Semantics**:
  - `POST /api/v1/auth/logout/`: Deactivates only the current calling device identified by `device_id` (`is_active = False`).
  - `POST /api/v1/auth/logout-all/`: Deactivates all `UserDevice` records for the authenticated user.

### 10.2. Per-Device Delivery Model: `NotificationDelivery`
To guarantee reliable delivery across multiple devices without premature status completion:

```python
class NotificationDelivery(BaseModel):
    class DeliveryStatus(models.TextChoices):
        PENDING = "PENDING", "Pending"
        SENT = "SENT", "Sent"
        FAILED = "FAILED", "Failed"
        CANCELLED = "CANCELLED", "Cancelled"

    reminder = models.ForeignKey("Reminder", on_delete=models.CASCADE, related_name="deliveries")
    user_device = models.ForeignKey("UserDevice", on_delete=models.CASCADE, related_name="deliveries")
    status = models.CharField(max_length=32, choices=DeliveryStatus.choices, default=DeliveryStatus.PENDING)
    attempts = models.PositiveIntegerField(default=0)
    last_error = models.TextField(blank=True, default="")
    sent_at = models.DateTimeField(null=True, blank=True)
    next_attempt_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "notification_deliveries"
        constraints = [
            models.UniqueConstraint(fields=["reminder", "user_device"], name="unique_reminder_device_delivery"),
        ]
        indexes = [
            models.Index(fields=["status", "next_attempt_at"], name="delivery_pending_idx"),
        ]
```

### 10.3. Dispatch Reliability & Multi-Device State Machine
Reminders are **not** marked `DISPATCHED` simply because one device succeeds:

```text
Reminder (SCHEDULED)
  │ (Worker claims due reminder and generates NotificationDelivery for each active UserDevice)
  ▼
NotificationDelivery per active device
  │
  ├──► SUCCESS ────────────► NotificationDelivery marked SENT (sent_at = now)
  │
  ├──► INVALID_TOKEN ──────► UserDevice marked is_active = False, NotificationDelivery marked CANCELLED
  │
  └──► TRANSIENT_FAILURE ──► NotificationDelivery remains PENDING (attempts++, next_attempt_at = backoff)
```

- **Logical Reminder Lifecycle**:
  - A success on Device A does **not** suppress retries for Device B that temporarily failed.
  - The logical `Reminder` transitions to `DISPATCHED` **only** when all deliverable device attempts are resolved (i.e. no remaining `PENDING` `NotificationDelivery` rows with `attempts < MAX_ATTEMPTS`).
  - If all active devices fail permanently (max attempts reached), the `Reminder` transitions to `FAILED`.

### 10.4. FCM Token Invalidation
- A device token is marked `is_active = False` **only** when FCM specifically returns `UnregisteredError` (`registration-token-not-registered`) or `SenderIdMismatchError`.
- Generic payload errors, invalid JSON parameters, or network timeouts never deactivate tokens.

### 10.5. FCM Priority & Payload Tiers
- **High / Alerting Priority** (`AndroidConfig(priority="high")`):
  - Exact-time commitments (`commitment.due_now`), security alerts (`auth.new_device_login`).
- **Normal / Default Priority** (`AndroidConfig(priority="normal")`):
  - `commitment.due_soon`, `commitment.overdue`, `goal.today_practice`, `goal.streak_protection`, `goal.at_risk_consistency`.

### 10.6. Android Direct Actions Boundary
- Direct actions from notifications (`Complete`, `Snooze`, `Check in`) are handled by an Android background service/workmanager that calls existing domain repositories (`CommitmentRepository`, `GoalRepository`, `AuthSession`).
- No raw networking or auth token logic is placed inside `BroadcastReceiver`.

---

## 11. Phase 10.4 — Android FCM Notification Receiver & Channels Architecture

### 11.1. Notification Channels
Channels are defined with explicit user-facing purpose and importance. Note that on Android API 26+, importance set at channel creation represents the default; users retain ultimate control in system settings.

| Channel ID | Name | Importance | Description |
|---|---|---|---|
| `channel_commitment_alerts` | **Commitment Alerts** | `IMPORTANCE_HIGH` | Exact deadline alarms and urgent time-sensitive alerts |
| `channel_commitment_reminders` | **Commitment Reminders**| `IMPORTANCE_DEFAULT`| Quiet upcoming reminders, overdue reviews, and snooze alerts |
| `channel_goal_reminders` | **Practice Reminders** | `IMPORTANCE_DEFAULT`| Daily practice reminders and evening check-ins |
| `channel_system` | **Security & System** | `IMPORTANCE_HIGH` | New device logins and essential account alerts |

### 11.2. FCM Token Lifecycle & Device Sync
The Android app does not rely exclusively on `onNewToken()`. Token retrieval and registration occurs after:
1. `FirebaseMessagingService.onNewToken(token)`
2. User `login`
3. User `register`
4. App cold launch with `session restoration`

The client calls `FirebaseMessaging.getInstance().token.await()`, then synchronizes with `POST /api/v1/notifications/devices/` passing the local unique `device_id` and token.
On single `logout`, the client passes `X-Device-Id: <device_id>` to deactivate only the current device. On `logout-all`, the backend revokes all devices.

### 11.3. Notification Manager Abstraction: `PromiseNotificationManager`
All notification operations are routed through a single abstraction:
```kotlin
interface PromiseNotificationManager {
    fun show(data: PromiseNotificationData)
    fun update(data: PromiseNotificationData)
    fun cancel(notificationId: Int)
    fun cancelByReminderId(reminderId: String)
    fun cancelByEntity(entityType: String, entityId: String)
}
```

### 11.4. Stable Deterministic Notification IDs
Notification IDs are generated using a 32-bit FNV-1a hash of the canonical `identity_key`:
```kotlin
fun computeNotificationId(identityKey: String): Int {
    var hash = 0x811c9dc5.toInt()
    for (b in identityKey.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (b.toInt() and 0xff)
        hash *= 0x01000193
    }
    return hash and 0x7fffffff
}
```
`reminder_id` and `identity_key` are also attached to the notification extras for unambiguous lookup.

### 11.5. Action Execution Architecture via WorkManager
```text
[ Notification Action Click (e.g. Complete) ]
                     │
                     ▼
         [ NotificationActionReceiver ]
  • Immediately dismisses shade item: notificationManager.cancel(id)
  • Extracts action payload (entity_id, action_type)
  • Enqueues OneTimeWorkRequest via WorkManager
                     │
                     ▼
         [ NotificationActionWorker ]
  (Hilt @HiltWorker injected on Dispatchers.IO)
  • Calls CommitmentRepository.completeCommitment(id)
    OR CommitmentRepository.snoozeCommitment(id, snoozedUntil)
    OR GoalRepository.recordCheckIn(id, periodDate, COMPLETED)
  • Fully utilizes existing Retrofit client and 401 refresh-once flow
```

### 11.6. Notification Permission State
- The app records `hasRequestedNotificationPermission` in local settings.
- Permission is prompted contextually (e.g., when creating a deadline), never on first blank app launch, and never repeatedly re-prompted once answered.

### 11.7. Shared Goal Chat Notifications (Phase 11.1+)
- When a chat message is sent, `goal.chat.message_created.v1` is published via the outbox.
- The notification consumer dispatches quiet push notifications (`channel_goal_reminders`) to all active participants in that Shared Goal, explicitly excluding the message sender.
- Respects quiet hours and category preferences.

### 11.8. Real-Time Chat Presence & Push Suppression (Phase 11.5.5+)
- When an active participant opens a Shared Goal Chat room, the WebSocket connection registers an ephemeral presence marker in Redis:
  `promise:chat_presence:{goal_id}:{user_id}` (TTL: 60 seconds, renewed automatically on heartbeat pings).
- When the participant navigates away or disconnects, the presence key is deleted immediately.
- **FCM Optimization**: During `dispatch_due_reminders`, if a valid presence key exists for the target recipient and goal, the FCM push is suppressed (`PRESENCE_ACTIVE_IN_CHAT`) to prevent noisy duplicate alerts while the user is actively reading the live chat.
- **Correctness Guarantees**: Presence is purely an optimization. If Redis is unavailable or the presence key has expired, the dispatcher safely falls back to standard FCM push dispatch.
