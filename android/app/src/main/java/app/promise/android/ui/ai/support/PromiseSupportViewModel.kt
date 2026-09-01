package app.promise.android.ui.ai.support

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.AiRepository
import app.promise.android.domain.SupportBotAnswer
import app.promise.android.domain.SupportBotMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SupportChatItem(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val text: String,
    val followups: List<String> = emptyList(),
    val isOffTopic: Boolean = false,
    val isLoading: Boolean = false,
)

data class PromiseSupportUiState(
    val messages: List<SupportChatItem> = emptyList(),
    val isGenerating: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
)

@HiltViewModel
class PromiseSupportViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val authSession: AuthSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PromiseSupportUiState(
            messages = listOf(
                SupportChatItem(
                    id = "welcome-init",
                    role = "assistant",
                    text = "👋 **Hi there! I'm your Promise Concierge.**\n\nAsk me anything about managing commitments, building recurring habits, shared goals, offline sync, home screen widgets, voice capture, or data privacy!",
                    followups = listOf(
                        "Goal vs. Commitment difference",
                        "How does offline sync work?",
                        "How do Shared Goals work?",
                        "Is my data private from AI training?",
                    ),
                    isOffTopic = false,
                )
            )
        )
    )
    val uiState: StateFlow<PromiseSupportUiState> = _uiState.asStateFlow()

    fun updateInputText(newText: String) {
        _uiState.update { it.copy(inputText = newText, error = null) }
    }

    fun sendMessage(promptText: String? = null) {
        val textToSend = (promptText ?: _uiState.value.inputText).trim()
        if (textToSend.isBlank() || _uiState.value.isGenerating) return

        val userMessage = SupportChatItem(
            role = "user",
            text = textToSend,
        )
        val loadingMessage = SupportChatItem(
            role = "assistant",
            text = "",
            isLoading = true,
        )

        val updatedList = _uiState.value.messages + userMessage + loadingMessage
        _uiState.update {
            it.copy(
                messages = updatedList,
                inputText = "",
                isGenerating = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val timezone = authSession.user.value?.timezone ?: "Asia/Kolkata"

                // Prepare conversation history (exclude loading placeholder)
                val history = updatedList
                    .dropLast(1)
                    .map { SupportBotMessage(role = it.role, text = it.text) }

                val response: SupportBotAnswer = aiRepository.askSupportBot(
                    question = textToSend,
                    conversationHistory = history,
                    timezone = timezone,
                )

                val assistantMessage = SupportChatItem(
                    id = loadingMessage.id,
                    role = "assistant",
                    text = response.answer,
                    followups = response.suggestedFollowups,
                    isOffTopic = response.isOffTopic,
                    isLoading = false,
                )

                _uiState.update { state ->
                    val finalMessages = state.messages.map {
                        if (it.id == loadingMessage.id) assistantMessage else it
                    }
                    state.copy(
                        messages = finalMessages,
                        isGenerating = false,
                    )
                }
            } catch (e: Exception) {
                // Fallback graceful answer on network error
                val fallbackAnswer = SupportChatItem(
                    id = loadingMessage.id,
                    role = "assistant",
                    text = "Promise is your calm, local-first commitment management & habit tracking system.\n\nYour commitments and habits are saved locally and sync quietly when online. Ask me about commitments, goals, widgets, or privacy!",
                    followups = listOf(
                        "Goal vs. Commitment difference",
                        "How does offline sync work?",
                        "How do Shared Goals work?",
                    ),
                    isOffTopic = false,
                    isLoading = false,
                )
                _uiState.update { state ->
                    val finalMessages = state.messages.map {
                        if (it.id == loadingMessage.id) fallbackAnswer else it
                    }
                    state.copy(
                        messages = finalMessages,
                        isGenerating = false,
                    )
                }
            }
        }
    }

    fun clearConversation() {
        _uiState.update {
            PromiseSupportUiState(
                messages = listOf(
                    SupportChatItem(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        text = "👋 **Conversation reset!**\n\nHow can I help you with Promise today?",
                        followups = listOf(
                            "Goal vs. Commitment difference",
                            "How does offline sync work?",
                            "How do Shared Goals work?",
                            "Is my data private from AI training?",
                        ),
                    )
                ),
                inputText = "",
                isGenerating = false,
            )
        }
    }
}
