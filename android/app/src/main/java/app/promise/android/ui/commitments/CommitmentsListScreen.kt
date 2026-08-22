package app.promise.android.ui.commitments

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.LoadState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentStatus
import app.promise.android.ui.components.TabSwipeContainer
import app.promise.android.ui.navigation.LocalTabSwipeHost
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitmentsListScreen(
    onOpenDetail: (String) -> Unit,
    viewModel: CommitmentsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createAction by viewModel.createAction.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
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
                modifier = Modifier.semantics { contentDescription = "New commitment" },
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
                text = "Commitments",
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.sm),
            )
            FilterChipsRow(
                selected = (state as? LoadState.Ready)?.value?.filter ?: CommitmentListFilter.OPEN,
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
                        currentIndex = swipeHost?.currentIndex ?: 1,
                        tabCount = swipeHost?.tabCount ?: 4,
                        enabled = swipeHost?.enabled == true,
                        modalBlocking = showCreate,
                        onSwipe = { direction -> swipeHost?.onSwipe(direction) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        PullToRefreshBox(
                            isRefreshing = s.isRefreshing,
                            onRefresh = { viewModel.refresh(fromPull = true) },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (s.value.items.isEmpty()) {
                                EmptyCommitments(
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
                                        CommitmentRow(
                                            commitment = item,
                                            timeZoneId = s.value.timeZoneId,
                                            onClick = { onOpenDetail(item.id) },
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
        CreateCommitmentSheet(
            action = createAction,
            timeZoneId = (state as? LoadState.Ready)?.value?.timeZoneId ?: "UTC",
            onDismiss = {
                showCreate = false
                viewModel.clearCreateError()
            },
            onSubmit = { title, description, dueAt, precision ->
                viewModel.create(title, description, dueAt, precision) {
                    showCreate = false
                }
            },
        )
    }
}

@Composable
private fun FilterChipsRow(
    selected: CommitmentListFilter,
    onSelect: (CommitmentListFilter) -> Unit,
) {
    val colors = PromiseThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.inset),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CommitmentListFilter.entries.forEach { filter ->
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
fun CommitmentRow(
    commitment: Commitment,
    timeZoneId: String,
    onClick: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val statusText = if (commitment.isOverdue) "Overdue" else "Due"
    val commitmentDesc = "${commitment.title}, $statusText · ${commitment.metaLine(timeZoneId)}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = commitmentDesc
            },
    ) {
        Text(
            text = commitment.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (commitment.isOverdue) {
                Box(
                    modifier = Modifier
                        .size(Spacing.statusMark)
                        .clip(CircleShape)
                        .background(colors.warning),
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
            }
            Text(
                text = commitment.metaLine(timeZoneId),
                style = MaterialTheme.typography.bodySmall,
                color = if (commitment.isOverdue) colors.warning else colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        app.promise.android.ui.components.PromiseHairlineDivider()
    }
}

@Composable
private fun EmptyCommitments(
    filter: CommitmentListFilter,
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
        if (filter == CommitmentListFilter.OPEN || filter == CommitmentListFilter.TODAY) {
            Spacer(modifier = Modifier.height(Spacing.md))
            TextButton(onClick = onCreate) {
                Text("New commitment", color = colors.accent)
            }
        }
    }
}

private fun CommitmentListFilter.label(): String = when (this) {
    CommitmentListFilter.OPEN -> "Open"
    CommitmentListFilter.OVERDUE -> "Overdue"
    CommitmentListFilter.TODAY -> "Today"
    CommitmentListFilter.UPCOMING -> "Upcoming"
    CommitmentListFilter.DONE -> "Done"
}

private fun CommitmentListFilter.emptyTitle(): String = when (this) {
    CommitmentListFilter.OPEN -> "No open commitments."
    CommitmentListFilter.OVERDUE -> "Nothing overdue."
    CommitmentListFilter.TODAY -> "Nothing due today."
    CommitmentListFilter.UPCOMING -> "Nothing upcoming."
    CommitmentListFilter.DONE -> "No completed commitments yet."
}

private fun CommitmentListFilter.emptyBody(): String = when (this) {
    CommitmentListFilter.OPEN, CommitmentListFilter.TODAY -> "Add a promise when you’re ready."
    else -> "Check another filter, or come back later."
}

fun Commitment.metaLine(timeZoneId: String): String {
    val parts = mutableListOf<String>()
    when (status) {
        CommitmentStatus.WAITING -> parts += "Waiting"
        CommitmentStatus.SNOOZED -> parts += "Snoozed"
        CommitmentStatus.COMPLETED -> parts += "Completed"
        CommitmentStatus.CANCELLED -> parts += "Cancelled"
        CommitmentStatus.PENDING -> Unit
    }
    if (isOverdue) parts += "Overdue"
    CommitmentTime.formatDue(dueAt, duePrecision, timeZoneId)?.let { parts += "Due $it" }
    snoozedUntil?.let {
        CommitmentTime.formatDue(it, app.promise.android.domain.DuePrecision.DATETIME, timeZoneId)
            ?.let { formatted -> parts += "Until $formatted" }
    }
    return parts.joinToString(" · ").ifBlank { "No due date" }
}
