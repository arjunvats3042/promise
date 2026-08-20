package app.promise.android.data.goals

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
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalTrackingKind
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

class GoalRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: GoalRepositoryImpl

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
        repo = GoalRepositoryImpl(retrofit(authed).create(GoalApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun create_omitsSourceStatusCreatedBy() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(goalJson("g1")))
        repo.create(
            CreateGoalInput(
                title = "Read",
                recurrenceKind = GoalRecurrenceKind.DAILY,
                trackingKind = GoalTrackingKind.BINARY,
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"title\":\"Read\""))
        assertTrue(body.contains("\"recurrence_kind\":\"DAILY\""))
        assertFalse(body.contains("source"))
        assertFalse(body.contains("created_by"))
        assertFalse(body.contains("\"status\""))
    }

    @Test
    fun listActive_usesStatusQuery() = runTest {
        server.enqueue(MockResponse().setBody(pageJson(goalJson("g1"))))
        val page = repo.list(GoalListFilter.ACTIVE)
        assertTrue(server.takeRequest().path!!.contains("status=ACTIVE"))
        assertEquals("g1", page.items.single().id)
        assertEquals(6, page.items.single().progress.weekProgress.completed)
    }

    @Test
    fun listCompleted_mergesTerminalStatuses() = runTest {
        server.enqueue(MockResponse().setBody(pageJson(goalJson("c1", status = "COMPLETED"))))
        server.enqueue(MockResponse().setBody(pageJson(goalJson("x1", status = "CANCELLED"))))
        val page = repo.list(GoalListFilter.COMPLETED)
        assertEquals(2, page.items.size)
        assertTrue(server.takeRequest().path!!.contains("status=COMPLETED"))
        assertTrue(server.takeRequest().path!!.contains("status=CANCELLED"))
    }

    @Test
    fun checkIn_accepts200Upsert() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(checkInJson(status = "SKIPPED")),
        )
        val result = repo.checkIn("g1", CheckInInput(status = GoalCheckInStatus.SKIPPED))
        assertEquals(GoalCheckInStatus.SKIPPED, result.status)
        assertTrue(server.takeRequest().path!!.endsWith("/goals/g1/check-ins/"))
    }

    @Test
    fun checkIn_mapsBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(checkInJson()))
        repo.checkIn("g1", CheckInInput(status = GoalCheckInStatus.COMPLETED, value = null))
        val request = server.takeRequest()
        assertTrue(request.path!!.endsWith("/goals/g1/check-ins/"))
        assertTrue(request.body.readUtf8().contains("\"status\":\"COMPLETED\""))
    }

    @Test
    fun pause_hitsPausePath() = runTest {
        server.enqueue(MockResponse().setBody(goalJson("g1", status = "PAUSED")))
        val goal = repo.pause("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/pause/"))
        assertEquals("PAUSED", goal.status.name)
    }

    @Test
    fun notFound_mapsApiException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":{"code":"GOAL_NOT_FOUND","message":"x"}}"""),
        )
        try {
            repo.get("missing")
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals("GOAL_NOT_FOUND", e.code)
        }
    }

    @Test
    fun scheduleLocked_maps() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"error":{"code":"GOAL_SCHEDULE_LOCKED","message":"x"}}"""),
        )
        // reuse get path for error mapping through repository
        try {
            repo.get("g1")
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals("GOAL_SCHEDULE_LOCKED", e.code)
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

    private fun goalJson(id: String, status: String = "ACTIVE"): String {
        return """
        {
          "id":"$id",
          "title":"Read",
          "description":"",
          "status":"$status",
          "timezone":"UTC",
          "start_date":"2026-08-01",
          "end_date":null,
          "recurrence_kind":"DAILY",
          "weekdays":[],
          "period_unit":null,
          "times_per_period":null,
          "tracking_kind":"BINARY",
          "target_value":null,
          "target_unit":"",
          "source":"MANUAL",
          "paused_at":null,
          "completed_at":null,
          "cancelled_at":null,
          "created_at":"2026-08-01T00:00:00Z",
          "updated_at":"2026-08-01T00:00:00Z",
          "is_ended":false,
          "progress":{
            "current_period":{"required":1,"completed":0},
            "week_progress":{"required":7,"completed":6},
            "consistency_percent":80
          },
          "current_streak":5
        }
        """.trimIndent()
    }

    private fun checkInJson(status: String = "COMPLETED"): String {
        return """
        {
          "id":"c1",
          "period_date":"2026-08-20",
          "status":"$status",
          "value":null,
          "note":"",
          "checked_at":"2026-08-20T12:00:00Z",
          "created_at":"2026-08-20T12:00:00Z",
          "updated_at":"2026-08-20T12:00:00Z"
        }
        """.trimIndent()
    }
}
