package app.promise.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DateWheelBoundsTest {
    @Test
    fun clampsDayForShortMonths() {
        assertEquals(28, DateWheelBounds.clampDay(2026, 2, 31))
        assertEquals(30, DateWheelBounds.clampDay(2026, 4, 31))
        assertEquals(31, DateWheelBounds.clampDay(2026, 1, 31))
    }

    @Test
    fun resolveDateBuildsValidLocalDate() {
        assertEquals("2026-02-28", DateWheelBounds.resolveDate(2026, 2, 31).toString())
    }
}
