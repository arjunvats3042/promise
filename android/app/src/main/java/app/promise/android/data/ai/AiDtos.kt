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
    val status: String = "READY",
    @SerialName("clarification_question") val clarificationQuestion: String? = null,
    val title: String = "",
    val description: String = "",
    @SerialName("recurrence_kind") val recurrenceKind: String = "DAILY",
    val weekdays: List<Int> = emptyList(),
    @SerialName("period_unit") val periodUnit: String? = null,
    @SerialName("times_per_period") val timesPerPeriod: Int? = null,
    @SerialName("tracking_kind") val trackingKind: String = "BINARY",
    @SerialName("target_value") val targetValue: Double? = null,
    @SerialName("target_unit") val targetUnit: String = "",
    val reasoning: String = "",
) {
    fun toDomain(): GoalSuggestion = GoalSuggestion(
        status = status,
        clarificationQuestion = clarificationQuestion,
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
    val status: String = "READY",
    @SerialName("current_interpretation") val currentInterpretation: String = "",
    @SerialName("missing_information") val missingInformation: String? = null,
    @SerialName("clarifying_question") val clarifyingQuestion: String? = null,
    @SerialName("refined_title") val refinedTitle: String = "",
    @SerialName("refined_description") val refinedDescription: String = "",
    @SerialName("suggested_due_at") val suggestedDueAt: String? = null,
    @SerialName("suggested_due_precision") val suggestedDuePrecision: String? = null,
    val reasoning: String = "",
) {
    fun toDomain(): CommitmentRefinement = CommitmentRefinement(
        status = status,
        currentInterpretation = currentInterpretation,
        missingInformation = missingInformation,
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
    val confidence: String = "HIGH",
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
        confidence = confidence,
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
data class WeeklyAiFactsDto(
    val period: String = "past_7_days",
    @SerialName("commitments_total") val commitmentsTotal: Int = 0,
    @SerialName("commitments_completed") val commitmentsCompleted: Int = 0,
    @SerialName("commitments_overdue") val commitmentsOverdue: Int = 0,
    @SerialName("active_goals_count") val activeGoalsCount: Int = 0,
    @SerialName("check_ins_past_7_days") val checkInsPast7Days: Int = 0,
)

@Serializable
data class WeeklyAiInsightsContentDto(
    val summary: String,
    @SerialName("observed_patterns") val observedPatterns: List<String> = emptyList(),
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
            totalCommitments = facts.commitmentsTotal,
            completedCommitments = facts.commitmentsCompleted,
            missedCommitments = facts.commitmentsOverdue,
            activeGoalsCount = facts.activeGoalsCount,
            checkInsPast7Days = facts.checkInsPast7Days,
        ),
        insights = WeeklyAiInsightsContent(
            summary = insights.summary,
            observedPatterns = insights.observedPatterns,
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
    @SerialName("is_fixed_deadline") val isFixedDeadline: Boolean = false,
    val note: String = "",
) {
    fun toDomain(): PlannedOrderItem = PlannedOrderItem(
        commitmentId = commitmentId,
        suggestedTimeSlot = suggestedTimeSlot,
        priorityRank = priorityRank,
        isFixedDeadline = isFixedDeadline,
        note = note,
    )
}

@Serializable
data class PlannerResponseDto(
    @SerialName("planned_order") val plannedOrder: List<PlannedOrderItemDto> = emptyList(),
    @SerialName("conflict_notes") val conflictNotes: String? = null,
    @SerialName("summary_advice") val summaryAdvice: String = "",
) {
    fun toDomain(): PlanningSuggestion = PlanningSuggestion(
        plannedOrder = plannedOrder.map { it.toDomain() },
        conflictNotes = conflictNotes,
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
    @SerialName("open_questions") val openQuestions: List<String> = emptyList(),
) {
    fun toDomain(): GoalChatAiSummary = GoalChatAiSummary(
        summary = summary,
        keyDecisions = keyDecisions,
        agreedActions = agreedActions,
        importantDates = importantDates,
        openQuestions = openQuestions,
    )
}
