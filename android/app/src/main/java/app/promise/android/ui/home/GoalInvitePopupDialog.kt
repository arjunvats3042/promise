package app.promise.android.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.promise.android.R
import app.promise.android.domain.GoalInvitePreview
import app.promise.android.ui.components.PromisePrimaryButton
import app.promise.android.ui.components.PromiseSecondaryButton
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

@Composable
fun GoalInvitePopupDialog(
    invites: List<GoalInvitePreview>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (invites.isEmpty()) return

    var currentIndex by remember(invites) { mutableIntStateOf(0) }
    val invite = invites.getOrNull(currentIndex) ?: invites.first()
    val total = invites.size
    val colors = PromiseThemeColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.5.dp,
                        color = colors.accent.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(24.dp),
                    ),
                color = colors.surfaceRaised,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Header Bar with Badge and Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(colors.accent.copy(alpha = 0.15f))
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Group,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Shared Goal Invitation",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                        }

                        if (total > 1) {
                            Text(
                                text = "${currentIndex + 1} of $total",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Inviter Avatar / Name
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.2f))
                            .border(1.dp, colors.accent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (invite.inviterName.takeIf { it.isNotBlank() } ?: "P").take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = "${invite.inviterName.ifBlank { "A partner" }} invited you",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    // Goal Title
                    Text(
                        text = invite.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (invite.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = invite.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Summary Details Chip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(colors.surfaceMuted)
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "RECURRENCE",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                fontSize = 10.sp,
                            )
                            Text(
                                text = invite.recurrenceKind.name.replace("_", " "),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                        }

                        if (invite.targetValue != null && invite.targetValue > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TARGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    fontSize = 10.sp,
                                )
                                Text(
                                    text = "${invite.targetValue} ${invite.targetUnit}".trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    // Action Buttons
                    PromisePrimaryButton(
                        text = "Accept & Join Goal",
                        onClick = {
                            onAccept(invite.id)
                            if (total > 1 && currentIndex < total - 1) {
                                currentIndex++
                            } else {
                                onDismiss()
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    PromiseSecondaryButton(
                        text = "Decline",
                        onClick = {
                            onDecline(invite.id)
                            if (total > 1 && currentIndex < total - 1) {
                                currentIndex++
                            } else {
                                onDismiss()
                            }
                        },
                    )

                    if (total > 1) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (currentIndex > 0) {
                                Text(
                                    text = "← Previous",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.accent,
                                    modifier = Modifier
                                        .padding(horizontal = Spacing.sm)
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .padding(Spacing.xxs),
                                )
                            }
                            if (currentIndex < total - 1) {
                                Text(
                                    text = "Next invite →",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.accent,
                                    modifier = Modifier
                                        .padding(horizontal = Spacing.sm)
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .padding(Spacing.xxs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
