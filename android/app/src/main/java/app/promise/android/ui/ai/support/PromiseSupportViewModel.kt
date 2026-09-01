package app.promise.android.ui.ai.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.AiRepository
import app.promise.android.domain.SupportActionChip
import app.promise.android.domain.SupportBotAnswer
import app.promise.android.domain.SupportBotMessage
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val actionChips: List<SupportActionChip> = emptyList(),
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
                        "How can I create a goal?",
                        "How does offline sync work?",
                        "How do Shared Goals work?",
                        "Is my data private from AI training?",
                    ),
                    actionChips = listOf(
                        SupportActionChip("＋ Create Goal", "CREATE_GOAL"),
                        SupportActionChip("＋ Create Commitment", "CREATE_COMMITMENT"),
                        SupportActionChip("🎨 Appearance Settings", "OPEN_THEME"),
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

    /**
     * Instant 0ms local knowledge resolution for common app queries and suggestions.
     */
    private fun getInstantLocalAnswer(query: String): SupportBotAnswer? {
        val lower = query.lowercase().trim()

        // 1. Goal Creation
        if ((lower.contains("goal") || lower.contains("habit")) && listOf("create", "make", "add", "build", "set up", "setup", "banaye", "karein", "karna").any { lower.contains(it) } || lower.contains("create goal") || lower.contains("make goal")) {
            return SupportBotAnswer(
                answer = "I'm your Promise concierge guide! While I cannot directly create goals inside your account from this chat window, you can easily create goals in two quick ways:\n\n**Method 1: Manual Create (+ button)**\n1. Tap the **+** (Create) button on the bottom bar or Home tab.\n2. Select **Goal** (for recurring daily or weekly practices).\n3. Choose your frequency (**Daily**, **Specific Weekdays**, or **X times per period**).\n4. Set your target (yes/no check-in or count like pages/minutes) and tap **Save**.\n\n**Method 2: Voice Quick Capture (AI Thought Dump)**\n1. Tap the **Mic / Voice** button on your Home screen or widget.\n2. Speak or type freely in English, Hindi, or Hinglish (e.g., *'Roz subah 6 baje yoga karna hai'*).\n3. Promise AI parses your thought into a structured goal card for you to confirm in 1 tap!",
                suggestedFollowups = listOf(
                    "What is the difference between a Goal and a Commitment?",
                    "How does Voice Quick Capture work?",
                    "How do Shared Goals work?",
                ),
                actionChips = listOf(
                    SupportActionChip("＋ Create Goal", "CREATE_GOAL"),
                    SupportActionChip("🎙️ Voice Capture", "OPEN_VOICE_CAPTURE"),
                ),
                isOffTopic = false,
            )
        }

        // 2. Commitment Creation
        if ((lower.contains("commitment") || lower.contains("task")) && listOf("create", "make", "add", "set up", "setup", "schedule", "new", "banaye", "karein").any { lower.contains(it) } || lower.contains("create commitment")) {
            return SupportBotAnswer(
                answer = "I'm your Promise concierge guide! While I cannot directly add tasks to your account from this chat, you can create commitments in two easy ways:\n\n**Method 1: Manual Create (+ button)**\n1. Tap the **+** (Create) button on the bottom bar.\n2. Select **Commitment** (for one-time tasks with deadlines).\n3. Enter the title, set an optional deadline, and choose your alert timing.\n4. Tap **Save**.\n\n**Method 2: Voice Quick Capture (AI Thought Dump)**\n1. Tap the **Mic / Voice** button.\n2. Speak or type in English, Hindi, or Hinglish (e.g., *'Kal dopahar 3 baje presentation bhejna hai'*).\n3. Promise AI automatically parses the deadline and creates a commitment card for your confirmation.",
                suggestedFollowups = listOf(
                    "What is the difference between a Goal and a Commitment?",
                    "When does Promise send notifications?",
                    "How does offline sync work?",
                ),
                actionChips = listOf(
                    SupportActionChip("＋ Create Commitment", "CREATE_COMMITMENT"),
                    SupportActionChip("🎙️ Voice Capture", "OPEN_VOICE_CAPTURE"),
                ),
                isOffTopic = false,
            )
        }

        // 3. Goal vs Commitment
        if (lower.contains("difference") || (lower.contains("commitment") && lower.contains("goal")) || lower.contains("kya antar") || lower.contains("farak") || lower.contains("farq")) {
            return SupportBotAnswer(
                answer = "**Commitments vs. Goals in Promise:**\n\n- **Commitments**: One-time accountable tasks with optional deadlines (e.g., *'Submit project report by Friday 5 PM'*). They have precision timing and urgency badges (**Overdue**, **Imminent**, **Upcoming**).\n- **Goals**: Recurring daily or weekly practices designed to build lasting consistency (e.g., *'Read 20 pages daily'*, *'Gym 4x/week'*). Supports yes/no check-ins or numeric targets with streak tracking.",
                suggestedFollowups = listOf(
                    "How do Shared Goals work?",
                    "How does Voice / Brain dump parsing work?",
                    "How do check-in streaks work?",
                ),
                actionChips = listOf(
                    SupportActionChip("＋ Create Goal", "CREATE_GOAL"),
                    SupportActionChip("＋ Create Commitment", "CREATE_COMMITMENT"),
                ),
                isOffTopic = false,
            )
        }

        // 4. Offline Sync
        if (lower.contains("offline") || lower.contains("sync") || lower.contains("bina internet") || lower.contains("no internet")) {
            return SupportBotAnswer(
                answer = "**Local-First Architecture & Offline Sync:**\n\n- **100% Offline Capability**: You can create commitments, complete habit check-ins, and use the Home Screen widget without internet connection. Everything is saved instantly to your device's local database.\n- **Automatic Sync**: As soon as internet connectivity returns, Promise silently syncs all pending changes to the cloud in the background.",
                suggestedFollowups = listOf(
                    "How do I add the Promise Widget to my Home Screen?",
                    "What data is shared with AI?",
                    "When does Promise send notifications?",
                ),
                actionChips = listOf(
                    SupportActionChip("＋ Create Commitment", "CREATE_COMMITMENT"),
                ),
                isOffTopic = false,
            )
        }

        // 5. Shared Goals
        if (lower.contains("shared") || lower.contains("invite") || lower.contains("friend") || lower.contains("teammate") || lower.contains("partner")) {
            return SupportBotAnswer(
                answer = "**How Shared Goals Work:**\n\n1. **Open or Create a Goal**: Tap on any goal from your Goals list, or create a new one.\n2. **Invite Teammates**: Tap **Invite** and enter your teammate's registered Promise email.\n3. **Group Accountability**: Once accepted, all members check in together, track group streaks, and discuss progress in the private group chat with member avatars.",
                suggestedFollowups = listOf(
                    "Can teammates see my private commitments?",
                    "How does the group chat summary work?",
                    "What is the difference between a Goal and a Commitment?",
                ),
                actionChips = listOf(
                    SupportActionChip("👥 Shared Goals", "OPEN_SHARED_GOALS"),
                ),
                isOffTopic = false,
            )
        }

        // 6. Account Deletion
        if (lower.contains("delete") || lower.contains("account delete") || lower.contains("khatam") || lower.contains("remove account") || lower.contains("wipe")) {
            return SupportBotAnswer(
                answer = "**How to permanently delete your account:**\n\n1. Open the **Profile** tab in the bottom navigation bar.\n2. Scroll down to the **Account Management** section.\n3. Tap **Delete account** (highlighted in red).\n4. Confirm deletion in the popup dialog.\n\n*Note: This immediately anonymizes your profile, cancels all active commitments, and permanently clears your local and cloud data.*",
                suggestedFollowups = listOf(
                    "What is the difference between a Goal and a Commitment?",
                    "How does offline sync work?",
                    "Is my data private from AI?",
                ),
                actionChips = listOf(
                    SupportActionChip("👤 Account Management", "OPEN_PROFILE"),
                ),
                isOffTopic = false,
            )
        }

        // 7. Theme / Appearance
        if (lower.contains("theme") || lower.contains("dark mode") || lower.contains("light mode") || lower.contains("appearance") || lower.contains("dark") || lower.contains("light")) {
            return SupportBotAnswer(
                answer = "**Changing App Theme (Light / Dark Mode):**\n\n1. Go to the **Profile** tab in the bottom navigation bar.\n2. Scroll to the bottom to the **Appearance** section.\n3. Use the sliding segmented controller to toggle between **Light** (warm daytime brightness) and **Dark** (deep night contrast).",
                suggestedFollowups = listOf(
                    "How do I add the Promise Widget to my Home Screen?",
                    "How do notifications work?",
                    "What is the difference between a Goal and a Commitment?",
                ),
                actionChips = listOf(
                    SupportActionChip("🎨 Appearance Settings", "OPEN_THEME"),
                ),
                isOffTopic = false,
            )
        }

        // 8. Privacy
        if (lower.contains("privacy") || lower.contains("security") || lower.contains("data") || lower.contains("google")) {
            return SupportBotAnswer(
                answer = "**Privacy & Data Security at Promise:**\n\n- **Zero AI Training**: Your private commitments, goals, and account details are **never** used to train AI models.\n- **Secure Google Authentication**: Sign in securely with Google OAuth without storing passwords.\n- **Instant Account Deletion**: You can permanently delete and anonymize your account at any time from Profile → Account Management.",
                suggestedFollowups = listOf(
                    "How do I delete my account?",
                    "How does offline sync work?",
                    "What is the difference between a Goal and a Commitment?",
                ),
                actionChips = listOf(
                    SupportActionChip("👤 Account Management", "OPEN_PROFILE"),
                ),
                isOffTopic = false,
            )
        }

        return null
    }

    fun sendMessage(promptText: String? = null) {
        val textToSend = (promptText ?: _uiState.value.inputText).trim()
        if (textToSend.isBlank() || _uiState.value.isGenerating) return

        val userMessage = SupportChatItem(
            role = "user",
            text = textToSend,
        )

        // Check for 0ms instant local resolution
        val instantAnswer = getInstantLocalAnswer(textToSend)
        if (instantAnswer != null) {
            val assistantMessage = SupportChatItem(
                role = "assistant",
                text = instantAnswer.answer,
                followups = instantAnswer.suggestedFollowups,
                actionChips = instantAnswer.actionChips,
                isOffTopic = instantAnswer.isOffTopic,
                isLoading = false,
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage + assistantMessage,
                    inputText = "",
                    isGenerating = false,
                    error = null,
                )
            }
            return
        }

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
                    actionChips = response.actionChips,
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
                val fallbackAnswer = SupportChatItem(
                    id = loadingMessage.id,
                    role = "assistant",
                    text = "I'm here exclusively as your Promise app concierge!\n\nPlease ask me anything related to using the Promise app (such as commitments, daily habits, shared goals, widgets, offline sync, voice capture, or notifications), and I'll be glad to help.",
                    followups = listOf(
                        "Goal vs. Commitment difference",
                        "How can I create a goal?",
                        "How does offline sync work?",
                        "How do Shared Goals work?",
                    ),
                    actionChips = listOf(
                        SupportActionChip("＋ Create Goal", "CREATE_GOAL"),
                        SupportActionChip("＋ Create Commitment", "CREATE_COMMITMENT"),
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
                            "How can I create a goal?",
                            "How does offline sync work?",
                            "How do Shared Goals work?",
                        ),
                        actionChips = listOf(
                            SupportActionChip("＋ Create Goal", "CREATE_GOAL"),
                            SupportActionChip("＋ Create Commitment", "CREATE_COMMITMENT"),
                            SupportActionChip("🎨 Appearance Settings", "OPEN_THEME"),
                        ),
                        isOffTopic = false,
                    )
                )
            )
        }
    }
}
