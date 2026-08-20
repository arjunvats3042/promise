package app.promise.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test
    fun durationsMatchSpec() {
        assertEquals(90, Motion.PressMs)
        assertEquals(120, Motion.MicroMs)
        assertEquals(160, Motion.FilterChangeMs)
        assertEquals(180, Motion.ListInsertMs)
        assertEquals(180, Motion.CompletionMs)
        assertEquals(200, Motion.TabMs)
        assertEquals(210, Motion.ScreenPushMs)
        assertEquals(220, Motion.SheetOpenMs)
        assertEquals(200, Motion.SheetCloseMs)
        assertEquals(220, Motion.ThemeMs)
        assertEquals(180, Motion.GreetingInitialDelayMs)
        assertEquals(52, Motion.GreetingTypePerCharMs)
        assertEquals(28, Motion.GreetingDeletePerCharMs)
        assertEquals(900, Motion.GreetingChangeHoldMs)
        assertEquals(120, Motion.GreetingChangeGapMs)
        assertEquals(100, Motion.ReducedMotionFadeMs)
    }
}
