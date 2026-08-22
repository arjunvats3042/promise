package app.promise.android.ui.goals

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.promise.android.core.ActionState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalParticipantRole
import app.promise.android.domain.GoalParticipantStatus
import app.promise.android.ui.components.PromiseMicroLabel
import app.promise.android.ui.components.PromiseModalSheet
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsSheet(
    goal: Goal,
    roster: List<GoalParticipant>,
    inviteSheetAction: ActionState,
    onDismiss: () -> Unit,
    onRemove: (GoalParticipant) -> Unit,
    onLeave: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val busy = inviteSheetAction is ActionState.InFlight
    val actionError = (inviteSheetAction as? ActionState.Failed)?.kind

    val activeMembers = roster.filter { it.status == GoalParticipantStatus.ACTIVE }
    val pendingInvites = roster.filter { it.status == GoalParticipantStatus.INVITED }

    PromiseModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.inset, vertical = Spacing.md)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Participants",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.md))

            if (activeMembers.isNotEmpty()) {
                PromiseMicroLabel("ACTIVE MEMBERS (${activeMembers.size})")
                Spacer(modifier = Modifier.height(Spacing.xs))
                activeMembers.forEach { participant ->
                    ParticipantRow(
                        participant = participant,
                        canRemove = goal.canManageParticipants &&
                            participant.role != GoalParticipantRole.OWNER,
                        busy = busy,
                        onRemove = { onRemove(participant) },
                    )
                }
            } else {
                Text(
                    text = "No active participants.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            if (goal.canManageParticipants && pendingInvites.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                PromiseMicroLabel("PENDING INVITATIONS (${pendingInvites.size})")
                Spacer(modifier = Modifier.height(Spacing.xs))
                pendingInvites.forEach { participant ->
                    ParticipantRow(
                        participant = participant,
                        canRemove = true,
                        busy = busy,
                        onRemove = { onRemove(participant) },
                        actionLabel = "Revoke",
                    )
                }
            }

            if (actionError != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = actionError.toUserMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (goal.canLeave) {
                Spacer(modifier = Modifier.height(Spacing.section))
                TextButton(
                    onClick = onLeave,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Leave goal" },
                ) {
                    Text("Leave goal", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: GoalParticipant,
    canRemove: Boolean,
    busy: Boolean,
    onRemove: () -> Unit,
    actionLabel: String = "Remove",
) {
    val colors = PromiseThemeColors.current
    val initial = participant.userName.firstOrNull()?.uppercase() ?: "?"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.userName,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (participant.role == GoalParticipantRole.OWNER) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Owner",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent,
                        )
                    }
                } else {
                    Text(
                        text = if (participant.status == GoalParticipantStatus.INVITED) "Invited" else "Member",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        if (canRemove) {
            TextButton(
                onClick = onRemove,
                enabled = !busy,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "$actionLabel ${participant.userName}" },
            ) {
                Text(actionLabel, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
