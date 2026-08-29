package app.promise.android.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.IconButton
import app.promise.android.core.speech.SpeechRecognitionManager
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtParserSheet(
    onDismiss: () -> Unit,
    onParseThought: suspend (String) -> List<ParsedThoughtItem>,
    onCreateCommitment: (CreateCommitmentInput) -> Unit,
    onCreateGoal: (CreateGoalInput) -> Unit,
    speechManager: SpeechRecognitionManager? = null,
    initialThoughtText: String = "",
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var thoughtText by remember { mutableStateOf(initialThoughtText) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var parsedItems by remember { mutableStateOf<List<ParsedThoughtItem>?>(null) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showVoiceCapture by remember { mutableStateOf(false) }

    fun triggerParse(textToParse: String) {
        val query = textToParse.trim()
        if (query.isNotBlank()) {
            isLoading = true
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
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Ignored silently on lifecycle/sheet cancel
                } catch (e: java.net.SocketTimeoutException) {
                    errorMessage = "Request timed out. Please try again."
                } catch (e: java.io.IOException) {
                    errorMessage = "Cannot reach server. Check your network or backend."
                } catch (e: retrofit2.HttpException) {
                    errorMessage = when (e.code()) {
                        400 -> "Thought text is invalid or exceeds 500 characters."
                        401 -> "Session expired. Please log in again."
                        429 -> "Rate limit reached. Please wait a moment."
                        in 500..599 -> "AI service is temporarily unavailable. Please try again."
                        else -> "Error (${e.code()}). Please try again."
                    }
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Could not parse thoughts. Please try again."
                } finally {
                    isLoading = false
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialThoughtText) {
        if (initialThoughtText.isNotBlank() && parsedItems == null && !isLoading) {
            thoughtText = initialThoughtText
            triggerParse(initialThoughtText)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xl)
                .imePadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Thought → Promise",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Paste a brain dump of multiple tasks or habits. AI will decompose it into structured items for your selection.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            if (parsedItems == null) {
                OutlinedTextField(
                    value = thoughtText,
                    onValueChange = {
                        if (it.length <= 500) {
                            thoughtText = it
                            if (errorMessage != null) errorMessage = null
                        }
                    },
                    label = { Text("E.g. I need to submit resume, call doctor, and read 20 mins every day") },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = "${thoughtText.length} / 500",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (thoughtText.length >= 450) colors.accent else colors.textSecondary,
                            )
                        }
                    },
                    trailingIcon = if (speechManager != null) {
                        {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showVoiceCapture = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = "Speak thoughts",
                                    tint = colors.accent,
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )

                if (thoughtText.isBlank() && !isLoading) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "IDEAS TO TRY",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        app.promise.android.core.copy.PromiseCopy.INSPIRATION_STARTER_CHIPS.forEach { starter ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(colors.surfaceMuted)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Radius.pill))
                                    .clickable {
                                        thoughtText = starter
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(horizontal = Spacing.sm, vertical = 6.dp),
                            ) {
                                Text(
                                    text = starter,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Button(
                    onClick = {
                        triggerParse(thoughtText)
                    },
                    enabled = thoughtText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TouchTarget.min),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.surfaceMuted,
                    ),
                    shape = RoundedCornerShape(Radius.sm),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.surfaceMuted,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Decompose Thoughts",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                val items = parsedItems!!
                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = "No tasks or habits detected",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "Try describing concrete tasks, deadlines, or recurring habits. Tap any starter below to try:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Spacing.md),
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            app.promise.android.core.copy.PromiseCopy.INSPIRATION_STARTER_CHIPS.take(3).forEach { starter ->
                                Surface(
                                    onClick = {
                                        thoughtText = starter
                                        triggerParse(starter)
                                    },
                                    shape = RoundedCornerShape(Radius.sm),
                                    color = colors.surfaceMuted,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(
                                            text = starter,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textPrimary,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.lg))
                        Button(
                            onClick = {
                                parsedItems = null
                                selectedIndices.clear()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TouchTarget.min),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.surfaceMuted,
                            ),
                            shape = RoundedCornerShape(Radius.sm),
                        ) {
                            Text(
                                text = "Edit My Thoughts",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "SELECT ITEMS TO CREATE (${selectedIndices.size}/${items.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            color = colors.accent,
                                        )
                                        if (!item.dueAt.isNullOrBlank()) {
                                            val formattedDue = app.promise.android.ui.commitments.CommitmentTime.formatDue(
                                                dueAt = item.dueAt,
                                                precision = if (item.duePrecision?.equals("DAY", ignoreCase = true) == true) DuePrecision.DATE else DuePrecision.DATETIME,
                                                timeZoneId = "Asia/Kolkata",
                                            ) ?: item.dueAt
                                            Text(
                                                text = "• Due $formattedDue",
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
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
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

                var isSubmitting by remember { mutableStateOf(false) }

                Button(
                    onClick = {
                        if (isSubmitting) return@Button
                        isSubmitting = true
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

                                onCreateCommitment(
                                    CreateCommitmentInput(
                                        title = item.title,
                                        description = item.description,
                                        dueAt = finalDueAt,
                                        duePrecision = duePrecision,
                                    ),
                                )
                            } else {
                                onCreateGoal(
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
                        onDismiss()
                    },
                    enabled = selectedIndices.isNotEmpty() && !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TouchTarget.min),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.surfaceMuted,
                    ),
                    shape = RoundedCornerShape(Radius.sm),
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
                        Text(
                            text = "Create ${selectedIndices.size} Item(s)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(
                    onClick = {
                        parsedItems = null
                        selectedIndices.clear()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Edit thoughts",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(Spacing.lg))
}

    if (showVoiceCapture && speechManager != null) {
        VoiceCaptureSheet(
            speechManager = speechManager,
            onDismiss = { showVoiceCapture = false },
            onTranscriptReady = { voiceText ->
                showVoiceCapture = false
                thoughtText = voiceText
                triggerParse(voiceText)
            },
        )
    }
}
