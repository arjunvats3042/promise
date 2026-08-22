package app.promise.android.ui.goals.chat

import androidx.lifecycle.SavedStateHandle
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.data.realtime.ChatConnectionState
import app.promise.android.data.realtime.GoalChatRealtimeClient
import app.promise.android.data.realtime.GoalChatRealtimeEvent
import app.promise.android.domain.ChatMessage
import app.promise.android.domain.ChatMessageDeliveryStatus
import app.promise.android.domain.ChatMessageSender
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalPage
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.User
import app.promise.android.ui.haptics.FakePromiseHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent

@OptIn(ExperimentalCoroutinesApi::class)
class GoalChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var authSession: AuthSession
    private lateinit var haptics: FakePromiseHaptics
    private lateinit var fakeRealtimeClient: FakeGoalChatRealtimeClient
    private lateinit var eventBus: AppEventBus

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authSession = AuthSession()
        authSession.setUser(User(id = "u1", email = "arjun@example.com", name = "Arjun", timezone = "UTC", createdAt = "2026-08-01T00:00:00Z"))
        haptics = FakePromiseHaptics()
        fakeRealtimeClient = FakeGoalChatRealtimeClient()
        eventBus = AppEventBus()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadInitial_fetchesGoalAndMessages_andMarksRead() = runTest {
        val messages = listOf(
            ChatMessage("m1", ChatMessageSender("u1", "Arjun"), "Hello", "2026-08-20T10:00:00Z"),
            ChatMessage("m2", ChatMessageSender("u2", "Rahul"), "Hey!", "2026-08-20T10:05:00Z"),
        )
        val repo = FakeChatGoalRepository(initialMessages = messages)
        val vm = GoalChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            repository = repo,
            authSession = authSession,
            haptics = haptics,
            realtimeClient = fakeRealtimeClient,
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.goal)
        assertEquals(2, state.messages.size)
        // Index 0 must be newest (m2) for reverseLayout
        assertEquals("m2", state.messages[0].id)
        assertEquals("m1", state.messages[1].id)
        assertEquals("m2", repo.lastReadMessageId)
        assertEquals("g1", fakeRealtimeClient.connectedGoalId)
    }

    @Test
    fun loadOlder_paginatesMessagesUsingCursor() = runTest {
        val initialBatch = (1..50).map { i ->
            ChatMessage("m$i", ChatMessageSender("u1", "Arjun"), "Msg $i", "2026-08-20T10:${i.toString().padStart(2, '0')}:00Z")
        }
        val olderBatch = listOf(
            ChatMessage("old1", ChatMessageSender("u1", "Arjun"), "Old Msg", "2026-08-19T10:00:00Z"),
        )
        val repo = FakeChatGoalRepository(
            initialMessages = initialBatch,
            olderMessages = olderBatch,
        )
        val vm = GoalChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            repository = repo,
            authSession = authSession,
            haptics = haptics,
            realtimeClient = fakeRealtimeClient,
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        assertTrue(vm.state.value.hasMore)
        assertEquals(50, vm.state.value.messages.size)

        vm.loadOlder()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isPaginating)
        assertEquals(51, state.messages.size)
        assertEquals("old1", state.messages.last().id)
        assertEquals("m1", repo.lastBeforeIdQueried)
    }

    @Test
    fun sendMessage_optimisticUpdate_reconcilesWithServer() = runTest {
        val repo = FakeChatGoalRepository()
        val vm = GoalChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            repository = repo,
            authSession = authSession,
            haptics = haptics,
            realtimeClient = fakeRealtimeClient,
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        vm.sendMessage("Let's do this! 💪")

        // First state before network response should have optimistic message
        val optimisticState = vm.state.value
        assertEquals(1, optimisticState.messages.size)
        assertTrue(optimisticState.messages[0].id.startsWith("temp-"))
        assertEquals("Let's do this! 💪", optimisticState.messages[0].body)

        advanceUntilIdle()

        // After completion: reconciled with server message
        val completedState = vm.state.value
        assertEquals(1, completedState.messages.size)
        assertEquals("server-msg-1", completedState.messages[0].id)
        assertEquals(ChatMessageDeliveryStatus.SENT, completedState.messages[0].deliveryStatus)
        assertEquals("server-msg-1", repo.lastReadMessageId)
    }

    @Test
    fun realtimeEvent_messageCreated_addsMessageAndDeduplicates() = runTest {
        val repo = FakeChatGoalRepository()
        val vm = GoalChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            repository = repo,
            authSession = authSession,
            haptics = haptics,
            realtimeClient = fakeRealtimeClient,
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        val incoming = ChatMessage("rt-1", ChatMessageSender("u2", "Partner"), "Live message! ⚡", "2026-08-20T12:05:00Z")
        fakeRealtimeClient.emit(GoalChatRealtimeEvent.MessageCreated(incoming))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.messages.size)
        assertEquals("rt-1", vm.state.value.messages[0].id)
        assertEquals("Live message! ⚡", vm.state.value.messages[0].body)
        assertEquals("rt-1", repo.lastReadMessageId)

        // Emit same message again -> duplicate ignored
        fakeRealtimeClient.emit(GoalChatRealtimeEvent.MessageCreated(incoming))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.messages.size)
    }

    @Test
    fun realtimeEvent_reconnect_catchesUpMissedMessages() = runTest {
        val repo = FakeChatGoalRepository(initialMessages = listOf(
            ChatMessage("m1", ChatMessageSender("u1", "Arjun"), "Initial", "2026-08-20T10:00:00Z"),
        ))
        val vm = GoalChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            repository = repo,
            authSession = authSession,
            haptics = haptics,
            realtimeClient = fakeRealtimeClient,
            appEventBus = eventBus,
        )

        advanceUntilIdle()
        assertEquals(1, vm.state.value.messages.size)

        // Simulate disconnect then new message on server then reconnect
        fakeRealtimeClient.setConnectionState(ChatConnectionState.DISCONNECTED)
        advanceUntilIdle()

        repo.setMessages(listOf(
            ChatMessage("m1", ChatMessageSender("u1", "Arjun"), "Initial", "2026-08-20T10:00:00Z"),
            ChatMessage("m2", ChatMessageSender("u2", "Partner"), "Missed while offline", "2026-08-20T10:30:00Z"),
        ))

        fakeRealtimeClient.setConnectionState(ChatConnectionState.CONNECTED)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.messages.size)
        assertEquals("m2", state.messages[0].id)
        assertEquals("Missed while offline", state.messages[0].body)
    }

    @Test
    fun realtimeEvent_participantRevoked_showsErrorMessage() = runTest {
        val repo = FakeChatGoalRepository()
        val vm = GoalChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            repository = repo,
            authSession = authSession,
            haptics = haptics,
            realtimeClient = fakeRealtimeClient,
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        fakeRealtimeClient.emit(GoalChatRealtimeEvent.ParticipantRevoked)
        advanceUntilIdle()

        assertEquals("You are no longer an active participant in this goal.", vm.state.value.errorMessage)
    }

    @Test
    fun sendMessage_blankOrTooLong_ignored() = runTest {
        val repo = FakeChatGoalRepository()
        val vm = GoalChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            repository = repo,
            authSession = authSession,
            haptics = haptics,
            realtimeClient = fakeRealtimeClient,
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        vm.sendMessage("   ")
        advanceUntilIdle()
        assertEquals(0, vm.state.value.messages.size)

        val longBody = "a".repeat(2001)
        vm.sendMessage(longBody)
        advanceUntilIdle()
        assertEquals(0, vm.state.value.messages.size)
    }

    @Test
    fun sendMessage_failure_setsFailedStatus_andAllowsRetry() = runTest {
        val repo = FakeChatGoalRepository(failSend = true)
        val vm = GoalChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            repository = repo,
            authSession = authSession,
            haptics = haptics,
            realtimeClient = fakeRealtimeClient,
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        vm.sendMessage("Important message")
        advanceUntilIdle()

        val failedState = vm.state.value
        assertEquals(1, failedState.messages.size)
        val failedMsg = failedState.messages[0]
        assertEquals(ChatMessageDeliveryStatus.FAILED, failedMsg.deliveryStatus)

        // Retry after fixing failure
        repo.failSend = false
        vm.retryMessage(failedMsg.id)
        advanceUntilIdle()

        val recoveredState = vm.state.value
        assertEquals(1, recoveredState.messages.size)
        assertEquals("server-msg-1", recoveredState.messages[0].id)
        assertEquals(ChatMessageDeliveryStatus.SENT, recoveredState.messages[0].deliveryStatus)
    }
}

