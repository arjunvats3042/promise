package app.promise.android.domain

enum class GoalStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED,
}

enum class GoalRecurrenceKind {
    DAILY,
    WEEKLY_DAYS,
    N_PER_PERIOD,
}

enum class GoalPeriodUnit {
    WEEK,
}

enum class GoalTrackingKind {
    BINARY,
    COUNT,
}

enum class GoalCheckInStatus {
    COMPLETED,
    SKIPPED,
}

data class GoalPeriodCounts(
    val required: Int,
    val completed: Int,
    val value: Int? = null,
    val targetValue: Int? = null,
)

data class GoalProgress(
    val currentPeriod: GoalPeriodCounts,
    val weekProgress: GoalPeriodCounts,
    val consistencyPercent: Int,
)

data class Goal(
    val id: String,
    val title: String,
    val description: String,
    val status: GoalStatus,
    val timezone: String,
    val startDate: String,
    val endDate: String?,
    val recurrenceKind: GoalRecurrenceKind,
    val weekdays: List<Int>,
    val periodUnit: GoalPeriodUnit?,
    val timesPerPeriod: Int?,
    val trackingKind: GoalTrackingKind,
    val targetValue: Int?,
    val targetUnit: String,
    val source: String,
    val pausedAt: String?,
    val completedAt: String?,
    val cancelledAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val isEnded: Boolean,
    val progress: GoalProgress,
    val currentStreak: Int,
) {
    val isTerminal: Boolean
        get() = status == GoalStatus.COMPLETED || status == GoalStatus.CANCELLED

    val canCheckIn: Boolean
        get() = status == GoalStatus.ACTIVE && !isEnded

    val canPause: Boolean
        get() = status == GoalStatus.ACTIVE

    val canResume: Boolean
        get() = status == GoalStatus.PAUSED

    val canCompleteGoal: Boolean
        get() = status == GoalStatus.ACTIVE || status == GoalStatus.PAUSED

    val canCancel: Boolean
        get() = status == GoalStatus.ACTIVE || status == GoalStatus.PAUSED
}

data class GoalCheckIn(
    val id: String,
    val periodDate: String,
    val status: GoalCheckInStatus,
    val value: Int?,
    val note: String,
    val checkedAt: String,
    val createdAt: String,
    val updatedAt: String,
)

enum class GoalListFilter {
    ACTIVE,
    PAUSED,
    COMPLETED,
}

data class CreateGoalInput(
    val title: String,
    val description: String = "",
    val timezone: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val recurrenceKind: GoalRecurrenceKind,
    val weekdays: List<Int>? = null,
    val periodUnit: GoalPeriodUnit? = null,
    val timesPerPeriod: Int? = null,
    val trackingKind: GoalTrackingKind = GoalTrackingKind.BINARY,
    val targetValue: Int? = null,
    val targetUnit: String = "",
)

data class CheckInInput(
    val status: GoalCheckInStatus,
    val periodDate: String? = null,
    val value: Int? = null,
    val note: String = "",
)

data class GoalPage(
    val items: List<Goal>,
    val nextPage: Int?,
)

data class GoalCheckInPage(
    val items: List<GoalCheckIn>,
    val nextPage: Int?,
)
