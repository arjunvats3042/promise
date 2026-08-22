package app.promise.android.ui.commitments

import androidx.lifecycle.SavedStateHandle
import app.promise.android.core.LoadState
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.data.home.HomeFreshness
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentPage
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.CreateCommitmentInput
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
class CommitmentDetailViewModelTest {
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
        val repo = DetailFakeRepo(sample("c1"))
        val haptics = FakePromiseHaptics()
        val vm = CommitmentDetailViewModel(handle("c1"), repo, AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.complete()
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(CommitmentStatus.COMPLETED, ready.value.status)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun snooze_usesLightHaptic() = runTest {
        val repo = DetailFakeRepo(sample("c1"))
        val haptics = FakePromiseHaptics()
        val vm = CommitmentDetailViewModel(handle("c1"), repo, AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.snooze("2026-08-21T09:00:00Z")
        advanceUntilIdle()
        assertEquals(listOf("light"), haptics.events)
        assertEquals(CommitmentStatus.SNOOZED, (vm.state.value as LoadState.Ready).value.status)
    }

    @Test
    fun conflict_emitsErrorHaptic() = runTest {
        val repo = DetailFakeRepo(sample("c1"), failWait = true)
        val haptics = FakePromiseHaptics()
        val vm = CommitmentDetailViewModel(handle("c1"), repo, AuthSession(), HomeFreshness(), haptics, eventBus)
        advanceUntilIdle()
        vm.waitOn()
        advanceUntilIdle()
        assertTrue(haptics.events.contains("error"))
    }

    @Test
    fun externalMutationEvent_reloadsQuietly() = runTest {
        val repo = DetailFakeRepo(sample("c1"))
        val vm = CommitmentDetailViewModel(handle("c1"), repo, AuthSession(), HomeFreshness(), FakePromiseHaptics(), eventBus)
        advanceUntilIdle()

        // Simulate external mutation
        eventBus.emit(AppMutationEvent.CommitmentUpdated("c1"))
        advanceUntilIdle()

        assertTrue(vm.state.value is LoadState.Ready)
    }

    private fun handle(id: String): SavedStateHandle {
        return SavedStateHandle(mapOf("commitmentId" to id))
    }
}

private class DetailFakeRepo(
    private var current: Commitment,
    private val failWait: Boolean = false,
) : CommitmentRepository {
    override suspend fun list(
        filter: CommitmentListFilter,
        page: Int,
        timeZoneId: String,
        pageSize: Int,
    ): CommitmentPage = CommitmentPage(emptyList(), null)

    override suspend fun get(id: String): Commitment = current

    override suspend fun create(input: CreateCommitmentInput): Commitment = current

    override suspend fun complete(id: String): Commitment {
        current = current.copy(status = CommitmentStatus.COMPLETED, isOverdue = false)
        return current
    }

    override suspend fun snooze(id: String, snoozedUntilIso: String): Commitment {
        current = current.copy(status = CommitmentStatus.SNOOZED, snoozedUntil = snoozedUntilIso)
        return current
    }

    override suspend fun unsnooze(id: String): Commitment {
        current = current.copy(status = CommitmentStatus.PENDING, snoozedUntil = null)
        return current
    }

    override suspend fun wait(id: String): Commitment {
        if (failWait) {
            throw ApiException(status = 409, code = "COMMITMENT_INVALID_TRANSITION")
        }
        current = current.copy(status = CommitmentStatus.WAITING)
        return current
    }

    override suspend fun cancel(id: String): Commitment {
        current = current.copy(status = CommitmentStatus.CANCELLED)
        return current
    }
}
