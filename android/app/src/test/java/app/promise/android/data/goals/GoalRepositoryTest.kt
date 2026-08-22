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
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
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
        val membership = page.items.single() as GoalListItem.Membership
        assertEquals("g1", membership.goal.id)
        assertEquals(6, membership.goal.progress.weekProgress.completed)
        assertEquals(5, membership.goal.currentStreak)
        assertEquals(80, membership.goal.progress.consistencyPercent)
    }

    @Test
    fun listActive_pinsInvitesBeforeMemberships() = runTest {
        server.enqueue(
            MockResponse().setBody(pageJson(goalJson("g1"), inviteJson("i1"))),
        )
        val page = repo.list(GoalListFilter.ACTIVE)
        assertEquals(2, page.items.size)
        assertTrue(page.items[0] is GoalListItem.Invite)
        assertTrue(page.items[1] is GoalListItem.Membership)
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
    fun listCompleted_skipsInviteShapedItems() = runTest {
        server.enqueue(MockResponse().setBody(pageJson(goalJson("c1", status = "COMPLETED"), inviteJson("i1"))))
        server.enqueue(MockResponse().setBody(pageJson()))
        val page = repo.list(GoalListFilter.COMPLETED)
        assertEquals(1, page.items.size)
        assertTrue(page.items[0] is GoalListItem.Membership)
    }

    @Test
    fun list_parsesNextPage() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"count":40,"next":"http://localhost/api/v1/goals/?page=2","previous":null,"results":[${goalJson("g1")}]}""",
            ),
        )
        val page = repo.list(GoalListFilter.ACTIVE)
        assertEquals(2, page.nextPage)
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
        repo.checkIn(
            "g1",
            CheckInInput(
                status = GoalCheckInStatus.COMPLETED,
                periodDate = "2026-08-20",
                value = 3,
                note = "ok",
            ),
        )
        val request = server.takeRequest()
        assertTrue(request.path!!.endsWith("/goals/g1/check-ins/"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"status\":\"COMPLETED\""))
        assertTrue(body.contains("\"period_date\":\"2026-08-20\""))
        assertTrue(body.contains("\"value\":3"))
    }

    @Test
    fun pause_hitsPausePath() = runTest {
        server.enqueue(MockResponse().setBody(goalJson("g1", status = "PAUSED")))
        val goal = repo.pause("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/pause/"))
        assertEquals("PAUSED", goal.status.name)
    }

    @Test
    fun resume_hitsResumePath() = runTest {
        server.enqueue(MockResponse().setBody(goalJson("g1", status = "ACTIVE")))
        val goal = repo.resume("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/resume/"))
        assertEquals("ACTIVE", goal.status.name)
    }

    @Test
    fun complete_hitsCompletePath() = runTest {
        server.enqueue(MockResponse().setBody(goalJson("g1", status = "COMPLETED")))
        val goal = repo.complete("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/complete/"))
        assertEquals("COMPLETED", goal.status.name)
    }

    @Test
    fun cancel_hitsCancelPath() = runTest {
        server.enqueue(MockResponse().setBody(goalJson("g1", status = "CANCELLED")))
        val goal = repo.cancel("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/cancel/"))
        assertEquals("CANCELLED", goal.status.name)
    }

    @Test
    fun getDetail_full_returnsGoalDetail() = runTest {
        server.enqueue(MockResponse().setBody(goalJson("g1")))
        val detail = repo.getDetail("g1")
        assertTrue(detail is GoalDetail.Full)
        assertEquals("g1", (detail as GoalDetail.Full).goal.id)
    }

    @Test
    fun getDetail_invite_returnsInviteDetail() = runTest {
        server.enqueue(MockResponse().setBody(inviteJson("i1")))
        val detail = repo.getDetail("i1")
        assertTrue(detail is GoalDetail.Invite)
        assertEquals("i1", (detail as GoalDetail.Invite).preview.id)
    }

    @Test
    fun inviteParticipant_hitsParticipantsPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(participantJson("p1")))
        val participant = repo.inviteParticipant("g1", "u2")
        val request = server.takeRequest()
        assertTrue(request.path!!.endsWith("/goals/g1/participants/"))
        assertTrue(request.body.readUtf8().contains("\"user_id\":\"u2\""))
        assertEquals("p1", participant.id)
    }

    @Test
    fun listParticipants_hitsParticipantsPath() = runTest {
        server.enqueue(MockResponse().setBody("[${participantJson("p1")}]"))
        val list = repo.listParticipants("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/goals/g1/participants/"))
        assertEquals(1, list.size)
        assertEquals("p1", list[0].id)
    }

    @Test
    fun acceptInvitation_hitsAcceptPath() = runTest {
        server.enqueue(MockResponse().setBody(goalJson("g1")))
        val detail = repo.acceptInvitation("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/accept/"))
        assertTrue(detail is GoalDetail.Full)
    }

    @Test
    fun declineInvitation_hitsDeclinePath() = runTest {
        server.enqueue(MockResponse().setBody(participantJson("p1")))
        val participant = repo.declineInvitation("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/decline/"))
        assertEquals("p1", participant.id)
    }

    @Test
    fun removeParticipant_hitsDeletePath() = runTest {
        server.enqueue(MockResponse().setBody(participantJson("p1")))
        val participant = repo.removeParticipant("g1", "u2")
        val path = server.takeRequest().path!!
        assertTrue(path.contains("/goals/g1/participants/u2/"))
        assertEquals("p1", participant.id)
    }

    @Test
    fun leave_hitsLeavePath() = runTest {
        server.enqueue(MockResponse().setBody(participantJson("p1")))
        val participant = repo.leave("g1")
        assertTrue(server.takeRequest().path!!.endsWith("/leave/"))
        assertEquals("p1", participant.id)
    }

    @Test
    fun conflict_mapsApiException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"error":{"code":"GOAL_INVALID_TRANSITION","message":"x"}}"""),
        )
        try {
            repo.pause("g1")
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertEquals("GOAL_INVALID_TRANSITION", e.code)
        }
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
    fun rateLimited_parsesRetryAfter() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "12")
                .setBody("""{"error":{"code":"RATE_LIMITED","message":"slow"}}"""),
        )
        try {
            repo.cancel("g1")
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals(429, e.status)
            assertEquals(12, e.retryAfterSeconds)
        }
    }

    @Test
    fun networkFailure_mapsNetwork() = runTest {
        server.shutdown()
        try {
            repo.list(GoalListFilter.ACTIVE)
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals("NETWORK", e.code)
        }
    }

    @Test
    fun scheduleLocked_maps() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"error":{"code":"GOAL_SCHEDULE_LOCKED","message":"x"}}"""),
        )
        try {
            repo.get("g1")
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals("GOAL_SCHEDULE_LOCKED", e.code)
        }
    }

    @Test
    fun listActivity_parsesGoalActivityItems() = runTest {
        val json = """
        [
          {
            "id": "act-1",
            "event_type": "CHECKIN_RECORDED",
            "actor": {
              "id": "u1",
              "name": "Arjun"
            },
            "target_user": null,
            "summary": "Arjun completed today's practice",
            "period_date": "2026-08-22",
            "created_at": "2026-08-22T10:00:00Z"
          }
        ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val items = repo.listActivity("g1", limit = 10)
        assertEquals(1, items.size)
        assertEquals("act-1", items[0].id)
        assertEquals("CHECKIN_RECORDED", items[0].eventType)
        assertEquals("Arjun", items[0].actor?.name)
        assertEquals("Arjun completed today's practice", items[0].summary)
    }

    @Test
    fun transferOwnership_postsToTransferEndpointAndReturnsDetail() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(goalJson("g1")))
        val detail = repo.transferOwnership("g1", participantId = "p2")
        val req = server.takeRequest()
        assertEquals("/api/v1/goals/g1/ownership/transfer/", req.path)
        assertEquals("POST", req.method)
        assertTrue(detail is GoalDetail.Full)
        assertEquals("g1", (detail as GoalDetail.Full).goal.id)
    }

    @Test
    fun reinviteParticipant_postsToReinviteEndpointAndReturnsParticipant() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(participantJson("p-reinvited")))
        val participant = repo.reinviteParticipant("g1", participantId = "p-old")
        val req = server.takeRequest()
        assertEquals("/api/v1/goals/g1/participants/reinvite/", req.path)
        assertEquals("POST", req.method)
        assertEquals("p-reinvited", participant.id)
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

    private fun inviteJson(id: String): String {
        return """
        {
          "id":"$id",
          "title":"Shared goal",
          "description":"",
          "timezone":"UTC",
          "start_date":"2026-08-01",
          "end_date":null,
          "recurrence_kind":"DAILY",
          "weekdays":[],
          "tracking_kind":"BINARY",
          "target_unit":"",
          "inviter_user_id":"u1",
          "inviter_name":"Alice",
          "invitation_status":"INVITED",
          "invitation_expires_at":null
        }
        """.trimIndent()
    }

    private fun participantJson(id: String): String {
        return """
        {
          "id":"$id",
          "user_id":"u1",
          "user_name":"Alice",
          "role":"OWNER",
          "status":"ACTIVE",
          "invited_at":"2026-08-01T00:00:00Z",
          "joined_at":"2026-08-01T00:00:00Z",
          "left_at":null
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
