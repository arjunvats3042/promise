package app.promise.android.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SyncActionItemDto(
    @SerialName("action_id") val actionId: String,
    @SerialName("action_type") val actionType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("payload") val payload: JsonObject? = null,
    @SerialName("client_timestamp") val clientTimestamp: String? = null,
)

@Serializable
data class SyncBatchRequestDto(
    @SerialName("actions") val actions: List<SyncActionItemDto>,
)

@Serializable
data class SyncActionResultDto(
    @SerialName("action_id") val actionId: String,
    @SerialName("status") val status: String,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
data class SyncBatchResponseDto(
    @SerialName("results") val results: List<SyncActionResultDto>,
    @SerialName("server_timestamp") val serverTimestamp: String,
)
