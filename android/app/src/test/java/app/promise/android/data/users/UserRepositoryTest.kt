package app.promise.android.data.users

import app.promise.android.data.local.InMemoryTokenStore
import app.promise.android.data.network.AccessTokenInterceptor
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthApi
import app.promise.android.data.network.AuthRetryInterceptor
import app.promise.android.data.network.AuthSession
import app.promise.android.data.network.JsonAcceptInterceptor
import app.promise.android.data.network.NetworkJson
import app.promise.android.data.network.SessionRefresher
import app.promise.android.data.network.UnitConverterFactory
import app.promise.android.data.remote.ApiConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UserRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: UserRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val session = AuthSession().also { it.setAccessToken("access") }
        val publicClient = OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(ApiConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(JsonAcceptInterceptor())
            .build()
        val publicApi = retrofit(publicClient).create(AuthApi::class.java)
        val refresher = SessionRefresher(publicApi, InMemoryTokenStore(), session)
        val authed = publicClient.newBuilder()
            .addInterceptor(AccessTokenInterceptor(session))
            .addInterceptor(AuthRetryInterceptor(session, refresher))
            .build()
        repo = UserRepositoryImpl(retrofit(authed).create(UserApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lookupByEmail_canonicalizesAndCallsApi() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"id":"u1","name":"Alice","email":"alice@example.com"}"""),
        )
        val user = repo.lookupByEmail("  ALICE@example.com  ")
        val request = server.takeRequest()
        assertEquals("alice@example.com", user.email)
        assertEquals("u1", user.id)
        assertEquals("Alice", user.name)
        val path = request.path!!
        assert(path.contains("email=alice%40example.com") || path.contains("email=alice@example.com")) {
            "Expected email query param but got: $path"
        }
    }

    @Test
    fun notFound_mapsApiException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":{"code":"USER_NOT_FOUND","message":"x"}}"""),
        )
        try {
            repo.lookupByEmail("nobody@example.com")
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals("USER_NOT_FOUND", e.code)
            assertEquals(404, e.status)
        }
    }

    @Test
    fun networkFailure_mapsNetwork() = runTest {
        server.shutdown()
        try {
            repo.lookupByEmail("test@example.com")
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals("NETWORK", e.code)
        }
    }

    private fun retrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(NetworkJson.json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
