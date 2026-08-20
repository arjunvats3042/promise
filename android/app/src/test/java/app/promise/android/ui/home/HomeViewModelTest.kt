package app.promise.android.ui.home

import app.promise.android.core.LoadState
import app.promise.android.data.home.PreviewHomeRepository
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.User
import app.promise.android.ui.haptics.FakePromiseHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    fun readyStateOrdersCommitmentsBeforePracticesAndIncludesDate() = runTest {
        val session = AuthSession()
        session.setUser(
            User(
                id = "1",
                email = "a@b.com",
                name = "Arjun",
                timezone = "UTC",
                createdAt = "2026-01-01T00:00:00Z",
            ),
        )
        val haptics = FakePromiseHaptics()
        val vm = HomeViewModel(PreviewHomeRepository(), session, haptics)
        advanceUntilIdle()
        val ready = vm.state.first { it is LoadState.Ready } as LoadState.Ready
        assertEquals("Arjun", ready.value.userName)
        assertEquals(listOf("c-overdue", "c-today", "c-upcoming"), ready.value.commitments.map { it.id })
        assertEquals(3, ready.value.practices.size)
        assertTrue(ready.value.dateLabel.isNotBlank())
        assertTrue(ready.value.commitments.isNotEmpty() || ready.value.practices.isNotEmpty())
    }

    @Test
    fun completeTriggersConfirmHaptic() = runTest {
        val haptics = FakePromiseHaptics()
        val vm = HomeViewModel(PreviewHomeRepository(), AuthSession(), haptics)
        advanceUntilIdle()
        vm.completeCommitment("c-today")
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
        val ready = vm.state.value as LoadState.Ready
        assertTrue(ready.value.commitments.none { it.id == "c-today" })
    }

    @Test
    fun checkInTriggersConfirmHaptic() = runTest {
        val haptics = FakePromiseHaptics()
        val vm = HomeViewModel(PreviewHomeRepository(), AuthSession(), haptics)
        advanceUntilIdle()
        vm.checkInPractice("p-read")
        advanceUntilIdle()
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun emptyFeedStillReadyWithEmptySections() = runTest {
        val repo = PreviewHomeRepository()
        repo.loadEmptyScenario()
        val vm = HomeViewModel(repo, AuthSession(), FakePromiseHaptics())
        advanceUntilIdle()
        val ready = vm.state.first { it is LoadState.Ready } as LoadState.Ready
        assertTrue(ready.value.commitments.isEmpty())
        assertTrue(ready.value.practices.isEmpty())
        assertEquals("there", ready.value.userName)
    }
}
