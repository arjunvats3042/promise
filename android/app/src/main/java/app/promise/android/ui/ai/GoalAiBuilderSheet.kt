package app.promise.android.ui.ai

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.TrackChanges
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
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalSuggestion
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalAiBuilderSheet(
    onDismiss: () -> Unit,
    onSuggestGoal: suspend (String) -> GoalSuggestion,
    onConfirmCreate: (CreateGoalInput) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var promptText by remember { mutableStateOf("") }
    var clarificationAnswer by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var suggestion by remember { mutableStateOf<GoalSuggestion?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xl)
                .imePadding()
                .verticalScroll(scrollState),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.TrackChanges,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Design a Habit",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Tell us what you want to practice. We'll set up the schedule and format so you can start right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            if (suggestion == null) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = {
                        promptText = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("e.g. Read 20 minutes every night, or gym 4x a week") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )

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
                        val query = promptText.trim()
                        if (query.isNotBlank()) {
                            isLoading = true
                            errorMessage = null
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                try {
                                    suggestion = onSuggestGoal(query)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: kotlinx.coroutines.CancellationException) {
                                    // Ignored silently on lifecycle/sheet cancel
                                } catch (e: Exception) {
                                    suggestion = buildLocalGoalFallback(query)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = promptText.isNotBlank() && !isLoading,
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
                            text = "Build Habit",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else if (suggestion!!.status == "NEEDS_CLARIFICATION") {
                val sug = suggestion!!
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(Radius.md)),
                    color = colors.surfaceMuted,
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "QUICK QUESTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.cardTitleBottom))
                        Text(
                            text = sug.clarificationQuestion ?: "How often would you like to practice this goal?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                OutlinedTextField(
                    value = clarificationAnswer,
                    onValueChange = { clarificationAnswer = it },
                    label = { Text("E.g. 5 days a week, 30 minutes") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                Button(
                    onClick = {
                        val combined = "$promptText. Details: $clarificationAnswer".trim()
                        isLoading = true
                        errorMessage = null
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            try {
                                suggestion = onSuggestGoal(combined)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } catch (e: Exception) {
                                errorMessage = "Could not refine goal. Please try again."
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = clarificationAnswer.isNotBlank() && !isLoading,
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
                            text = "Continue with Details",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(
                    onClick = { suggestion = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Start over",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            } else {
                val sug = suggestion!!
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(Radius.md)),
                    color = colors.surfaceMuted,
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        Text(
                            text = "SUGGESTED GOAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                        )
                        Spacer(modifier = Modifier.height(Spacing.cardTitleBottom))
                        Text(
                            text = sug.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        if (sug.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Spacing.cardSubtitleBottom))
                            Text(
                                text = sug.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.cardSubtitleBottom))
                        Text(
                            text = "Cadence: ${sug.recurrenceKind} • Tracking: ${sug.trackingKind}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Button(
                    onClick = {
                        val input = CreateGoalInput(
                            title = sug.title,
                            description = sug.description,
                            recurrenceKind = when (sug.recurrenceKind.uppercase()) {
                                "WEEKLY_DAYS" -> GoalRecurrenceKind.WEEKLY_DAYS
                                "N_PER_PERIOD" -> GoalRecurrenceKind.N_PER_PERIOD
                                else -> GoalRecurrenceKind.DAILY
                            },
                            weekdays = sug.weekdays,
                            trackingKind = if (sug.trackingKind.uppercase() == "COUNT") GoalTrackingKind.COUNT else GoalTrackingKind.BINARY,
                            targetValue = sug.targetValue?.toInt(),
                            targetUnit = sug.targetUnit,
                        )
                        onConfirmCreate(input)
                        onDismiss()
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
                    Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "Confirm & Create Goal",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(
                    onClick = { suggestion = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Edit prompt",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

private fun buildLocalGoalFallback(prompt: String): GoalSuggestion {
    val lower = prompt.lowercase(java.util.Locale.ROOT)
    val isWeekend = lower.contains("weekend") || lower.contains("sat-sun") || lower.contains("sat sun")
    val isWeekday = lower.contains("weekday") || lower.contains("mon-fri")

    val recurrence = when {
        isWeekend || isWeekday -> "WEEKLY_DAYS"
        lower.contains("week") -> "N_PER_PERIOD"
        else -> "DAILY"
    }
    val weekdays = when {
        isWeekend -> listOf(5, 6)
        isWeekday -> listOf(0, 1, 2, 3, 4)
        else -> emptyList()
    }

    val hasMinutes = Regex("(\\d+)\\s*(?:mins?|minutes?)", RegexOption.IGNORE_CASE).find(prompt)
    val hasHours = Regex("(\\d+)\\s*(?:hrs?|hours?)", RegexOption.IGNORE_CASE).find(prompt)
    val hasPages = Regex("(\\d+)\\s*(?:pages?)", RegexOption.IGNORE_CASE).find(prompt)
    val hasLiters = Regex("(\\d+)\\s*(?:liters?|ltrs?)", RegexOption.IGNORE_CASE).find(prompt)

    val (trackingKind, targetVal, targetUnit) = when {
        hasMinutes != null -> Triple("COUNT", hasMinutes.groupValues[1].toDoubleOrNull(), "minutes")
        hasHours != null -> Triple("COUNT", hasHours.groupValues[1].toDoubleOrNull(), "hours")
        hasPages != null -> Triple("COUNT", hasPages.groupValues[1].toDoubleOrNull(), "pages")
        hasLiters != null -> Triple("COUNT", hasLiters.groupValues[1].toDoubleOrNull(), "liters")
        else -> Triple("BINARY", null, "")
    }

    val cleanTitle = prompt
        .replace(Regex("^(?:wanna|want to|need to|have to|plan to|i will|start|do)\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:everyday|every day|daily|every weekday|every weekend|for a month)\\b", RegexOption.IGNORE_CASE), "")
        .trim()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        .take(60)
        .ifBlank { prompt.take(40) }

    return GoalSuggestion(
        status = "READY",
        title = cleanTitle,
        description = "Practice: $prompt",
        recurrenceKind = recurrence,
        weekdays = weekdays,
        periodUnit = if (recurrence == "N_PER_PERIOD") "WEEK" else null,
        timesPerPeriod = if (recurrence == "N_PER_PERIOD") 3 else null,
        trackingKind = trackingKind,
        targetValue = targetVal,
        targetUnit = targetUnit,
        reasoning = "Configured practice schedule from your description.",
    )
}
