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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent

import app.promise.android.domain.AiRepository
import app.promise.android.domain.GoalChatAiSummary

data class GoalChatUiState(
    val goal: Goal? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isPaginating: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val sendAction: ActionState = ActionState.Idle,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<app.promise.android.domain.ChatSearchResult> = emptyList(),
)

@HiltViewModel
class GoalChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    private val authSession: AuthSession,
    private val haptics: PromiseHaptics,
    private val realtimeClient: GoalChatRealtimeClient,
    private val appEventBus: AppEventBus,
    private val aiRepository: AiRepository,
) : ViewModel() {
    val goalId: String = savedStateHandle.get<String>("goalId")
        ?: savedStateHandle.toRoute<GoalChatRoute>().goalId

    suspend fun summarizeChat(goalId: String, limit: Int = 50): GoalChatAiSummary {
        return aiRepository.summarizeChat(goalId, limit)
    }

    private val _state = MutableStateFlow(GoalChatUiState())
    val state: StateFlow<GoalChatUiState> = _state.asStateFlow()

    private val _chatSearchQuery = MutableStateFlow("")

    init {
        loadInitial()
        realtimeClient.connect(goalId)
        observeRealtimeEvents()
        observeRealtimeConnection()
        observeChatSearch()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeChatSearch() {
        viewModelScope.launch {
            _chatSearchQuery
                .debounce(300L)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    flow {
                        val trimmed = q.trim()
                        if (trimmed.isEmpty()) {
                            emit(emptyList<app.promise.android.domain.ChatSearchResult>())
                            return@flow
                        }
                        _state.update { it.copy(isSearching = true) }
                        try {
                            val results = repository.searchChatMessages(goalId, trimmed)
                            emit(results)
                        } catch (t: Throwable) {
                            emit(emptyList())
                        } finally {
                            _state.update { it.copy(isSearching = false) }
                        }
                    }
                }
                .collect { results ->
                    _state.update { it.copy(searchResults = results) }
                }
        }
    }

    fun toggleSearch(open: Boolean) {
        _state.update {
            it.copy(
                isSearchOpen = open,
                searchQuery = if (!open) "" else it.searchQuery,
                searchResults = if (!open) emptyList() else it.searchResults,
            )
        }
        if (!open) {
            _chatSearchQuery.value = ""
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        _chatSearchQuery.value = query
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
                val descending = fetched.sortedWith(ChatDateUtil.messageComparator)
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
            val updated = reconcileMessage(current.messages, msg)
            current.copy(messages = updated)
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
                val sorted = existingMap.values.sortedWith(ChatDateUtil.messageComparator)
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
                val olderDescending = older.sortedWith(ChatDateUtil.messageComparator)
                _state.update {
                    it.copy(
                        messages = (it.messages + olderDescending).distinctBy { msg -> msg.id }.sortedWith(ChatDateUtil.messageComparator),
                        isPaginating = false,
                        hasMore = older.size >= PAGE_SIZE,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(isPaginating = false) }
            }
        }
    }

    private fun reconcileMessage(
        currentMessages: List<ChatMessage>,
        incomingMessage: ChatMessage,
        preferredTempId: String? = null,
    ): List<ChatMessage> {
        // 1. If incoming permanent ID is already in list, update it
        val existingIndex = currentMessages.indexOfFirst { it.id == incomingMessage.id }
        if (existingIndex >= 0) {
            return currentMessages.map { if (it.id == incomingMessage.id) incomingMessage else it }
                .sortedWith(ChatDateUtil.messageComparator)
        }

        // 2. Find matching optimistic temp message
        val tempMatch = currentMessages.firstOrNull { msg ->
            if (!msg.id.startsWith("temp-")) return@firstOrNull false
            if (preferredTempId != null && msg.id == preferredTempId) return@firstOrNull true
            msg.body == incomingMessage.body && (
                msg.sender.id == incomingMessage.sender.id ||
                msg.sender.id.isBlank() ||
                incomingMessage.sender.id.isBlank() ||
                msg.sender.name == incomingMessage.sender.name
            )
        }

        val updated = if (tempMatch != null) {
            currentMessages.map { if (it.id == tempMatch.id) incomingMessage else it }
        } else {
            listOf(incomingMessage) + currentMessages
        }

        return updated.distinctBy { it.id }.sortedWith(ChatDateUtil.messageComparator)
    }

    fun sendMessage(body: String) {
        val goal = _state.value.goal
        if (goal != null && (goal.status != app.promise.android.domain.GoalStatus.ACTIVE || !goal.canSendChat)) {
            return
        }
        val trimmed = body.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_MESSAGE_LENGTH) return

        val currentUser = authSession.user.value
        val tempId = "temp-${UUID.randomUUID()}"
        
        // Prevent local clock skew from sorting optimistic message above existing messages
        val latestEpoch = _state.value.messages.firstOrNull()?.let { ChatDateUtil.parseIsoToEpochMillis(it.createdAt) } ?: 0L
        val nowEpoch = maxOf(System.currentTimeMillis(), latestEpoch + 1)
        val nowIso = Instant.ofEpochMilli(nowEpoch).toString()

        val optimisticMessage = ChatMessage(
            id = tempId,
            sender = ChatMessageSender(
                id = currentUser?.id ?: "",
                name = currentUser?.name ?: "You",
                avatarUrl = currentUser?.avatarUrl,
            ),
            body = trimmed,
            createdAt = nowIso,
            deliveryStatus = ChatMessageDeliveryStatus.SENDING,
        )

        _state.update { current ->
            current.copy(
                messages = (listOf(optimisticMessage) + current.messages).sortedWith(ChatDateUtil.messageComparator),
                sendAction = ActionState.InFlight,
            )
        }

        viewModelScope.launch {
            try {
                val serverMessage = repository.sendChatMessage(goalId, trimmed)
                _state.update { current ->
                    val reconciled = reconcileMessage(
                        currentMessages = current.messages,
                        incomingMessage = serverMessage.copy(deliveryStatus = ChatMessageDeliveryStatus.SENT),
                        preferredTempId = tempId,
                    )
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

internal object ChatDateUtil {
    fun parseIsoToEpochMillis(isoString: String): Long {
        if (isoString.isBlank()) return 0L
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Throwable) {
            try {
                java.time.OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
            } catch (_: Throwable) {
                try {
                    java.time.ZonedDateTime.parse(isoString).toInstant().toEpochMilli()
                } catch (_: Throwable) {
                    0L
                }
            }
        }
    }

    val messageComparator = Comparator<ChatMessage> { a, b ->
        val timeA = parseIsoToEpochMillis(a.createdAt)
        val timeB = parseIsoToEpochMillis(b.createdAt)
        if (timeA != timeB) {
            timeB.compareTo(timeA) // Descending: newest first (index 0 at bottom)
        } else {
            b.id.compareTo(a.id)
        }
    }
}
