package app.promise.android.domain

import app.promise.android.core.ErrorKind

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
    val trackingKind: GoalTrackingKind = GoalTrackingKind.BINARY,
    val targetValue: Int? = null,
    val periodValue: Int? = null,
    val isShared: Boolean = false,
    val unreadChatCount: Int = 0,
    val latestChatMessage: GoalChatMessageSummary? = null,
)

data class HomeFeed(
    val commitments: List<HomeCommitment>,
    val practices: List<HomePractice>,
    val pendingInvites: List<GoalInvitePreview> = emptyList(),
    val commitmentsError: ErrorKind? = null,
    val practicesError: ErrorKind? = null,
)
