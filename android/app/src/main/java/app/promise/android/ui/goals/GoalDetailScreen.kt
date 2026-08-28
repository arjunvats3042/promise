package app.promise.android.ui.goals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalParticipantStatus
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.GroupMilestone
import app.promise.android.domain.GroupSummary
import app.promise.android.domain.WeeklyReflection
import app.promise.android.ui.components.AvatarSize
import app.promise.android.ui.components.PromiseAvatar
import app.promise.android.ui.components.PromiseAvatarStack
import app.promise.android.ui.components.PromiseCardSurface
import app.promise.android.ui.components.PromiseErrorBanner
import app.promise.android.ui.components.PromiseGoalHero
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.components.PromiseLinearProgressBar
import app.promise.android.ui.components.PromiseMicroLabel
import app.promise.android.ui.components.PromisePrimaryButton
import app.promise.android.ui.components.PromiseSecondaryButton
import app.promise.android.ui.components.PromiseSectionHeader
import app.promise.android.ui.components.PromiseStatusChip
import app.promise.android.ui.components.PromiseStreakBadge
import app.promise.android.ui.components.PromiseTimelineItem
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget

private enum class ConfirmKind { Pause, Resume, Complete, Cancel, Leave, ConvertToShared }

@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onNotFound: () -> Unit,
    onOpenChat: ((String) -> Unit)? = null,
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val inviteAction by viewModel.inviteAction.collectAsStateWithLifecycle()
    val inviteSheetAction by viewModel.inviteSheetAction.collectAsStateWithLifecycle()
    val lookupState by viewModel.lookupState.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ConfirmKind?>(null) }
    var showParticipants by remember { mutableStateOf(false) }
    var showInviteParticipant by remember { mutableStateOf(false) }
    var showActivityHistory by remember { mutableStateOf(false) }
    var participantToRemove by remember { mutableStateOf<GoalParticipant?>(null) }
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
            when (val ui = s.value) {
                is GoalDetailUi.Full -> {
                    DetailContent(
                        ui = ui,
                        busy = busy,
                        actionError = actionError,
                        inviteBusy = inviteAction is ActionState.InFlight,
                        onCheckIn = viewModel::checkIn,
                        onPause = { confirm = ConfirmKind.Pause },
                        onResume = { confirm = ConfirmKind.Resume },
                        onComplete = { confirm = ConfirmKind.Complete },
                        onCancel = { confirm = ConfirmKind.Cancel },
                        onLeave = { confirm = ConfirmKind.Leave },
                        onConvertToShared = { confirm = ConfirmKind.ConvertToShared },
                        onManageParticipants = { showParticipants = true },
                        onInviteParticipant = { showInviteParticipant = true },
                        onOpenChat = onOpenChat,
                        onOpenActivityHistory = { showActivityHistory = true },
                        onBack = onBack,
                        onClearError = viewModel::clearActionError,
                    )
                }
                is GoalDetailUi.Invite -> {
                    InviteContent(
                        ui = ui,
                        inviteAction = inviteAction,
                        onAccept = { viewModel.acceptInvite() },
                        onDecline = { viewModel.declineInvite(onBack) },
                        onBack = onBack,
                        onClearError = viewModel::clearInviteActionError,
                    )
                }
            }
        }
        else -> Unit
    }

    when (confirm) {
        ConfirmKind.Pause -> ConfirmDialog(
            title = "Pause this goal?",
            body = "Your streak is preserved. You can resume anytime.",
            confirmLabel = "Pause",
            destructive = false,
            onConfirm = {
                confirm = null
                viewModel.pause()
            },
            onDismiss = { confirm = null },
        )
        ConfirmKind.Resume -> ConfirmDialog(
            title = "Resume this goal?",
            body = "You'll be able to check in again starting today.",
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
            body = "Congratulations on finishing your practice.",
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
            body = "This can't be undone.",
            confirmLabel = "Cancel goal",
            destructive = true,
            onConfirm = {
                confirm = null
                viewModel.cancel()
            },
            onDismiss = { confirm = null },
        )
        ConfirmKind.Leave -> ConfirmDialog(
            title = "Leave this goal?",
            body = "You'll no longer be a participant.",
            confirmLabel = "Leave",
            destructive = true,
            onConfirm = {
                confirm = null
                viewModel.leave(onBack)
            },
            onDismiss = { confirm = null },
        )
        ConfirmKind.ConvertToShared -> ConfirmDialog(
            title = "Upgrade to Shared Goal?",
            body = "You'll be able to invite friends, track team streaks, celebrate milestones together, and use group chat.\n\nNote: Once upgraded to a shared goal, it cannot be changed back to an individual goal.",
            confirmLabel = "Upgrade to Shared Goal",
            destructive = false,
            onConfirm = {
                confirm = null
                viewModel.convertToShared {
                    showInviteParticipant = true
                }
            },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }

    participantToRemove?.let { participant ->
        val isInvite = participant.status == GoalParticipantStatus.INVITED
        ConfirmDialog(
            title = if (isInvite) "Revoke invitation?" else "Remove participant?",
            body = if (isInvite) {
                "Revoke invitation for ${participant.userName}?"
            } else {
                "Remove ${participant.userName} from this goal? Their past check-in history will be preserved."
            },
            confirmLabel = if (isInvite) "Revoke" else "Remove",
            destructive = true,
            onConfirm = {
                val userToRemove = participant.userId
                participantToRemove = null
                viewModel.removeParticipant(userToRemove)
            },
            onDismiss = { participantToRemove = null },
        )
    }

    if (showParticipants) {
        val readyUi = (state as? LoadState.Ready)?.value as? GoalDetailUi.Full
        if (readyUi != null) {
            ParticipantsSheet(
                goal = readyUi.goal,
                roster = readyUi.roster,
                inviteSheetAction = inviteSheetAction,
                onDismiss = { showParticipants = false },
                onRemove = { participantToRemove = it },
                onReinvite = { viewModel.reinviteParticipant(participantId = it.id, userId = it.userId) },
                onTransferOwnership = { viewModel.transferOwnership(participantId = it.id, userId = it.userId) },
                onLeave = { confirm = ConfirmKind.Leave },
            )
        }
    }

    if (showInviteParticipant) {
        InviteParticipantSheet(
            inviteSheetAction = inviteSheetAction,
            lookupState = lookupState,
            onDismiss = {
                showInviteParticipant = false
                viewModel.clearInviteSheetActionError()
                viewModel.clearLookup()
            },
            onLookup = viewModel::lookupUser,
            onInvite = viewModel::inviteParticipant,
            onClearLookup = viewModel::clearLookup,
            onInviteSucceeded = {
                showInviteParticipant = false
                viewModel.clearLookup()
            },
        )
    }

    if (showActivityHistory) {
        val activityState by viewModel.activityState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            viewModel.loadFullActivity()
        }
        GoalActivityHistorySheet(
            state = activityState,
            onLoadMore = viewModel::loadMoreActivity,
            onDismiss = { showActivityHistory = false },
        )
    }
}

