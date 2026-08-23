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

enum class GoalParticipantRole {
    OWNER,
    PARTICIPANT,
}

enum class GoalParticipantStatus {
    INVITED,
    ACTIVE,
    DECLINED,
    LEFT,
    REMOVED,
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

data class GoalParticipant(
    val id: String,
    val userId: String,
    val userName: String,
    val role: GoalParticipantRole,
    val status: GoalParticipantStatus,
    val invitedAt: String?,
    val joinedAt: String?,
    val leftAt: String?,
    val isExpired: Boolean = false,
    val invitationExpiresAt: String? = null,
    val avatarUrl: String? = null,
)

data class GroupSummary(
    val activeParticipantsCount: Int,
    val todayCompletedCount: Int,
    val todayCompletionRate: Float,
    val currentPeriodCompletedCount: Int,
    val currentPeriodTargetCount: Int,
    val currentPeriodCompletionRate: Float,
    val headline: String,
)

data class GroupMilestone(
    val key: String,
    val title: String,
    val description: String,
    val achieved: Boolean,
    val target: Int,
    val current: Int,
)

data class WeeklyReflection(
    val periodStart: String,
    val periodEnd: String,
    val completed: Int,
    val expected: Int,
    val percentage: Float,
    val reflectionText: String,
    val trendText: String,
)

data class CollectivePeriodCounts(
    val required: Int,
    val completed: Int,
    val requiredParticipants: Int? = null,
    val completedParticipants: Int? = null,
    val valueSum: Int? = null,
    val targetSum: Int? = null,
)

data class CollectiveProgress(
    val currentPeriod: CollectivePeriodCounts,
    val weekProgress: CollectivePeriodCounts,
)

data class GoalInvitePreview(
    val id: String,
    val title: String,
    val description: String,
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
    val inviterUserId: String,
    val inviterName: String,
    val invitationStatus: GoalParticipantStatus,
    val invitationExpiresAt: String?,
    val isExpired: Boolean = false,
)

data class GoalChatMessageSummary(
    val id: String,
    val text: String,
    val senderName: String,
    val senderId: String,
    val createdAt: String,
    val senderAvatarUrl: String? = null,
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
    val collectiveProgress: CollectiveProgress? = null,
    val participants: List<GoalParticipant> = emptyList(),
    val membershipRole: GoalParticipantRole? = null,
    val membershipStatus: GoalParticipantStatus? = null,
    val groupSummary: GroupSummary? = null,
    val milestones: List<GroupMilestone> = emptyList(),
    val weeklyReflection: WeeklyReflection? = null,
    val participantLimit: Int = 10,
    val isSharedField: Boolean = false,
    val unreadChatCount: Int = 0,
    val latestChatMessage: GoalChatMessageSummary? = null,
) {
    val isTerminal: Boolean
        get() = status == GoalStatus.COMPLETED || status == GoalStatus.CANCELLED

    val isShared: Boolean
        get() = isSharedField || participants.isNotEmpty() || collectiveProgress != null || membershipRole != null

    val isOwnerViewer: Boolean
        get() = membershipRole == GoalParticipantRole.OWNER || membershipRole == null

    val canManageParticipants: Boolean
        get() = isOwnerViewer && !isTerminal

    val canLeave: Boolean
        get() = membershipRole == GoalParticipantRole.PARTICIPANT &&
            membershipStatus == GoalParticipantStatus.ACTIVE

    val canCheckIn: Boolean
        get() = status == GoalStatus.ACTIVE && !isEnded

    val canPause: Boolean
        get() = status == GoalStatus.ACTIVE && isOwnerViewer

    val canResume: Boolean
        get() = status == GoalStatus.PAUSED && isOwnerViewer

    val canCompleteGoal: Boolean
        get() = (status == GoalStatus.ACTIVE || status == GoalStatus.PAUSED) && isOwnerViewer

    val canCancel: Boolean
        get() = (status == GoalStatus.ACTIVE || status == GoalStatus.PAUSED) && isOwnerViewer

    val canViewChat: Boolean
        get() = isShared && (membershipStatus == GoalParticipantStatus.ACTIVE || isOwnerViewer)

    val canSendChat: Boolean
        get() = canViewChat && !isTerminal
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

enum class ChatMessageDeliveryStatus {
    SENDING,
    SENT,
    FAILED,
}

data class ChatMessageSender(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class ChatMessage(
    val id: String,
    val sender: ChatMessageSender,
    val body: String,
    val createdAt: String,
    val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT,
)

data class GoalChatSummary(
    val unreadCount: Int,
    val latestMessage: ChatMessage?,
)

data class GoalChatReadState(
    val lastReadMessageId: String?,
    val lastReadAt: String,
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
    val isShared: Boolean = false,
)

data class CheckInInput(
    val status: GoalCheckInStatus,
    val periodDate: String? = null,
    val value: Int? = null,
    val note: String = "",
)

sealed class GoalListItem {
    data class Membership(val goal: Goal) : GoalListItem()
    data class Invite(val preview: GoalInvitePreview) : GoalListItem()
}

sealed class GoalDetail {
    data class Full(val goal: Goal) : GoalDetail()
    data class Invite(val preview: GoalInvitePreview) : GoalDetail()
}

data class GoalPage(
    val items: List<GoalListItem>,
    val nextPage: Int?,
)

data class GoalCheckInPage(
    val items: List<GoalCheckIn>,
    val nextPage: Int?,
)

data class LookupUser(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
)

data class GoalActivityActor(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class GoalActivityItem(
    val id: String,
    val eventType: String,
    val actor: GoalActivityActor?,
    val targetUser: GoalActivityActor?,
    val summary: String,
    val periodDate: String?,
    val createdAt: String,
)

