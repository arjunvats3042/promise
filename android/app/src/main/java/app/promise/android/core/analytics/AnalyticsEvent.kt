package app.promise.android.core.analytics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.UUID

@Serializable
data class AnalyticsEvent(
    @SerialName("event_id")
    val eventId: String = UUID.randomUUID().toString(),
    @SerialName("event_name")
    val eventName: String,
    @SerialName("event_version")
    val eventVersion: Int = 1,
    @SerialName("occurred_at")
    val occurredAt: String = Instant.now().toString(),
    @SerialName("platform")
    val platform: String = "android",
    @SerialName("app_version")
    val appVersion: String = "1.0.0",
    @SerialName("properties")
    val properties: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class AnalyticsBatchPayload(
    @SerialName("events")
    val events: List<AnalyticsEvent>
)

@Serializable
data class AnalyticsIngestResponse(
    @SerialName("status")
    val status: String = "ok",
    @SerialName("ingested")
    val ingested: Int = 0,
    @SerialName("duplicates")
    val duplicates: Int = 0
)
