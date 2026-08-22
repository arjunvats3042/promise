package app.promise.android.data.realtime

import app.promise.android.data.goals.ChatMessageDto
import app.promise.android.data.goals.ChatMessageSenderDto
import app.promise.android.data.network.AuthSession
import app.promise.android.data.network.SessionRefresher
import app.promise.android.data.remote.ApiConfig
import app.promise.android.di.PublicHttp
import app.promise.android.domain.ChatMessage
import app.promise.android.domain.ChatMessageDeliveryStatus
import app.promise.android.domain.ChatMessageSender
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class ChatConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

sealed interface GoalChatRealtimeEvent {
    data class MessageCreated(val message: ChatMessage) : GoalChatRealtimeEvent
    data object ParticipantRevoked : GoalChatRealtimeEvent
}

@Serializable
internal data class WsEventDto(
    val type: String,
    val code: String? = null,
    val detail: String? = null,
    val message: ChatMessageDto? = null,
)

interface GoalChatRealtimeClient {
    val connectionState: StateFlow<ChatConnectionState>
    val events: Flow<GoalChatRealtimeEvent>
    fun connect(goalId: String)
    fun disconnect()
}

@Singleton
class GoalChatRealtimeClientImpl @Inject constructor(
    @param:PublicHttp private val okHttpClient: OkHttpClient,
    private val authSession: AuthSession,
    private val sessionRefresher: SessionRefresher,
    private val json: Json,
) : GoalChatRealtimeClient {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var baseUrl: String? = null

    constructor(
        okHttpClient: OkHttpClient,
        authSession: AuthSession,
        sessionRefresher: SessionRefresher,
        json: Json,
        scope: CoroutineScope,
        baseUrl: String? = null,
    ) : this(okHttpClient, authSession, sessionRefresher, json) {
        this.scope = scope
        this.baseUrl = baseUrl
    }
    private var connectionJob: Job? = null
    private var activeWebSocket: WebSocket? = null
    private var currentGoalId: String? = null

    private val _connectionState = MutableStateFlow(ChatConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    private val eventChannel = Channel<GoalChatRealtimeEvent>(Channel.BUFFERED)
    override val events: Flow<GoalChatRealtimeEvent> = eventChannel.receiveAsFlow()

    private val wsHttpClient by lazy {
        okHttpClient.newBuilder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    private val backoffDelays = listOf(1000L, 2000L, 5000L, 10000L, 30000L)

    private var activeDisconnectChannel: Channel<DisconnectReason>? = null

    override fun connect(goalId: String) {
        if (currentGoalId == goalId && connectionJob?.isActive == true) return
        disconnect()
        currentGoalId = goalId

        connectionJob = scope.launch {
            var attempt = 0
            while (isActive && currentGoalId == goalId) {
                var token = authSession.accessToken
                if (token.isNullOrBlank()) {
                    val refreshed = sessionRefresher.refresh()
                    if (!refreshed) {
                        delay(backoffDelays.minOrNull() ?: 1000L)
                        continue
                    }
                    token = authSession.accessToken
                }

                if (token.isNullOrBlank()) {
                    delay(1000L)
                    continue
                }

                _connectionState.value = ChatConnectionState.CONNECTING
                val wsUrl = ApiConfig.webSocketUrl(goalId, baseUrl)
                val request = Request.Builder()
                    .url(wsUrl)
                    .header("Authorization", "Bearer $token")
                    .build()

                val disconnectChannel = Channel<DisconnectReason>(Channel.CONFLATED)
                activeDisconnectChannel = disconnectChannel

                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        _connectionState.value = ChatConnectionState.CONNECTED
                        attempt = 0
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val parsed = json.decodeFromString<WsEventDto>(text)
                            when (parsed.type) {
                                "message.created" -> {
                                    parsed.message?.let { dto ->
                                        val domainMsg = ChatMessage(
                                            id = dto.id,
                                            sender = ChatMessageSender(
                                                id = dto.sender.id,
                                                name = dto.sender.name,
                                            ),
                                            body = dto.body,
                                            createdAt = dto.createdAt,
                                            deliveryStatus = ChatMessageDeliveryStatus.SENT,
                                        )
                                        eventChannel.trySend(GoalChatRealtimeEvent.MessageCreated(domainMsg))
                                    }
                                }
                                "chat.error" -> {
                                    if (parsed.code == "FORBIDDEN") {
                                        eventChannel.trySend(GoalChatRealtimeEvent.ParticipantRevoked)
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        _connectionState.value = ChatConnectionState.DISCONNECTED
                        disconnectChannel.trySend(DisconnectReason(code, null))
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        _connectionState.value = ChatConnectionState.DISCONNECTED
                        disconnectChannel.trySend(DisconnectReason(response?.code ?: 0, t))
                    }
                }

                val ws = wsHttpClient.newWebSocket(request, listener)
                activeWebSocket = ws

                val disconnectReason = try {
                    disconnectChannel.receive()
                } catch (_: Throwable) {
                    DisconnectReason(1000, null)
                } finally {
                    activeWebSocket = null
                    activeDisconnectChannel = null
                }

                if (!isActive || currentGoalId != goalId) {
                    break
                }

                if (disconnectReason.code == 4401) {
                    sessionRefresher.refresh()
                } else if (disconnectReason.code == 4403 || disconnectReason.code == 4404 || disconnectReason.code == 1000) {
                    // Terminal authorization failure or clean client disconnect
                    _connectionState.value = ChatConnectionState.DISCONNECTED
                    break
                }

                val baseDelay = backoffDelays[attempt.coerceAtMost(backoffDelays.lastIndex)]
                val jitterMultiplier = 0.8 + (Math.random() * 0.4)
                val delayMs = (baseDelay * jitterMultiplier).toLong()
                attempt++
                delay(delayMs)
            }
        }
    }

    override fun disconnect() {
        currentGoalId = null
        activeDisconnectChannel?.trySend(DisconnectReason(1000, null))
        activeDisconnectChannel = null
        try {
            activeWebSocket?.cancel()
        } catch (_: Throwable) {}
        activeWebSocket = null
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = ChatConnectionState.DISCONNECTED
    }

    private data class DisconnectReason(val code: Int, val throwable: Throwable?)
}
