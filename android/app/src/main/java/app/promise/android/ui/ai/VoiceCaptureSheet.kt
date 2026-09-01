package app.promise.android.ui.ai

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.speech.SpeechRecognitionManager
import app.promise.android.core.speech.SpeechRecognitionState
import app.promise.android.core.toErrorKind
import app.promise.android.core.toUserMessage
import app.promise.android.data.network.ApiException
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.DuePrecision
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.ParsedThoughtItem
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.pressScale
import app.promise.android.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch

enum class VoicePipelineStep {
    RECORDING,
    REVIEW,
    AI_PROCESSING,
    FINALIZE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCaptureSheet(
    speechManager: SpeechRecognitionManager,
    onDismiss: () -> Unit,
    onTranscriptReady: ((String) -> Unit)? = null,
    onParseThought: (suspend (String) -> List<ParsedThoughtItem>)? = null,
    onCreateCommitment: (suspend (CreateCommitmentInput) -> Unit)? = null,
    onCreateGoal: (suspend (CreateGoalInput) -> Unit)? = null,
    titleText: String = "Voice Intent Engine",
    subtitleText: String = "Speak naturally — mentions of tasks, dates, or routines are resolved automatically.",
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val speechState by speechManager.state.collectAsStateWithLifecycle()
    var currentStep by remember { mutableStateOf(VoicePipelineStep.RECORDING) }
    var transcriptText by remember { mutableStateOf("") }
    var parsedItems by remember { mutableStateOf<List<ParsedThoughtItem>?>(null) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var hasRequestedPermissionOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            speechManager.startListening()
        }
    }

