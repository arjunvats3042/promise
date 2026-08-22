package app.promise.android.data.goals

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoalPeriodCountsDto(
    val required: Int = 0,
    val completed: Int = 0,
    val value: Int? = null,
    @SerialName("target_value")
    val targetValue: Int? = null,
)

@Serializable
data class GoalProgressDto(
    @SerialName("current_period")
    val currentPeriod: GoalPeriodCountsDto = GoalPeriodCountsDto(),
    @SerialName("week_progress")
    val weekProgress: GoalPeriodCountsDto = GoalPeriodCountsDto(),
    @SerialName("consistency_percent")
    val consistencyPercent: Int = 0,
)

@Serializable
data class CollectivePeriodCountsDto(
    val required: Int = 0,
    val completed: Int = 0,
    @SerialName("required_participants")
    val requiredParticipants: Int? = null,
    @SerialName("completed_participants")
    val completedParticipants: Int? = null,
    @SerialName("value_sum")
    val valueSum: Int? = null,
    @SerialName("target_sum")
    val targetSum: Int? = null,
)

@Serializable
data class CollectiveProgressDto(
    @SerialName("current_period")
    val currentPeriod: CollectivePeriodCountsDto = CollectivePeriodCountsDto(),
    @SerialName("week_progress")
    val weekProgress: CollectivePeriodCountsDto = CollectivePeriodCountsDto(),
)

@Serializable
data class GroupSummaryDto(
    @SerialName("active_participants_count")
    val activeParticipantsCount: Int = 0,
    @SerialName("today_completed_count")
    val todayCompletedCount: Int = 0,
    @SerialName("today_completion_rate")
    val todayCompletionRate: Float = 0f,
    @SerialName("current_period_completed_count")
    val currentPeriodCompletedCount: Int = 0,
    @SerialName("current_period_target_count")
    val currentPeriodTargetCount: Int = 0,
    @SerialName("current_period_completion_rate")
    val currentPeriodCompletionRate: Float = 0f,
    val headline: String = "",
)

@Serializable
data class GroupMilestoneDto(
    val key: String,
    val title: String,
    val description: String = "",
    val achieved: Boolean = false,
    val target: Int = 0,
    val current: Int = 0,
)

@Serializable
data class WeeklyReflectionDto(
    @SerialName("period_start")
    val periodStart: String,
    @SerialName("period_end")
    val periodEnd: String,
    val completed: Int = 0,
    val expected: Int = 0,
    val percentage: Float = 0f,
    @SerialName("reflection_text")
    val reflectionText: String = "",
    @SerialName("trend_text")
    val trendText: String = "",
)

@Serializable
data class TransferOwnershipRequest(
    @SerialName("participant_id")
    val participantId: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
)

@Serializable
data class ReinviteParticipantRequest(
    @SerialName("participant_id")
    val participantId: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
)

@Serializable
data class GoalParticipantDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String,
    val role: String,
    val status: String,
    @SerialName("invited_at")
    val invitedAt: String? = null,
    @SerialName("invitation_expires_at")
    val invitationExpiresAt: String? = null,
    @SerialName("is_expired")
    val isExpired: Boolean = false,
    @SerialName("joined_at")
    val joinedAt: String? = null,
    @SerialName("left_at")
    val leftAt: String? = null,
)

