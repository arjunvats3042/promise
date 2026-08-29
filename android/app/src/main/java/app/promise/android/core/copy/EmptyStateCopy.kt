package app.promise.android.core.copy

import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.GoalListFilter

data class EmptyStateContent(
    val title: String,
    val description: String,
    val actionLabel: String? = null,
)

/**
 * Pure models for all zero-state screens that eliminate boilerplate
 * ("Check another filter, or come back later") with intentional, supportive copy.
 */
object EmptyStateCopy {

    fun forCommitmentFilter(filter: CommitmentListFilter): EmptyStateContent {
        return when (filter) {
            CommitmentListFilter.OPEN -> EmptyStateContent(
                title = "No open commitments",
                description = "Your slate is completely clear. Capture a new promise whenever you are ready.",
                actionLabel = "Create Commitment",
            )
            CommitmentListFilter.OVERDUE -> EmptyStateContent(
                title = "Zero overdue promises",
                description = "You're completely on schedule. Integrity and pace are right on track.",
                actionLabel = null,
            )
            CommitmentListFilter.TODAY -> EmptyStateContent(
                title = "Nothing scheduled for today",
                description = "Enjoy the open space or add a priority task for today.",
                actionLabel = "Add for Today",
            )
            CommitmentListFilter.UPCOMING -> EmptyStateContent(
                title = "No upcoming deadlines",
                description = "Future promises and target dates will be organized here as you plan ahead.",
                actionLabel = "Schedule Commitment",
            )
            CommitmentListFilter.DONE -> EmptyStateContent(
                title = "No completed promises yet",
                description = "Every promise fulfilled will be preserved here as proof of your consistency.",
                actionLabel = null,
            )
        }
    }

    fun forGoalFilter(filter: GoalListFilter): EmptyStateContent {
        return when (filter) {
            GoalListFilter.ACTIVE -> EmptyStateContent(
                title = "No active habits yet",
                description = "Start a recurring daily or weekly practice to build unbreakable consistency.",
                actionLabel = "Create Goal",
            )
            GoalListFilter.PAUSED -> EmptyStateContent(
                title = "No paused practices",
                description = "All your active habits are in steady motion. Paused goals rest here.",
                actionLabel = null,
            )
            GoalListFilter.COMPLETED -> EmptyStateContent(
                title = "No archived milestones",
                description = "Accomplished habits and finished goal challenges will be preserved here.",
                actionLabel = null,
            )
        }
    }

    object Home {
        val EmptyPersonalPractices = EmptyStateContent(
            title = "No active personal habits",
            description = "Create daily or weekly goals in the Goals tab to build compounding momentum.",
            actionLabel = "Add a Habit",
        )

        val EmptyTodayCommitments = EmptyStateContent(
            title = "No commitments due today",
            description = "Capture an intention using your voice or tap + to schedule a task.",
            actionLabel = "Add Promise",
        )

        val EmptySharedPractices = EmptyStateContent(
            title = "No shared goals yet",
            description = "Invite friends or accountability partners to build daily practices together.",
            actionLabel = "Create Shared Goal",
        )
    }

    object Search {
        val Idle = EmptyStateContent(
            title = "Search everything in Promise",
            description = "Find commitments, recurring goals, deadlines, and shared groups.",
            actionLabel = null,
        )

        val NoMatches = EmptyStateContent(
            title = "No matching promises found",
            description = "Try searching with a broader keyword, date, or tag name.",
            actionLabel = null,
        )
    }
}
