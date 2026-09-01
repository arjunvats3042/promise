package app.promise.android.ui.goals

import app.promise.android.domain.Goal
import app.promise.android.domain.GoalInvitePreview
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind

object GoalPresentation {
    fun recurrenceLabel(goal: Goal): String {
        return when (goal.recurrenceKind) {
            GoalRecurrenceKind.DAILY -> "Every day"
            GoalRecurrenceKind.WEEKLY_DAYS -> {
                val days = goal.weekdays.sorted().joinToString(", ") { weekdayShort(it) }
                if (days.isBlank()) "Selected days" else "Selected days · $days"
            }
            GoalRecurrenceKind.N_PER_PERIOD -> {
                val n = goal.timesPerPeriod ?: 0
                if (n == 1) "1 time each week" else "$n times each week"
            }
        }
    }

    /**
     * Clear daily status line representing today's execution reality.
     */
    fun progressLine(goal: Goal): String {
        val current = goal.progress.currentPeriod
        val isDueToday = current.required > 0
        val isCompletedToday = isDueToday && current.completed >= current.required

        return when {
            goal.status == GoalStatus.PAUSED -> "Goal is paused"
            goal.status == GoalStatus.COMPLETED -> "Goal completed"
            goal.status == GoalStatus.CANCELLED -> "Goal cancelled"
            goal.trackingKind == GoalTrackingKind.COUNT -> {
                val target = goal.targetValue ?: 1
                val value = current.value ?: 0
                if (isCompletedToday) {
                    "Today: Done ✓ ($value / $target)"
                } else if (isDueToday) {
                    "Today: $value of $target target"
                } else {
                    "Rest day today"
                }
            }
            isCompletedToday -> "Today: Completed ✓"
            isDueToday -> "Today: Pending check-in"
            else -> "Rest day today"
        }
    }

    fun streakLine(goal: Goal): String? {
        val streak = goal.currentStreak
        if (streak <= 0) return null
        return when (goal.recurrenceKind) {
            GoalRecurrenceKind.N_PER_PERIOD ->
                if (streak == 1) "1-week streak" else "$streak-week streak"
            else ->
                if (streak == 1) "1-day streak" else "$streak-day streak"
        }
    }

    fun statusLabel(status: GoalStatus): String = when (status) {
        GoalStatus.ACTIVE -> "Active"
        GoalStatus.PAUSED -> "Paused"
        GoalStatus.COMPLETED -> "Completed"
        GoalStatus.CANCELLED -> "Cancelled"
    }

    fun trackingLabel(kind: GoalTrackingKind): String = when (kind) {
        GoalTrackingKind.BINARY -> "Yes / Skip"
        GoalTrackingKind.COUNT -> "Count"
    }

    fun sharedMetaLine(goal: Goal): String? {
        if (!goal.isShared) return null
        val count = goal.participants.size.coerceAtLeast(1)
        return if (count == 1) "Shared · 1 person" else "Shared · $count people"
    }

    fun collectiveLine(goal: Goal): String? {
        val summary = goal.groupSummary
        if (summary != null) {
            return "Team: ${summary.todayCompletedCount} of ${summary.activeParticipantsCount} completed today"
        }
        val collective = goal.collectiveProgress ?: return null
        val current = collective.currentPeriod
        return if (current.required > 0) {
            "Team: ${current.completed}/${current.required} completed today"
        } else {
            val week = collective.weekProgress
            if (week.required > 0) "Team: ${week.completed}/${week.required} this week" else null
        }
    }

    fun inviteScheduleLabel(preview: GoalInvitePreview): String {
        return when (preview.recurrenceKind) {
            GoalRecurrenceKind.DAILY -> "Every day"
            GoalRecurrenceKind.WEEKLY_DAYS -> {
                val days = preview.weekdays.sorted().joinToString(", ") { weekdayShort(it) }
                if (days.isBlank()) "Selected days" else "Selected days · $days"
            }
            GoalRecurrenceKind.N_PER_PERIOD -> {
                val n = preview.timesPerPeriod ?: 0
                if (n == 1) "1 time each week" else "$n times each week"
            }
        }
    }

    fun needsCheckInToday(goal: Goal): Boolean {
        if (!goal.canCheckIn) return false
        val period = goal.progress.currentPeriod
        return period.required > 0 && period.completed < period.required
    }

    fun progressFraction(goal: Goal): Float {
        val current = goal.progress.currentPeriod
        if (current.required <= 0) {
            val week = goal.progress.weekProgress
            return if (week.required > 0) (week.completed.toFloat() / week.required.toFloat()).coerceIn(0f, 1f) else 0f
        }
        if (goal.trackingKind == GoalTrackingKind.COUNT) {
            val target = (goal.targetValue ?: 1).toFloat()
            val value = (current.value ?: 0).toFloat()
            return (value / target).coerceIn(0f, 1f)
        }
        return if (current.completed >= current.required) 1f else 0f
    }

    private fun weekdayShort(iso: Int): String = when (iso) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "?"
    }
}
