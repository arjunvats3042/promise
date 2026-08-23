package app.promise.android.data.ai

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AiApi {
    @POST("ai/goals/suggest/")
    suspend fun suggestGoal(@Body request: AiPromptRequestDto): GoalSuggestionDto

    @POST("ai/commitments/refine/")
    suspend fun refineCommitment(@Body request: AiPromptRequestDto): CommitmentRefinementDto

    @POST("ai/parse-thought/")
    suspend fun parseThought(@Body request: AiThoughtRequestDto): ThoughtParserResponseDto

    @GET("ai/insights/weekly/")
    suspend fun getWeeklyInsights(): WeeklyAiInsightsDto

    @GET("ai/motivation/today/")
    suspend fun getDailyMotivation(): DailyMotivationQuoteDto

    @POST("ai/planner/")
    suspend fun planCommitments(@Body request: PlannerRequestDto): PlannerResponseDto

    @POST("ai/reflection/")
    suspend fun reflectOnItem(@Body request: ReflectionRequestDto): ReflectionResponseDto

    @GET("ai/goals/{goalId}/summary/weekly/")
    suspend fun getSharedGoalSummary(@Path("goalId") goalId: String): SharedGoalWeeklySummaryDto

    @POST("ai/goals/{goalId}/chat/summarize/")
    suspend fun summarizeChat(
        @Path("goalId") goalId: String,
        @Body request: ChatSummaryRequestDto,
    ): GoalChatAiSummaryDto
}
