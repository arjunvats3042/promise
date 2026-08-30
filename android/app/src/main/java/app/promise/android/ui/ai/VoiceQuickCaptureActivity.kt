package app.promise.android.ui.ai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.core.speech.SpeechRecognitionManager
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.AiRepository
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.ParsedThoughtItem
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.theme.AccentSession
import app.promise.android.ui.theme.PromiseTheme
import app.promise.android.ui.theme.ThemeController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VoiceQuickCaptureActivity : ComponentActivity() {

    @Inject
    lateinit var themeController: ThemeController

    @Inject
    lateinit var accentSession: AccentSession

    @Inject
    lateinit var speechManager: SpeechRecognitionManager

    @Inject
    lateinit var aiRepository: AiRepository

    @Inject
    lateinit var commitmentRepository: CommitmentRepository

    @Inject
    lateinit var goalRepository: GoalRepository

    @Inject
    lateinit var appEventBus: AppEventBus

    @Inject
    lateinit var haptics: PromiseHaptics

    @Inject
    lateinit var authSession: AuthSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        haptics.selection()

        setContent {
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) {
                themeController.syncSystem(systemDark)
            }
            val mode by themeController.mode.collectAsStateWithLifecycle()
            val sessionAccent by accentSession.accent.collectAsStateWithLifecycle()

            PromiseTheme(mode = mode, sessionAccent = sessionAccent) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VoiceCaptureSheet(
                        speechManager = speechManager,
                        onDismiss = { finish() },
                        onParseThought = { thought -> parseThought(thought) },
                        onCreateCommitment = { input -> createCommitment(input) },
                        onCreateGoal = { input -> createGoal(input) },
                        titleText = "Quick Voice Promise",
                        subtitleText = "Speak your task, deadline, or habit to add it instantly.",
                    )
                }
            }
        }
    }

    private suspend fun parseThought(thought: String): List<ParsedThoughtItem> {
        val timezone = authSession.user.value?.timezone?.takeIf { it.isNotBlank() } ?: "Asia/Kolkata"
        return aiRepository.parseThought(thought, timezone)
    }

    private suspend fun createCommitment(input: CreateCommitmentInput) {
        try {
            val created = commitmentRepository.create(input)
            haptics.confirm()
            appEventBus.emit(AppMutationEvent.CommitmentCreated(created.id))
            Toast.makeText(this@VoiceQuickCaptureActivity, "Promise created: ${input.title}", Toast.LENGTH_SHORT).show()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            haptics.error()
            Toast.makeText(this@VoiceQuickCaptureActivity, "Failed to create promise", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun createGoal(input: CreateGoalInput) {
        try {
            val created = goalRepository.create(input)
            haptics.confirm()
            appEventBus.emit(AppMutationEvent.GoalCreated(created.id))
            Toast.makeText(this@VoiceQuickCaptureActivity, "Goal created: ${input.title}", Toast.LENGTH_SHORT).show()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            haptics.error()
            Toast.makeText(this@VoiceQuickCaptureActivity, "Failed to create goal", Toast.LENGTH_SHORT).show()
        }
    }
}
