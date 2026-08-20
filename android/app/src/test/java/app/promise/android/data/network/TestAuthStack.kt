package app.promise.android.data.network

import app.promise.android.data.local.InMemoryTokenStore
import app.promise.android.data.remote.ApiConfig
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TestAuthStack(
    baseUrl: HttpUrl,
) {
    val tokenStore = InMemoryTokenStore()
    val session = AuthSession()
    private val json = NetworkJson.json

    private val publicClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(ApiConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(JsonAcceptInterceptor())
        .build()

    val publicApi: AuthApi = retrofit(baseUrl, publicClient)

    val refresher = SessionRefresher(publicApi, tokenStore, session)

    private val authedClient: OkHttpClient = publicClient.newBuilder()
        .addInterceptor(AccessTokenInterceptor(session))
        .addInterceptor(AuthRetryInterceptor(session, refresher))
        .build()

    val authedApi: AuthApi = retrofit(baseUrl, authedClient)

    val repository = AuthRepositoryImpl(
        publicApi = publicApi,
        authedApi = authedApi,
        tokenStore = tokenStore,
        memory = session,
        refresher = refresher,
    )

    private fun retrofit(baseUrl: HttpUrl, client: OkHttpClient): AuthApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
    }
}

object AuthFixtures {
    const val USER_JSON =
        """{"id":"11111111-1111-1111-1111-111111111111","email":"ada@example.com","name":"Ada","timezone":"UTC","created_at":"2026-01-01T00:00:00Z"}"""

    fun tokens(access: String, refresh: String): String {
        return """{"access_token":"$access","refresh_token":"$refresh","token_type":"Bearer","expires_in":900}"""
    }

    fun loginBody(access: String = "access-1", refresh: String = "refresh-1"): String {
        return """{"user":$USER_JSON,"tokens":${tokens(access, refresh)}}"""
    }

    fun refreshBody(access: String = "access-2", refresh: String = "refresh-2"): String {
        return """{"tokens":${tokens(access, refresh)}}"""
    }

    fun meBody(): String = """{"user":$USER_JSON}"""

    fun error(code: String, message: String = "x"): String {
        return """{"error":{"code":"$code","message":"$message"}}"""
    }
}
