package app.promise.android.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class ErrorEnvelope(
    val error: ErrorBody,
)

@Serializable
internal data class ErrorBody(
    val code: String,
    val message: String = "",
    val details: JsonElement? = null,
)

object ApiErrorParser {
    fun parse(
        status: Int,
        body: String?,
        retryAfterHeader: String?,
        json: Json,
    ): ApiException {
        val retryAfter = parseRetryAfter(retryAfterHeader)
        val envelope = parseEnvelope(body, json)
        val code = envelope?.error?.code ?: fallbackCode(status)
        val fieldErrors = parseFieldErrors(envelope?.error?.details)
        return ApiException(
            status = status,
            code = code,
            retryAfterSeconds = retryAfter,
            fieldErrors = fieldErrors,
        )
    }

    fun parseRetryAfter(header: String?): Int? {
        if (header.isNullOrBlank()) return null
        return header.trim().toIntOrNull()?.takeIf { it >= 0 }
    }

    private fun parseEnvelope(body: String?, json: Json): ErrorEnvelope? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(ErrorEnvelope.serializer(), body)
        }.getOrNull()
    }

    private fun parseFieldErrors(details: JsonElement?): Map<String, String> {
        val obj = details as? JsonObject ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        for ((key, value) in obj) {
            val message = firstDetailMessage(value) ?: continue
            out[key] = message
        }
        return out
    }

    private fun firstDetailMessage(value: JsonElement): String? {
        return when (value) {
            is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonArray -> value.firstOrNull()?.let { firstDetailMessage(it) }
            else -> null
        }
    }

    private fun fallbackCode(status: Int): String {
        return when (status) {
            401 -> "UNAUTHENTICATED"
            404 -> "NOT_FOUND"
            409 -> "CONFLICT"
            429 -> "RATE_LIMITED"
            500 -> "INTERNAL_SERVER_ERROR"
            else -> "UNKNOWN"
        }
    }
}
