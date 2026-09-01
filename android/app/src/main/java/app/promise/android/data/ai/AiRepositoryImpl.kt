package app.promise.android.data.ai

import app.promise.android.data.network.toApiException
import app.promise.android.domain.AiRepository
import app.promise.android.domain.CommitmentRefinement
import app.promise.android.domain.DailyMotivationQuote
import app.promise.android.domain.GoalChatAiSummary
import app.promise.android.domain.GoalSuggestion
import app.promise.android.domain.ParsedThoughtItem
import app.promise.android.domain.PlanningSuggestion
import app.promise.android.domain.ReflectionCoaching
import app.promise.android.domain.SharedGoalAiSummary
import app.promise.android.domain.SupportBotAnswer
import app.promise.android.domain.SupportBotMessage
import app.promise.android.domain.WeeklyAiInsights
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val api: AiApi,
) : AiRepository {

    override suspend fun suggestGoal(prompt: String, timezone: String): GoalSuggestion = try {
        api.suggestGoal(AiPromptRequestDto(prompt = prompt, timezone = timezone)).toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun refineCommitment(prompt: String, timezone: String): CommitmentRefinement = try {
        api.refineCommitment(AiPromptRequestDto(prompt = prompt, timezone = timezone)).toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun parseThought(thought: String, timezone: String): List<ParsedThoughtItem> = try {
        api.parseThought(AiThoughtRequestDto(thought = thought, timezone = timezone)).items.map { it.toDomain() }
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun getWeeklyInsights(): WeeklyAiInsights = try {
        api.getWeeklyInsights().toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun getDailyMotivation(): DailyMotivationQuote = try {
        api.getDailyMotivation().toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun planCommitments(prompt: String?, commitmentIds: List<String>): PlanningSuggestion = try {
        api.planCommitments(PlannerRequestDto(prompt = prompt, commitmentIds = commitmentIds)).toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun reflectOnItem(itemType: String, itemId: String?, notes: String?): ReflectionCoaching = try {
        api.reflectOnItem(ReflectionRequestDto(itemType = itemType, itemId = itemId, notes = notes)).toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun getSharedGoalSummary(goalId: String): SharedGoalAiSummary = try {
        api.getSharedGoalSummary(goalId).toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun summarizeChat(goalId: String, limit: Int): GoalChatAiSummary = try {
        api.summarizeChat(goalId, ChatSummaryRequestDto(limit = limit)).toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }

    override suspend fun askSupportBot(
        question: String,
        conversationHistory: List<SupportBotMessage>,
        timezone: String,
    ): SupportBotAnswer = try {
        val historyDtos = conversationHistory.map { SupportBotMessageDto.fromDomain(it) }
        api.askSupportBot(
            SupportBotRequestDto(
                question = question,
                conversationHistory = historyDtos,
                timezone = timezone,
            )
        ).toDomain()
    } catch (e: Exception) {
        throw e.toApiException()
    }
}
