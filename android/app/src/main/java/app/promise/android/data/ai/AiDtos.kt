package app.promise.android.data.ai

import app.promise.android.domain.CommitmentRefinement
import app.promise.android.domain.GoalChatAiSummary
import app.promise.android.domain.GoalSuggestion
import app.promise.android.domain.ParsedThoughtItem
import app.promise.android.domain.PlannedOrderItem
import app.promise.android.domain.PlanningSuggestion
import app.promise.android.domain.ReflectionCoaching
import app.promise.android.domain.SharedGoalAiSummary
import app.promise.android.domain.WeeklyAiFacts
import app.promise.android.domain.WeeklyAiInsights
import app.promise.android.domain.WeeklyAiInsightsContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiPromptRequestDto(
    val prompt: String,
    val timezone: String = "UTC",
)

@Serializable
data class AiThoughtRequestDto(
    val thought: String,
    val timezone: String = "UTC",
)

@Serializable
data class GoalSuggestionDto(
    val title: String,
    val description: String = "",
    @SerialName("recurrence_kind") val recurrenceKind: String,
    val weekdays: List<Int> = emptyList(),
    @SerialName("period_unit") val periodUnit: String? = null,
    @SerialName("times_per_period") val timesPerPeriod: Int? = null,
    @SerialName("tracking_kind") val trackingKind: String,
    @SerialName("target_value") val targetValue: Double? = null,
    @SerialName("target_unit") val targetUnit: String = "",
    val reasoning: String = "",
) {
    fun toDomain(): GoalSuggestion = GoalSuggestion(
        title = title,
        description = description,
        recurrenceKind = recurrenceKind,
        weekdays = weekdays,
        periodUnit = periodUnit,
        timesPerPeriod = timesPerPeriod,
        trackingKind = trackingKind,
        targetValue = targetValue,
        targetUnit = targetUnit,
        reasoning = reasoning,
    )
}

@Serializable
data class CommitmentRefinementDto(
    @SerialName("is_ambiguous") val isAmbiguous: Boolean,
    @SerialName("clarifying_question") val clarifyingQuestion: String? = null,
    @SerialName("refined_title") val refinedTitle: String,
    @SerialName("refined_description") val refinedDescription: String = "",
    @SerialName("suggested_due_at") val suggestedDueAt: String? = null,
    @SerialName("suggested_due_precision") val suggestedDuePrecision: String? = null,
    val reasoning: String = "",
) {
    fun toDomain(): CommitmentRefinement = CommitmentRefinement(
        isAmbiguous = isAmbiguous,
        clarifyingQuestion = clarifyingQuestion,
        refinedTitle = refinedTitle,
        refinedDescription = refinedDescription,
        suggestedDueAt = suggestedDueAt,
        suggestedDuePrecision = suggestedDuePrecision,
        reasoning = reasoning,
    )
}

@Serializable
data class ParsedThoughtItemDto(
    val type: String,
    val title: String,
    val description: String = "",
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("due_precision") val duePrecision: String? = null,
    @SerialName("recurrence_kind") val recurrenceKind: String? = null,
    val weekdays: List<Int> = emptyList(),
    @SerialName("tracking_kind") val trackingKind: String? = null,
    @SerialName("target_value") val targetValue: Double? = null,
    @SerialName("target_unit") val targetUnit: String = "",
) {
    fun toDomain(): ParsedThoughtItem = ParsedThoughtItem(
        type = type,
        title = title,
        description = description,
        dueAt = dueAt,
        duePrecision = duePrecision,
        recurrenceKind = recurrenceKind,
        weekdays = weekdays,
        trackingKind = trackingKind,
        targetValue = targetValue,
        targetUnit = targetUnit,
    )
}

@Serializable
data class ThoughtParserResponseDto(
    val items: List<ParsedThoughtItemDto> = emptyList(),
)

@Serializable
data class WeeklyAiFactsCommitmentsDto(
    val total: Int = 0,
    val completed: Int = 0,
    val missed: Int = 0,
)

@Serializable
data class WeeklyAiFactsGoalsDto(
    @SerialName("active_goals_count") val activeGoalsCount: Int = 0,
    @SerialName("check_ins_past_7_days") val checkInsPast7Days: Int = 0,
)

