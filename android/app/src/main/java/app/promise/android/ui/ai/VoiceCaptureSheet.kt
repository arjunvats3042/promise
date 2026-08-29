package app.promise.android.ui.ai

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.speech.SpeechRecognitionManager
import app.promise.android.core.speech.SpeechRecognitionState
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.DuePrecision
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.ParsedThoughtItem
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
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
    onCreateCommitment: ((CreateCommitmentInput) -> Unit)? = null,
    onCreateGoal: ((CreateGoalInput) -> Unit)? = null,
    titleText: String = "Voice → Promise",
    subtitleText: String = "Speak naturally. Mention tasks, deadlines, or recurring habits.",
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

    fun startProcessingAi(text: String) {
        val query = text.trim()
        if (query.isBlank()) return

        if (onParseThought == null) {
            // Direct transcript mode
            onTranscriptReady?.invoke(query)
            onDismiss()
            return
        }

        currentStep = VoicePipelineStep.AI_PROCESSING
        errorMessage = null
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        scope.launch {
            try {
                val items = onParseThought(query)
                parsedItems = items
                selectedIndices.clear()
                selectedIndices.addAll(items.indices)
                currentStep = VoicePipelineStep.FINALIZE
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Could not structure thoughts. Please try again."
                currentStep = VoicePipelineStep.REVIEW
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
    }

    fun finalizeAndCreate() {
        if (isSubmitting) return
        isSubmitting = true

        val items = parsedItems ?: emptyList()
        val chosen = items.filterIndexed { index, _ -> selectedIndices.contains(index) }

        chosen.forEach { item ->
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
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = when (currentStep) {
                                VoicePipelineStep.RECORDING -> titleText
                                VoicePipelineStep.REVIEW -> "Review Spoken Words"
                                VoicePipelineStep.AI_PROCESSING -> "Structuring with AI..."
                                VoicePipelineStep.FINALIZE -> "Finalize Promise(s)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = when (currentStep) {
                                VoicePipelineStep.RECORDING -> subtitleText
                                VoicePipelineStep.REVIEW -> "Check what was heard or tap Retake."
                                VoicePipelineStep.AI_PROCESSING -> "Extracting exact tasks and timeline..."
                                VoicePipelineStep.FINALIZE -> "Select items to add to your plan."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        speechManager.reset()
                        onDismiss()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
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

                                AcousticWaveform(
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

                                Spacer(modifier = Modifier.height(Spacing.md))

                                Text(
                                    text = if (isListening) "Listening... speak now" else "Tap microphone to speak",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isListening) colors.accent else colors.textSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                Spacer(modifier = Modifier.height(Spacing.md))

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 90.dp, max = 180.dp)
                                        .border(
                                            1.dp,
                                            if (isListening) colors.accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            RoundedCornerShape(Radius.md),
                                        ),
                                    shape = RoundedCornerShape(Radius.md),
                                    color = colors.surfaceMuted,
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
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.textPrimary,
                                            )
                                        } else {
                                            Text(
                                                text = "e.g. \"Submit quarterly report by Thursday 5pm, and run 5k every morning\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.textSecondary.copy(alpha = 0.6f),
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.lg))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            transcriptText = ""
                                            speechManager.reset()
                                            speechManager.startListening()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(TouchTarget.min),
                                        shape = RoundedCornerShape(Radius.sm),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text("Retake")
                                    }

                                    Button(
                                        onClick = {
                                            speechManager.stopListening()
                                            if (transcriptText.isNotBlank()) {
                                                currentStep = VoicePipelineStep.REVIEW
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(TouchTarget.min),
                                        shape = RoundedCornerShape(Radius.sm),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.accent,
                                            contentColor = colors.surfaceMuted,
                                        ),
                                        enabled = transcriptText.isNotBlank(),
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text("Review")
                                    }
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
                                label = { Text("Spoken Thought") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 6,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
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
                                        transcriptText = ""
                                        currentStep = VoicePipelineStep.RECORDING
                                        speechManager.reset()
                                        speechManager.startListening()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(TouchTarget.min),
                                    shape = RoundedCornerShape(Radius.sm),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                ) {
                                    Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Retake")
                                }

                                Button(
                                    onClick = {
                                        startProcessingAi(transcriptText)
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(TouchTarget.min),
                                    shape = RoundedCornerShape(Radius.sm),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.surfaceMuted,
                                    ),
                                    enabled = transcriptText.isNotBlank(),
                                ) {
                                    Icon(imageVector = Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Continue with AI")
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp),
                                color = colors.accent,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(Spacing.lg))
                            Text(
                                text = "Extracting details & timeline...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Structuring promises with accurate deadlines",
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
                                    text = "Try speaking concrete tasks, deadlines, or habits.",
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
                                        .height(TouchTarget.min),
                                    shape = RoundedCornerShape(Radius.sm),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.surfaceMuted,
                                    ),
                                ) {
                                    Text("Edit Spoken Thought")
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "DETECTED PROMISES (${selectedIndices.size}/${items.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    itemsIndexed(items) { index, item ->
                                        val isSelected = selectedIndices.contains(index)
                                        Surface(
                                            onClick = {
                                                if (isSelected) selectedIndices.remove(index) else selectedIndices.add(index)
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(Radius.sm))
                                                .border(
                                                    1.dp,
                                                    if (isSelected) colors.accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                    RoundedCornerShape(Radius.sm),
                                                ),
                                            color = if (isSelected) colors.surfaceMuted else MaterialTheme.colorScheme.surface,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(Spacing.sm),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (isSelected) colors.accent else colors.textSecondary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.sm))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                                    ) {
                                                        Text(
                                                            text = item.type.uppercase(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = colors.accent,
                                                        )
                                                        if (!item.dueAt.isNullOrBlank()) {
                                                            Text(
                                                                text = "• Due ${item.dueAt}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = colors.textSecondary,
                                                            )
                                                        } else if (item.recurrenceKind != null) {
                                                            Text(
                                                                text = "• ${item.recurrenceKind}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = colors.textSecondary,
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = item.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = colors.textPrimary,
                                                    )
                                                    if (item.description.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
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
                                    onClick = { finalizeAndCreate() },
                                    enabled = selectedIndices.isNotEmpty() && !isSubmitting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(TouchTarget.min),
                                    shape = RoundedCornerShape(Radius.sm),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.surfaceMuted,
                                    ),
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = colors.surfaceMuted,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text("Finalize & Create (${selectedIndices.size})")
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                TextButton(
                                    onClick = { currentStep = VoicePipelineStep.REVIEW },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Edit Spoken Thought")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun AcousticWaveform(
    isListening: Boolean,
    rmsNormalized: Float,
    reduceMotion: Boolean,
    onToggleListening: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    val smoothRms by animateFloatAsState(
        targetValue = if (isListening) rmsNormalized else 0f,
        animationSpec = spring(stiffness = 200f, dampingRatio = 0.7f),
        label = "rmsAnimation",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseLoop")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idlePulse",
    )

    val baseScale = if (reduceMotion) 1f else if (isListening) (1f + smoothRms * 0.4f) else idlePulse

    Box(
        modifier = Modifier
            .size(180.dp)
            .semantics { contentDescription = if (isListening) "Microphone active" else "Microphone idle" },
        contentAlignment = Alignment.Center,
    ) {
        // Acoustic ripple rings drawn on Canvas
        if (!reduceMotion && isListening) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val baseRadius = 42.dp.toPx()

                // Outer Ring 2
                drawCircle(
                    color = colors.accent.copy(alpha = (0.12f + smoothRms * 0.25f).coerceIn(0f, 0.4f)),
                    radius = baseRadius + 36.dp.toPx() * (0.6f + smoothRms * 0.6f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // Outer Ring 1
                drawCircle(
                    color = colors.accent.copy(alpha = (0.2f + smoothRms * 0.35f).coerceIn(0f, 0.6f)),
                    radius = baseRadius + 18.dp.toPx() * (0.4f + smoothRms * 0.6f),
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Inner Action Circle Button
        Surface(
            onClick = onToggleListening,
            modifier = Modifier
                .size((72 * baseScale).dp)
                .clip(CircleShape),
            shape = CircleShape,
            color = if (isListening) colors.accent else colors.surfaceMuted,
            shadowElevation = if (isListening) 4.dp else 1.dp,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Outlined.Mic else Icons.Outlined.MicOff,
                    contentDescription = if (isListening) "Tap to pause" else "Tap to speak",
                    tint = if (isListening) colors.surfaceMuted else colors.textPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
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
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = "Promise uses speech recognition to turn your thoughts into promises and daily habits.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
            onClick = onGrantPermission,
            shape = RoundedCornerShape(Radius.sm),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.surfaceMuted,
            ),
        ) {
            Text("Grant Microphone Access")
        }
    }
}
