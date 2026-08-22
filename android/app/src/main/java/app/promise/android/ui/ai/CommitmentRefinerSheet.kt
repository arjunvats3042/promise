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
import androidx.compose.material.icons.outlined.HelpOutline
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

    var promptText by remember { mutableStateOf(initialPrompt) }
    var deadlineAnswer by remember { mutableStateOf("") }
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
                    text = "Refine Commitment with AI",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Turn vague thoughts into clear, bounded commitments.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            if (refinement == null) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("E.g. Finish taxes soon") },
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
                                    refinement = onRefineCommitment(query)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        Text("Refine with AI")
                    }
                }
            } else if (refinement!!.status == "NEEDS_CLARIFICATION") {
                val ref = refinement!!
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(Radius.md)),
                    color = colors.surfaceMuted,
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "CLARIFICATION NEEDED",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                            )
                        }
                        if (ref.missingInformation != null) {
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Text(
                                text = ref.missingInformation,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = ref.clarifyingQuestion ?: "When would you like to finish this by?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                OutlinedTextField(
                    value = deadlineAnswer,
                    onValueChange = { deadlineAnswer = it },
                    label = { Text("E.g. by Friday 5 PM") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Button(
                    onClick = {
                        val combined = "$promptText $deadlineAnswer".trim()
                        isLoading = true
                        errorMessage = null
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            try {
                                refinement = onRefineCommitment(combined)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } catch (e: Exception) {
                                errorMessage = "Could not refine commitment. Please try again."
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = deadlineAnswer.isNotBlank() && !isLoading,
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
                        Text("Add Deadline & Refine")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                TextButton(
                    onClick = { refinement = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start over", color = colors.textSecondary)
                }
            } else {
                val ref = refinement!!
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(Radius.md)),
                    color = colors.surfaceMuted,
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "REFINED COMMITMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = ref.refinedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        if (ref.refinedDescription.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Text(
                                text = ref.refinedDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        if (ref.suggestedDueAt != null) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Suggested due: ${ref.suggestedDueAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        if (ref.reasoning.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "“${ref.reasoning}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Button(
                    onClick = {
                        val input = CreateCommitmentInput(
                            title = ref.refinedTitle,
                            description = ref.refinedDescription,
                            dueAt = ref.suggestedDueAt,
                            duePrecision = when (ref.suggestedDuePrecision?.uppercase()) {
                                "MINUTE" -> DuePrecision.DATETIME
                                "HOUR" -> DuePrecision.DATETIME
                                "DAY" -> DuePrecision.DATE
                                else -> DuePrecision.NONE
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
                    Text("Confirm & Create Commitment")
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                TextButton(
                    onClick = { refinement = null },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit prompt", color = colors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}
