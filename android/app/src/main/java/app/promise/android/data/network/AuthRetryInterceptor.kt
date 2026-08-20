package app.promise.android.data.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthRetryInterceptor(
    private val session: AuthSession,
    private val refresher: SessionRefresher,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val first = chain.proceed(request)
        if (first.code != 401) {
            return first
        }
        if (AuthPaths.isPublic(request.url.encodedPath)) {
            return first
        }
        if (request.header(RETRY_HEADER) != null) {
            return first
        }
        val refreshed = try {
            runBlocking { refresher.refresh() }
        } catch (_: IOException) {
            false
        } catch (_: ApiException) {
            false
        }
        if (!refreshed) {
            return first
        }
        first.close()
        val retry = request.newBuilder()
            .header(RETRY_HEADER, "1")
            .removeHeader("Authorization")
            .apply {
                val token = session.accessToken
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()
        return chain.proceed(retry)
    }

    companion object {
        const val RETRY_HEADER = "X-Promise-Auth-Retry"
    }
}
