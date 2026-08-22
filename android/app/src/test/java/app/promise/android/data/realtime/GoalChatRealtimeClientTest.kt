package app.promise.android.data.realtime

import app.promise.android.data.network.NetworkJson
import app.promise.android.data.network.TestAuthStack
import app.promise.android.domain.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalChatRealtimeClientTest {

    private lateinit var server: MockWebServer
    private lateinit var authStack: TestAuthStack

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        authStack = TestAuthStack(server.url("/"))
        authStack.session.setUser(User("u1", "arjun@example.com", "Arjun", "UTC", "2026-08-01T00:00:00Z"))
        authStack.session.setAccessToken("valid-token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun connect_and_receive_messageCreated_event() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """
                            {
                                "type": "message.created",
                                "message": {
                                    "id": "m123",
                                    "sender": {
                                        "id": "u2",
                                        "name": "Partner"
                                    },
                                    "body": "Hello live!",
                                    "created_at": "2026-08-22T10:00:00Z"
                                }
                            }
                            """.trimIndent()
                        )
                    }
                })
        )

        val clientScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val client = GoalChatRealtimeClientImpl(
            okHttpClient = okhttp3.OkHttpClient(),
            authSession = authStack.session,
            sessionRefresher = authStack.refresher,
            json = NetworkJson.json,
            scope = clientScope,
            baseUrl = server.url("/").toString(),
        )

        client.connect("g1")

        val event = kotlinx.coroutines.withTimeout(5000) { client.events.first() }
        assertNotNull(event)
        assertEquals(true, event is GoalChatRealtimeEvent.MessageCreated)
        val msg = (event as GoalChatRealtimeEvent.MessageCreated).message
        assertEquals("m123", msg.id)
        assertEquals("Hello live!", msg.body)
        assertEquals("Partner", msg.sender.name)

        client.disconnect()
        clientScope.cancel()
    }
}
