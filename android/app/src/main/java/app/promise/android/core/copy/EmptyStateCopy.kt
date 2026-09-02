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
                title = "All clear right now",
                description = "You have no open tasks. Take a breather, or add something when you're ready.",
                actionLabel = "Add Commitment",
            )
            CommitmentListFilter.OVERDUE -> EmptyStateContent(
                title = "Zero overdue tasks",
                description = "Everything is right on track. Keep up the steady pace.",
                actionLabel = null,
            )
            CommitmentListFilter.TODAY -> EmptyStateContent(
                title = "Nothing due today",
                description = "Enjoy the free space or pick one priority to focus on.",
                actionLabel = "Add for Today",
            )
            CommitmentListFilter.UPCOMING -> EmptyStateContent(
                title = "No upcoming deadlines",
                description = "Tasks scheduled for future dates will appear here as you plan ahead.",
                actionLabel = "Schedule Task",
            )
            CommitmentListFilter.DONE -> EmptyStateContent(
                title = "No completed tasks yet",
                description = "As you complete commitments, your fulfilled promises will show up here.",
                actionLabel = null,
            )
        }
    }

    fun forGoalFilter(filter: GoalListFilter): EmptyStateContent {
        return when (filter) {
            GoalListFilter.ACTIVE -> EmptyStateContent(
                title = "No active habits yet",
                description = "What's one small practice you'd like to do every day? Start simple.",
                actionLabel = "Create Habit",
            )
            GoalListFilter.PAUSED -> EmptyStateContent(
                title = "No paused habits",
                description = "All your habits are currently in progress. Paused routines will rest here.",
                actionLabel = null,
            )
            GoalListFilter.COMPLETED -> EmptyStateContent(
                title = "No archived goals",
                description = "Completed goals and achieved milestones will be preserved here.",
                actionLabel = null,
            )
        }
    }

    object Home {
        val EmptyPersonalPractices = EmptyStateContent(
            title = "No daily habits yet",
            description = "Add daily or weekly practices to build steady momentum over time.",
            actionLabel = "Add a Habit",
        )

        val EmptyTodayCommitments = EmptyStateContent(
            title = "No tasks scheduled for today",
            description = "Speak a quick thought or tap + to plan something for today.",
            actionLabel = "Add Task",
        )

        val EmptySharedPractices = EmptyStateContent(
            title = "No shared goals yet",
            description = "Invite friends or teammates to build daily practices together.",
            actionLabel = "Create Shared Goal",
        )
    }

    object Search {
        val Idle = EmptyStateContent(
            title = "Search your promises",
            description = "Find tasks, recurring habits, deadlines, and group goals.",
            actionLabel = null,
        )

        val NoMatches = EmptyStateContent(
            title = "No results found",
            description = "Try searching with a different word, name, or date.",
            actionLabel = null,
        )
    }
}