@Serializable
data class WeeklyAiFactsDto(
    val period: String = "past_7_days",
    val commitments: WeeklyAiFactsCommitmentsDto = WeeklyAiFactsCommitmentsDto(),
    val goals: WeeklyAiFactsGoalsDto = WeeklyAiFactsGoalsDto(),
)

@Serializable
data class WeeklyAiInsightsContentDto(
    val summary: String,
    @SerialName("key_patterns") val keyPatterns: List<String> = emptyList(),
    @SerialName("constructive_suggestion") val constructiveSuggestion: String,
)

@Serializable
data class WeeklyAiInsightsDto(
    val facts: WeeklyAiFactsDto,
    val insights: WeeklyAiInsightsContentDto,
) {
    fun toDomain(): WeeklyAiInsights = WeeklyAiInsights(
        facts = WeeklyAiFacts(
            period = facts.period,
            totalCommitments = facts.commitments.total,
            completedCommitments = facts.commitments.completed,
            missedCommitments = facts.commitments.missed,
            activeGoalsCount = facts.goals.activeGoalsCount,
            checkInsPast7Days = facts.goals.checkInsPast7Days,
        ),
        insights = WeeklyAiInsightsContent(
            summary = insights.summary,
            keyPatterns = insights.keyPatterns,
            constructiveSuggestion = insights.constructiveSuggestion,
        ),
    )
}

@Serializable
data class PlannerRequestDto(
    val prompt: String? = null,
    @SerialName("commitment_ids") val commitmentIds: List<String> = emptyList(),
)

@Serializable
data class PlannedOrderItemDto(
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("suggested_time_slot") val suggestedTimeSlot: String,
    @SerialName("priority_rank") val priorityRank: Int,
    val note: String = "",
) {
    fun toDomain(): PlannedOrderItem = PlannedOrderItem(
        commitmentId = commitmentId,
        suggestedTimeSlot = suggestedTimeSlot,
        priorityRank = priorityRank,
        note = note,
    )
}

@Serializable
data class PlannerResponseDto(
    @SerialName("planned_order") val plannedOrder: List<PlannedOrderItemDto> = emptyList(),
    @SerialName("summary_advice") val summaryAdvice: String = "",
) {
    fun toDomain(): PlanningSuggestion = PlanningSuggestion(
        plannedOrder = plannedOrder.map { it.toDomain() },
        summaryAdvice = summaryAdvice,
    )
}

@Serializable
data class ReflectionRequestDto(
    @SerialName("item_type") val itemType: String,
    @SerialName("item_id") val itemId: String? = null,
    val notes: String? = null,
)

@Serializable
data class ReflectionResponseDto(
    @SerialName("reflection_summary") val reflectionSummary: String,
    @SerialName("suggested_adjustments") val suggestedAdjustments: List<String> = emptyList(),
    @SerialName("smaller_next_action") val smallerNextAction: String,
) {
    fun toDomain(): ReflectionCoaching = ReflectionCoaching(
        reflectionSummary = reflectionSummary,
        suggestedAdjustments = suggestedAdjustments,
        smallerNextAction = smallerNextAction,
    )
}

@Serializable
data class SharedGoalAiSummaryContentDto(
    @SerialName("group_summary") val groupSummary: String,
    @SerialName("collective_completion_rate") val collectiveCompletionRate: String,
    val encouragement: String,
)

@Serializable
data class SharedGoalWeeklySummaryDto(
    val summary: SharedGoalAiSummaryContentDto,
) {
    fun toDomain(): SharedGoalAiSummary = SharedGoalAiSummary(
        groupSummary = summary.groupSummary,
        collectiveCompletionRate = summary.collectiveCompletionRate,
        encouragement = summary.encouragement,
    )
}

@Serializable
data class ChatSummaryRequestDto(
    val limit: Int = 50,
)

@Serializable
data class GoalChatAiSummaryDto(
    val summary: String,
    @SerialName("key_decisions") val keyDecisions: List<String> = emptyList(),
    @SerialName("agreed_actions") val agreedActions: List<String> = emptyList(),
    @SerialName("important_dates") val importantDates: List<String> = emptyList(),
) {
    fun toDomain(): GoalChatAiSummary = GoalChatAiSummary(
        summary = summary,
        keyDecisions = keyDecisions,
        agreedActions = agreedActions,
        importantDates = importantDates,
    )
}