@Composable
private fun DetailContent(
    ui: GoalDetailUi.Full,
    busy: Boolean,
    actionError: ErrorKind?,
    inviteBusy: Boolean,
    onCheckIn: (CheckInInput) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onLeave: () -> Unit,
    onConvertToShared: () -> Unit,
    onManageParticipants: () -> Unit,
    onInviteParticipant: () -> Unit,
    onOpenChat: ((String) -> Unit)?,
    onOpenActivityHistory: () -> Unit,
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
            .padding(horizontal = Spacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.surfaceMuted)
                    .clickable(onClick = onBack)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                Text(
                    text = "← Back",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        AnimatedContent(
            targetState = goal.status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "goal-status",
        ) { status ->
            PromiseMicroLabel(
                text = GoalPresentation.statusLabel(status),
                color = colors.accent,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = goal.title,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
            text = detailMeta(goal),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        GoalPresentation.sharedMetaLine(goal)?.let { sharedMeta ->
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = sharedMeta,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Hero Progress Card
        PromiseGoalHero(
            sectionLabel = if (goal.isShared) "YOUR PROGRESS" else if (goal.trackingKind == GoalTrackingKind.COUNT) "WEEKLY PROGRESS" else "CONSISTENCY",
            primaryProgressText = GoalPresentation.progressLine(goal),
            progressFraction = GoalPresentation.progressFraction(goal),
            progressSubtitle = GoalPresentation.streakLine(goal)?.let { "$it streak" },
            streakDays = goal.currentStreak,
            nextCheckInLabel = if (ui.canCheckInToday()) "Today" else "Scheduled",
            collectiveText = GoalPresentation.collectiveLine(goal),
        )

        if (goal.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = goal.description,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
        }

        GoalPresentation.collectiveLine(goal)?.let { collective ->
            Spacer(modifier = Modifier.height(Spacing.md))
            PromiseCardSurface {
                PromiseMicroLabel("EVERYONE")
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = collective.removePrefix("Everyone: ").trimStart(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
        }

        if (goal.isShared && goal.groupSummary != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            GroupProgressSummaryCard(summary = goal.groupSummary)
        }

        if (goal.isShared && goal.milestones.any { it.achieved }) {
            Spacer(modifier = Modifier.height(Spacing.md))
            GroupMilestoneCard(milestones = goal.milestones)
        }

        if (goal.isShared && goal.weeklyReflection != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            WeeklyReflectionCard(reflection = goal.weeklyReflection)
        }

        if (actionError != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            PromiseErrorBanner(
                message = actionError.toUserMessage(),
                onRetry = onClearError,
            )
        }

        if (ui.canCheckInToday()) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            InlineCheckIn(
                goal = goal,
                prompt = ui.checkInPrompt(),
                busy = busy,
                onSubmit = onCheckIn,
            )
        }

        if (!goal.isShared && goal.isOwnerViewer && !goal.isTerminal) {
            Spacer(modifier = Modifier.height(Spacing.section))
            PromiseCardSurface(
                onClick = onConvertToShared,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "🤝",
                            fontSize = 24.sp,
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = "Make this a Shared Goal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Text(
                                text = "Invite partners, track group streaks & chat",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    Text(
                        text = "Upgrade",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                    )
                }
            }
        }

        if (goal.isShared) {
            Spacer(modifier = Modifier.height(Spacing.section))
            PromiseSectionHeader(
                title = "Collaboration",
                actionLabel = if (goal.canManageParticipants && !inviteBusy) "+ Invite" else null,
                onActionClick = onInviteParticipant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onManageParticipants,
                    modifier = Modifier.semantics { contentDescription = "View participants" },
                ) {
                    Text("View ${goal.participants.size.takeIf { it > 0 } ?: ""} Participants", color = colors.accent)
                }
            }

            if (goal.canViewChat && onOpenChat != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                PromiseCardSurface(
                    modifier = Modifier.clickable { onOpenChat(goal.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Group Conversation",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                    )
                                    if (goal.unreadChatCount > 0) {
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(Radius.sm))
                                                .background(colors.accent)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "${goal.unreadChatCount} NEW",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.surfaceMuted,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = goal.latestChatMessage?.let { "${it.senderName}: ${it.text}" } ?: "Tap to view and send messages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (goal.unreadChatCount > 0) colors.textPrimary else colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    }
                }
            }

            if (ui.recentActivity.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.md))
                PromiseCardSurface {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PromiseMicroLabel("RECENT ACTIVITY")
                        Text(
                            text = "View all",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accent,
                            modifier = Modifier
                                .clickable { onOpenActivityHistory() }
                                .padding(4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Column {
                        ui.recentActivity.take(4).forEach { item ->
                            ActivityRowItem(item = item)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.section))
        if (goal.canPause) {
            PromiseSecondaryButton(
                text = "Pause",
                onClick = onPause,
                enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Pause goal" },
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        if (goal.canResume) {
            PromiseSecondaryButton(
                text = "Resume",
                onClick = onResume,
                enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Resume goal" },
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        if (goal.canCompleteGoal) {
            PromisePrimaryButton(
                text = if (busy) "Working…" else "Complete",
                onClick = onComplete,
                enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Complete goal" },
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        if (goal.canCancel) {
            TextButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.buttonMin)
                    .semantics { contentDescription = "Cancel goal" },
            ) {
                Text("Cancel goal", color = MaterialTheme.colorScheme.error)
            }
        }
        if (goal.canLeave) {
            TextButton(
                onClick = onLeave,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.buttonMin)
                    .semantics { contentDescription = "Leave goal" },
            ) {
                Text("Leave goal", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.section))
        PromiseSectionHeader(
            title = "History",
            subtitle = if (ui.checkIns.isNotEmpty()) "${ui.checkIns.size} check-ins" else null,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        if (ui.checkIns.isEmpty()) {
            Text(
                text = "No check-ins yet.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        } else {
            Column {
                ui.checkIns.forEach { row ->
                    HistoryRow(row)
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun InviteContent(
    ui: GoalDetailUi.Invite,
    inviteAction: ActionState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val preview = ui.preview
    val busy = inviteAction is ActionState.InFlight
    val inviteError = (inviteAction as? ActionState.Failed)?.kind

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
        Text(
            text = "Invitation",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = preview.title,
            style = MaterialTheme.typography.displayLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = "Invited by ${preview.inviterName}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Text(
            text = GoalPresentation.inviteScheduleLabel(preview),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Text(
            text = preview.timezone,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        preview.invitationExpiresAt?.let { expires ->
            Text(
                text = "Expires $expires",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        if (preview.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.section))
            Text(
                text = preview.description,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
        }
        if (inviteError != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(inviteError.toUserMessage(), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onClearError) {
                Text("Dismiss", color = colors.textSecondary)
            }
        }
        Spacer(modifier = Modifier.height(Spacing.section))
        Button(
            onClick = onAccept,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Accept invitation" },
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            shape = RoundedCornerShape(Radius.sm),
        ) {
            Text(if (busy) "Working…" else "Accept")
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        TextButton(
            onClick = onDecline,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Decline invitation" },
        ) {
            Text("Decline", color = colors.textSecondary)
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
        app.promise.android.ui.components.PromisePrimaryButton(
            text = if (busy) "Saving…" else "Done",
            onClick = {
                val value = valueText.toIntOrNull() ?: return@PromisePrimaryButton
                onSubmit(CheckInInput(status = GoalCheckInStatus.COMPLETED, value = value))
            },
            enabled = !busy && valueText.toIntOrNull() != null,
            modifier = Modifier.semantics { contentDescription = "Save check-in" },
        )
        TextButton(
            onClick = { onSubmit(CheckInInput(status = GoalCheckInStatus.SKIPPED)) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min),
        ) {
            Text("Skip today", color = colors.textSecondary)
        }
    } else {
        app.promise.android.ui.components.PromisePrimaryButton(
            text = if (busy) "Saving…" else "Done",
            onClick = { onSubmit(CheckInInput(status = GoalCheckInStatus.COMPLETED)) },
            enabled = !busy,
            modifier = Modifier.semantics { contentDescription = "Mark done" },
        )
        TextButton(
            onClick = { onSubmit(CheckInInput(status = GoalCheckInStatus.SKIPPED)) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min),
        ) {
            Text("Skip today", color = colors.textSecondary)
        }
    }
}

@Composable
private fun HistoryRow(checkIn: GoalCheckIn) {
    val status = when (checkIn.status) {
        GoalCheckInStatus.COMPLETED -> "Done"
        GoalCheckInStatus.SKIPPED -> "Skipped"
    }
    val valuePart = checkIn.value?.let { " · $it" }.orEmpty()
    PromiseTimelineItem(
        title = status + valuePart,
        timestamp = checkIn.periodDate,
        isCompleted = checkIn.status == GoalCheckInStatus.COMPLETED,
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

@Composable
private fun GroupProgressSummaryCard(summary: GroupSummary) {
    val colors = PromiseThemeColors.current
    app.promise.android.ui.components.PromiseCardSurface {
        app.promise.android.ui.components.PromiseMicroLabel("GROUP PROGRESS SUMMARY")
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = summary.headline,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        app.promise.android.ui.components.PromiseLinearProgressBar(
            progress = summary.currentPeriodCompletionRate,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${summary.todayCompletedCount} of ${summary.activeParticipantsCount} active completed today",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                text = "${(summary.currentPeriodCompletionRate * 100).toInt()}% pace",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun GroupMilestoneCard(milestones: List<GroupMilestone>) {
    val colors = PromiseThemeColors.current
    val achievedMilestone = milestones.lastOrNull { it.achieved } ?: return
    app.promise.android.ui.components.PromiseCardSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                app.promise.android.ui.components.PromiseMicroLabel("GROUP MILESTONE")
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = achievedMilestone.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                if (achievedMilestone.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = achievedMilestone.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.md))
                    .background(colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Achieved",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun WeeklyReflectionCard(reflection: WeeklyReflection) {
    val colors = PromiseThemeColors.current
    app.promise.android.ui.components.PromiseCardSurface {
        app.promise.android.ui.components.PromiseMicroLabel("WEEKLY GROUP REFLECTION")
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = reflection.reflectionText,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
        if (reflection.trendText.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = reflection.trendText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
