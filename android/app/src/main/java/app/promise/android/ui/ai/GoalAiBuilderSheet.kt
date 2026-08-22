package app.promise.android.ui.ai

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
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

    var promptText by remember { mutableStateOf("") }
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
                .padding(horizontal = Spacing.inset, vertical = Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "AI Goal Builder",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Describe your habit or practice. AI will suggest a structured goal for your review and confirmation.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            if (suggestion == null) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("E.g. Read 30 minutes every weekday") },
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
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

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
                                } catch (e: Exception) {
                                    errorMessage = "Could not generate suggestion. Please try again."
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
                        Text("Generate Suggestion")
                    }
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
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "SUGGESTED GOAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = sug.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        if (sug.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Text(
                                text = sug.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "Cadence: ${sug.recurrenceKind} • Tracking: ${sug.trackingKind}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        if (sug.reasoning.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "“${sug.reasoning}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

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
                    Text("Confirm & Create Goal")
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                TextButton(
                    onClick = { suggestion = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit prompt", color = colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}
