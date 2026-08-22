package app.promise.android.core.analytics

import app.promise.android.core.AppLog
import app.promise.android.data.remote.AnalyticsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe, non-blocking, privacy-conscious in-memory Analytics Tracker for Android.
 *
 * Privacy Guarantees:
 * - NEVER logs or sends commitment text, goal descriptions, notes, chat bodies,
 *   AI prompts/responses, or raw search query strings.
 * - Stores events in bounded short-lived in-memory queue (no Room persistence).
 * - Best-effort: Failure to send analytics NEVER disrupts app execution or UI thread.
 */
class AnalyticsTracker(
    private val analyticsApi: AnalyticsApi? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<AnalyticsEvent>()
    private val flushMutex = Mutex()

    companion object {
        private const val TAG = "AnalyticsTracker"
        private const val BATCH_SIZE = 10
        private const val MAX_QUEUE_CAPACITY = 200
        private const val PERIODIC_FLUSH_MS = 30_000L
    }

    init {
        startPeriodicFlushTimer()
    }

    fun trackEvent(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        scope.launch {
            try {
                while (queue.size >= MAX_QUEUE_CAPACITY) {
                    queue.poll()
                }

                val jsonProps = properties.mapNotNull { (key, value) ->
                    toJsonPrimitive(value)?.let { key to it }
                }.toMap()

                val event = AnalyticsEvent(
                    eventName = eventName,
                    properties = jsonProps
                )
                queue.offer(event)

                if (queue.size >= BATCH_SIZE) {
                    flushQueue()
                }
            } catch (e: Exception) {
                AppLog.d(TAG, "Failed to enqueue event $eventName: ${e.message}")
            }
        }
    }

    // Typed Convenience Trackers
    fun trackAppOpened() = trackEvent("app_opened")
    fun trackLoginSuccess() = trackEvent("login_success")
    fun trackRegisterSuccess() = trackEvent("register_success")

    fun trackGoalCreated(isShared: Boolean) = trackEvent("goal_created", mapOf("is_shared" to isShared))
    fun trackGoalCheckedIn() = trackEvent("goal_checked_in")

    fun trackCommitmentCreated() = trackEvent("commitment_created")
    fun trackCommitmentCompleted() = trackEvent("commitment_completed")

    fun trackSharedGoalChatOpened() = trackEvent("shared_goal_chat_opened")
    fun trackSharedGoalMessageSent() = trackEvent("shared_goal_message_sent")

    fun trackNotificationOpened(channelId: String? = null) =
        trackEvent("notification_opened", mapOf("channel_id" to (channelId ?: "unknown")))
    fun trackNotificationActionClicked(action: String) =
        trackEvent("notification_action_clicked", mapOf("action" to action))

    fun trackSearchStarted(searchType: String) =
        trackEvent("search_started", mapOf("search_type" to searchType))
    fun trackSearchResultSelected() = trackEvent("search_result_selected")

    fun trackAiFeatureUsed(feature: String) =
        trackEvent("ai_${feature}_used", mapOf("feature" to feature))
    fun trackAiFeatureConfirmed(feature: String) =
        trackEvent("ai_${feature}_confirmed", mapOf("feature" to feature))

    fun trackOnboardingStarted() = trackEvent("onboarding_started")
    fun trackOnboardingCompleted() = trackEvent("onboarding_completed")

    suspend fun flushQueue() {
        if (queue.isEmpty() || analyticsApi == null) return

        flushMutex.withLock {
            val batch = mutableListOf<AnalyticsEvent>()
            while (batch.size < BATCH_SIZE && queue.isNotEmpty()) {
                queue.poll()?.let { batch.add(it) }
            }

            if (batch.isEmpty()) return

            try {
                val payload = AnalyticsBatchPayload(events = batch)
                val response = analyticsApi.sendEventBatch(payload)
                if (!response.isSuccessful) {
                    AppLog.d(TAG, "Analytics batch send returned HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                AppLog.d(TAG, "Analytics batch flush failed non-fatally: ${e.message}")
            }
        }
    }

    private fun startPeriodicFlushTimer() {
        scope.launch {
            while (true) {
                delay(PERIODIC_FLUSH_MS)
                flushQueue()
            }
        }
    }

    fun getPendingQueueSize(): Int = queue.size

    private fun toJsonPrimitive(value: Any?): JsonElement? {
        return when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> value?.let { JsonPrimitive(it.toString()) }
        }
    }
}
