package app.promise.android.domain

interface AiRepository {
    suspend fun suggestGoal(prompt: String, timezone: String = "Asia/Kolkata"): GoalSuggestion
    suspend fun refineCommitment(prompt: String, timezone: String = "Asia/Kolkata"): CommitmentRefinement
    suspend fun parseThought(thought: String, timezone: String = "Asia/Kolkata"): List<ParsedThoughtItem>
    suspend fun getWeeklyInsights(): WeeklyAiInsights
    suspend fun getDailyMotivation(): DailyMotivationQuote
    suspend fun planCommitments(prompt: String? = null, commitmentIds: List<String> = emptyList()): PlanningSuggestion
    suspend fun reflectOnItem(itemType: String, itemId: String? = null, notes: String? = null): ReflectionCoaching
    suspend fun getSharedGoalSummary(goalId: String): SharedGoalAiSummary
    suspend fun summarizeChat(goalId: String, limit: Int = 50): GoalChatAiSummary
}
