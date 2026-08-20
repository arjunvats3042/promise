package app.promise.android.ui.commitments

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentTimeTest {
    @Test
    fun snoozeValidation_rejectsPastAndBeyond30Days() {
        val now = Instant.parse("2026-08-20T12:00:00Z")
        assertFalse(CommitmentTime.isValidSnooze(now.minusSeconds(60), now))
        assertTrue(CommitmentTime.isValidSnooze(now.plusSeconds(3600), now))
        assertFalse(CommitmentTime.isValidSnooze(now.plusSeconds(31L * 24 * 60 * 60), now))
    }

    @Test
    fun presets_areInTheFuture() {
        val later = CommitmentTime.laterToday("UTC")
        val tomorrow = CommitmentTime.tomorrowMorning("UTC")
        val week = CommitmentTime.nextWeek("UTC")
        val now = Instant.now()
        assertTrue(later.isAfter(now.minusSeconds(1)))
        assertTrue(tomorrow.isAfter(now))
        assertTrue(week.isAfter(now))
        assertTrue(CommitmentTime.isValidSnooze(later, now.minusSeconds(1)))
    }
}
