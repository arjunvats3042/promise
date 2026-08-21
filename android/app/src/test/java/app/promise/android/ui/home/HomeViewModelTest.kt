package app.promise.android.ui.home

import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.data.home.HomeFreshness
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomeFeed
import app.promise.android.domain.HomePractice
import app.promise.android.domain.HomeRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    fun greetingAndInitialsHelpers() {
        assertEquals("Good morning", HomeViewModel.greetingForNow(8))
        assertEquals("Good afternoon", HomeViewModel.greetingForNow(14))
        assertEquals("Good evening", HomeViewModel.greetingForNow(20))
        assertEquals("AV", HomeViewModel.initialsFor("Arjun Vats"))
        assertEquals("A", HomeViewModel.initialsFor("Arjun"))
        assertEquals(
            "Friday, 21 August",
            HomeViewModel.dateLabelFor(
                java.time.LocalDate.of(2026, 8, 21),
                java.util.Locale.UK,
            ),
        )
    }

    @Test
    fun loadingToReady() = runTest {
        val session = AuthSession().also {
            it.setUser(User("1", "a@b.com", "Arjun", "UTC", "2026-01-01T00:00:00Z"))
        }
        val vm = HomeViewModel(
            FakeHomeRepository(
                HomeFeed(
                    commitments = listOf(
                        HomeCommitment("c1", "Call", "Due today", false, true),
                    ),
                    practices = listOf(
                        HomePractice("g1", "Walk", 0.5f, "3 / 7 this week", 2),
                    ),
                ),
            ),
            session,
            HomeFreshness(),
            FakePromiseHaptics(),
        )
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals("Arjun", ready.value.userName)
        assertEquals(listOf("c1"), ready.value.commitments.map { it.id })
        assertEquals(listOf("g1"), ready.value.practices.map { it.id })
    }

    @Test
    fun emptyFeedStillReady() = runTest {
        val vm = HomeViewModel(
            FakeHomeRepository(HomeFeed(emptyList(), emptyList())),
            AuthSession(),
            HomeFreshness(),
            FakePromiseHaptics(),
        )
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertTrue(ready.value.commitments.isEmpty())
        assertTrue(ready.value.practices.isEmpty())
    }

    @Test
    fun partialDegradedState() = runTest {
        val vm = HomeViewModel(
            FakeHomeRepository(
                HomeFeed(
                    commitments = emptyList(),
                    practices = listOf(HomePractice("g1", "Walk", 0.2f, "1 / 7 this week", 0)),
                    commitmentsError = ErrorKind.Network,
                ),
            ),
            AuthSession(),
            HomeFreshness(),
            FakePromiseHaptics(),
        )
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertEquals(ErrorKind.Network, ready.value.commitmentsError)
        assertEquals(1, ready.value.practices.size)
    }

    @Test
    fun bothFail_fullError() = runTest {
        val vm = HomeViewModel(
            FakeHomeRepository(error = ApiException(status = null, code = "NETWORK")),
            AuthSession(),
            HomeFreshness(),
            FakePromiseHaptics(),
        )
        advanceUntilIdle()
        val error = vm.state.value as LoadState.Error
        assertEquals(ErrorKind.Network, error.kind)
        assertTrue(error.canRetry)
    }

    @Test
    fun completeTriggersConfirmHapticAndReloads() = runTest {
        val haptics = FakePromiseHaptics()
        val repo = FakeHomeRepository(
            HomeFeed(
                commitments = listOf(HomeCommitment("c1", "Call", "Due today", false, true)),
                practices = emptyList(),
            ),
        )
        val vm = HomeViewModel(repo, AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.completeCommitment("c1")
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
        assertTrue(repo.completeCalled)
        assertTrue(repo.loadCount >= 2)
    }

    @Test
    fun checkInTriggersConfirmHaptic() = runTest {
        val haptics = FakePromiseHaptics()
        val repo = FakeHomeRepository(
            HomeFeed(
                commitments = emptyList(),
                practices = listOf(HomePractice("g1", "Walk", 0.2f, "1 / 7 this week", 0)),
            ),
        )
        val vm = HomeViewModel(repo, AuthSession(), HomeFreshness(), haptics)
        advanceUntilIdle()
        vm.checkInPractice("g1")
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
        assertTrue(repo.checkInCalled)
    }

    @Test
    fun onVisible_skipsWhenFresh() = runTest {
        val freshness = HomeFreshness()
        val repo = FakeHomeRepository(HomeFeed(emptyList(), emptyList()))
        val vm = HomeViewModel(repo, AuthSession(), freshness, FakePromiseHaptics())
        advanceUntilIdle()
        val loadsAfterInit = repo.loadCount
        freshness.markSuccessfulLoad(System.currentTimeMillis())
        vm.onVisible()
        advanceUntilIdle()
        assertEquals(loadsAfterInit, repo.loadCount)
    }

    @Test
    fun onVisible_refreshesWhenDirty() = runTest {
        val freshness = HomeFreshness()
        val repo = FakeHomeRepository(HomeFeed(emptyList(), emptyList()))
        val vm = HomeViewModel(repo, AuthSession(), freshness, FakePromiseHaptics())
        advanceUntilIdle()
        val loadsAfterInit = repo.loadCount
        freshness.markDirty()
        vm.onVisible()
        advanceUntilIdle()
        assertTrue(repo.loadCount > loadsAfterInit)
    }

    @Test
    fun pullRefresh_setsRefreshingThenReady() = runTest {
        val repo = FakeHomeRepository(HomeFeed(emptyList(), emptyList()))
        val vm = HomeViewModel(repo, AuthSession(), HomeFreshness(), FakePromiseHaptics())
        advanceUntilIdle()
        assertFalse((vm.state.value as LoadState.Ready).isRefreshing)
        vm.refresh(fromPull = true, force = true)
        // Mid-refresh may race; after idle must be Ready not refreshing
        advanceUntilIdle()
        val ready = vm.state.value as LoadState.Ready
        assertFalse(ready.isRefreshing)
    }
}

private class FakeHomeRepository(
    private var feed: HomeFeed = HomeFeed(emptyList(), emptyList()),
    private val error: ApiException? = null,
) : HomeRepository {
    var loadCount = 0
    var completeCalled = false
    var checkInCalled = false

    override suspend fun loadFeed(timeZoneId: String): HomeFeed {
        loadCount++
        error?.let { throw it }
        return feed
    }

    override suspend fun completeCommitment(id: String) {
        completeCalled = true
        feed = feed.copy(commitments = feed.commitments.filterNot { it.id == id })
    }

    override suspend fun checkInPractice(id: String, input: CheckInInput) {
        checkInCalled = true
        feed = feed.copy(
            practices = feed.practices.map {
                if (it.id == id) it.copy(checkedInToday = true) else it
            },
        )
    }
}
