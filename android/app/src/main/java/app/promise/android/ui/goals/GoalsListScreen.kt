package app.promise.android.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.ui.components.AvatarSize
import app.promise.android.ui.components.PromiseAvatar
import app.promise.android.ui.components.PromiseCardSurface
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.components.PromiseProgressRing
import app.promise.android.ui.components.PromiseStreakBadge
import app.promise.android.ui.components.TabSwipeContainer
import app.promise.android.ui.home.HomeViewModel
import app.promise.android.ui.navigation.LocalTabSwipeHost
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsListScreen(
    onOpenDetail: (String) -> Unit,
    viewModel: GoalsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val createAction by viewModel.createAction.collectAsStateWithLifecycle()
    val inviteAction by viewModel.inviteAction.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showAiBuilder by remember { mutableStateOf(false) }
    var checkInGoal by remember { mutableStateOf<Goal?>(null) }
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val inviteBusy = inviteAction is ActionState.InFlight

    var showCelebration by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showCreate = true
                },
                containerColor = colors.accent,
                contentColor = Color.White,
                shape = RoundedCornerShape(Radius.button),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 0.dp,
                ),
                modifier = Modifier
                    .semantics { contentDescription = "Create new goal" }
                    .pressScale(0.92f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                // 1. Refined Goals Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.inset)
                        .padding(top = Spacing.sm, bottom = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Goals & Practices",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        val totalGoals = (state as? LoadState.Ready)?.value?.items?.count { it is GoalListItem.Membership } ?: 0
                        val completedToday = (state as? LoadState.Ready)?.value?.items
                            ?.filterIsInstance<GoalListItem.Membership>()
                            ?.count { !GoalPresentation.needsCheckInToday(it.goal) && it.goal.status == GoalStatus.ACTIVE } ?: 0

                        val subtitleText = when {
                            totalGoals > 0 -> "$totalGoals active practice${if (totalGoals != 1) "s" else ""} · $completedToday completed today"
                            else -> "Build quiet discipline, day by day"
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }

                    // AI Intention trigger pill
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(Radius.pill))
                            .pressScale(0.94f)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showAiBuilder = true
                            },
                        color = colors.accent.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "AI Intention",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                // 2. Segmented Filter Navigation Bar
                FilterChipsRow(
                    selected = selectedFilter,
                    onSelect = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.selectFilter(it)
                    },
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // 3. Goal Feed & Active Invites
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = s.kind.toUserMessage(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            if (s.canRetry) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                TextButton(onClick = viewModel::refresh) {
                                    Text("Try again", color = colors.accent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    is LoadState.Ready -> {
                        val swipeHost = LocalTabSwipeHost.current
                        TabSwipeContainer(
                            currentIndex = swipeHost?.currentIndex ?: 3,
                            tabCount = swipeHost?.tabCount ?: 5,
                            enabled = swipeHost?.enabled == true,
                            modalBlocking = showCreate || checkInGoal != null || showAiBuilder,
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
                                            Box(modifier = Modifier.animateItem()) {
                                                when (item) {
                                                    is GoalListItem.Membership -> GoalCard(
                                                        goal = item.goal,
                                                        onClick = { onOpenDetail(item.goal.id) },
                                                        onCheckIn = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            showCelebration = true
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
                                                    is GoalListItem.Invite -> InviteBannerCard(
                                                        preview = item.preview,
                                                        busy = inviteBusy,
                                                        onAccept = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            viewModel.acceptInvite(item.preview.id)
                                                        },
                                                        onDecline = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            viewModel.declineInvite(item.preview.id)
                                                        },
                                                        onOpenDetail = { onOpenDetail(item.preview.id) },
                                                    )
                                                }
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

            app.promise.android.ui.components.PromiseCelebrationBurst(
                trigger = showCelebration,
                onFinished = { showCelebration = false },
                modifier = Modifier.fillMaxSize(),
            )
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
            speechManager = viewModel.speechManager,
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        GoalListFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val chipBg by animateColorAsState(
                targetValue = if (isSelected) colors.accent.copy(alpha = 0.14f) else colors.surfaceMuted,
                animationSpec = Motion.standardTween(Motion.FilterChangeMs),
                label = "chip-bg",
            )
            val chipBorder by animateColorAsState(
                targetValue = if (isSelected) colors.accent.copy(alpha = 0.38f) else Color.Transparent,
                animationSpec = Motion.standardTween(Motion.FilterChangeMs),
                label = "chip-border",
            )
            val chipFg by animateColorAsState(
                targetValue = if (isSelected) colors.accent else colors.textSecondary,
                animationSpec = Motion.standardTween(Motion.FilterChangeMs),
                label = "chip-fg",
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(chipBg)
                    .border(1.dp, chipBorder, RoundedCornerShape(Radius.pill))
                    .pressScale(0.95f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = colors.accent),
                        onClick = { onSelect(filter) },
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs + 2.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = filter.label(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = chipFg,
                )
            }
        }
    }
}

/**
 * World-class redesigned Goal Card featuring rich cadence chips, team presence,
 * fluid progress ring gauge, and direct 1-tap check-in ribbon.
 */
@Composable
fun GoalCard(
    goal: Goal,
    onClick: () -> Unit,
    onCheckIn: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val progressLine = GoalPresentation.progressLine(goal)
    val fraction = GoalPresentation.progressFraction(goal)
    val streak = GoalPresentation.streakLine(goal)
    val needsCheckIn = GoalPresentation.needsCheckInToday(goal)
    val isPaused = goal.status == GoalStatus.PAUSED
    val isCompleted = goal.status == GoalStatus.COMPLETED

    PromiseCardSurface(
        onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${goal.title}, $progressLine"
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 1. TOP HEADER: Cadence / Category Pill + Streak Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Cadence chip
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(colors.surfaceMuted),
                        color = colors.surfaceMuted,
                    ) {
                        Text(
                            text = GoalPresentation.recurrenceLabel(goal).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }

                    // Shared Goal presence pill
                    if (goal.isShared) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(colors.accent.copy(alpha = 0.12f)),
                            color = colors.accent.copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Group,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "${goal.participants.size} MEMBERS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    fontSize = 9.5.sp,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                        }
                    }
                }

                // Streak counter or status badge
                when {
                    isPaused -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(colors.warning.copy(alpha = 0.15f)),
                            color = colors.warning.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "PAUSED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.warning,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    streak != null -> {
                        PromiseStreakBadge(streakText = streak)
                    }
                }
            }

            // 2. MAIN ROW: Progress Visual + Goal Title + Team Meta
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Visual Anchor: Circular Progress Ring Gauge
                PromiseProgressRing(
                    progress = fraction,
                    size = 46.dp,
                    strokeWidth = 4.dp,
                ) {
                    if (fraction >= 1f && !isPaused) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.md))

                // Title + Description / Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (goal.isShared && goal.participants.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        // Teammate Avatars Cluster
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            TeammateAvatarCluster(participants = goal.participants)
                            GoalPresentation.collectiveLine(goal)?.let { teamStatus ->
                                Text(
                                    text = teamStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // 3. UNREAD CHAT SNIPPET (If Shared)
            if (goal.isShared && (goal.unreadChatCount > 0 || goal.latestChatMessage != null)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(if (goal.unreadChatCount > 0) colors.accent.copy(alpha = 0.12f) else colors.surfaceMuted)
                        .border(
                            width = 1.dp,
                            color = if (goal.unreadChatCount > 0) colors.accent.copy(alpha = 0.3f) else Color.Transparent,
                            shape = RoundedCornerShape(Radius.sm),
                        ),
                    color = if (goal.unreadChatCount > 0) colors.accent.copy(alpha = 0.12f) else colors.surfaceMuted,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (goal.unreadChatCount > 0) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                        if (goal.unreadChatCount > 0) {
                            Text(
                                text = "${goal.unreadChatCount} new",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Text(
                            text = goal.latestChatMessage?.let { "${it.senderName}: ${it.text}" } ?: "Group discussion active",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (goal.unreadChatCount > 0) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            PromiseHairlineDivider()

            // 4. BOTTOM ACTION & DAILY EXECUTION RIBBON
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Today's Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        isPaused -> {
                            Icon(
                                imageVector = Icons.Outlined.PauseCircleOutline,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Goal is currently paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        needsCheckIn -> {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                            )
                            Text(
                                text = progressLine,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = progressLine,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent,
                            )
                        }
                    }
                }

                // Tactile 1-Tap Check-In Button
                if (needsCheckIn) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(colors.accent)
                            .pressScale(0.92f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = Color.White),
                                onClick = onCheckIn,
                            )
                            .padding(horizontal = Spacing.md, vertical = 6.dp)
                            .heightIn(min = TouchTarget.min)
                            .semantics { contentDescription = "Check in to ${goal.title}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (goal.trackingKind == GoalTrackingKind.COUNT) "Log Progress" else "Check In",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overlapping avatar cluster to display active participants in shared goals.
 */
@Composable
private fun TeammateAvatarCluster(participants: List<GoalParticipant>) {
    val displayParticipants = participants.take(3)
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        displayParticipants.forEach { participant ->
            val initials = HomeViewModel.initialsFor(participant.userName.ifBlank { "User" })
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            ) {
                PromiseAvatar(
                    initials = initials,
                    photoPath = participant.avatarUrl,
                    size = AvatarSize.SM,
                )
            }
        }
        if (participants.size > 3) {
            val colors = PromiseThemeColors.current
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceMuted)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${participants.size - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * Elevated Shared Goal Invitation Card with inviter presence and 1-tap accept.
 */
@Composable
private fun InviteBannerCard(
    preview: GoalInvitePreview,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val initials = HomeViewModel.initialsFor(preview.inviterName.ifBlank { "Host" })

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(Radius.lg))
            .clickable(onClick = onOpenDetail),
        color = colors.surfaceRaised,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(colors.accent.copy(alpha = 0.15f)),
                    color = colors.accent.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MailOutline,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "SHARED GOAL INVITATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }

                Text(
                    text = GoalPresentation.inviteScheduleLabel(preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PromiseAvatar(
                    initials = initials,
                    photoPath = null,
                    size = AvatarSize.MD,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preview.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Invited by ${preview.inviterName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))
            PromiseHairlineDivider()
            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDecline,
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = TouchTarget.min),
                ) {
                    Text("Decline", color = colors.textSecondary)
                }
                Spacer(modifier = Modifier.width(Spacing.xs))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(colors.accent)
                        .clickable(enabled = !busy, onClick = onAccept)
                        .padding(horizontal = Spacing.md, vertical = 6.dp)
                        .heightIn(min = TouchTarget.min),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (busy) "Joining…" else "Accept & Join",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGoals(
    filter: GoalListFilter,
    onCreate: () -> Unit,
) {
    val content = app.promise.android.core.copy.EmptyStateCopy.forGoalFilter(filter)
    app.promise.android.ui.components.PromiseEmptyState(
        title = content.title,
        description = content.description,
        actionLabel = content.actionLabel,
        onActionClick = if (content.actionLabel != null) onCreate else null,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun GoalListFilter.label(): String = when (this) {
    GoalListFilter.ACTIVE -> "Active"
    GoalListFilter.PAUSED -> "Paused"
    GoalListFilter.COMPLETED -> "Completed"
}
