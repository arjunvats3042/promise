package app.promise.android.data.home

import app.promise.android.data.commitments.CommitmentListBucketing
import app.promise.android.domain.Commitment
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomePractice
import app.promise.android.ui.commitments.CommitmentTime
import app.promise.android.ui.goals.GoalPresentation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object HomeFeedMapper {
    const val UPCOMING_DAYS = 7L

    fun commitmentsForHome(
        open: List<Commitment>,
        timeZoneId: String,
        now: Instant = Instant.now(),
    ): List<HomeCommitment> {
        val zone = zoneId(timeZoneId)
        val today = LocalDate.ofInstant(now, zone)
        val upcomingEnd = today.plusDays(UPCOMING_DAYS)
        val filtered = open.filter { c ->
            if (c.dueAt.isNullOrBlank()) return@filter false
            if (c.isOverdue) return@filter true
            val dueDate = localDate(c.dueAt, zone) ?: return@filter false
            when {
                dueDate == today -> true
                dueDate.isAfter(today) && !dueDate.isAfter(upcomingEnd) -> true
                else -> false
            }
        }
        return CommitmentListBucketing.sortOpen(filtered).map { toHomeCommitment(it, today, zone) }
    }

    fun practicesForHome(goals: List<Goal>): List<HomePractice> {
        return goals
            .filter { it.status == GoalStatus.ACTIVE && !it.isEnded }
            .sortedWith(
                compareByDescending<Goal> { GoalPresentation.needsCheckInToday(it) }
                    .thenBy { it.title.lowercase() },
            )
            .map { toHomePractice(it) }
    }

    fun toHomeCommitment(
        commitment: Commitment,
        today: LocalDate,
        zone: ZoneId,
    ): HomeCommitment {
        val dueDate = commitment.dueAt?.let { localDate(it, zone) }
        val isDueToday = dueDate == today
        val dueLabel = when {
            commitment.isOverdue -> {
                val formatted = CommitmentTime.formatDue(
                    commitment.dueAt,
                    commitment.duePrecision,
                    zone.id,
                )
                if (formatted != null) "Overdue · $formatted" else "Overdue"
            }
            isDueToday -> "Due today"
            else -> {
                val formatted = CommitmentTime.formatDue(
                    commitment.dueAt,
                    commitment.duePrecision,
                    zone.id,
                )
                if (formatted != null) "Due $formatted" else "Upcoming"
            }
        }
        return HomeCommitment(
            id = commitment.id,
            title = commitment.title,
            dueLabel = dueLabel,
            isOverdue = commitment.isOverdue,
            isDueToday = isDueToday,
            isCompleted = false,
        )
    }

    fun toHomePractice(goal: Goal): HomePractice {
        val needs = GoalPresentation.needsCheckInToday(goal)
        return HomePractice(
            id = goal.id,
            title = goal.title,
            progressFraction = GoalPresentation.progressFraction(goal),
            progressLabel = GoalPresentation.progressLine(goal),
            streakDays = goal.currentStreak,
            checkedInToday = !needs,
            trackingKind = goal.trackingKind,
            targetValue = goal.targetValue,
            periodValue = goal.progress.currentPeriod.value,
            isShared = goal.isShared,
            unreadChatCount = goal.unreadChatCount,
            latestChatMessage = goal.latestChatMessage,
        )
    }

    private fun localDate(iso: String, zone: ZoneId): LocalDate? {
        return runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate()
        }.recoverCatching {
            ZonedDateTime.parse(iso).withZoneSameInstant(zone).toLocalDate()
        }.getOrNull()
    }

    private fun zoneId(id: String): ZoneId {
        return runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
    }
}
