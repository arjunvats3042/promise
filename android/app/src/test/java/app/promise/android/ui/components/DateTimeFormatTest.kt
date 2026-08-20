package app.promise.android.ui.components

import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeFormatTest {
    @Test
    fun midnightAndNoonRoundTrip() {
        assertEquals(LocalTime.of(0, 0), DateTimeFormat.toLocalTime(12, 0, isPm = false))
        assertEquals(LocalTime.of(12, 0), DateTimeFormat.toLocalTime(12, 0, isPm = true))
        assertEquals(12, DateTimeFormat.toHour12(LocalTime.of(0, 0)))
        assertEquals(12, DateTimeFormat.toHour12(LocalTime.of(12, 0)))
        assertFalse(DateTimeFormat.isPm(LocalTime.of(0, 0)))
        assertTrue(DateTimeFormat.isPm(LocalTime.of(12, 0)))
    }

    @Test
    fun elevenPmAndOneAm() {
        assertEquals(LocalTime.of(23, 45), DateTimeFormat.toLocalTime(11, 45, isPm = true))
        assertEquals(LocalTime.of(1, 5), DateTimeFormat.toLocalTime(1, 5, isPm = false))
        assertEquals(11, DateTimeFormat.toHour12(LocalTime.of(23, 0)))
        assertTrue(DateTimeFormat.isPm(LocalTime.of(23, 0)))
    }

    @Test
    fun formatTimeUs() {
        assertEquals("6:00 PM", DateTimeFormat.formatTime(LocalTime.of(18, 0), Locale.US))
        assertEquals("12:00 AM", DateTimeFormat.formatTime(LocalTime.of(0, 0), Locale.US))
    }
}
