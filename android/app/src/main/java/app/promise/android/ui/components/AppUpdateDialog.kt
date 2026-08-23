package app.promise.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.update.AppReleaseInfo
import app.promise.android.update.AppUpdateDownloadState
import app.promise.android.update.AppUpdateUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateDialog(
    uiState: AppUpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState !is AppUpdateUiState.UpdateAvailable) return

    val release = uiState.release
    val downloadState = uiState.downloadState
    val colors = PromiseThemeColors.current
    val isDownloading = downloadState is AppUpdateDownloadState.Downloading

    val talkbackSummary = buildString {
        append("New version available. Promise ")
        append(release.versionName)
        append(" is ready to install.")
        if (!release.releaseNotes.isNullOrBlank()) {
            append(" Release notes: ")
            append(release.releaseNotes)
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.inset)
            .semantics {
                contentDescription = talkbackSummary
            },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(Radius.lg)),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.lg),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = Motion.standardTween(Motion.CompletionMs))
                    .padding(Spacing.cardPadding),
            ) {
                // Icon + Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "New version available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Version ${release.versionName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "Promise ${release.versionName} is ready to download and install.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )

                // Optional release notes
                if (!release.releaseNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.surfaceMuted)
                            .padding(Spacing.sm)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = release.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                // Download Progress Feedback
                AnimatedVisibility(visible = isDownloading) {
                    val progress = (downloadState as? AppUpdateDownloadState.Downloading)?.progressPercent ?: 0f
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.md),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Downloading update...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            if (progress > 0f) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.accent,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        PromiseLinearProgressBar(
                            progress = progress,
                            color = colors.accent,
                            trackColor = colors.surfaceMuted,
                        )
                    }
                }

                // Error Feedback
                if (downloadState is AppUpdateDownloadState.Error) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = downloadState.userMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isDownloading) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .heightIn(min = TouchTarget.min)
                                .semantics { contentDescription = "Dismiss update for now" },
                        ) {
                            Text(
                                text = "Later",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.sm))
                    }

                    TextButton(
                        onClick = onUpdate,
                        enabled = !isDownloading,
                        modifier = Modifier
                            .heightIn(min = TouchTarget.min)
                            .clip(RoundedCornerShape(Radius.button))
                            .background(if (isDownloading) colors.surfaceRaised else colors.primaryControl)
                            .padding(horizontal = Spacing.md)
                            .semantics {
                                contentDescription = if (isDownloading) {
                                    "Downloading update"
                                } else {
                                    "Update to version ${release.versionName} now"
                                }
                            },
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = colors.onPrimaryControl,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                        }
                        Text(
                            text = if (isDownloading) "Updating..." else "Update",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDownloading) colors.textSecondary else colors.onPrimaryControl,
                        )
                    }
                }
            }
        }
    }
}
