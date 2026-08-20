package app.promise.android.ui.goals

import app.promise.android.core.ActionState
import app.promise.android.core.LoadState
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalPage
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
class GoalsListViewModelTest {
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
    fun loadsActiveFilterOnInit() = runTest {
        val repo = FakeGoalRepository(
            pages = mapOf(GoalListFilter.ACTIVE to listOf(sample("1"))),
        )
        val session = AuthSession().also {
            it.setUser(User("1", "a@b.com", "Ada", "UTC", "2026-01-01T00:00:00Z"))
        }
        val vm = GoalsListViewModel(repo, session, FakePromiseHaptics())
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(GoalListFilter.ACTIVE, ready.value.filter)
        assertEquals(listOf("1"), ready.value.items.map { it.id })
    }

    @Test
    fun createSuccess_confirmsHapticAndPrepends() = runTest {
        val repo = FakeGoalRepository(
            pages = mapOf(GoalListFilter.ACTIVE to listOf(sample("1"))),
        )
        val haptics = FakePromiseHaptics()
        val vm = GoalsListViewModel(repo, AuthSession(), haptics)
        advanceUntilIdle()
        vm.create(
            CreateGoalInput(title = "New", recurrenceKind = GoalRecurrenceKind.DAILY),
        ) {}
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
        assertTrue(vm.createAction.value is ActionState.Idle)
        val ready = vm.state.value as LoadState.Ready
        assertEquals("New", ready.value.items.first().title)
    }

    @Test
    fun selectFilter_reloads() = runTest {
        val repo = FakeGoalRepository(
            pages = mapOf(
                GoalListFilter.ACTIVE to listOf(sample("1")),
                GoalListFilter.PAUSED to listOf(sample("2", status = GoalStatus.PAUSED)),
            ),
        )
        val haptics = FakePromiseHaptics()
        val vm = GoalsListViewModel(repo, AuthSession(), haptics)
        advanceUntilIdle()
        vm.selectFilter(GoalListFilter.PAUSED)
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(GoalListFilter.PAUSED, ready.value.filter)
        assertEquals(listOf("2"), ready.value.items.map { it.id })
        assertEquals(emptyList<String>(), haptics.events)
        assertEquals(false, ready.isRefreshing)
    }
}

class FakeGoalRepository(
    private val pages: Map<GoalListFilter, List<Goal>> = emptyMap(),
    private var createResult: Goal = sample("created", title = "New"),
) : GoalRepository {
    override suspend fun list(filter: GoalListFilter, page: Int): GoalPage {
        return GoalPage(items = pages[filter].orEmpty(), nextPage = null)
    }

    override suspend fun get(id: String): Goal = sample(id)

    override suspend fun create(input: CreateGoalInput): Goal {
        createResult = sample("created", title = input.title)
        return createResult
    }

    override suspend fun pause(id: String): Goal = sample(id, status = GoalStatus.PAUSED)

    override suspend fun resume(id: String): Goal = sample(id)

    override suspend fun complete(id: String): Goal = sample(id, status = GoalStatus.COMPLETED)

    override suspend fun cancel(id: String): Goal = sample(id, status = GoalStatus.CANCELLED)

    override suspend fun checkIn(id: String, input: CheckInInput): GoalCheckIn {
        return GoalCheckIn(
            id = "c1",
            periodDate = "2026-08-20",
            status = input.status,
            value = input.value,
            note = input.note,
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

fun sample(
    id: String,
    title: String = "Read",
    status: GoalStatus = GoalStatus.ACTIVE,
    required: Int = 1,
    completed: Int = 0,
    tracking: GoalTrackingKind = GoalTrackingKind.BINARY,
): Goal {
    return Goal(
        id = id,
        title = title,
        description = "",
        status = status,
        timezone = "UTC",
        startDate = "2026-08-01",
        endDate = null,
        recurrenceKind = GoalRecurrenceKind.DAILY,
        weekdays = emptyList(),
        periodUnit = null,
        timesPerPeriod = null,
        trackingKind = tracking,
        targetValue = if (tracking == GoalTrackingKind.COUNT) 10 else null,
        targetUnit = "",
        source = "MANUAL",
        pausedAt = null,
        completedAt = null,
        cancelledAt = null,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        isEnded = false,
        progress = GoalProgress(
            currentPeriod = GoalPeriodCounts(required, completed),
            weekProgress = GoalPeriodCounts(7, 6),
            consistencyPercent = 80,
        ),
        currentStreak = 5,
    )
}
