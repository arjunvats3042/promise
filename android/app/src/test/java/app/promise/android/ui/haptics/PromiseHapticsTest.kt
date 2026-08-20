package app.promise.android.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Test

class PromiseHapticsTest {
    @Test
    fun fakeRecordsLightConfirmErrorSeparately() {
        val haptics = FakePromiseHaptics()
        haptics.light()
        haptics.confirm()
        haptics.error()
        assertEquals(listOf("light", "confirm", "error"), haptics.events)
    }
}
