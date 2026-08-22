package app.promise.android.core.events

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppEventBusTest {
    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun emit_deliversEventsToActiveSubscribers() = runTest(testDispatcher) {
        val bus = AppEventBus()
        val received = mutableListOf<AppMutationEvent>()

        val job = launch {
            bus.events.collect { received.add(it) }
        }
        advanceUntilIdle()

        bus.emit(AppMutationEvent.GoalCreated("g1"))
        bus.emit(AppMutationEvent.GoalCheckedIn("g1"))
        bus.emit(AppMutationEvent.CommitmentCompleted("c1"))
        bus.emit(AppMutationEvent.SharedGoalMembershipChanged("g1", MembershipChangeType.ACCEPTED))
        bus.emit(AppMutationEvent.NotificationPreferencesChanged)
        advanceUntilIdle()

        assertEquals(5, received.size)
        assertTrue(received[0] is AppMutationEvent.GoalCreated)
        assertEquals("g1", (received[0] as AppMutationEvent.GoalCreated).goalId)
        assertTrue(received[1] is AppMutationEvent.GoalCheckedIn)
        assertTrue(received[2] is AppMutationEvent.CommitmentCompleted)
        assertEquals("c1", (received[2] as AppMutationEvent.CommitmentCompleted).commitmentId)
        assertTrue(received[3] is AppMutationEvent.SharedGoalMembershipChanged)
        assertEquals(MembershipChangeType.ACCEPTED, (received[3] as AppMutationEvent.SharedGoalMembershipChanged).changeType)
        assertTrue(received[4] is AppMutationEvent.NotificationPreferencesChanged)

        job.cancel()
    }
}
