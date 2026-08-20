package app.promise.android.ui.goals

import androidx.lifecycle.SavedStateHandle
import app.promise.android.core.LoadState
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalPage
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalStatus
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
        val vm = GoalDetailViewModel(handle("g1"), repo, AuthSession(), haptics)
        advanceUntilIdle()
        vm.complete()
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(GoalStatus.COMPLETED, ready.value.goal.status)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun pause_usesLightHaptic() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, AuthSession(), haptics)
        advanceUntilIdle()
        vm.pause()
        advanceUntilIdle()
        assertEquals(listOf("light"), haptics.events)
        assertEquals(GoalStatus.PAUSED, (vm.state.value as LoadState.Ready).value.goal.status)
    }

    @Test
    fun checkIn_confirmsAndReloads() = runTest {
        val repo = DetailFakeRepo(sample("g1"))
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, AuthSession(), haptics)
        advanceUntilIdle()
        vm.checkIn(CheckInInput(status = GoalCheckInStatus.COMPLETED))
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
        assertTrue(repo.checkInCalled)
    }

    @Test
    fun conflict_emitsErrorHaptic() = runTest {
        val repo = DetailFakeRepo(sample("g1"), failPause = true)
        val haptics = FakePromiseHaptics()
        val vm = GoalDetailViewModel(handle("g1"), repo, AuthSession(), haptics)
        advanceUntilIdle()
        vm.pause()
        advanceUntilIdle()
        assertTrue(haptics.events.contains("error"))
    }

    private fun handle(id: String): SavedStateHandle {
        return SavedStateHandle(mapOf("goalId" to id))
    }
}

private class DetailFakeRepo(
    private var current: Goal,
    private val failPause: Boolean = false,
) : GoalRepository {
    var checkInCalled = false

    override suspend fun list(filter: GoalListFilter, page: Int): GoalPage =
        GoalPage(emptyList(), null)

    override suspend fun get(id: String): Goal = current

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
}
