package app.promise.android.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.promise.android.core.ActionState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalParticipantRole
import app.promise.android.domain.GoalParticipantStatus
import app.promise.android.ui.components.PromiseModalSheet
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsSheet(
    goal: Goal,
    roster: List<GoalParticipant>,
    inviteSheetAction: ActionState,
    onDismiss: () -> Unit,
    onRemove: (userId: String) -> Unit,
    onLeave: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val busy = inviteSheetAction is ActionState.InFlight
    val actionError = (inviteSheetAction as? ActionState.Failed)?.kind

    PromiseModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.md)) {
            Text(
                text = "Participants",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.md))

            if (roster.isEmpty()) {
                Text(
                    text = "No participants yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            } else {
                roster.forEach { participant ->
                    ParticipantRow(
                        participant = participant,
                        canRemove = goal.canManageParticipants &&
                            participant.role != GoalParticipantRole.OWNER,
                        busy = busy,
                        onRemove = { onRemove(participant.userId) },
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
) {
    val colors = PromiseThemeColors.current
    val roleLabel = when (participant.role) {
        GoalParticipantRole.OWNER -> "Owner"
        GoalParticipantRole.PARTICIPANT -> "Member"
    }
    val statusLabel = when (participant.status) {
        GoalParticipantStatus.INVITED -> " · Invited"
        GoalParticipantStatus.ACTIVE -> ""
        GoalParticipantStatus.DECLINED -> " · Declined"
        GoalParticipantStatus.LEFT -> " · Left"
        GoalParticipantStatus.REMOVED -> " · Removed"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.userName,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Text(
                text = roleLabel + statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        if (canRemove) {
            TextButton(
                onClick = onRemove,
                enabled = !busy,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Remove ${participant.userName}" },
            ) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
