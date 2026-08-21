package app.promise.android.ui.goals

import app.promise.android.data.home.HomeFreshness

import androidx.lifecycle.SavedStateHandle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalPage
import app.promise.android.domain.GoalInvitePreview
import app.promise.android.domain.GoalParticipant
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun complete_updatesStateAndConfirms() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics)
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
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.pause()
        advanceUntilIdle()
        assertEquals(listOf("light"), haptics.events)
        assertEquals(GoalStatus.PAUSED, ((vm.state.value as LoadState.Ready).value as GoalDetailUi.Full).goal.status)
    }

    @Test
    fun resume_updatesState() = runTest {
        val repo = DetailFakeRepo(sample("g1", status = GoalStatus.PAUSED))
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), FakePromiseHaptics())
        advanceUntilIdle()
        vm.resume()
        advanceUntilIdle()
        assertEquals(GoalStatus.ACTIVE, ((vm.state.value as LoadState.Ready).value as GoalDetailUi.Full).goal.status)
    }

    @Test
    fun cancel_updatesStateAndConfirms() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        assertEquals(GoalStatus.CANCELLED, ((vm.state.value as LoadState.Ready).value as GoalDetailUi.Full).goal.status)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun checkIn_confirmsAndReloads() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.checkIn(CheckInInput(status = GoalCheckInStatus.COMPLETED))
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
        assertTrue(repo.checkInCalled)
        assertTrue(vm.action.value is ActionState.Idle)
    }

    @Test
    fun checkInFailure_setsActionState() = runTest {
        val repo = DetailFakeRepo(sample("g1"), failCheckIn = true)
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.checkIn(CheckInInput(status = GoalCheckInStatus.COMPLETED))
        advanceUntilIdle()
        val failed = vm.action.value as ActionState.Failed
        assertEquals(ErrorKind.Conflict, failed.kind)
        assertTrue(haptics.events.contains("error"))
    }

    @Test
    fun conflict_emitsErrorHaptic() = runTest {
        val repo = DetailFakeRepo(sample("g1"), failPause = true)
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.pause()
        advanceUntilIdle()
        assertTrue(haptics.events.contains("error"))
        assertEquals(ErrorKind.Conflict, (vm.action.value as ActionState.Failed).kind)
    }

    @Test
    fun loadsAsInvite_whenDetailIsInvite() = runTest {
        val repo = DetailFakeRepo(sample("g1"), returnInvite = true)
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), FakePromiseHaptics())
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertTrue(ready.value is GoalDetailUi.Invite)
    }

    @Test
    fun acceptInvite_confirmsAndReloads() = runTest {
        val repo = DetailFakeRepo(sample("g1"), returnInvite = true)
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, FakeUserRepository(), AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.acceptInvite()
        advanceUntilIdle()
        assertTrue(haptics.events.contains("confirm"))
        assertTrue(vm.state.value is LoadState.Ready)
    }

    private fun handle(id: String): SavedStateHandle {
        return SavedStateHandle(mapOf("goalId" to id))
    }
}

private class DetailFakeRepo(
    private var current: Goal,
    private val failPause: Boolean = false,
    private val failCheckIn: Boolean = false,
    private val returnInvite: Boolean = false,
) : GoalRepository {
    var checkInCalled = false

    override suspend fun list(filter: GoalListFilter, page: Int, pageSize: Int): GoalPage =
        GoalPage(emptyList(), null)

    override suspend fun get(id: String): Goal = current

    override suspend fun getDetail(id: String): GoalDetail {
        if (returnInvite) {
            return GoalDetail.Invite(sampleInvitePreview(id))
        }
        return GoalDetail.Full(current)
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

    override suspend fun inviteParticipant(goalId: String, userId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun listParticipants(goalId: String): List<GoalParticipant> = emptyList()

    override suspend fun acceptInvitation(goalId: String): GoalDetail =
        GoalDetail.Full(current)

    override suspend fun declineInvitation(goalId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun removeParticipant(goalId: String, userId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun leave(goalId: String): GoalParticipant =
        throw UnsupportedOperationException()
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
    private val result: LookupUser = LookupUser("u1", "Test User", "test@example.com"),
    private val error: ApiException? = null,
) : UserRepository {
    override suspend fun lookupByEmail(email: String): LookupUser {
        error?.let { throw it }
        return result
    }
}
