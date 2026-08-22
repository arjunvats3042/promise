package app.promise.android.ui.goals.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.promise.android.core.ActionState
import app.promise.android.core.toErrorKind
import app.promise.android.core.toUserMessage
import app.promise.android.data.network.AuthSession
import app.promise.android.data.network.toApiException
import app.promise.android.data.realtime.ChatConnectionState
import app.promise.android.data.realtime.GoalChatRealtimeClient
import app.promise.android.data.realtime.GoalChatRealtimeEvent
import app.promise.android.domain.ChatMessage
import app.promise.android.domain.ChatMessageDeliveryStatus
import app.promise.android.domain.ChatMessageSender
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalRepository
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.navigation.GoalChatRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent

data class GoalChatUiState(
    val goal: Goal? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isPaginating: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val sendAction: ActionState = ActionState.Idle,
)

@HiltViewModel
class GoalChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    private val authSession: AuthSession,
    private val haptics: PromiseHaptics,
    private val realtimeClient: GoalChatRealtimeClient,
    private val appEventBus: AppEventBus,
) : ViewModel() {
    val goalId: String = savedStateHandle.get<String>("goalId")
        ?: savedStateHandle.toRoute<GoalChatRoute>().goalId

    private val _state = MutableStateFlow(GoalChatUiState())
    val state: StateFlow<GoalChatUiState> = _state.asStateFlow()

    init {
        loadInitial()
        realtimeClient.connect(goalId)
        observeRealtimeEvents()
        observeRealtimeConnection()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val goalDetail = repository.getDetail(goalId)
                val goal = when (goalDetail) {
                    is GoalDetail.Full -> goalDetail.goal
                    is GoalDetail.Invite -> null
                }
                val fetched = repository.listChatMessages(goalId, limit = PAGE_SIZE)
                val descending = fetched.sortedWith(
                    compareByDescending<ChatMessage> { it.createdAt }.thenByDescending { it.id },
                )
                _state.update {
                    it.copy(
                        goal = goal,
                        messages = descending,
                        isLoading = false,
                        hasMore = fetched.size >= PAGE_SIZE,
                    )
                }
                descending.firstOrNull()?.let { newest ->
                    runCatching { repository.markChatRead(goalId, newest.id) }
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.toApiException().toErrorKind().toUserMessage(),
                    )
                }
            }
        }
    }

    private fun observeRealtimeEvents() {
        viewModelScope.launch {
            realtimeClient.events.collect { event ->
                when (event) {
                    is GoalChatRealtimeEvent.MessageCreated -> {
                        onRealtimeMessage(event.message)
                    }
                    is GoalChatRealtimeEvent.ParticipantRevoked -> {
                        _state.update {
                            it.copy(errorMessage = "You are no longer an active participant in this goal.")
                        }
                    }
                }
            }
        }
    }

    private fun onRealtimeMessage(msg: ChatMessage) {
        _state.update { current ->
            if (current.messages.any { it.id == msg.id }) {
                return@update current
            }
            val tempMatch = current.messages.firstOrNull {
                it.id.startsWith("temp-") && it.body == msg.body && it.sender.id == msg.sender.id
            }
            val updatedMessages = if (tempMatch != null) {
                current.messages.map { if (it.id == tempMatch.id) msg else it }
            } else {
                listOf(msg) + current.messages
            }
            val sorted = updatedMessages.sortedWith(
                compareByDescending<ChatMessage> { it.createdAt }.thenByDescending { it.id },
            )
            current.copy(messages = sorted)
        }
        appEventBus.emit(AppMutationEvent.ChatMessageCreated(goalId, msg.id))
        viewModelScope.launch {
            runCatching { repository.markChatRead(goalId, msg.id) }
        }
    }

    private fun observeRealtimeConnection() {
        var hadDisconnect = false
        viewModelScope.launch {
            realtimeClient.connectionState.collect { connState ->
                if (connState == ChatConnectionState.DISCONNECTED) {
                    hadDisconnect = true
                } else if (connState == ChatConnectionState.CONNECTED && hadDisconnect) {
                    hadDisconnect = false
                    catchUpMessages()
                }
            }
        }
    }

    private suspend fun catchUpMessages() {
        try {
            val fresh = repository.listChatMessages(goalId, limit = PAGE_SIZE)
            _state.update { current ->
                val existingMap = current.messages.associateBy { it.id }.toMutableMap()
                for (msg in fresh) {
                    existingMap[msg.id] = msg
                }
                val sorted = existingMap.values.sortedWith(
                    compareByDescending<ChatMessage> { it.createdAt }.thenByDescending { it.id },
                )
                current.copy(messages = sorted)
            }
            fresh.firstOrNull()?.let { newest ->
                runCatching { repository.markChatRead(goalId, newest.id) }
            }
        } catch (_: Throwable) {}
    }

    fun loadOlder() {
        val current = _state.value
        if (current.isPaginating || !current.hasMore || current.isLoading) return
        val oldest = current.messages.lastOrNull() ?: return

        viewModelScope.launch {
            _state.update { it.copy(isPaginating = true) }
            try {
                val older = repository.listChatMessages(
                    goalId = goalId,
                    limit = PAGE_SIZE,
                    beforeCreatedAt = oldest.createdAt,
                    beforeId = oldest.id,
                )
                val olderDescending = older.sortedWith(
                    compareByDescending<ChatMessage> { it.createdAt }.thenByDescending { it.id },
                )
                _state.update {
                    it.copy(
                        messages = it.messages + olderDescending,
                        isPaginating = false,
                        hasMore = older.size >= PAGE_SIZE,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isPaginating = false) }
            }
        }
    }

    fun sendMessage(body: String) {
        val trimmed = body.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_MESSAGE_LENGTH) return

        val currentUser = authSession.user.value
        val tempId = "temp-${UUID.randomUUID()}"
        val nowIso = Instant.now().toString()

        val optimisticMessage = ChatMessage(
            id = tempId,
            sender = ChatMessageSender(
                id = currentUser?.id ?: "",
                name = currentUser?.name ?: "You",
            ),
            body = trimmed,
            createdAt = nowIso,
            deliveryStatus = ChatMessageDeliveryStatus.SENDING,
        )

        _state.update {
            it.copy(
                messages = listOf(optimisticMessage) + it.messages,
                sendAction = ActionState.InFlight,
            )
        }

        viewModelScope.launch {
            try {
                val serverMessage = repository.sendChatMessage(goalId, trimmed)
                _state.update { current ->
                    val reconciled = current.messages.map { msg ->
                        if (msg.id == tempId) serverMessage.copy(deliveryStatus = ChatMessageDeliveryStatus.SENT)
                        else msg
                    }
                    current.copy(
                        messages = reconciled,
                        sendAction = ActionState.Idle,
                    )
                }
                haptics.light()
                appEventBus.emit(AppMutationEvent.ChatMessageCreated(goalId, serverMessage.id))
                runCatching { repository.markChatRead(goalId, serverMessage.id) }
            } catch (t: Throwable) {
                _state.update { current ->
                    val failed = current.messages.map { msg ->
                        if (msg.id == tempId) msg.copy(deliveryStatus = ChatMessageDeliveryStatus.FAILED)
                        else msg
                    }
                    current.copy(
                        messages = failed,
                        sendAction = ActionState.Idle,
                    )
                }
            }
        }
    }

    fun retryMessage(tempId: String) {
        val message = _state.value.messages.firstOrNull { it.id == tempId } ?: return
        _state.update { current ->
            current.copy(messages = current.messages.filterNot { it.id == tempId })
        }
        sendMessage(message.body)
    }

    fun currentUserId(): String? = authSession.user.value?.id

    override fun onCleared() {
        super.onCleared()
        realtimeClient.disconnect()
    }

    companion object {
        const val PAGE_SIZE = 50
        const val MAX_MESSAGE_LENGTH = 2000
    }
}
