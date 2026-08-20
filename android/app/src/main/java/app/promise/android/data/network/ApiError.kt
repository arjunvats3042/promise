package app.promise.android.data.network

class ApiException(
    val status: Int?,
    val code: String,
    val retryAfterSeconds: Int? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : Exception(code, cause) {
    val isSessionEnded: Boolean
        get() = status == 401 ||
            code == "UNAUTHENTICATED" ||
            code == "TOKEN_INVALID" ||
            code == "TOKEN_EXPIRED" ||
            code == "SESSION_REVOKED"

    companion object {
        fun network(cause: Throwable? = null) =
            ApiException(status = null, code = "NETWORK", cause = cause)

        fun timeout(cause: Throwable? = null) =
            ApiException(status = null, code = "TIMEOUT", cause = cause)

        fun unknown(cause: Throwable? = null) =
            ApiException(status = null, code = "UNKNOWN", cause = cause)
    }
}
