package app.promise.android.ui.goals

import androidx.lifecycle.SavedStateHandle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.core.events.MembershipChangeType
import app.promise.android.data.home.HomeFreshness
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalActivityActor
import app.promise.android.domain.GoalActivityItem
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalInvitePreview
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalPage
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalParticipantRole
import app.promise.android.domain.GoalParticipantStatus
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.LookupUser
import app.promise.android.domain.UserRepository
import app.promise.android.ui.haptics.FakePromiseHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var eventBus: AppEventBus

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        eventBus = AppEventBus()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun complete_updatesStateAndConfirms() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.complete()
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(GoalStatus.COMPLETED, (ready.value as GoalDetailUi.Full).goal.status)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun pause_usesLightHaptic() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.pause()
        advanceUntilIdle()
        assertEquals(listOf("light"), haptics.events)
        assertEquals(GoalStatus.PAUSED, ((vm.state.value as LoadState.Ready).value as GoalDetailUi.Full).goal.status)
    }

    @Test
    fun resume_updatesState() = runTest {
        val repo = DetailFakeRepo(sample("g1", status = GoalStatus.PAUSED))
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), FakePromiseHaptics(), eventBus)
        advanceUntilIdle()
        vm.resume()
        advanceUntilIdle()
        assertEquals(GoalStatus.ACTIVE, ((vm.state.value as LoadState.Ready).value as GoalDetailUi.Full).goal.status)
    }

    @Test
    fun cancel_updatesStateAndConfirms() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        assertEquals(GoalStatus.CANCELLED, ((vm.state.value as LoadState.Ready).value as GoalDetailUi.Full).goal.status)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun checkIn_updatesStateAndConfirms() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.checkIn(CheckInInput(status = GoalCheckInStatus.COMPLETED))
        advanceUntilIdle()
        assertTrue(repo.checkInCalled)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun inviteParticipant_callsRepositoryAndRefreshesRoster() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.inviteParticipant("u2")
        advanceUntilIdle()
        assertTrue(repo.inviteCalled)
        assertEquals("u2", repo.invitedUserId)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun removeParticipant_callsRepositoryAndRefreshesRoster() = runTest {
        val initialRoster = listOf(
            GoalParticipant("p1", "u1", "Owner", GoalParticipantRole.OWNER, GoalParticipantStatus.ACTIVE, null, null, null),
            GoalParticipant("p2", "u2", "Member", GoalParticipantRole.PARTICIPANT, GoalParticipantStatus.ACTIVE, null, null, null),
        )
        val repo = DetailFakeRepo(sample("g1"), initialRoster = initialRoster)
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.removeParticipant("u2")
        advanceUntilIdle()
        assertTrue(repo.removeCalled)
        assertEquals("u2", repo.removedUserId)
    }

    @Test
    fun userLookup_findsUser() = runTest {
        val fakeUsers = FakeUserRepository(mapOf("test@example.com" to LookupUser("u2", "test@example.com", "Test User")))
        val vm = GoalDetailViewModel(handle("g1"), DetailFakeRepo(sample("g1")), fakeUsers, AuthSession(), HomeFreshness(), FakePromiseHaptics(), eventBus)
        advanceUntilIdle()
        vm.lookupUser("test@example.com")
        advanceUntilIdle()
        val found = vm.lookupState.value as UserLookupUi.Found
        assertEquals("u2", found.user.id)
    }

    @Test
    fun userLookup_notFound() = runTest {
        val fakeUsers = FakeUserRepository(emptyMap())
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), DetailFakeRepo(sample("g1")), fakeUsers, AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.lookupUser("unknown@example.com")
        advanceUntilIdle()
        val notFound = vm.lookupState.value as UserLookupUi.NotFound
        assertEquals("unknown@example.com", notFound.email)
    }

    @Test
    fun userLookup_conflict_handlesError() = runTest {
        val fakeUsers = FakeUserRepository(
            error = ApiException(status = 409, code = "USER_LOOKUP_FAILED"),
        )
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), DetailFakeRepo(sample("g1")), fakeUsers, AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.lookupUser("error@example.com")
        advanceUntilIdle()
        val failed = vm.lookupState.value as UserLookupUi.Failed
        assertEquals(ErrorKind.Conflict, failed.kind)
    }

    @Test
    fun leaveGoal_callsRepositoryAndNotifies() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val homeFreshness = HomeFreshness()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), homeFreshness, haptics, eventBus)
        advanceUntilIdle()

        var leftCalled = false
        vm.leave(onLeft = { leftCalled = true })
        advanceUntilIdle()

        assertTrue(repo.leaveCalled)
        assertTrue(leftCalled)
        assertTrue(homeFreshness.shouldRefresh())
    }

    @Test
    fun acceptInvite_callsRepositoryAndReloads() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()

        var acceptedCalled = false
        vm.acceptInvite(onAccepted = { acceptedCalled = true })
        advanceUntilIdle()

        assertTrue(repo.acceptCalled)
        assertTrue(acceptedCalled)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun declineInvite_callsRepositoryAndNotifies() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()

        var leftCalled = false
        vm.declineInvite(onLeft = { leftCalled = true })
        advanceUntilIdle()

        assertTrue(repo.declineCalled)
        assertTrue(leftCalled)
        assertTrue(haptics.events.contains("light"))
    }

    @Test
    fun loadDetail_fetchesRecentActivity_forSharedGoal() = runTest {
        val roster = listOf(
            GoalParticipant("p1", "u1", "Arjun", GoalParticipantRole.OWNER, GoalParticipantStatus.ACTIVE, null, null, null),
        )
        val activity = listOf(
            GoalActivityItem("a1", "CHECKIN_RECORDED", GoalActivityActor("u1", "Arjun"), null, "Arjun completed today's practice", "2026-08-22", "2026-08-22T10:00:00Z"),
        )
        val repo = DetailFakeRepo(current = sample("g_shared"), initialRoster = roster, initialActivity = activity)
        val vm = GoalDetailViewModel(
            savedStateHandle = handle("g_shared"),
            repository = repo,
            userRepository = FakeUserRepository(),
            authSession = AuthSession(),
            homeFreshness = HomeFreshness(),
            haptics = FakePromiseHaptics(),
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        val state = vm.state.value as LoadState.Ready
        val ui = state.value as GoalDetailUi.Full
        assertEquals(1, ui.recentActivity.size)
        assertEquals("Arjun completed today's practice", ui.recentActivity[0].summary)
    }

    @Test
    fun loadFullActivity_fetchesAndUpdatesActivityState() = runTest {
        val roster = listOf(
            GoalParticipant("p1", "u1", "Arjun", GoalParticipantRole.OWNER, GoalParticipantStatus.ACTIVE, null, null, null),
        )
        val activity = (1..20).map { i ->
            GoalActivityItem("a$i", "CHECKIN_RECORDED", GoalActivityActor("u1", "Arjun"), null, "Activity $i", "2026-08-22", "2026-08-22T10:00:00Z")
        }
        val repo = DetailFakeRepo(current = sample("g_shared"), initialRoster = roster, initialActivity = activity)
        val vm = GoalDetailViewModel(
            savedStateHandle = handle("g_shared"),
            repository = repo,
            userRepository = FakeUserRepository(),
            authSession = AuthSession(),
            homeFreshness = HomeFreshness(),
            haptics = FakePromiseHaptics(),
            appEventBus = eventBus,
        )

        advanceUntilIdle()

        vm.loadFullActivity()
        advanceUntilIdle()

        val actState = vm.activityState.value
        assertFalse(actState.isLoading)
        assertEquals(20, actState.items.size)
        assertTrue(actState.hasMore)
    }

    @Test
    fun transferOwnership_callsRepositoryAndUpdatesState() = runTest {
        val roster = listOf(
            GoalParticipant("p1", "u1", "Owner", GoalParticipantRole.OWNER, GoalParticipantStatus.ACTIVE, null, null, null),
            GoalParticipant("p2", "u2", "Rahul", GoalParticipantRole.PARTICIPANT, GoalParticipantStatus.ACTIVE, null, null, null),
        )
        val repo = DetailFakeRepo(current = sample("g_shared"), initialRoster = roster)
        val vm = GoalDetailViewModel(
            savedStateHandle = handle("g_shared"),
            repository = repo,
            userRepository = FakeUserRepository(),
            authSession = AuthSession(),
            homeFreshness = HomeFreshness(),
            haptics = FakePromiseHaptics(),
            appEventBus = eventBus,
        )
        advanceUntilIdle()

        var done = false
        vm.transferOwnership(participantId = "p2") { done = true }
        advanceUntilIdle()

        assertTrue(done)
        val state = vm.state.value as LoadState.Ready
        val ui = state.value as GoalDetailUi.Full
        assertFalse(ui.goal.isOwnerViewer)
    }

    @Test
    fun reinviteParticipant_callsRepositoryAndRefreshesRoster() = runTest {
        val roster = listOf(
            GoalParticipant("p1", "u1", "Owner", GoalParticipantRole.OWNER, GoalParticipantStatus.ACTIVE, null, null, null),
            GoalParticipant("p2", "u2", "Declined", GoalParticipantRole.PARTICIPANT, GoalParticipantStatus.DECLINED, null, null, null),
        )
        val repo = DetailFakeRepo(current = sample("g_shared"), initialRoster = roster)
        val vm = GoalDetailViewModel(
            savedStateHandle = handle("g_shared"),
            repository = repo,
            userRepository = FakeUserRepository(),
            authSession = AuthSession(),
            homeFreshness = HomeFreshness(),
            haptics = FakePromiseHaptics(),
            appEventBus = eventBus,
        )
        advanceUntilIdle()

        vm.reinviteParticipant(participantId = "p2")
        advanceUntilIdle()

        val action = vm.inviteSheetAction.value
        assertTrue(action is ActionState.Idle)
    }

    private fun handle(id: String): SavedStateHandle {
        return SavedStateHandle(mapOf("goalId" to id))
    }
}

