package app.promise.android.data.home

import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.DuePrecision
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedMapperTest {
    private val zone = "Asia/Kolkata"
    // 2026-08-21 12:00 IST = 2026-08-21 06:30 UTC
    private val noonIst = Instant.parse("2026-08-21T06:30:00Z")

    @Test
    fun includesOverdueDueTodayAndUpcomingWithin7Days() {
        val open = listOf(
            commitment("overdue", dueAt = "2026-08-19T18:29:59Z", isOverdue = true),
            commitment("today", dueAt = "2026-08-21T18:29:59Z", isOverdue = false),
            commitment("soon", dueAt = "2026-08-25T18:29:59Z", isOverdue = false),
            commitment("far", dueAt = "2026-09-10T18:29:59Z", isOverdue = false),
            commitment("undated", dueAt = null, isOverdue = false),
        )
        val home = HomeFeedMapper.commitmentsForHome(open, zone, noonIst)
        assertEquals(listOf("overdue", "today", "soon"), home.map { it.id })
        assertTrue(home[0].isOverdue)
        assertTrue(home[1].isDueToday)
        assertFalse(home[2].isDueToday)
    }

    @Test
    fun pastDueWithoutOverdueFlag_excludedAfterLocalMidnight() {
        // Due end of 21 Aug IST; after IST midnight that due is in the past and not overdue-flagged
        val justAfterMidnightIst = Instant.parse("2026-08-21T18:30:00Z")
        val dueEndOf21 = "2026-08-21T18:29:59Z"
        val open = listOf(commitment("c1", dueAt = dueEndOf21, isOverdue = false))
        assertTrue(HomeFeedMapper.commitmentsForHome(open, zone, justAfterMidnightIst).isEmpty())

        val overdue = listOf(commitment("c1", dueAt = dueEndOf21, isOverdue = true))
        assertEquals(1, HomeFeedMapper.commitmentsForHome(overdue, zone, justAfterMidnightIst).size)
    }

    @Test
    fun overdueFlag_includedEvenIfLocalDateIsToday() {
        val open = listOf(
            commitment("c1", dueAt = "2026-08-21T06:00:00Z", isOverdue = true),
        )
        val home = HomeFeedMapper.commitmentsForHome(open, zone, noonIst)
        assertEquals(1, home.size)
        assertTrue(home.single().isOverdue)
    }

    @Test
    fun practices_excludeEndedAndPreferNeedsCheckIn() {
        val goals = listOf(
            goal("done-today", required = 1, completed = 1, title = "Zulu"),
            goal("needs", required = 1, completed = 0, title = "Alpha"),
            goal("ended", required = 1, completed = 0, title = "Beta", isEnded = true),
        )
        val practices = HomeFeedMapper.practicesForHome(goals)
        assertEquals(listOf("needs", "done-today"), practices.map { it.id })
        assertFalse(practices[0].checkedInToday)
        assertTrue(practices[1].checkedInToday)
        assertEquals(5, practices[0].streakDays)
    }

    private fun commitment(
        id: String,
        dueAt: String?,
        isOverdue: Boolean,
    ): Commitment {
        return Commitment(
            id = id,
            title = id,
            description = "",
            status = CommitmentStatus.PENDING,
            dueAt = dueAt,
            duePrecision = DuePrecision.DATE,
            source = "MANUAL",
            snoozedUntil = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            isOverdue = isOverdue,
        )
    }

    private fun goal(
        id: String,
        required: Int,
        completed: Int,
        title: String,
        isEnded: Boolean = false,
    ): Goal {
        return Goal(
            id = id,
            title = title,
            description = "",
            status = GoalStatus.ACTIVE,
            timezone = "UTC",
            startDate = "2026-08-01",
            endDate = null,
            recurrenceKind = GoalRecurrenceKind.DAILY,
            weekdays = emptyList(),
            periodUnit = null,
            timesPerPeriod = null,
            trackingKind = GoalTrackingKind.BINARY,
            targetValue = null,
            targetUnit = "",
            source = "MANUAL",
            pausedAt = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            isEnded = isEnded,
            progress = GoalProgress(
                currentPeriod = GoalPeriodCounts(required, completed),
                weekProgress = GoalPeriodCounts(7, completed),
                consistencyPercent = 50,
            ),
            currentStreak = 5,
        )
    }
}
