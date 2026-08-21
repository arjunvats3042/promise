package app.promise.android.ui.goals

import app.promise.android.domain.Goal
import app.promise.android.domain.GoalInvitePreview
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalParticipantRole
import app.promise.android.domain.GoalParticipantStatus
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalPresentationTest {
    @Test
    fun recurrenceLabels() {
        assertEquals("Every day", GoalPresentation.recurrenceLabel(sample()))
        assertEquals(
            "Selected days · Mon, Wed",
            GoalPresentation.recurrenceLabel(
                sample(kind = GoalRecurrenceKind.WEEKLY_DAYS, weekdays = listOf(3, 1)),
            ),
        )
        assertEquals(
            "5 times each week",
            GoalPresentation.recurrenceLabel(
                sample(kind = GoalRecurrenceKind.N_PER_PERIOD, times = 5),
            ),
        )
    }

    @Test
    fun sharedMetaLine_nullForPersonal() {
        assertNull(GoalPresentation.sharedMetaLine(sample()))
    }

    @Test
    fun sharedMetaLine_singlePerson() {
        val participant = GoalParticipant(
            id = "p1",
            userId = "u2",
            userName = "Bob",
            role = GoalParticipantRole.PARTICIPANT,
            status = GoalParticipantStatus.ACTIVE,
            invitedAt = null,
            joinedAt = null,
            leftAt = null,
        )
        val goal = sample().copy(participants = listOf(participant))
        assertEquals("Shared · 1 person", GoalPresentation.sharedMetaLine(goal))
    }

    @Test
    fun sharedMetaLine_multiplePeople() {
        val makeParticipant = { id: String ->
            GoalParticipant(
                id = id,
                userId = id,
                userName = "User $id",
                role = GoalParticipantRole.PARTICIPANT,
                status = GoalParticipantStatus.ACTIVE,
                invitedAt = null,
                joinedAt = null,
                leftAt = null,
            )
        }
        val goal = sample().copy(participants = listOf(makeParticipant("u2"), makeParticipant("u3")))
        assertEquals("Shared · 2 people", GoalPresentation.sharedMetaLine(goal))
    }

    @Test
    fun inviteScheduleLabel_daily() {
        val preview = sampleInvitePreview(GoalRecurrenceKind.DAILY)
        assertEquals("Every day", GoalPresentation.inviteScheduleLabel(preview))
    }

    @Test
    fun inviteScheduleLabel_weeklyDays() {
        val preview = sampleInvitePreview(GoalRecurrenceKind.WEEKLY_DAYS, weekdays = listOf(1, 3))
        assertEquals("Selected days · Mon, Wed", GoalPresentation.inviteScheduleLabel(preview))
    }

    @Test
    fun inviteScheduleLabel_nPerPeriod() {
        val preview = sampleInvitePreview(GoalRecurrenceKind.N_PER_PERIOD, times = 4)
        assertEquals("4 times each week", GoalPresentation.inviteScheduleLabel(preview))
    }

    @Test
    fun needsCheckInToday() {
        assertTrue(GoalPresentation.needsCheckInToday(sample(required = 1, completed = 0)))
        assertFalse(GoalPresentation.needsCheckInToday(sample(required = 1, completed = 1)))
        assertFalse(
            GoalPresentation.needsCheckInToday(
                sample(status = GoalStatus.PAUSED, required = 1, completed = 0),
            ),
        )
    }

    private fun sampleInvitePreview(
        kind: GoalRecurrenceKind = GoalRecurrenceKind.DAILY,
        weekdays: List<Int> = emptyList(),
        times: Int? = null,
    ): GoalInvitePreview = GoalInvitePreview(
        id = "g1",
        title = "Read",
        description = "",
        timezone = "UTC",
        startDate = "2026-08-01",
        endDate = null,
        recurrenceKind = kind,
        weekdays = weekdays,
        periodUnit = null,
        timesPerPeriod = times,
        trackingKind = GoalTrackingKind.BINARY,
        targetValue = null,
        targetUnit = "",
        inviterUserId = "u2",
        inviterName = "Alice",
        invitationStatus = GoalParticipantStatus.INVITED,
        invitationExpiresAt = null,
    )

    private fun sample(
        kind: GoalRecurrenceKind = GoalRecurrenceKind.DAILY,
        weekdays: List<Int> = emptyList(),
        times: Int? = null,
        status: GoalStatus = GoalStatus.ACTIVE,
        required: Int = 1,
        completed: Int = 0,
    ): Goal {
        return Goal(
            id = "g1",
            title = "Read",
            description = "",
            status = status,
            timezone = "UTC",
            startDate = "2026-08-01",
            endDate = null,
            recurrenceKind = kind,
            weekdays = weekdays,
            periodUnit = null,
            timesPerPeriod = times,
            trackingKind = GoalTrackingKind.BINARY,
            targetValue = null,
            targetUnit = "",
            source = "MANUAL",
            pausedAt = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            isEnded = false,
            progress = GoalProgress(
                currentPeriod = GoalPeriodCounts(required, completed),
                weekProgress = GoalPeriodCounts(7, 6),
                consistencyPercent = 80,
            ),
            currentStreak = 5,
        )
    }
}
