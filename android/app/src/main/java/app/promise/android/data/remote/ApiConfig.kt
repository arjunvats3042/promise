package app.promise.android.data.remote

import app.promise.android.BuildConfig

object ApiConfig {
    val baseUrl: String
        get() = BuildConfig.API_BASE_URL

    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_WRITE_TIMEOUT_SECONDS = 20L
    const val CALL_TIMEOUT_SECONDS = 30L
}