class FakeGoalChatRealtimeClient : GoalChatRealtimeClient {
    var connectedGoalId: String? = null
    private val _connectionState = MutableStateFlow(ChatConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    private val eventChannel = Channel<GoalChatRealtimeEvent>(Channel.BUFFERED)
    override val events: Flow<GoalChatRealtimeEvent> = eventChannel.receiveAsFlow()

    override fun connect(goalId: String) {
        connectedGoalId = goalId
        _connectionState.value = ChatConnectionState.CONNECTED
    }

    override fun disconnect() {
        connectedGoalId = null
        _connectionState.value = ChatConnectionState.DISCONNECTED
    }

    fun emit(event: GoalChatRealtimeEvent) {
        eventChannel.trySend(event)
    }

    fun setConnectionState(state: ChatConnectionState) {
        _connectionState.value = state
    }
}

private class FakeChatGoalRepository(
    private var initialMessages: List<ChatMessage> = emptyList(),
    private val olderMessages: List<ChatMessage> = emptyList(),
    var failSend: Boolean = false,
) : GoalRepository {
    var lastReadMessageId: String? = null
    var lastBeforeIdQueried: String? = null
    private var sendCount = 0

    fun setMessages(messages: List<ChatMessage>) {
        this.initialMessages = messages
    }

    override suspend fun list(filter: GoalListFilter, page: Int, pageSize: Int): GoalPage =
        GoalPage(emptyList(), null)

    override suspend fun get(id: String): Goal = sampleGoal(id)

    override suspend fun getDetail(id: String): GoalDetail =
        GoalDetail.Full(sampleGoal(id))

    override suspend fun create(input: CreateGoalInput): Goal = sampleGoal("g1")

    override suspend fun pause(id: String): Goal = sampleGoal(id)

    override suspend fun resume(id: String): Goal = sampleGoal(id)

    override suspend fun complete(id: String): Goal = sampleGoal(id)

    override suspend fun cancel(id: String): Goal = sampleGoal(id)

    override suspend fun checkIn(id: String, input: CheckInInput): GoalCheckIn =
        throw UnsupportedOperationException()

    override suspend fun listCheckIns(id: String, startDate: String?, endDate: String?, page: Int): GoalCheckInPage =
        GoalCheckInPage(emptyList(), null)

    override suspend fun inviteParticipant(goalId: String, userId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun listParticipants(goalId: String): List<GoalParticipant> = emptyList()

    override suspend fun acceptInvitation(goalId: String): GoalDetail =
        GoalDetail.Full(sampleGoal(goalId))

    override suspend fun declineInvitation(goalId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun reinviteParticipant(
        goalId: String,
        participantId: String?,
        userId: String?,
    ): GoalParticipant = throw UnsupportedOperationException()

    override suspend fun transferOwnership(
        goalId: String,
        participantId: String?,
        userId: String?,
    ): GoalDetail = GoalDetail.Full(sampleGoal(goalId))

    override suspend fun removeParticipant(goalId: String, userId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun leave(goalId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun listChatMessages(
        goalId: String,
        limit: Int,
        beforeCreatedAt: String?,
        beforeId: String?,
    ): List<ChatMessage> {
        if (beforeId != null) {
            lastBeforeIdQueried = beforeId
            return olderMessages
        }
        return initialMessages
    }

    override suspend fun sendChatMessage(goalId: String, body: String): ChatMessage {
        if (failSend) {
            throw ApiException(status = 500, code = "NETWORK_ERROR")
        }
        sendCount++
        return ChatMessage(
            id = "server-msg-$sendCount",
            sender = ChatMessageSender("u1", "Arjun"),
            body = body,
            createdAt = "2026-08-20T12:00:00Z",
        )
    }

    override suspend fun markChatRead(
        goalId: String,
        lastReadMessageId: String,
    ): app.promise.android.domain.GoalChatReadState {
        this.lastReadMessageId = lastReadMessageId
        return app.promise.android.domain.GoalChatReadState(lastReadMessageId, "2026-08-20T12:00:00Z")
    }

    override suspend fun getChatSummary(goalId: String): app.promise.android.domain.GoalChatSummary =
        app.promise.android.domain.GoalChatSummary(0, null)

    override suspend fun listActivity(
        goalId: String,
        limit: Int,
        beforeCreatedAt: String?,
        beforeId: String?,
    ): List<app.promise.android.domain.GoalActivityItem> = emptyList()
}

private fun sampleGoal(id: String): Goal = Goal(
    id = id,
    title = "Read together",
    description = "",
    status = GoalStatus.ACTIVE,
    timezone = "UTC",
    startDate = "2026-08-01",
    endDate = null,
    recurrenceKind = GoalRecurrenceKind.DAILY,
    weekdays = emptyList(),
    periodUnit = null,
    timesPerPeriod = null,
    trackingKind = GoalTrackingKind.BINARY,
    targetValue = null,
    targetUnit = "",
    source = "MANUAL",
    pausedAt = null,
    completedAt = null,
    cancelledAt = null,
    createdAt = "2026-08-01T00:00:00Z",
    updatedAt = "2026-08-01T00:00:00Z",
    isEnded = false,
    progress = GoalProgress(
        currentPeriod = GoalPeriodCounts(1, 0),
        weekProgress = GoalPeriodCounts(7, 6),
        consistencyPercent = 80,
    ),
    currentStreak = 5,
)
