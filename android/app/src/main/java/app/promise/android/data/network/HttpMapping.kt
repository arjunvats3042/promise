package app.promise.android.data.network

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

fun HttpException.toApiException(json: Json = NetworkJson.json): ApiException {
    val raw = response()?.errorBody()?.string()
    val retryAfter = response()?.headers()?.get("Retry-After")
    return ApiErrorParser.parse(
        status = code(),
        body = raw,
        retryAfterHeader = retryAfter,
        json = json,
    )
}

fun Throwable.toApiException(json: Json = NetworkJson.json): ApiException {
    return when (this) {
        is ApiException -> this
        is HttpException -> toApiException(json)
        is SocketTimeoutException -> ApiException.timeout(this)
        is IOException -> ApiException.network(this)
        else -> ApiException.unknown(this)
    }
}
