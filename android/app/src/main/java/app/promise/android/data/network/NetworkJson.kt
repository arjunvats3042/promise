package app.promise.android.data.network

import kotlinx.serialization.json.Json

object NetworkJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}
