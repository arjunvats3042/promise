package app.promise.android.ui.goals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

private enum class ConfirmKind { Pause, Resume, Complete, Cancel }

@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onNotFound: () -> Unit,
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmKind?>(null) }
    val colors = PromiseThemeColors.current
    val busy = action is ActionState.InFlight
    val actionError = (action as? ActionState.Failed)?.kind

    LaunchedEffect(state) {
        val error = state as? LoadState.Error
        if (error?.kind == ErrorKind.NotFound) {
            onNotFound()
        }
    }

    when (val s = state) {
        is LoadState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
            }
        }
        is LoadState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(Spacing.inset),
            ) {
                Text(s.kind.toUserMessage(), color = colors.textPrimary)
                if (s.canRetry) {
                    TextButton(onClick = viewModel::reload) {
                        Text("Try again", color = colors.accent)
                    }
                }
                TextButton(onClick = onBack) {
                    Text("Back", color = colors.textSecondary)
                }
            }
        }
        is LoadState.Ready -> {
            DetailContent(
                ui = s.value,
                busy = busy,
                actionError = actionError,
                onCheckIn = viewModel::checkIn,
                onPause = { confirm = ConfirmKind.Pause },
                onResume = { confirm = ConfirmKind.Resume },
                onComplete = { confirm = ConfirmKind.Complete },
                onCancel = { confirm = ConfirmKind.Cancel },
                onBack = onBack,
                onClearError = viewModel::clearActionError,
            )
        }
        else -> Unit
    }

    when (confirm) {
        ConfirmKind.Pause -> ConfirmDialog(
            title = "Pause this practice?",
            body = "Check-ins pause until you resume.",
            confirmLabel = "Pause",
            destructive = false,
            onConfirm = {
                confirm = null
                viewModel.pause()
            },
            onDismiss = { confirm = null },
        )
        ConfirmKind.Resume -> ConfirmDialog(
            title = "Resume this practice?",
            body = "You’ll be able to check in again.",
            confirmLabel = "Resume",
            destructive = false,
            onConfirm = {
                confirm = null
                viewModel.resume()
            },
            onDismiss = { confirm = null },
        )
        ConfirmKind.Complete -> ConfirmDialog(
            title = "Complete this goal?",
            body = "Mark it finished. You can still view history.",
            confirmLabel = "Complete",
            destructive = false,
            onConfirm = {
                confirm = null
                viewModel.complete()
            },
            onDismiss = { confirm = null },
        )
        ConfirmKind.Cancel -> ConfirmDialog(
            title = "Cancel this goal?",
            body = "This can’t be undone.",
            confirmLabel = "Cancel goal",
            destructive = true,
            onConfirm = {
                confirm = null
                viewModel.cancel()
            },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun DetailContent(
    ui: GoalDetailUi,
    busy: Boolean,
    actionError: ErrorKind?,
    onCheckIn: (CheckInInput) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val goal = ui.goal
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.inset),
    ) {
        TextButton(onClick = onBack) {
            Text("Back", color = colors.textSecondary)
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        AnimatedContent(
            targetState = goal.status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "goal-status",
        ) { status ->
            Text(
                text = GoalPresentation.statusLabel(status),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = goal.title,
            style = MaterialTheme.typography.displayLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = detailMeta(goal),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        if (goal.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.section))
            Text(
                text = goal.description,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.section))
        Text(
            text = GoalPresentation.progressLine(goal),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics {
                contentDescription = GoalPresentation.progressLine(goal).replace("/", " of ")
            },
        )
        GoalPresentation.streakLine(goal)?.let { streak ->
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(streak, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        ProgressTrack(fraction = GoalPresentation.progressFraction(goal))
        if (actionError != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(actionError.toUserMessage(), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onClearError) {
                Text("Dismiss", color = colors.textSecondary)
            }
        }
        if (ui.canCheckInToday()) {
            Spacer(modifier = Modifier.height(Spacing.section))
            InlineCheckIn(
                goal = goal,
                prompt = ui.checkInPrompt(),
                busy = busy,
                onSubmit = onCheckIn,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.section))
        if (goal.canPause) {
            TextButton(
                onClick = onPause,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Pause goal" },
            ) {
                Text("Pause", color = colors.accent)
            }
        }
        if (goal.canResume) {
            TextButton(
                onClick = onResume,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Resume goal" },
            ) {
                Text("Resume", color = colors.accent)
            }
        }
        if (goal.canCompleteGoal) {
            Button(
                onClick = onComplete,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Complete goal" },
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(Radius.sm),
            ) {
                Text(if (busy) "Working…" else "Complete")
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        if (goal.canCancel) {
            TextButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Cancel goal" },
            ) {
                Text("Cancel goal", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(Spacing.section))
        Text(
            text = "History",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        if (ui.checkIns.isEmpty()) {
            Text(
                text = "No check-ins yet.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        } else {
            ui.checkIns.forEach { row ->
                HistoryRow(row)
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun InlineCheckIn(
    goal: Goal,
    prompt: String,
    busy: Boolean,
    onSubmit: (CheckInInput) -> Unit,
) {
    val colors = PromiseThemeColors.current
    var valueText by remember {
        mutableStateOf(
            goal.progress.currentPeriod.value?.toString()
                ?: goal.targetValue?.toString()
                ?: "",
        )
    }
    Text(
        text = prompt,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
    )
    Spacer(modifier = Modifier.height(Spacing.sm))
    if (goal.trackingKind == GoalTrackingKind.COUNT) {
        OutlinedTextField(
            value = valueText,
            onValueChange = { valueText = it.filter { ch -> ch.isDigit() } },
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = {
                Text(
                    if (goal.targetUnit.isNotBlank()) "Value (${goal.targetUnit})" else "Value",
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
        Spacer(modifier = Modifier.height(Spacing.sm))
        Button(
            onClick = {
                val value = valueText.toIntOrNull() ?: return@Button
                onSubmit(CheckInInput(status = GoalCheckInStatus.COMPLETED, value = value))
            },
            enabled = !busy && valueText.toIntOrNull() != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Save check-in" },
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            shape = RoundedCornerShape(Radius.sm),
        ) {
            Text(if (busy) "Saving…" else "Done")
        }
        TextButton(
            onClick = { onSubmit(CheckInInput(status = GoalCheckInStatus.SKIPPED)) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Skip today", color = colors.textSecondary)
        }
    } else {
        Button(
            onClick = { onSubmit(CheckInInput(status = GoalCheckInStatus.COMPLETED)) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Mark done" },
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            shape = RoundedCornerShape(Radius.sm),
        ) {
            Text(if (busy) "Saving…" else "Done")
        }
        TextButton(
            onClick = { onSubmit(CheckInInput(status = GoalCheckInStatus.SKIPPED)) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Skip today", color = colors.textSecondary)
        }
    }
}

@Composable
private fun HistoryRow(checkIn: GoalCheckIn) {
    val colors = PromiseThemeColors.current
    val status = when (checkIn.status) {
        GoalCheckInStatus.COMPLETED -> "Done"
        GoalCheckInStatus.SKIPPED -> "Skipped"
    }
    val valuePart = checkIn.value?.let { " · $it" }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = checkIn.periodDate,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = status + valuePart,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep", color = colors.textSecondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

private fun detailMeta(goal: Goal): String {
    val parts = mutableListOf(GoalPresentation.recurrenceLabel(goal))
    if (goal.timezone.isNotBlank()) parts += goal.timezone
    if (goal.status == GoalStatus.PAUSED) parts += "Paused"
    return parts.joinToString(" · ")
}
