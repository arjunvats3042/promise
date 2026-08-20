package app.promise.android.domain

data class HomeCommitment(
    val id: String,
    val title: String,
    val dueLabel: String,
    val isOverdue: Boolean,
    val isDueToday: Boolean,
    val isCompleted: Boolean = false,
)

data class HomePractice(
    val id: String,
    val title: String,
    val progressFraction: Float,
    val progressLabel: String,
    val streakDays: Int,
    val checkedInToday: Boolean = false,
)

data class HomeFeed(
    val commitments: List<HomeCommitment>,
    val practices: List<HomePractice>,
)
