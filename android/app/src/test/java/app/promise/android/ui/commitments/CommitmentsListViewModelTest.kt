package app.promise.android.ui.commitments

import app.promise.android.core.ActionState
import app.promise.android.core.LoadState
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentPage
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.DuePrecision
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
class CommitmentsListViewModelTest {
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
    fun loadsOpenFilterOnInit() = runTest {
        val repo = FakeCommitmentRepository(
            pages = mapOf(
                CommitmentListFilter.OPEN to listOf(sample("1")),
            ),
        )
        val session = AuthSession().also {
            it.setUser(User("1", "a@b.com", "Ada", "UTC", "2026-01-01T00:00:00Z"))
        }
        val vm = CommitmentsListViewModel(repo, session, FakePromiseHaptics())
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(CommitmentListFilter.OPEN, ready.value.filter)
        assertEquals(listOf("1"), ready.value.items.map { it.id })
    }

    @Test
    fun createSuccess_confirmsHapticAndPrepends() = runTest {
        val repo = FakeCommitmentRepository(
            pages = mapOf(CommitmentListFilter.OPEN to listOf(sample("1"))),
        )
        val haptics = FakePromiseHaptics()
        val vm = CommitmentsListViewModel(repo, AuthSession(), haptics)
        advanceUntilIdle()
        vm.create("New", "", null, DuePrecision.NONE) {}
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
        assertTrue(vm.createAction.value is ActionState.Idle)
        val ready = vm.state.value as LoadState.Ready
        assertEquals("New", ready.value.items.first().title)
    }

    @Test
    fun selectFilter_reloads() = runTest {
        val repo = FakeCommitmentRepository(
            pages = mapOf(
                CommitmentListFilter.OPEN to listOf(sample("1")),
                CommitmentListFilter.OVERDUE to listOf(sample("2", overdue = true)),
            ),
        )
        val haptics = FakePromiseHaptics()
        val vm = CommitmentsListViewModel(repo, AuthSession(), haptics)
        advanceUntilIdle()
        vm.selectFilter(CommitmentListFilter.OVERDUE)
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(CommitmentListFilter.OVERDUE, ready.value.filter)
        assertEquals(listOf("2"), ready.value.items.map { it.id })
        assertEquals(emptyList<String>(), haptics.events)
        assertEquals(false, ready.isRefreshing)
    }
}

class FakeCommitmentRepository(
    private val pages: Map<CommitmentListFilter, List<Commitment>> = emptyMap(),
    private var createResult: Commitment = sample("created", title = "New"),
) : CommitmentRepository {
    override suspend fun list(
        filter: CommitmentListFilter,
        page: Int,
        timeZoneId: String,
    ): CommitmentPage {
        return CommitmentPage(items = pages[filter].orEmpty(), nextPage = null)
    }

    override suspend fun get(id: String): Commitment = sample(id)

    override suspend fun create(input: CreateCommitmentInput): Commitment {
        createResult = sample("created", title = input.title)
        return createResult
    }

    override suspend fun complete(id: String): Commitment =
        sample(id, status = CommitmentStatus.COMPLETED)

    override suspend fun snooze(id: String, snoozedUntilIso: String): Commitment =
        sample(id, status = CommitmentStatus.SNOOZED)

    override suspend fun unsnooze(id: String): Commitment = sample(id)

    override suspend fun wait(id: String): Commitment =
        sample(id, status = CommitmentStatus.WAITING)

    override suspend fun cancel(id: String): Commitment =
        sample(id, status = CommitmentStatus.CANCELLED)
}

fun sample(
    id: String,
    title: String = "Title $id",
    status: CommitmentStatus = CommitmentStatus.PENDING,
    overdue: Boolean = false,
): Commitment {
    return Commitment(
        id = id,
        title = title,
        description = "",
        status = status,
        dueAt = "2026-08-20T12:00:00Z",
        duePrecision = DuePrecision.DATETIME,
        source = "MANUAL",
        snoozedUntil = null,
        completedAt = null,
        cancelledAt = null,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        isOverdue = overdue,
    )
}
