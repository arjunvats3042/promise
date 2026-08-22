package app.promise.android.data.search

import app.promise.android.data.local.InMemoryTokenStore
import app.promise.android.data.network.AccessTokenInterceptor
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SearchRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: SearchRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val session = AuthSession().also { it.setAccessToken("access") }
        val json = NetworkJson.json
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
        repo = SearchRepositoryImpl(retrofit(authed).create(SearchApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/api/v1/"))
        .client(client)
        .addConverterFactory(UnitConverterFactory())
        .addConverterFactory(NetworkJson.json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Test
    fun search_blankQuery_returnsEmptyWithoutNetworkCall() = runTest {
        val result = repo.search("   ")
        assertTrue(result.isEmpty)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun search_success_parsesGroupedResults() = runTest {
        val jsonBody = """
        {
            "commitments": [
                {
                    "id": "c1111111-1111-1111-1111-111111111111",
                    "title": "Buy groceries",
                    "description": "Milk and eggs",
                    "status": "PENDING",
                    "due_at": "2026-08-23T10:00:00Z",
                    "due_precision": "DAY",
                    "created_at": "2026-08-22T08:00:00Z",
                    "updated_at": "2026-08-22T08:00:00Z"
                }
            ],
            "goals": [
                {
                    "id": "g1111111-1111-1111-1111-111111111111",
                    "title": "Morning Yoga",
                    "description": "",
                    "status": "ACTIVE",
                    "recurrence_kind": "DAILY",
                    "start_date": "2026-08-01",
                    "is_shared": false,
                    "participant_count": 1,
                    "created_at": "2026-08-01T00:00:00Z",
                    "updated_at": "2026-08-01T00:00:00Z"
                }
            ],
            "shared_goals": [
                {
                    "id": "sg111111-1111-1111-1111-111111111111",
                    "title": "Yoga Buddies",
                    "description": "Shared group",
                    "status": "ACTIVE",
                    "recurrence_kind": "DAILY",
                    "start_date": "2026-08-01",
                    "is_shared": true,
                    "participant_count": 4,
                    "created_at": "2026-08-01T00:00:00Z",
                    "updated_at": "2026-08-01T00:00:00Z"
                }
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonBody))

        val result = repo.search("Yoga")
        assertFalse(result.isEmpty)
        assertEquals(1, result.commitments.size)
        assertEquals(1, result.goals.size)
        assertEquals(1, result.sharedGoals.size)

        assertEquals("Buy groceries", result.commitments[0].title)
        assertEquals("Morning Yoga", result.goals[0].title)
        assertEquals("Yoga Buddies", result.sharedGoals[0].title)
        assertEquals(4, result.sharedGoals[0].participantCount)

        val request = server.takeRequest()
        assertEquals("/api/v1/search/?q=Yoga&type=all", request.path)
    }
}
