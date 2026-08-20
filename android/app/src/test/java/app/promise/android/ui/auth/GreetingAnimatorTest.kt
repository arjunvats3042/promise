package app.promise.android.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingAnimatorTest {
    @Test
    fun hourBuckets() {
        assertEquals("Good morning", GreetingClock.greetingForHour(5))
        assertEquals("Good morning", GreetingClock.greetingForHour(11))
        assertEquals("Good afternoon", GreetingClock.greetingForHour(12))
        assertEquals("Good afternoon", GreetingClock.greetingForHour(16))
        assertEquals("Good evening", GreetingClock.greetingForHour(17))
        assertEquals("Good evening", GreetingClock.greetingForHour(4))
        assertEquals("Good evening", GreetingClock.greetingForHour(0))
    }

    @Test
    fun typesCharacterByCharacter() {
        var state = GreetingAnimationState(fullText = "")
        state = GreetingAnimator.reduce(state, GreetingEvent.Start("Hi"))
        assertEquals(GreetingPhase.Typing, state.phase)
        state = GreetingAnimator.reduce(state, GreetingEvent.Tick)
        assertEquals("H", state.visibleText)
        state = GreetingAnimator.reduce(state, GreetingEvent.Tick)
        assertEquals("Hi", state.visibleText)
        assertEquals(GreetingPhase.Settled, state.phase)
    }

    @Test
    fun reduceMotionSkipsTyping() {
        var state = GreetingAnimationState(fullText = "")
        state = GreetingAnimator.reduce(
            state,
            GreetingEvent.Start("Good evening"),
            reduceMotion = true,
        )
        assertEquals("Good evening", state.visibleText)
        assertEquals(GreetingPhase.Settled, state.phase)
    }

    @Test
    fun changeDeletesThenTypes() {
        var state = GreetingAnimationState(
            fullText = "Good morning",
            visibleText = "Good morning",
            phase = GreetingPhase.Settled,
        )
        state = GreetingAnimator.reduce(state, GreetingEvent.Change("Good evening"))
        assertEquals(GreetingPhase.Deleting, state.phase)
        repeat(state.visibleText.length) {
            state = GreetingAnimator.reduce(state, GreetingEvent.Tick)
        }
        assertEquals("", state.visibleText)
        assertEquals(GreetingPhase.Typing, state.phase)
        assertEquals("Good evening", state.fullText)
        repeat("Good evening".length) {
            state = GreetingAnimator.reduce(state, GreetingEvent.Tick)
        }
        assertEquals("Good evening", state.visibleText)
        assertEquals(GreetingPhase.Settled, state.phase)
    }

    @Test
    fun changeWithSameTextIsNoOp() {
        val settled = GreetingAnimationState(
            fullText = "Good morning",
            visibleText = "Good morning",
            phase = GreetingPhase.Settled,
        )
        val next = GreetingAnimator.reduce(settled, GreetingEvent.Change("Good morning"))
        assertEquals(settled, next)
    }

    @Test
    fun skipToCompleteExposesFullString() {
        var state = GreetingAnimator.reduce(
            GreetingAnimationState(fullText = ""),
            GreetingEvent.Start("Good afternoon"),
        )
        state = GreetingAnimator.reduce(state, GreetingEvent.SkipToComplete)
        assertEquals("Good afternoon", state.visibleText)
        assertTrue(state.phase == GreetingPhase.Settled)
    }
}