@Serializable
data class GoalDto(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String = "ACTIVE",
    val timezone: String = "",
    @SerialName("start_date")
    val startDate: String = "",
    @SerialName("end_date")
    val endDate: String? = null,
    @SerialName("recurrence_kind")
    val recurrenceKind: String = "DAILY",
    val weekdays: List<Int> = emptyList(),
    @SerialName("period_unit")
    val periodUnit: String? = null,
    @SerialName("times_per_period")
    val timesPerPeriod: Int? = null,
    @SerialName("tracking_kind")
    val trackingKind: String = "BINARY",
    @SerialName("target_value")
    val targetValue: Int? = null,
    @SerialName("target_unit")
    val targetUnit: String = "",
    val source: String = "MANUAL",
    @SerialName("paused_at")
    val pausedAt: String? = null,
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("cancelled_at")
    val cancelledAt: String? = null,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
    @SerialName("is_ended")
    val isEnded: Boolean = false,
    val progress: GoalProgressDto = GoalProgressDto(),
    @SerialName("current_streak")
    val currentStreak: Int = 0,
    @SerialName("collective_progress")
    val collectiveProgress: CollectiveProgressDto? = null,
    val participants: List<GoalParticipantDto> = emptyList(),
    @SerialName("membership_role")
    val membershipRole: String? = null,
    @SerialName("membership_status")
    val membershipStatus: String? = null,
    @SerialName("inviter_user_id")
    val inviterUserId: String? = null,
    @SerialName("inviter_name")
    val inviterName: String? = null,
    @SerialName("invitation_status")
    val invitationStatus: String? = null,
    @SerialName("invitation_expires_at")
    val invitationExpiresAt: String? = null,
    @SerialName("is_expired")
    val isExpired: Boolean = false,
    @SerialName("is_shared")
    val isShared: Boolean = false,
    @SerialName("group_summary")
    val groupSummary: GroupSummaryDto? = null,
    val milestones: List<GroupMilestoneDto> = emptyList(),
    @SerialName("weekly_reflection")
    val weeklyReflection: WeeklyReflectionDto? = null,
    @SerialName("participant_limit")
    val participantLimit: Int = 10,
)

@Serializable
data class GoalPageDto(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<GoalDto> = emptyList(),
)

@Serializable
data class CreateGoalRequest(
    val title: String,
    val description: String = "",
    val timezone: String? = null,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("end_date")
    val endDate: String? = null,
    @SerialName("recurrence_kind")
    val recurrenceKind: String,
    val weekdays: List<Int>? = null,
    @SerialName("period_unit")
    val periodUnit: String? = null,
    @SerialName("times_per_period")
    val timesPerPeriod: Int? = null,
    @SerialName("tracking_kind")
    val trackingKind: String = "BINARY",
    @SerialName("target_value")
    val targetValue: Int? = null,
    @SerialName("target_unit")
    val targetUnit: String = "",
    @SerialName("is_shared")
    val isShared: Boolean = false,
)

@Serializable
data class GoalCheckInDto(
    val id: String,
    @SerialName("period_date")
    val periodDate: String,
    val status: String,
    val value: Int? = null,
    val note: String = "",
    @SerialName("checked_at")
    val checkedAt: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class GoalCheckInPageDto(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<GoalCheckInDto> = emptyList(),
)

@Serializable
data class CheckInRequest(
    val status: String,
    @SerialName("period_date")
    val periodDate: String? = null,
    val value: Int? = null,
    val note: String = "",
)

@Serializable
data class InviteParticipantRequest(
    @SerialName("user_id")
    val userId: String,
)

@Serializable
data class ChatMessageSenderDto(
    val id: String,
    val name: String,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    val sender: ChatMessageSenderDto,
    val body: String,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class SendChatMessageRequest(
    val body: String,
)

@Serializable
data class MarkChatReadRequest(
    @SerialName("last_read_message_id")
    val lastReadMessageId: String,
)

@Serializable
data class GoalChatReadStateDto(
    @SerialName("last_read_message_id")
    val lastReadMessageId: String? = null,
    @SerialName("last_read_at")
    val lastReadAt: String,
)

@Serializable
data class GoalChatSummaryDto(
    @SerialName("unread_count")
    val unreadCount: Int = 0,
    @SerialName("latest_message")
    val latestMessage: ChatMessageDto? = null,
)

@Serializable
data class GoalActivityActorDto(
    val id: String,
    val name: String,
)

@Serializable
data class GoalActivityItemDto(
    val id: String,
    @SerialName("event_type")
    val eventType: String,
    val actor: GoalActivityActorDto? = null,
    @SerialName("target_user")
    val targetUser: GoalActivityActorDto? = null,
    val summary: String,
    @SerialName("period_date")
    val periodDate: String? = null,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class ChatSearchResultDto(
    val id: String,
    @SerialName("message_id") val messageId: String = "",
    val sender: ChatMessageSenderDto,
    val body: String,
    val snippet: String = "",
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ChatSearchResponseDto(
    val results: List<ChatSearchResultDto> = emptyList(),
)
