package app.promise.android.data.commitments

import app.promise.android.data.network.AccessTokenInterceptor
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthRetryInterceptor
import app.promise.android.data.network.AuthSession
import app.promise.android.data.network.JsonAcceptInterceptor
import app.promise.android.data.network.NetworkJson
import app.promise.android.data.network.SessionRefresher
import app.promise.android.data.network.UnitConverterFactory
import app.promise.android.data.local.InMemoryTokenStore
import app.promise.android.data.network.AuthApi
import app.promise.android.data.remote.ApiConfig
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.DuePrecision
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class CommitmentRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: CommitmentRepositoryImpl
    private lateinit var session: AuthSession

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session = AuthSession()
        session.setAccessToken("access-token")
        val json = NetworkJson.json
        val publicClient = OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(ApiConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(JsonAcceptInterceptor())
            .build()
        val tokenStore = InMemoryTokenStore()
        val publicApi = retrofit(publicClient).create(AuthApi::class.java)
        val refresher = SessionRefresher(publicApi, tokenStore, session)
        val authedClient = publicClient.newBuilder()
            .addInterceptor(AccessTokenInterceptor(session))
            .addInterceptor(AuthRetryInterceptor(session, refresher))
            .build()
        val api = retrofit(authedClient).create(CommitmentApi::class.java)
        repo = CommitmentRepositoryImpl(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun create_omitsSourceAndCreatedBy() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(commitmentJson("c1", "PENDING")))
        repo.create(CreateCommitmentInput(title = "Call Mom", duePrecision = DuePrecision.NONE))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"title\":\"Call Mom\""))
        assertFalse(body.contains("source"))
        assertFalse(body.contains("created_by"))
        assertFalse(body.contains("status"))
    }

    @Test
    fun overdueList_usesIsOverdueQuery() = runTest {
        server.enqueue(MockResponse().setBody(pageJson(commitmentJson("o1", "PENDING", overdue = true))))
        val page = repo.list(CommitmentListFilter.OVERDUE, page = 1, timeZoneId = "UTC")
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("is_overdue=true"))
        assertEquals("o1", page.items.single().id)
        assertTrue(page.items.single().isOverdue)
    }

    @Test
    fun complete_returnsUpdatedCommitment() = runTest {
        server.enqueue(MockResponse().setBody(commitmentJson("c1", "COMPLETED", overdue = false)))
        val result = repo.complete("c1")
        assertEquals("COMPLETED", result.status.name)
        assertTrue(server.takeRequest().path!!.endsWith("/complete/"))
    }

    @Test
    fun snooze_sendsSnoozedUntil() = runTest {
        server.enqueue(MockResponse().setBody(commitmentJson("c1", "SNOOZED")))
        repo.snooze("c1", "2026-08-21T10:00:00Z")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"snoozed_until\":\"2026-08-21T10:00:00Z\""))
    }

    @Test
    fun notFound_mapsToApiException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":{"code":"COMMITMENT_NOT_FOUND","message":"x"}}"""),
        )
        try {
            repo.get("missing")
            throw AssertionError("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals("COMMITMENT_NOT_FOUND", e.code)
        }
    }

    @Test
    fun conflict_mapsToApiException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"error":{"code":"COMMITMENT_INVALID_TRANSITION","message":"x"}}"""),
        )
        try {
            repo.wait("c1")
            throw AssertionError("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertEquals("COMMITMENT_INVALID_TRANSITION", e.code)
        }
    }

    @Test
    fun rateLimited_parsesRetryAfter() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "12")
                .setBody("""{"error":{"code":"RATE_LIMITED","message":"slow"}}"""),
        )
        try {
            repo.cancel("c1")
            throw AssertionError("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(429, e.status)
            assertEquals(12, e.retryAfterSeconds)
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

    private fun pageJson(vararg items: String): String {
        return """{"count":${items.size},"next":null,"previous":null,"results":[${items.joinToString(",")}]}"""
    }

    private fun commitmentJson(
        id: String,
        status: String,
        overdue: Boolean = false,
    ): String {
        return """
            {
              "id":"$id",
              "title":"Title $id",
              "description":"",
              "status":"$status",
              "due_at":"2026-08-20T12:00:00Z",
              "due_precision":"DATETIME",
              "source":"MANUAL",
              "snoozed_until":null,
              "completed_at":null,
              "cancelled_at":null,
              "created_at":"2026-08-01T00:00:00Z",
              "updated_at":"2026-08-01T00:00:00Z",
              "is_overdue":$overdue
            }
        """.trimIndent()
    }
}
