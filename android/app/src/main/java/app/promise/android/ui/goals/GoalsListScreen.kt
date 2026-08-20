package app.promise.android.ui.goals

import androidx.compose.foundation.background
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
import app.promise.android.core.LoadState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalListFilter
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
    var showCreate by remember { mutableStateOf(false) }
    var checkInGoal by remember { mutableStateOf<Goal?>(null) }
    val colors = PromiseThemeColors.current

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
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.md),
            )
            FilterChipsRow(
                selected = (state as? LoadState.Ready)?.value?.filter ?: GoalListFilter.ACTIVE,
                onSelect = viewModel::selectFilter,
            )
            when (val s = state) {
                is LoadState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                        )
                    }
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
                                ) {
                                    items(s.value.items, key = { it.id }) { item ->
                                        GoalRow(
                                            goal = item,
                                            onClick = { onOpenDetail(item.id) },
                                            onCheckIn = {
                                                if (item.trackingKind == GoalTrackingKind.BINARY) {
                                                    viewModel.quickCheckIn(
                                                        item.id,
                                                        CheckInInput(status = GoalCheckInStatus.COMPLETED),
                                                    )
                                                } else {
                                                    checkInGoal = item
                                                }
                                            },
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(88.dp)) }
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
            timeZoneId = (state as? LoadState.Ready)?.value?.timeZoneId ?: "UTC",
            onDismiss = {
                showCreate = false
                viewModel.clearCreateError()
            },
            onSubmit = { input ->
                viewModel.create(input) {
                    showCreate = false
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
            .padding(horizontal = Spacing.inset),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        GoalListFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Text(
                text = filter.label(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) colors.accent else colors.textSecondary,
                modifier = Modifier
                    .heightIn(min = TouchTarget.min)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(filter) },
                    )
                    .padding(horizontal = Spacing.sm)
                    .semantics { contentDescription = "${filter.label()} filter" },
            )
        }
    }
    Spacer(modifier = Modifier.height(Spacing.sm))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm)
            .semantics { contentDescription = goal.title },
    ) {
        Text(
            text = goal.title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (goal.status == GoalStatus.PAUSED) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.textSecondary)
                        .semantics { contentDescription = "Paused" },
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
            }
            Text(
                text = goalMetaLine(goal),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = progressLine,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.semantics {
                contentDescription = progressLine.replace("/", " of ")
            },
        )
        if (streak != null) {
            Text(
                text = streak,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        ProgressTrack(fraction = GoalPresentation.progressFraction(goal))
        if (GoalPresentation.needsCheckInToday(goal)) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            TextButton(
                onClick = onCheckIn,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Check in" },
            ) {
                Text("Check in", color = colors.accent)
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
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
