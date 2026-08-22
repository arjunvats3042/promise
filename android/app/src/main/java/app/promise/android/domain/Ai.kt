package app.promise.android.domain

import kotlinx.serialization.Serializable

@Serializable
data class GoalSuggestion(
    val status: String = "READY", // "READY" or "NEEDS_CLARIFICATION"
    val clarificationQuestion: String? = null,
    val title: String = "",
    val description: String = "",
    val recurrenceKind: String = "DAILY",
    val weekdays: List<Int> = emptyList(),
    val periodUnit: String? = null,
    val timesPerPeriod: Int? = null,
    val trackingKind: String = "BINARY",
    val targetValue: Double? = null,
    val targetUnit: String = "",
    val reasoning: String = "",
)

@Serializable
data class CommitmentRefinement(
    val status: String = "READY", // "READY" or "NEEDS_CLARIFICATION"
    val currentInterpretation: String = "",
    val missingInformation: String? = null,
    val clarifyingQuestion: String? = null,
    val refinedTitle: String = "",
    val refinedDescription: String = "",
    val suggestedDueAt: String? = null,
    val suggestedDuePrecision: String? = null,
    val reasoning: String = "",
)

@Serializable
data class ParsedThoughtItem(
    val type: String, // "commitment" or "goal"
    val title: String,
    val description: String = "",
    val confidence: String = "HIGH", // "HIGH", "MEDIUM", "LOW"
    val dueAt: String? = null,
    val duePrecision: String? = null,
    val recurrenceKind: String? = null,
    val weekdays: List<Int> = emptyList(),
    val trackingKind: String? = null,
    val targetValue: Double? = null,
    val targetUnit: String = "",
)

@Serializable
data class WeeklyAiFacts(
    val period: String = "past_7_days",
    val totalCommitments: Int = 0,
    val completedCommitments: Int = 0,
    val missedCommitments: Int = 0,
    val activeGoalsCount: Int = 0,
    val checkInsPast7Days: Int = 0,
)

@Serializable
data class WeeklyAiInsightsContent(
    val summary: String,
    val observedPatterns: List<String> = emptyList(),
    val constructiveSuggestion: String,
)

@Serializable
data class WeeklyAiInsights(
    val facts: WeeklyAiFacts,
    val insights: WeeklyAiInsightsContent,
)

@Serializable
data class ParsedCommandResult(
    val entity: String,
    val interpretedQueryPreview: String = "",
    val resultsCount: Int,
)

@Serializable
data class PlannedOrderItem(
    val commitmentId: String,
    val suggestedTimeSlot: String,
    val priorityRank: Int,
    val isFixedDeadline: Boolean = false,
    val note: String = "",
)

@Serializable
data class PlanningSuggestion(
    val plannedOrder: List<PlannedOrderItem> = emptyList(),
    val conflictNotes: String? = null,
    val summaryAdvice: String = "",
)

@Serializable
data class ReflectionCoaching(
    val reflectionSummary: String,
    val suggestedAdjustments: List<String> = emptyList(),
    val smallerNextAction: String,
)

@Serializable
data class SharedGoalAiSummary(
    val groupSummary: String,
    val collectiveCompletionRate: String,
    val encouragement: String,
)

@Serializable
data class GoalChatAiSummary(
    val summary: String,
    val keyDecisions: List<String> = emptyList(),
    val agreedActions: List<String> = emptyList(),
    val importantDates: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
)
