package app.promise.android.data.network

import okhttp3.Interceptor
import okhttp3.Response

class AccessTokenInterceptor(
    private val session: AuthSession,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (AuthPaths.isPublic(original.url.encodedPath)) {
            return chain.proceed(original)
        }
        val token = session.accessToken ?: return chain.proceed(original)
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}

object AuthPaths {
    fun isPublic(encodedPath: String): Boolean {
        return encodedPath.endsWith("/auth/login/") ||
            encodedPath.endsWith("/auth/register/") ||
            encodedPath.endsWith("/auth/refresh/")
    }
}
