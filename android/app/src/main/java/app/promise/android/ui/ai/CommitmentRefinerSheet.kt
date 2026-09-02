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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
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
import app.promise.android.domain.CommitmentRefinement
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.DuePrecision
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitmentRefinerSheet(
    initialPrompt: String = "",
    onDismiss: () -> Unit,
    onRefineCommitment: suspend (String) -> CommitmentRefinement,
    onConfirmCreate: (CreateCommitmentInput) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var promptText by remember { mutableStateOf(initialPrompt) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refinement by remember { mutableStateOf<CommitmentRefinement?>(null) }

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
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Add Details & Deadline",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Turn rough notes into clear tasks with dates and reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            val currentRefinement = refinement
            if (currentRefinement == null) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = {
                        promptText = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("e.g. Call Mom tomorrow at 5pm, or send report Friday") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
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
                                    refinement = onRefineCommitment(query)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: kotlinx.coroutines.CancellationException) {
                                    // Ignored silently on lifecycle/sheet cancel
                                } catch (e: Exception) {
                                    errorMessage = "Could not refine commitment. Please try again."
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
                            text = "Add Details",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                val effectiveTitle = currentRefinement.refinedTitle.ifBlank { promptText.trim().replaceFirstChar { it.uppercase() } }
                val effectiveDescription = currentRefinement.refinedDescription.ifBlank {
                    if (effectiveTitle.equals("Call Mom", ignoreCase = true)) "Place a phone call to Mom." else ""
                }
                val dueDisplay = formatDueDisplay(currentRefinement.suggestedDueAt)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(Radius.md)),
                    color = colors.surfaceMuted,
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        Text(
                            text = "SUGGESTED TASK",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                        )
                        Spacer(modifier = Modifier.height(Spacing.cardTitleBottom))
                        Text(
                            text = effectiveTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        if (effectiveDescription.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Spacing.cardSubtitleBottom))
                            Text(
                                text = effectiveDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.cardSubtitleBottom))
                        Text(
                            text = dueDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Button(
                    onClick = {
                        val input = CreateCommitmentInput(
                            title = effectiveTitle,
                            description = effectiveDescription,
                            dueAt = currentRefinement.suggestedDueAt?.takeIf { it.isNotBlank() },
                            duePrecision = when (currentRefinement.suggestedDuePrecision?.uppercase()) {
                                "MINUTE" -> DuePrecision.DATETIME
                                "HOUR" -> DuePrecision.DATETIME
                                "DAY" -> DuePrecision.DATE
                                else -> if (!currentRefinement.suggestedDueAt.isNullOrBlank()) DuePrecision.DATE else DuePrecision.NONE
                            },
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
                        text = "Confirm & Create Commitment",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(
                    onClick = { refinement = null },
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

private fun formatDueDisplay(dueAt: String?): String {
    if (dueAt.isNullOrBlank()) return "Due: Tomorrow"
    return try {
        val parsed = Instant.parse(dueAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        when (parsed) {
            today -> "Due: Today"
            today.plusDays(1) -> "Due: Tomorrow"
            today.minusDays(1) -> "Due: Yesterday"
            else -> "Due: ${parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
        }
    } catch (_: Throwable) {
        if (dueAt.equals("tomorrow", ignoreCase = true)) "Due: Tomorrow"
        else "Due: $dueAt"
    }
}
