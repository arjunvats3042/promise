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
                    text = "👋 **Hi! I'm here to help you get the most out of Promise.**\n\nAsk me anything about building daily habits, setting deadlines, group goals, offline sync, or your privacy.",
                    followups = listOf(
                        "Difference between habits and tasks?",
                        "How do I build a new habit?",
                        "Can friends see my private tasks?",
                        "How does offline sync work?",
                        "How do shared goals work?",
                    ),
                    actionChips = listOf(
                        SupportActionChip("＋ New Goal", "CREATE_GOAL"),
                        SupportActionChip("＋ New Commitment", "CREATE_COMMITMENT"),
                        SupportActionChip("🎨 Appearance", "OPEN_THEME"),
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

        // 1. Teammate Privacy ("Can teammates see my private commitments?", "Are commitments private from friends?")
        if (lower.contains("private commitment") || lower.contains("see my private") || lower.contains("teammate see") || lower.contains("friends see") || lower.contains("visible to friends") || lower.contains("visible to teammates") || lower.contains("privacy with friends") || (lower.contains("teammate") && lower.contains("private")) || (lower.contains("friend") && lower.contains("private")) || lower.contains("can teammates see")) {
            return SupportBotAnswer(
                answer = "**Strict Privacy Guarantee:**\n\n**No, teammates can NEVER see your private commitments or personal goals.**\n\n- **What Teammates See**: Only check-in completions, group streaks, and messages inside that specific **Shared Goal** room.\n- **What Stays 100% Private**: All your one-time commitments, personal habit practices, private notes, and AI thought captures are visible strictly to you alone.",
                suggestedFollowups = listOf(
                    "What is the difference between a Personal Goal and a Shared Goal?",
                    "How do Shared Goals work?",
                    "What data is shared with AI?",
                ),
                actionChips = listOf(
                    SupportActionChip("👥 Shared Goals", "OPEN_SHARED_GOALS"),
                    SupportActionChip("👤 Profile Settings", "OPEN_PROFILE"),
                ),
                isOffTopic = false,
            )
        }

        // 2. Goal vs Shared Goal ("goal vs shared goal", "difference between personal and shared")
        if ((lower.contains("goal") && lower.contains("shared") && (lower.contains("vs") || lower.contains("difference") || lower.contains("kya antar") || lower.contains("farak"))) || lower.contains("personal vs shared") || lower.contains("goal vs shared goal")) {
            return SupportBotAnswer(
                answer = "**Personal Goals vs. Shared Goals:**\n\n- **Personal Goal**: A private, individual habit tracked exclusively by you (e.g., *'Morning meditation 10 min'*, *'Read 20 pages'*). Nobody else has access to your progress.\n- **Shared Goal**: A collaborative group habit where you invite friends or teammates by email. Everyone checks in together, maintains a shared group streak, and discusses progress in a private room.\n\n*Note: Even in a Shared Goal, your personal commitments and other goals remain completely private.*",
                suggestedFollowups = listOf(
                    "Can teammates see my private commitments?",
                    "How do I invite teammates to a Shared Goal?",
                    "How does the group chat summary work?",
                ),
                actionChips = listOf(
                    SupportActionChip("👥 Shared Goals", "OPEN_SHARED_GOALS"),
                    SupportActionChip("＋ Create Goal", "CREATE_GOAL"),
                ),
                isOffTopic = false,
            )
        }

        // 3. What is Promise / App Overview ("what is promise", "about promise", "what does this app do")
        if (lower.contains("what is promise") || lower.contains("about promise") || lower.contains("what is this app") || lower.contains("how does promise work") || lower.contains("overview") || lower.contains("what can promise do") || lower == "promise") {
            return SupportBotAnswer(
                answer = "**Welcome to Promise!**\n\nPromise is a calm, intentional, local-first productivity system designed to turn your daily intentions into reality:\n\n- **Commitments**: One-time tasks with precision deadlines (Minute/Hour/Day) and zero-overdue tracking.\n- **Goals**: Recurring daily/weekly practices (habits) with streak tracking and interactive monthly history calendars.\n- **Shared Goals**: Group habits with teammates, shared streak accountability, and private room chat.\n- **Voice AI Thought Capture**: Speak or type freely in English, Hindi, or Hinglish to parse ideas into tasks in 1 tap.\n- **100% Offline-First**: Instant Room database storage with automatic background cloud sync.",
                suggestedFollowups = listOf(
                    "What is the difference between a Goal and a Commitment?",
                    "How do Shared Goals work?",
                    "How does Voice Quick Capture work?",
                ),
                actionChips = listOf(
                    SupportActionChip("＋ Create Commitment", "CREATE_COMMITMENT"),
                    SupportActionChip("＋ Create Goal", "CREATE_GOAL"),
                    SupportActionChip("🎙️ Voice Capture", "OPEN_VOICE_CAPTURE"),
                ),
                isOffTopic = false,
            )
        }

        // 4. Uninstall / Remove App
        if (lower.contains("uninstall") || lower.contains("remove app") || lower.contains("delete app") || lower.contains("wipe promise") || lower.contains("remove promise")) {
            return SupportBotAnswer(
                answer = "**How to uninstall Promise and clear your data:**\n\n1. **Wipe Cloud & Local Data (Recommended)**: Open **Profile** → scroll down to **Account Management** → tap **Delete account** (red button). This permanently anonymizes your account, cancels all active promises, and clears local/cloud data.\n2. **Uninstall App**: Return to your Android home screen or app drawer, long-press the **Promise** icon, and tap **Uninstall**.",
                suggestedFollowups = listOf(
                    "How do I delete my account?",
                    "Is my data private from AI?",
                    "What is the difference between a Goal and a Commitment?",
                ),
                actionChips = listOf(
                    SupportActionChip("👤 Account Management", "OPEN_PROFILE"),
                ),
                isOffTopic = false,
            )
        }

        // 5. Quit / Pause / Delete / Archive Goal ("quit goal", "pause goal", "archive goal", "stop habit", "delete a habit")
        if (lower.contains("quit") || lower.contains("pause goal") || lower.contains("stop goal") || lower.contains("stop habit") || lower.contains("delete goal") || lower.contains("leave goal") || lower.contains("archive goal") || lower.contains("remove goal") || lower.contains("delete habit") || lower.contains("delete a habit") || lower.contains("remove habit")) {
            return SupportBotAnswer(
                answer = "**Managing, Pausing, or Quitting a Goal:**\n\n1. Open the **Goals** tab from the bottom navigation bar.\n2. Tap on the goal you want to modify to open its details.\n3. Tap the **Settings (⚙️)** or options menu in the top right.\n4. You have two options:\n   - **Pause Goal**: Keeps your past streak and check-in history intact while pausing daily reminders.\n   - **Delete Goal / Leave Room**: Permanently removes the goal (or leaves the group if it's a Shared Goal).",
                suggestedFollowups = listOf(
                    "How do Shared Goals work?",
                    "How do check-in streaks work?",
                    "What is the difference between a Goal and a Commitment?",
                ),
                actionChips = listOf(
                    SupportActionChip("🎯 View Goals", "OPEN_SHARED_GOALS"),
                ),
                isOffTopic = false,
            )
        }

        // 6. Group Chat AI Summary
        if (lower.contains("chat summary") || lower.contains("group chat summary") || lower.contains("ai summary in chat") || lower.contains("summarize chat") || lower.contains("summary work")) {
            return SupportBotAnswer(
                answer = "**How Shared Goal AI Chat Summary Works:**\n\n1. Open any **Shared Goal** from your Goals tab.\n2. Tap into the **Room Chat**.\n3. Tap the **✨ AI Summary** chip at the top.\n4. Promise AI analyzes recent member check-ins, updates, and messages to give you a 2-sentence recap of team discussions, key milestones, and open questions without needing to scroll through hundreds of messages.",
                suggestedFollowups = listOf(
                    "How do Shared Goals work?",
                    "Can teammates see my private commitments?",
                    "What data is shared with AI?",
                ),
                actionChips = listOf(
                    SupportActionChip("👥 Shared Goals", "OPEN_SHARED_GOALS"),
                ),
                isOffTopic = false,
            )
        }

        // 7. AI Privacy & Data Sharing ("What data is shared with AI?", "Is my data private?")
        if (lower.contains("shared with ai") || lower.contains("data shared with ai") || lower.contains("is my data private from ai") || lower.contains("ai model training") || lower.contains("ai privacy") || (lower.contains("data") && lower.contains("ai"))) {
            return SupportBotAnswer(
                answer = "**AI Privacy & Zero Model Training:**\n\n- **Zero Training**: Your thoughts, commitments, and account details are **never** used to train public or proprietary AI models.\n- **Ephemeral Processing**: When you use Voice Quick Capture or AI Goal Builder, text is sent securely to Google Gemini solely to return structured suggestions in that moment, and is never retained for training.\n- **Local-First Fallback**: If offline, Promise utilizes on-device deterministic heuristics without sending any data over the network.",
                suggestedFollowups = listOf(
                    "How does Voice Quick Capture work?",
                    "How does offline sync work?",
                    "How do I delete my account?",
                ),
                actionChips = listOf(
                    SupportActionChip("👤 Account Management", "OPEN_PROFILE"),
                ),
                isOffTopic = false,
            )
        }

        // 8. Goal Creation Guide ("Can you create a goal", "How to create a goal")
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

        // 9. Commitment Creation Guide ("Can you create a commitment", "How to create commitment")
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

        // 10. Check-ins & Habit Streaks
        if (lower.contains("check-in") || lower.contains("check in") || lower.contains("streak") || lower.contains("streaks") || lower.contains("complete goal") || lower.contains("mark done") || lower.contains("checkin")) {
            return SupportBotAnswer(
                answer = "**How to check in and build streaks:**\n\n- **From Home Feed**: Tap the check-in circle next to the goal on your today's feed.\n- **From Goals Tab**: Tap the goal card, enter your progress (if count-based), and submit.\n- **From Home Screen Widget**: Tap the check-in circle directly on your Android Glance widget without opening the app!\n- **Streak Progression**: Each on-time check-in maintains your active streak and logs proof on your monthly calendar.",
                suggestedFollowups = listOf(
                    "How do Shared Goals work?",
                    "How does the History Calendar work?",
                    "What is the difference between a Goal and a Commitment?",
                ),
                actionChips = listOf(
                    SupportActionChip("🎯 View Goals", "OPEN_SHARED_GOALS"),
                ),
                isOffTopic = false,
            )
        }

        // 11. Account Deletion (Direct, Step-by-Step)
        if ((lower.contains("account") && (lower.contains("delete") || lower.contains("remove") || lower.contains("wipe") || lower.contains("close"))) || lower.contains("account delete") || lower.contains("khatam") || lower.contains("remove account") || ((lower.contains("delete") || lower.contains("wipe")) && !lower.contains("goal") && !lower.contains("habit") && !lower.contains("task") && !lower.contains("commitment"))) {
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

        // 12. Commitment vs Goal
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

        // 13. Shared Goals & Invites
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

        // 14. Offline sync & Local-first
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

        // 15. Home Screen Glance Widgets
        if (lower.contains("widget") || lower.contains("home screen")) {
            return SupportBotAnswer(
                answer = "**Adding Promise Home Screen Widgets:**\n\n1. Long-press any empty space on your Android home screen.\n2. Select **Widgets** and scroll to **Promise**.\n3. Choose from:\n   - **Goal Check-Ins**: 1-tap habit check-in directly from your launcher.\n   - **Commitments**: High-priority task list with overdue indicators.\n   - **Voice Capture**: 1-tap floating mic to record speech into structured tasks.",
                suggestedFollowups = listOf(
                    "How does offline sync work?",
                    "How does Voice / Brain dump parsing work?",
                    "When does Promise send notifications?",
                ),
                actionChips = listOf(
                    SupportActionChip("🎯 View Goals", "OPEN_SHARED_GOALS"),
                ),
                isOffTopic = false,
            )
        }

        // 16. Theme / Appearance
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

        // 17. Voice Quick Capture
        if (lower.contains("voice") || lower.contains("mic") || lower.contains("audio") || lower.contains("bol ke")) {
            return SupportBotAnswer(
                answer = "**How Voice Quick Capture Works:**\n\n1. Tap the floating **Mic** icon on the Home screen or widget.\n2. Speak naturally in English, Hindi, or Hinglish (e.g., *'Send report tomorrow 11am and workout every weekend'*).\n3. Promise AI automatically extracts deadlines and frequencies, letting you create commitments and goals in 1 tap!",
                suggestedFollowups = listOf(
                    "What is the difference between a Goal and a Commitment?",
                    "How do Shared Goals work?",
                    "How does offline sync work?",
                ),
                actionChips = listOf(
                    SupportActionChip("🎙️ Voice Capture", "OPEN_VOICE_CAPTURE"),
                ),
                isOffTopic = false,
            )
        }

        // 18. Notifications & Quiet Hours
        if (lower.contains("notification") || lower.contains("reminder") || lower.contains("quiet hours") || lower.contains("quiet time") || lower.contains("alert") || lower.contains("kab bhejta")) {
            return SupportBotAnswer(
                answer = "**Notifications & Quiet Hours:**\n\n- **Calm Alerts**: Promise sends timely reminders for morning overviews, imminent deadlines (15m before & due time), and evening streak protection.\n- **Quiet Hours**: Configure undisturbed quiet hours from **Profile → Notifications** to mute alerts during rest hours.",
                suggestedFollowups = listOf(
                    "How does offline sync work?",
                    "What is the difference between a Goal and a Commitment?",
                    "How do Shared Goals work?",
                ),
                actionChips = listOf(
                    SupportActionChip("🔔 Notification Settings", "OPEN_NOTIFICATIONS"),
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
