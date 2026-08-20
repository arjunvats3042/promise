package app.promise.android.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.promise.android.core.ActionState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.ui.components.PromiseModalSheet
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInSheet(
    goal: Goal,
    action: ActionState,
    onDismiss: () -> Unit,
    onSubmit: (CheckInInput) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial = goal.progress.currentPeriod.value?.toString()
        ?: goal.targetValue?.toString()
        ?: ""
    var valueText by remember { mutableStateOf(initial) }
    val submitting = action is ActionState.InFlight
    val error = (action as? ActionState.Failed)?.kind
    val isCount = goal.trackingKind == GoalTrackingKind.COUNT

    PromiseModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.md)) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Today’s check-in",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (isCount) {
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter { ch -> ch.isDigit() } },
                    enabled = !submitting,
                    singleLine = true,
                    label = {
                        Text(
                            if (goal.targetUnit.isNotBlank()) {
                                "Value (${goal.targetUnit})"
                            } else {
                                "Value"
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Check-in value" },
                    shape = RoundedCornerShape(Radius.sm),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = colors.accent,
                    ),
                )
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(error.toUserMessage(), color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            if (isCount) {
                Button(
                    onClick = {
                        val value = valueText.toIntOrNull() ?: return@Button
                        onSubmit(
                            CheckInInput(
                                status = GoalCheckInStatus.COMPLETED,
                                value = value,
                            ),
                        )
                    },
                    enabled = !submitting && valueText.toIntOrNull() != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Save check-in" },
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(Radius.sm),
                ) {
                    Text(if (submitting) "Saving…" else "Done")
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                TextButton(
                    onClick = {
                        onSubmit(CheckInInput(status = GoalCheckInStatus.SKIPPED))
                    },
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Skip today", color = colors.textSecondary)
                }
            } else {
                Row {
                    Button(
                        onClick = {
                            onSubmit(CheckInInput(status = GoalCheckInStatus.COMPLETED))
                        },
                        enabled = !submitting,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Mark done" },
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(Radius.sm),
                    ) {
                        Text("Done")
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                TextButton(
                    onClick = {
                        onSubmit(CheckInInput(status = GoalCheckInStatus.SKIPPED))
                    },
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Skip today", color = colors.textSecondary)
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}
