package app.promise.android.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.ActionState
import app.promise.android.core.LoadState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalInvitePreview
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.ui.components.TabSwipeContainer
import app.promise.android.ui.navigation.LocalTabSwipeHost
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsListScreen(
    onOpenDetail: (String) -> Unit,
    viewModel: GoalsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createAction by viewModel.createAction.collectAsStateWithLifecycle()
    val inviteAction by viewModel.inviteAction.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showAiBuilder by remember { mutableStateOf(false) }
    var checkInGoal by remember { mutableStateOf<Goal?>(null) }
    val colors = PromiseThemeColors.current
    val inviteBusy = inviteAction is ActionState.InFlight

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
                modifier = Modifier.semantics { contentDescription = "New goal" },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
        ) {
            Text(
                text = "Goals",
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.sm),
            )
            FilterChipsRow(
                selected = (state as? LoadState.Ready)?.value?.filter ?: GoalListFilter.ACTIVE,
                onSelect = viewModel::selectFilter,
            )
            when (val s = state) {
                is LoadState.Loading -> {
                    app.promise.android.ui.components.PromiseListSkeleton(itemCount = 4)
                }
                is LoadState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.inset),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(s.kind.toUserMessage(), color = colors.textPrimary)
                        if (s.canRetry) {
                            TextButton(onClick = viewModel::refresh) {
                                Text("Try again", color = colors.accent)
                            }
                        }
                    }
                }
                is LoadState.Ready -> {
                    val swipeHost = LocalTabSwipeHost.current
                    TabSwipeContainer(
                        currentIndex = swipeHost?.currentIndex ?: 2,
                        tabCount = swipeHost?.tabCount ?: 4,
                        enabled = swipeHost?.enabled == true,
                        modalBlocking = showCreate || checkInGoal != null,
                        onSwipe = { direction -> swipeHost?.onSwipe(direction) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        PullToRefreshBox(
                            isRefreshing = s.isRefreshing,
                            onRefresh = { viewModel.refresh(fromPull = true) },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (s.value.items.isEmpty()) {
                                EmptyGoals(
                                    filter = s.value.filter,
                                    onCreate = { showCreate = true },
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(
                                        horizontal = Spacing.inset,
                                        vertical = Spacing.sm,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                ) {
                                    items(
                                        s.value.items,
                                        key = { item ->
                                            when (item) {
                                                is GoalListItem.Membership -> item.goal.id
                                                is GoalListItem.Invite -> item.preview.id
                                            }
                                        },
                                    ) { item ->
                                        when (item) {
                                            is GoalListItem.Membership -> GoalRow(
                                                goal = item.goal,
                                                onClick = { onOpenDetail(item.goal.id) },
                                                onCheckIn = {
                                                    if (item.goal.trackingKind == GoalTrackingKind.BINARY) {
                                                        viewModel.quickCheckIn(
                                                            item.goal.id,
                                                            CheckInInput(status = GoalCheckInStatus.COMPLETED),
                                                        )
                                                    } else {
                                                        checkInGoal = item.goal
                                                    }
                                                },
                                            )
                                            is GoalListItem.Invite -> InvitePreviewRow(
                                                preview = item.preview,
                                                busy = inviteBusy,
                                                onAccept = { viewModel.acceptInvite(item.preview.id) },
                                                onDecline = { viewModel.declineInvite(item.preview.id) },
                                                onOpenDetail = { onOpenDetail(item.preview.id) },
                                            )
                                        }
                                    }
                                    item {
                                        LaunchedEffect(s.value.items.size) {
                                            viewModel.loadMore()
                                        }
                                        Spacer(modifier = Modifier.height(88.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    if (showCreate) {
        CreateGoalSheet(
            action = createAction,
            timeZoneId = (state as? LoadState.Ready)?.value?.timeZoneId ?: "Asia/Kolkata",
            onDismiss = {
                showCreate = false
                viewModel.clearCreateError()
            },
            onSubmit = { input ->
                viewModel.create(input) {
                    showCreate = false
                }
            },
            onOpenAiBuilder = {
                showCreate = false
                showAiBuilder = true
            },
        )
    }

    if (showAiBuilder) {
        app.promise.android.ui.ai.GoalAiBuilderSheet(
            onDismiss = { showAiBuilder = false },
            onSuggestGoal = { prompt ->
                val timeZoneId = (state as? LoadState.Ready)?.value?.timeZoneId ?: "Asia/Kolkata"
                viewModel.suggestGoal(prompt, timeZoneId)
            },
            onConfirmCreate = { input ->
                viewModel.create(input) {
                    showAiBuilder = false
                }
            },
        )
    }

    checkInGoal?.let { goal ->
        CheckInSheet(
            goal = goal,
            action = ActionState.Idle,
            onDismiss = { checkInGoal = null },
            onSubmit = { input ->
                viewModel.quickCheckIn(goal.id, input)
                checkInGoal = null
            },
        )
    }
}

@Composable
private fun FilterChipsRow(
    selected: GoalListFilter,
    onSelect: (GoalListFilter) -> Unit,
) {
    val colors = PromiseThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.inset),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        GoalListFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (isSelected) colors.surfaceMuted else MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(filter) },
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = filter.label(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(Spacing.xs))
}

@Composable
fun GoalRow(
    goal: Goal,
    onClick: () -> Unit,
    onCheckIn: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val progressLine = GoalPresentation.progressLine(goal)
    val streak = GoalPresentation.streakLine(goal)
    val statusPrefix = if (goal.status == GoalStatus.PAUSED) "Paused, " else ""
    val sharedDesc = if (goal.isShared) "Shared goal" else "Personal goal"
    val streakDesc = if (streak != null) ", $streak streak" else ""
    val readableProgress = progressLine.replace("/", " of ")
    val goalDesc = "$statusPrefix${goal.title}, $sharedDesc, ${goalMetaLine(goal)}, $readableProgress$streakDesc"

    app.promise.android.ui.components.PromiseCardSurface(
        onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = goalDesc
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (goal.status == GoalStatus.PAUSED) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.warning),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                    }
                    Text(
                        text = goalMetaLine(goal),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                if (goal.isShared && (goal.unreadChatCount > 0 || goal.latestChatMessage != null)) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (goal.unreadChatCount > 0) colors.accent.copy(alpha = 0.15f) else colors.surfaceMuted)
                            .border(
                                width = 1.dp,
                                color = if (goal.unreadChatCount > 0) colors.accent.copy(alpha = 0.35f) else Color.Transparent,
                                shape = RoundedCornerShape(Radius.sm),
                            )
                            .padding(horizontal = Spacing.xs + 2.dp, vertical = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "💬",
                            fontSize = 11.sp,
                        )
                        if (goal.unreadChatCount > 0) {
                            Text(
                                text = "${goal.unreadChatCount} new",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Text(
                            text = goal.latestChatMessage?.let { "${it.senderName}: ${it.text}" } ?: "Group chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (goal.unreadChatCount > 0) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (streak != null) {
                app.promise.android.ui.components.PromiseStreakBadge(
                    streakText = streak,
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            app.promise.android.ui.components.PromiseMicroLabel(
                text = if (goal.trackingKind == GoalTrackingKind.COUNT) "WEEKLY PROGRESS" else "CONSISTENCY",
            )
            Text(
                text = progressLine,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        app.promise.android.ui.components.PromiseLinearProgressBar(
            progress = GoalPresentation.progressFraction(goal),
        )
        if (GoalPresentation.needsCheckInToday(goal)) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onCheckIn,
                    modifier = Modifier
                        .heightIn(min = TouchTarget.min)
                        .semantics { contentDescription = "Check in" },
                ) {
                    Text(
                        text = "Check in today",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitePreviewRow(
    preview: GoalInvitePreview,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .padding(vertical = Spacing.sm)
            .semantics { contentDescription = "Invitation: ${preview.title}" },
    ) {
        Text(
            text = preview.title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
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
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row {
            TextButton(
                onClick = onAccept,
                enabled = !busy,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Accept invitation to ${preview.title}" },
            ) {
                Text("Accept", color = colors.accent)
            }
            TextButton(
                onClick = onDecline,
                enabled = !busy,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Decline invitation to ${preview.title}" },
            ) {
                Text("Decline", color = colors.textSecondary)
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        )
    }
}

@Composable
fun ProgressTrack(fraction: Float) {
    val colors = PromiseThemeColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(colors.accent),
        )
    }
}

@Composable
private fun EmptyGoals(
    filter: GoalListFilter,
    onCreate: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.inset),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = filter.emptyTitle(),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = filter.emptyBody(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        if (filter == GoalListFilter.ACTIVE) {
            Spacer(modifier = Modifier.height(Spacing.md))
            TextButton(onClick = onCreate) {
                Text("New goal", color = colors.accent)
            }
        }
    }
}

private fun goalMetaLine(goal: Goal): String {
    val parts = mutableListOf(GoalPresentation.recurrenceLabel(goal))
    GoalPresentation.sharedMetaLine(goal)?.let { parts += it }
    if (goal.status == GoalStatus.PAUSED) parts += "Paused"
    if (goal.status == GoalStatus.COMPLETED) parts += "Completed"
    if (goal.status == GoalStatus.CANCELLED) parts += "Cancelled"
    return parts.joinToString(" · ")
}

private fun GoalListFilter.label(): String = when (this) {
    GoalListFilter.ACTIVE -> "Active"
    GoalListFilter.PAUSED -> "Paused"
    GoalListFilter.COMPLETED -> "Completed"
}

private fun GoalListFilter.emptyTitle(): String = when (this) {
    GoalListFilter.ACTIVE -> "No active goals yet."
    GoalListFilter.PAUSED -> "No paused goals."
    GoalListFilter.COMPLETED -> "No completed goals yet."
}

private fun GoalListFilter.emptyBody(): String = when (this) {
    GoalListFilter.ACTIVE -> "Start a practice and build consistency."
    else -> "Check another filter, or come back later."
}
