package app.promise.android.data.ai

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AiRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AiRepositoryImpl

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
        repo = AiRepositoryImpl(retrofit(authed).create(AiApi::class.java))
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
    fun suggestGoal_parsesStructuredResponse() = runTest {
        val json = """
        {
            "status": "READY",
            "clarification_question": null,
            "title": "Read daily",
            "description": "Read 30 mins",
            "recurrence_kind": "DAILY",
            "weekdays": [],
            "period_unit": null,
            "times_per_period": null,
            "tracking_kind": "COUNT",
            "target_value": 30.0,
            "target_unit": "minutes",
            "reasoning": "Fits daily routine"
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = repo.suggestGoal("Read 30 mins every day")
        assertEquals("READY", result.status)
        assertEquals("Read daily", result.title)
        assertEquals("DAILY", result.recurrenceKind)
        assertEquals(30.0, result.targetValue)
        assertEquals("minutes", result.targetUnit)
    }

    @Test
    fun suggestGoal_parsesClarificationResponse() = runTest {
        val json = """
        {
            "status": "NEEDS_CLARIFICATION",
            "clarification_question": "How often would you like to read?",
            "title": "",
            "description": "",
            "recurrence_kind": "DAILY",
            "weekdays": [],
            "period_unit": null,
            "times_per_period": null,
            "tracking_kind": "BINARY",
            "target_value": null,
            "target_unit": "",
            "reasoning": "Missing frequency"
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = repo.suggestGoal("I want to read more")
        assertEquals("NEEDS_CLARIFICATION", result.status)
        assertEquals("How often would you like to read?", result.clarificationQuestion)
    }

    @Test
    fun refineCommitment_parsesRefinementResponse() = runTest {
        val json = """
        {
            "status": "READY",
            "current_interpretation": "Submit tax returns",
            "missing_information": null,
            "clarifying_question": null,
            "refined_title": "Submit taxes",
            "refined_description": "Final review and file electronically",
            "suggested_due_at": "2026-08-25T17:00:00Z",
            "suggested_due_precision": "HOUR",
            "reasoning": "Standard business hour filing"
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = repo.refineCommitment("Submit taxes by Friday 5pm")
        assertEquals("READY", result.status)
        assertEquals("Submit taxes", result.refinedTitle)
        assertEquals("2026-08-25T17:00:00Z", result.suggestedDueAt)
    }

    @Test
    fun parseThought_parsesMultipleItemsWithConfidence() = runTest {
        val json = """
        {
            "items": [
                {
                    "type": "commitment",
                    "title": "Call accountant",
                    "description": "",
                    "confidence": "HIGH",
                    "due_at": null,
                    "due_precision": null,
                    "recurrence_kind": null,
                    "weekdays": [],
                    "tracking_kind": null,
                    "target_value": null,
                    "target_unit": ""
                },
                {
                    "type": "goal",
                    "title": "Daily Walk",
                    "description": "Walk 5000 steps",
                    "confidence": "HIGH",
                    "due_at": null,
                    "due_precision": null,
                    "recurrence_kind": "DAILY",
                    "weekdays": [],
                    "tracking_kind": "COUNT",
                    "target_value": 5000.0,
                    "target_unit": "steps"
                }
            ]
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = repo.parseThought("Call accountant and walk 5000 steps every day")
        assertEquals(2, result.size)
        assertEquals("commitment", result[0].type)
        assertEquals("HIGH", result[0].confidence)
        assertEquals("Call accountant", result[0].title)
        assertEquals("goal", result[1].type)
        assertEquals("Daily Walk", result[1].title)
    }

    @Test
    fun summarizeChat_parsesKeyDecisionsActionsAndOpenQuestions() = runTest {
        val json = """
        {
            "summary": "Team agreed to meet on Friday.",
            "key_decisions": ["Meet on Friday at 4 PM"],
            "agreed_actions": ["Prepare presentation slides"],
            "important_dates": ["Friday at 4 PM"],
            "open_questions": ["Who will book the room?"]
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = repo.summarizeChat("goal-123")
        assertEquals("Team agreed to meet on Friday.", result.summary)
        assertEquals(1, result.keyDecisions.size)
        assertEquals("Meet on Friday at 4 PM", result.keyDecisions[0])
        assertEquals(1, result.openQuestions.size)
        assertEquals("Who will book the room?", result.openQuestions[0])
    }
}