    LaunchedEffect(Unit) {
        if (!speechManager.hasRecordAudioPermission()) {
            if (!hasRequestedPermissionOnce) {
                hasRequestedPermissionOnce = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            speechManager.startListening()
        }
    }

    // Capture transcript as user speaks
    LaunchedEffect(speechState) {
        when (val state = speechState) {
            is SpeechRecognitionState.Listening -> {
                if (state.partialText.isNotBlank()) {
                    transcriptText = state.partialText
                }
            }
            is SpeechRecognitionState.Finished -> {
                if (state.finalText.isNotBlank()) {
                    transcriptText = state.finalText
                    currentStep = VoicePipelineStep.REVIEW
                }
            }
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.reset()
        }
    }

    fun startListening() {
        errorMessage = null
        transcriptText = ""
        parsedItems = null
        selectedIndices.clear()
        currentStep = VoicePipelineStep.RECORDING
        speechManager.startListening()
    }

    fun proceedToAIProcessing() {
        val query = transcriptText.trim()
        if (query.isBlank()) {
            errorMessage = "No speech detected. Please speak or write your thoughts."
            return
        }

        if (onParseThought == null) {
            onTranscriptReady?.invoke(query)
            onDismiss()
            return
        }

        currentStep = VoicePipelineStep.AI_PROCESSING
        errorMessage = null
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        scope.launch {
            try {
                val rawItems = onParseThought(query)
                val items = rawItems.map { item ->
                    if (item.type == "commitment" && item.dueAt.isNullOrBlank()) {
                        val parsed = app.promise.android.core.util.NaturalLanguageDateParser.parse(item.title)
                        val fallback = if (parsed.dueAt == null) app.promise.android.core.util.NaturalLanguageDateParser.parse(query) else parsed
                        if (fallback.dueAt != null) {
                            item.copy(
                                title = parsed.cleanedTitle.takeIf { it.isNotBlank() } ?: item.title,
                                dueAt = fallback.dueAt,
                                duePrecision = if (fallback.duePrecision == DuePrecision.DATE) "DAY" else "HOUR",
                            )
                        } else {
                            item
                        }
                    } else {
                        item
                    }
                }
                parsedItems = items
                selectedIndices.clear()
                selectedIndices.addAll(items.indices)
                currentStep = VoicePipelineStep.FINALIZE
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                val fallbackItems = app.promise.android.core.util.NaturalLanguageDateParser.decomposeAndParse(query)
                if (fallbackItems.isNotEmpty()) {
                    parsedItems = fallbackItems
                    selectedIndices.clear()
                    selectedIndices.addAll(fallbackItems.indices)
                    currentStep = VoicePipelineStep.FINALIZE
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    errorMessage = when (e) {
                        is ApiException -> e.toErrorKind().toUserMessage()
                        else -> e.localizedMessage ?: "Could not structure voice input. Please try again."
                    }
                    currentStep = VoicePipelineStep.REVIEW
                }
            }
        }
    }

    fun submitFinalItems() {
        if (isSubmitting) return
        isSubmitting = true

        val items = parsedItems ?: emptyList()
        val chosen = items.filterIndexed { index, _ -> selectedIndices.contains(index) }

        scope.launch {
            try {
                for (item in chosen) {
                    if (item.type == "commitment") {
                        val rawDueAt = item.dueAt?.takeIf { it.isNotBlank() }
                        val duePrecision = when {
                            rawDueAt.isNullOrBlank() -> DuePrecision.NONE
                            item.duePrecision?.equals("MINUTE", ignoreCase = true) == true -> DuePrecision.DATETIME
                            item.duePrecision?.equals("HOUR", ignoreCase = true) == true -> DuePrecision.DATETIME
                            item.duePrecision?.equals("DATETIME", ignoreCase = true) == true -> DuePrecision.DATETIME
                            item.duePrecision?.equals("DAY", ignoreCase = true) == true -> DuePrecision.DATE
                            item.duePrecision?.equals("DATE", ignoreCase = true) == true -> DuePrecision.DATE
                            else -> DuePrecision.DATETIME
                        }
                        val finalDueAt = if (duePrecision == DuePrecision.NONE) null else rawDueAt

                        onCreateCommitment?.invoke(
                            CreateCommitmentInput(
                                title = item.title,
                                description = item.description,
                                dueAt = finalDueAt,
                                duePrecision = duePrecision,
                            ),
                        )
                    } else {
                        onCreateGoal?.invoke(
                            CreateGoalInput(
                                title = item.title,
                                description = item.description,
                                recurrenceKind = when (item.recurrenceKind?.uppercase()) {
                                    "WEEKLY_DAYS" -> GoalRecurrenceKind.WEEKLY_DAYS
                                    "N_PER_PERIOD" -> GoalRecurrenceKind.N_PER_PERIOD
                                    else -> GoalRecurrenceKind.DAILY
                                },
                                weekdays = item.weekdays,
                                trackingKind = if (item.trackingKind?.uppercase() == "COUNT") GoalTrackingKind.COUNT else GoalTrackingKind.BINARY,
                                targetValue = item.targetValue?.toInt(),
                                targetUnit = item.targetUnit,
                            ),
                        )
                    }
                }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                errorMessage = when (e) {
                    is ApiException -> e.toErrorKind().toUserMessage()
                    else -> e.localizedMessage ?: "Failed to create items. Please try again."
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            speechManager.reset()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(top = Spacing.sm, bottom = Spacing.xl)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header Bar with AI spark and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.12f))
                            .border(1.dp, colors.accent.copy(alpha = 0.28f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = when (currentStep) {
                                VoicePipelineStep.RECORDING -> titleText
                                VoicePipelineStep.REVIEW -> "Review Spoken Words"
                                VoicePipelineStep.AI_PROCESSING -> "Decomposing Intent…"
                                VoicePipelineStep.FINALIZE -> "Create Promise(s)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = when (currentStep) {
                                VoicePipelineStep.RECORDING -> subtitleText
                                VoicePipelineStep.REVIEW -> "Check what was heard or tap Retake."
                                VoicePipelineStep.AI_PROCESSING -> "Resolving dates, deadlines, and routines…"
                                VoicePipelineStep.FINALIZE -> "Confirm items to add to your plan."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        speechManager.reset()
                        onDismiss()
                    },
                    modifier = Modifier.size(TouchTarget.min),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "voiceStepTransition",
            ) { step ->
                when (step) {
                    VoicePipelineStep.RECORDING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (!speechManager.hasRecordAudioPermission()) {
                                PermissionRequiredView(
                                    onGrantPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                )
                            } else {
                                val isListening = speechState is SpeechRecognitionState.Listening

                                Spacer(modifier = Modifier.height(Spacing.sm))

                                // Luminous Aura Microphone visualizer (100% symmetrically centered)
                                LuminousAcousticAura(
                                    isListening = isListening,
                                    rmsNormalized = (speechState as? SpeechRecognitionState.Listening)?.rmsNormalized ?: 0f,
                                    reduceMotion = reduceMotion,
                                    onToggleListening = {
                                        if (isListening) {
                                            speechManager.stopListening()
                                            if (transcriptText.isNotBlank()) {
                                                currentStep = VoicePipelineStep.REVIEW
                                            }
                                        } else {
                                            speechManager.startListening()
                                        }
                                    },
                                )

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                // Sleek 9-bar reactive audio equalizer pill below the aura
                                ReactiveAudioWaveform(
                                    isListening = isListening,
                                    rmsNormalized = (speechState as? SpeechRecognitionState.Listening)?.rmsNormalized ?: 0f,
                                    reduceMotion = reduceMotion,
                                )

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                Text(
                                    text = if (isListening) "Listening… Speak your intentions" else "Tap microphone to speak",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isListening) colors.accent else colors.textSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                Spacer(modifier = Modifier.height(Spacing.md))

                                // Glassmorphic live transcript monitor
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 96.dp, max = 160.dp)
                                        .border(
                                            1.dp,
                                            if (isListening) colors.accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            RoundedCornerShape(Radius.md),
                                        ),
                                    shape = RoundedCornerShape(Radius.md),
                                    color = colors.surfaceRaised,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.md)
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        if (transcriptText.isNotBlank()) {
                                            Text(
                                                text = transcriptText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textPrimary,
                                                lineHeight = 22.sp,
                                            )
                                        } else {
                                            Text(
                                                text = "e.g. \"Send pnd mail on first of October 11 am, and gym 4 days a week\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.textSecondary.copy(alpha = 0.55f),
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.lg))

                                Button(
                                    onClick = {
                                        speechManager.stopListening()
                                        if (transcriptText.isNotBlank()) {
                                            proceedToAIProcessing()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(TouchTarget.min)
                                        .pressScale(0.97f),
                                    shape = RoundedCornerShape(Radius.pill),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = Color.Black,
                                    ),
                                    enabled = transcriptText.isNotBlank(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        "Decompose with AI",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    VoicePipelineStep.REVIEW -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OutlinedTextField(
                                value = transcriptText,
                                onValueChange = { transcriptText = it },
                                label = { Text("Spoken Words") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(Radius.md),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f),
                                    focusedContainerColor = colors.surfaceRaised,
                                    unfocusedContainerColor = colors.surfaceRaised,
                                ),
                            )

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.lg))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        startListening()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(TouchTarget.min)
                                        .pressScale(0.97f),
                                    shape = RoundedCornerShape(Radius.pill),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Retake", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        proceedToAIProcessing()
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(TouchTarget.min)
                                        .pressScale(0.97f),
                                    shape = RoundedCornerShape(Radius.pill),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = Color.Black,
                                    ),
                                    enabled = transcriptText.isNotBlank(),
                                ) {
                                    Text("Continue", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    VoicePipelineStep.AI_PROCESSING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "processingSpin")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 0.9f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "processingPulse",
                            )

                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.15f))
                                    .border(1.5.dp, colors.accent.copy(alpha = 0.40f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(32.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.lg))

                            Text(
                                text = "Structuring your promises…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Parsing dates, fixing grammar, and scheduling tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    VoicePipelineStep.FINALIZE -> {
                        val items = parsedItems ?: emptyList()

                        if (items.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "No tasks or habits detected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = "Try speaking concrete tasks, deadlines, or daily routines.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(Spacing.lg))
                                Button(
                                    onClick = {
                                        currentStep = VoicePipelineStep.REVIEW
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(TouchTarget.min)
                                        .pressScale(0.97f),
                                    shape = RoundedCornerShape(Radius.pill),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = Color.Black,
                                    ),
                                ) {
                                    Text("Edit Spoken Words", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "PROMISES TO CREATE (${selectedIndices.size}/${items.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.accent,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                    )
                                    TextButton(
                                        onClick = {
                                            if (selectedIndices.size == items.size) {
                                                selectedIndices.clear()
                                            } else {
                                                selectedIndices.clear()
                                                selectedIndices.addAll(items.indices)
                                            }
                                        },
                                    ) {
                                        Text(
                                            text = if (selectedIndices.size == items.size) "Deselect all" else "Select all",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    itemsIndexed(items) { index, item ->
                                        val isSelected = selectedIndices.contains(index)
                                        val isGoal = item.type == "goal"

                                        Surface(
                                            onClick = {
                                                if (isSelected) selectedIndices.remove(index) else selectedIndices.add(index)
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(Radius.md))
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) colors.accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                                                    RoundedCornerShape(Radius.md),
                                                )
                                                .pressScale(0.98f),
                                            color = if (isSelected) colors.accent.copy(alpha = 0.08f) else colors.surfaceRaised,
                                            shape = RoundedCornerShape(Radius.md),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(Spacing.md),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                                    tint = if (isSelected) colors.accent else colors.textSecondary,
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .padding(top = 2.dp),
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.sm))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(Radius.xs))
                                                                .background(
                                                                    if (isGoal) Color(0xFF8B5CF6).copy(alpha = 0.18f) else colors.accent.copy(alpha = 0.18f),
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        ) {
                                                            Text(
                                                                text = if (isGoal) "DAILY HABIT" else "COMMITMENT",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp,
                                                                color = if (isGoal) Color(0xFFC4B5FD) else colors.accent,
                                                            )
                                                        }

                                                        if (!item.dueAt.isNullOrBlank()) {
                                                            val formattedDue = app.promise.android.ui.commitments.CommitmentTime.formatDue(
                                                                dueAt = item.dueAt,
                                                                precision = if (item.duePrecision?.equals("DAY", ignoreCase = true) == true) DuePrecision.DATE else DuePrecision.DATETIME,
                                                                timeZoneId = "Asia/Kolkata",
                                                            ) ?: item.dueAt

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.CalendarToday,
                                                                    contentDescription = null,
                                                                    tint = colors.textSecondary,
                                                                    modifier = Modifier.size(11.dp),
                                                                )
                                                                Text(
                                                                    text = "Due $formattedDue",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = colors.textSecondary,
                                                                    fontSize = 11.sp,
                                                                )
                                                            }
                                                        } else if (item.recurrenceKind != null) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.Repeat,
                                                                    contentDescription = null,
                                                                    tint = colors.textSecondary,
                                                                    modifier = Modifier.size(11.dp),
                                                                )
                                                                Text(
                                                                    text = item.recurrenceKind,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = colors.textSecondary,
                                                                    fontSize = 11.sp,
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Text(
                                                        text = item.title,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.textPrimary,
                                                    )

                                                    if (item.description.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = item.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = colors.textSecondary,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.md))

                                Button(
                                    onClick = { submitFinalItems() },
                                    enabled = selectedIndices.isNotEmpty() && !isSubmitting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(TouchTarget.min)
                                        .pressScale(0.97f),
                                    shape = RoundedCornerShape(Radius.pill),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = Color.Black,
                                    ),
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.Black,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(
                                            "Create Promise(s) (${selectedIndices.size})",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                TextButton(
                                    onClick = { currentStep = VoicePipelineStep.REVIEW },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Edit Spoken Words",
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LuminousAcousticAura(
    isListening: Boolean,
    rmsNormalized: Float,
    reduceMotion: Boolean,
    onToggleListening: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    val smoothRms by animateFloatAsState(
        targetValue = if (isListening) rmsNormalized else 0f,
        animationSpec = spring(stiffness = 220f, dampingRatio = 0.60f),
        label = "rmsAnimation",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseLoop")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idlePulse",
    )
    val rippleExpansion by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rippleExpansion",
    )

    val orbScale = if (reduceMotion) 1f else if (isListening) (1f + smoothRms * 0.16f) else idlePulse

    Box(
        modifier = Modifier
            .size(190.dp)
            .semantics { contentDescription = if (isListening) "Microphone active" else "Microphone idle" },
        contentAlignment = Alignment.Center,
    ) {
        // Concentric Symmetrical Acoustic Rings (100% centered around the mic)
        if (!reduceMotion) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = 38.dp.toPx()

                if (isListening) {
                    // Outer Ripple Ring 3 (Breathing propagation)
                    val r3 = baseRadius + 44.dp.toPx() + (smoothRms * 20.dp.toPx()) + (rippleExpansion * 12.dp.toPx())
                    val a3 = ((1f - rippleExpansion) * (0.12f + smoothRms * 0.28f)).coerceIn(0.04f, 0.40f)
                    drawCircle(
                        color = colors.accent.copy(alpha = a3),
                        radius = r3,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )

                    // Mid Acoustic Ring 2
                    val r2 = baseRadius + 26.dp.toPx() + (smoothRms * 14.dp.toPx())
                    val a2 = (0.20f + smoothRms * 0.40f).coerceIn(0.12f, 0.65f)
                    drawCircle(
                        color = colors.accent.copy(alpha = a2),
                        radius = r2,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    // Inner Halo Ring 1
                    val r1 = baseRadius + 12.dp.toPx() + (smoothRms * 8.dp.toPx())
                    val a1 = (0.30f + smoothRms * 0.45f).coerceIn(0.20f, 0.85f)
                    drawCircle(
                        color = colors.accent.copy(alpha = a1),
                        radius = r1,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )

                    // Soft ambient glowing aura behind the mic orb
                    drawCircle(
                        color = colors.accent.copy(alpha = (0.08f + smoothRms * 0.15f).coerceIn(0.05f, 0.25f)),
                        radius = baseRadius + 16.dp.toPx(),
                        center = center,
                    )
                } else {
                    // Idle gentle resting ring
                    drawCircle(
                        color = colors.accent.copy(alpha = 0.18f),
                        radius = baseRadius + 8.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }

        // Floating Microphone Orb (Centered precisely on the canvas center)
        Surface(
            onClick = onToggleListening,
            modifier = Modifier
                .size(76.dp)
                .scale(orbScale)
                .clip(CircleShape)
                .border(
                    2.dp,
                    if (isListening) colors.accent else colors.surfaceRaised,
                    CircleShape,
                )
                .pressScale(0.92f),
            shape = CircleShape,
            color = if (isListening) colors.accent else colors.surfaceRaised,
            shadowElevation = if (isListening) 10.dp else 2.dp,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Outlined.Mic else Icons.Outlined.MicOff,
                    contentDescription = if (isListening) "Tap to stop" else "Tap to speak",
                    tint = if (isListening) Color.Black else colors.textPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun ReactiveAudioWaveform(
    isListening: Boolean,
    rmsNormalized: Float,
    reduceMotion: Boolean,
) {
    val colors = PromiseThemeColors.current
    if (reduceMotion || !isListening) return

    val smoothRms by animateFloatAsState(
        targetValue = if (isListening) rmsNormalized else 0f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.65f),
        label = "waveformRms",
    )

    val barMultipliers = listOf(0.4f, 0.7f, 1.1f, 1.6f, 2.0f, 1.6f, 1.1f, 0.7f, 0.4f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(28.dp),
    ) {
        barMultipliers.forEachIndexed { i, mult ->
            val barHeight by animateFloatAsState(
                targetValue = (4f + smoothRms * 20f * mult).coerceIn(4f, 26f),
                animationSpec = spring(stiffness = 350f + (i % 5) * 40f, dampingRatio = 0.65f),
                label = "waveBar-$i",
            )
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.accent.copy(alpha = (0.50f + smoothRms * 0.50f).coerceIn(0.4f, 1f))),
            )
        }
    }
}

@Composable
private fun PermissionRequiredView(
    onGrantPermission: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.MicOff,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Microphone Access Needed",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = "Promise uses on-device speech recognition to turn your thoughts into actionable promises and daily routines.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
            onClick = onGrantPermission,
            shape = RoundedCornerShape(Radius.pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = Color.Black,
            ),
        ) {
            Text("Grant Microphone Access", fontWeight = FontWeight.Bold)
        }
    }
}