private class DetailFakeRepo(
    private var current: Goal,
    private val failPause: Boolean = false,
    private val failCheckIn: Boolean = false,
    private val failLeave: Boolean = false,
    private val returnInvite: Boolean = false,
    private var initialRoster: List<GoalParticipant> = emptyList(),
    private var initialActivity: List<GoalActivityItem> = emptyList(),
) : GoalRepository {
    var checkInCalled = false
    var inviteCalled = false
    var invitedUserId: String? = null
    var removeCalled = false
    var removedUserId: String? = null
    var leaveCalled = false
    var acceptCalled = false
    var declineCalled = false

    override suspend fun list(filter: GoalListFilter, page: Int, pageSize: Int): GoalPage =
        GoalPage(emptyList(), null)

    override suspend fun get(id: String): Goal = current

    override suspend fun getDetail(id: String): GoalDetail {
        if (returnInvite) {
            return GoalDetail.Invite(sampleInvitePreview(id))
        }
        return GoalDetail.Full(current.copy(participants = initialRoster))
    }

    override suspend fun create(input: CreateGoalInput): Goal = current

    override suspend fun pause(id: String): Goal {
        if (failPause) {
            throw ApiException(status = 409, code = "GOAL_INVALID_TRANSITION")
        }
        current = current.copy(status = GoalStatus.PAUSED)
        return current
    }

    override suspend fun resume(id: String): Goal {
        current = current.copy(status = GoalStatus.ACTIVE)
        return current
    }

    override suspend fun complete(id: String): Goal {
        current = current.copy(status = GoalStatus.COMPLETED)
        return current
    }

    override suspend fun cancel(id: String): Goal {
        current = current.copy(status = GoalStatus.CANCELLED)
        return current
    }

    override suspend fun checkIn(id: String, input: CheckInInput): GoalCheckIn {
        if (failCheckIn) {
            throw ApiException(status = 409, code = "GOAL_INVALID_TRANSITION")
        }
        checkInCalled = true
        return GoalCheckIn(
            id = "c1",
            periodDate = "2026-08-20",
            status = input.status,
            value = input.value,
            note = "",
            checkedAt = "2026-08-20T12:00:00Z",
            createdAt = "2026-08-20T12:00:00Z",
            updatedAt = "2026-08-20T12:00:00Z",
        )
    }

    override suspend fun listCheckIns(
        id: String,
        startDate: String?,
        endDate: String?,
        page: Int,
    ): GoalCheckInPage = GoalCheckInPage(emptyList(), null)

    override suspend fun inviteParticipant(goalId: String, userId: String): GoalParticipant {
        inviteCalled = true
        invitedUserId = userId
        return GoalParticipant("p3", userId, "New Member", app.promise.android.domain.GoalParticipantRole.PARTICIPANT, GoalParticipantStatus.INVITED, null, null, null)
    }

    override suspend fun listParticipants(goalId: String): List<GoalParticipant> = initialRoster

    override suspend fun acceptInvitation(goalId: String): GoalDetail {
        acceptCalled = true
        return GoalDetail.Full(current)
    }

    override suspend fun declineInvitation(goalId: String): GoalParticipant {
        declineCalled = true
        return GoalParticipant("p1", "u1", "Me", app.promise.android.domain.GoalParticipantRole.PARTICIPANT, GoalParticipantStatus.DECLINED, null, null, null)
    }

    override suspend fun reinviteParticipant(
        goalId: String,
        participantId: String?,
        userId: String?,
    ): GoalParticipant =
        GoalParticipant(participantId ?: "p_re", userId ?: "u_re", "Reinvited", app.promise.android.domain.GoalParticipantRole.PARTICIPANT, GoalParticipantStatus.INVITED, null, null, null)

    override suspend fun transferOwnership(
        goalId: String,
        participantId: String?,
        userId: String?,
    ): GoalDetail {
        current = current.copy(membershipRole = app.promise.android.domain.GoalParticipantRole.PARTICIPANT)
        return GoalDetail.Full(current)
    }

    override suspend fun removeParticipant(goalId: String, userId: String): GoalParticipant {
        removeCalled = true
        removedUserId = userId
        initialRoster = initialRoster.filterNot { it.userId == userId || it.id == userId }
        return GoalParticipant("p_rem", userId, "Removed", app.promise.android.domain.GoalParticipantRole.PARTICIPANT, GoalParticipantStatus.REMOVED, null, null, null)
    }

    override suspend fun leave(goalId: String): GoalParticipant {
        if (failLeave) {
            throw ApiException(status = 409, code = "GOAL_OWNER_CANNOT_LEAVE")
        }
        leaveCalled = true
        return GoalParticipant("p_left", "u1", "Left", app.promise.android.domain.GoalParticipantRole.PARTICIPANT, GoalParticipantStatus.LEFT, null, null, null)
    }

    override suspend fun listChatMessages(
        goalId: String,
        limit: Int,
        beforeCreatedAt: String?,
        beforeId: String?,
    ): List<app.promise.android.domain.ChatMessage> = emptyList()

    override suspend fun sendChatMessage(
        goalId: String,
        body: String,
    ): app.promise.android.domain.ChatMessage = throw UnsupportedOperationException()

    override suspend fun markChatRead(
        goalId: String,
        lastReadMessageId: String,
    ): app.promise.android.domain.GoalChatReadState =
        app.promise.android.domain.GoalChatReadState(lastReadMessageId, "2026-08-20T12:00:00Z")

    override suspend fun getChatSummary(
        goalId: String,
    ): app.promise.android.domain.GoalChatSummary =
        app.promise.android.domain.GoalChatSummary(0, null)

    override suspend fun listActivity(
        goalId: String,
        limit: Int,
        beforeCreatedAt: String?,
        beforeId: String?,
    ): List<GoalActivityItem> = initialActivity

    override suspend fun searchChatMessages(
        goalId: String,
        query: String,
        limit: Int,
    ): List<app.promise.android.domain.ChatSearchResult> = emptyList()
}

private fun sample(
    id: String,
    status: GoalStatus = GoalStatus.ACTIVE,
): Goal = Goal(
    id = id,
    title = "Read",
    description = "",
    status = status,
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

private fun sampleInvitePreview(id: String): GoalInvitePreview = GoalInvitePreview(
    id = id,
    title = "Read together",
    description = "",
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
    inviterUserId = "u2",
    inviterName = "Bob",
    invitationStatus = GoalParticipantStatus.INVITED,
    invitationExpiresAt = null,
)

private class FakeUserRepository(
    private val usersByEmail: Map<String, LookupUser>? = null,
    private val defaultUser: LookupUser = LookupUser("u1", "Test User", "test@example.com"),
    private val error: ApiException? = null,
) : UserRepository {
    override suspend fun lookupByEmail(email: String): LookupUser {
        error?.let { throw it }
        if (usersByEmail != null) {
            return usersByEmail[email] ?: throw ApiException(status = 404, code = "USER_NOT_FOUND")
        }
        return defaultUser
    }

    override suspend fun searchUsers(query: String): List<LookupUser> {
        error?.let { throw it }
        return listOf(defaultUser)
    }
}
