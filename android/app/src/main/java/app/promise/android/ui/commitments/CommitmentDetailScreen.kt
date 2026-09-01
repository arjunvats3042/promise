package app.promise.android.ui.commitments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.Commitment
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget

@Composable
fun CommitmentDetailScreen(
    onBack: () -> Unit,
    onNotFound: () -> Unit,
    viewModel: CommitmentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    var showSnooze by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
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
            app.promise.android.ui.components.PromiseDetailSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding(),
            )
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
                commitment = s.value,
                timeZoneId = viewModel.timeZoneId,
                busy = busy,
                actionError = actionError,
                onComplete = viewModel::complete,
                onWait = viewModel::waitOn,
                onUnsnooze = viewModel::unsnooze,
                onSnooze = { showSnooze = true },
                onCancel = { showCancel = true },
                onBack = onBack,
                onClearError = viewModel::clearActionError,
            )
        }
        else -> Unit
    }

    if (showSnooze) {
        SnoozeSheet(
            timeZoneId = viewModel.timeZoneId,
            onDismiss = { showSnooze = false },
            onConfirm = { iso ->
                showSnooze = false
                viewModel.snooze(iso)
            },
        )
    }

    if (showCancel) {
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("Cancel commitment?") },
            text = { Text("This can’t be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancel = false
                        viewModel.cancel()
                    },
                ) {
                    Text("Cancel it", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancel = false }) {
                    Text("Keep", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun DetailContent(
    commitment: Commitment,
    timeZoneId: String,
    busy: Boolean,
    actionError: ErrorKind?,
    onComplete: () -> Unit,
    onWait: () -> Unit,
    onUnsnooze: () -> Unit,
    onSnooze: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = commitment.title,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        app.promise.android.ui.components.PromiseCardSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (commitment.isOverdue) {
                    Box(
                        modifier = Modifier
                            .size(Spacing.statusMark)
                            .clip(CircleShape)
                            .background(colors.warning)
                            .semantics { contentDescription = "Overdue" },
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
                Text(
                    text = if (commitment.isOverdue && !commitment.metaLine(timeZoneId).startsWith("Overdue", ignoreCase = true)) {
                        "Overdue · ${commitment.metaLine(timeZoneId)}"
                    } else {
                        commitment.metaLine(timeZoneId)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (commitment.isOverdue) colors.warning else colors.textSecondary,
                )
            }
            if (commitment.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = commitment.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
            }
        }
        if (actionError != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            app.promise.android.ui.components.PromiseErrorBanner(
                message = actionError.toUserMessage(),
                onRetry = onClearError,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xl))
        if (commitment.canComplete) {
            app.promise.android.ui.components.PromisePrimaryButton(
                text = if (busy) "Working…" else "Complete Commitment",
                onClick = onComplete,
                enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Complete commitment" },
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        if (commitment.canSnooze) {
            app.promise.android.ui.components.PromiseSecondaryButton(
                text = "Snooze",
                onClick = onSnooze,
                enabled = !busy,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        if (commitment.canWait) {
            app.promise.android.ui.components.PromiseSecondaryButton(
                text = "Mark waiting",
                onClick = onWait,
                enabled = !busy,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        if (commitment.canUnsnooze) {
            app.promise.android.ui.components.PromiseSecondaryButton(
                text = "Unsnooze",
                onClick = onUnsnooze,
                enabled = !busy,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        if (commitment.canCancel) {
            TextButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.buttonMin)
                    .semantics { contentDescription = "Cancel commitment" },
            ) {
                Text("Cancel commitment", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xxl))
    }
}
