package app.promise.android.data.remote

import app.promise.android.BuildConfig

object ApiConfig {
    val baseUrl: String
        get() {
            val url = BuildConfig.API_BASE_URL
            if (!BuildConfig.DEBUG && url.isBlank()) {
                throw IllegalStateException(
                    "Production API base URL is not configured. Set 'promise.prodApiBaseUrl' or PROMISE_PROD_API_BASE_URL.",
                )
            }
            return url
        }

    fun webSocketUrl(goalId: String, customBaseUrl: String? = null): String {
        val base = (customBaseUrl ?: baseUrl).replaceFirst(Regex("^http"), "ws")
        val root = if (base.contains("/api/v1")) {
            base.substringBefore("/api/v1")
        } else {
            base.removeSuffix("/")
        }
        return "${root.removeSuffix("/")}/ws/goals/$goalId/chat/"
    }

    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_WRITE_TIMEOUT_SECONDS = 45L
    const val CALL_TIMEOUT_SECONDS = 60L
}
