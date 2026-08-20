package app.promise.android.ui.goals

import app.promise.android.domain.Goal
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

    fun progressLine(goal: Goal): String {
        val week = goal.progress.weekProgress
        val required = week.required
        val completed = week.completed
        return if (required > 0) {
            "$completed / $required this week"
        } else {
            val current = goal.progress.currentPeriod
            if (current.required > 0) {
                "$completed / ${current.required} today"
            } else {
                "No check-in expected today"
            }
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

    fun needsCheckInToday(goal: Goal): Boolean {
        if (!goal.canCheckIn) return false
        val period = goal.progress.currentPeriod
        return period.required > 0 && period.completed < period.required
    }

    fun progressFraction(goal: Goal): Float {
        val week = goal.progress.weekProgress
        if (week.required <= 0) return 0f
        return (week.completed.toFloat() / week.required.toFloat()).coerceIn(0f, 1f)
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
