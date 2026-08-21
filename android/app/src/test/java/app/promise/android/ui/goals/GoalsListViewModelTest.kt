package app.promise.android.ui.goals

import app.promise.android.data.home.HomeFreshness

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
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
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
        val vm = GoalsListViewModel(repo, session, HomeFreshness(), FakePromiseHaptics())
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(GoalListFilter.ACTIVE, ready.value.filter)
        assertEquals(listOf("1"), ready.value.items.map { it.itemId() })
    }

    @Test
    fun createSuccess_confirmsHapticAndPrepends() = runTest {
        val repo = FakeGoalRepository(
            pages = mapOf(GoalListFilter.ACTIVE to listOf(sample("1"))),
        )
        val haptics = FakePromiseHaptics()
        val vm = GoalsListViewModel(repo, AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.create(
            CreateGoalInput(title = "New", recurrenceKind = GoalRecurrenceKind.DAILY),
        ) {}
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
        assertTrue(vm.createAction.value is ActionState.Idle)
        val ready = vm.state.value as LoadState.Ready
        assertEquals("New", (ready.value.items.first() as GoalListItem.Membership).goal.title)
    }

    @Test
    fun createFailure_setsActionState() = runTest {
        val repo = FakeGoalRepository(
            pages = mapOf(GoalListFilter.ACTIVE to listOf(sample("1"))),
            createError = ApiException(status = 429, code = "RATE_LIMITED", retryAfterSeconds = 9),
        )
        val haptics = FakePromiseHaptics()
        val vm = GoalsListViewModel(repo, AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.create(
            CreateGoalInput(title = "New", recurrenceKind = GoalRecurrenceKind.DAILY),
        ) {}
        advanceUntilIdle()
        val failed = vm.createAction.value as ActionState.Failed
        assertTrue(failed.kind is ErrorKind.RateLimited)
        assertTrue(haptics.events.contains("error"))
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
        val vm = GoalsListViewModel(repo, AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.selectFilter(GoalListFilter.PAUSED)
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(GoalListFilter.PAUSED, ready.value.filter)
        assertEquals(listOf("2"), ready.value.items.map { it.itemId() })
        assertEquals(emptyList<String>(), haptics.events)
        assertEquals(false, ready.isRefreshing)
    }

    @Test
    fun loadMore_appendsNextPage() = runTest {
        val repo = FakeGoalRepository(
            pages = mapOf(GoalListFilter.ACTIVE to listOf(sample("1"))),
            nextPage = 2,
            pageTwo = listOf(sample("2")),
        )
        val vm = GoalsListViewModel(repo, AuthSession(), HomeFreshness(), FakePromiseHaptics())
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(listOf("1", "2"), ready.value.items.map { it.itemId() })
    }
}

private fun GoalListItem.itemId(): String = when (this) {
    is GoalListItem.Membership -> goal.id
    is GoalListItem.Invite -> preview.id
}

class FakeGoalRepository(
    private val pages: Map<GoalListFilter, List<Goal>> = emptyMap(),
    private var createResult: Goal = sample("created", title = "New"),
    private val createError: ApiException? = null,
    private val nextPage: Int? = null,
    private val pageTwo: List<Goal> = emptyList(),
) : GoalRepository {
    override suspend fun list(filter: GoalListFilter, page: Int, pageSize: Int): GoalPage {
        if (page > 1) {
            return GoalPage(items = pageTwo.map { GoalListItem.Membership(it) }, nextPage = null)
        }
        return GoalPage(
            items = pages[filter].orEmpty().map { GoalListItem.Membership(it) },
            nextPage = nextPage,
        )
    }

    override suspend fun get(id: String): Goal = sample(id)

    override suspend fun getDetail(id: String): GoalDetail = GoalDetail.Full(sample(id))

    override suspend fun create(input: CreateGoalInput): Goal {
        createError?.let { throw it }
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

    override suspend fun inviteParticipant(goalId: String, userId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun listParticipants(goalId: String): List<GoalParticipant> = emptyList()

    override suspend fun acceptInvitation(goalId: String): GoalDetail =
        GoalDetail.Full(sample(goalId))

    override suspend fun declineInvitation(goalId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun removeParticipant(goalId: String, userId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun leave(goalId: String): GoalParticipant =
        throw UnsupportedOperationException()
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
