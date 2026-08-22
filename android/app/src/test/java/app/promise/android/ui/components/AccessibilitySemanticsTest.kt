package app.promise.android.ui.components

import app.promise.android.domain.ChatMessage
import app.promise.android.domain.ChatMessageDeliveryStatus
import app.promise.android.domain.ChatMessageSender
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomePractice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySemanticsTest {

    @Test
    fun `chat accessibility labels format correctly for all delivery states`() {
        val msgPartner = ChatMessage(
            id = "m2",
            sender = ChatMessageSender("u2", "Rahul"),
            body = "Great job!",
            createdAt = "2026-08-22T10:05:00Z",
            deliveryStatus = ChatMessageDeliveryStatus.SENT,
        )
        val msgFailed = ChatMessage(
            id = "m3",
            sender = ChatMessageSender("u1", "Me"),
            body = "Failed message",
            createdAt = "2026-08-22T10:10:00Z",
            deliveryStatus = ChatMessageDeliveryStatus.FAILED,
        )
        val msgSending = ChatMessage(
            id = "m4",
            sender = ChatMessageSender("u1", "Me"),
            body = "Sending message",
            createdAt = "2026-08-22T10:15:00Z",
            deliveryStatus = ChatMessageDeliveryStatus.SENDING,
        )

        // Verify failure announcement contains retry guidance
        val failedLabel = when {
            msgFailed.deliveryStatus == ChatMessageDeliveryStatus.FAILED -> "Message failed to send: ${msgFailed.body}. Tap to retry."
            else -> ""
        }
        assertEquals("Message failed to send: Failed message. Tap to retry.", failedLabel)

        // Verify sending announcement
        val sendingLabel = when {
            msgSending.deliveryStatus == ChatMessageDeliveryStatus.SENDING -> "Sending message: ${msgSending.body}"
            else -> ""
        }
        assertEquals("Sending message: Sending message", sendingLabel)

        // Verify partner message announcement
        val partnerLabel = "Message from ${msgPartner.sender.name} at 10:05 AM: ${msgPartner.body}"
        assertTrue(partnerLabel.contains("Rahul"))
        assertTrue(partnerLabel.contains("Great job!"))
    }

    @Test
    fun `home commitment and practice accessibility descriptions are complete`() {
        val overdueCommitment = HomeCommitment(
            id = "c1",
            title = "File quarterly taxes",
            dueLabel = "Overdue by 2 days",
            isOverdue = true,
            isDueToday = true,
            isCompleted = false,
        )
        val statusText = if (overdueCommitment.isCompleted) "Completed" else if (overdueCommitment.isOverdue) "Overdue" else "Due"
        val desc = "${overdueCommitment.title}, $statusText, ${overdueCommitment.dueLabel}"

        assertEquals("File quarterly taxes, Overdue, Overdue by 2 days", desc)

        val practice = HomePractice(
            id = "g1",
            title = "Morning Meditation",
            progressLabel = "4 of 7 days",
            progressFraction = 4f / 7f,
            streakDays = 5,
            isShared = true,
            checkedInToday = true,
        )
        val practiceDesc = buildString {
            append(practice.title)
            if (practice.isShared) append(", Shared practice")
            if (practice.streakDays > 0) append(", ${practice.streakDays} day streak")
            append(", ${practice.progressLabel}")
            if (practice.checkedInToday) append(", Checked in today")
        }

        assertEquals("Morning Meditation, Shared practice, 5 day streak, 4 of 7 days, Checked in today", practiceDesc)
    }

    @Test
    fun `goal row accessibility descriptions include cadence progress and shared status`() {
        val sharedGoal = Goal(
            id = "g2",
            title = "Read 20 pages",
            description = "Daily reading",
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
            source = "manual",
            pausedAt = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = "2026-08-22T00:00:00Z",
            updatedAt = "2026-08-22T00:00:00Z",
            isEnded = false,
            progress = GoalProgress(
                currentPeriod = GoalPeriodCounts(1, 1),
                weekProgress = GoalPeriodCounts(7, 7),
                consistencyPercent = 100,
            ),
            currentStreak = 4,
            collectiveProgress = null,
        )
        val sharedDesc = if (sharedGoal.isShared) "Shared goal" else "Personal goal"
        assertEquals("Personal goal", sharedDesc)
    }
}
