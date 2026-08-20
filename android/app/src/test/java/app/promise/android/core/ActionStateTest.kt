package app.promise.android.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ActionStateTest {
    @Test
    fun mutationStatesAreIdleInFlightOrFailed() {
        val idle: ActionState = ActionState.Idle
        val inFlight: ActionState = ActionState.InFlight
        val failed: ActionState = ActionState.Failed(ErrorKind.RateLimited())

        assertTrue(idle is ActionState.Idle)
        assertTrue(inFlight is ActionState.InFlight)
        assertTrue(failed is ActionState.Failed)
        assertTrue((failed as ActionState.Failed).kind is ErrorKind.RateLimited)
    }
}
