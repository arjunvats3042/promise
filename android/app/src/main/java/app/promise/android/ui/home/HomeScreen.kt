package app.promise.android.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.LoadState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomePractice
import app.promise.android.ui.components.PromiseGreetingText
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.components.TabSwipeContainer
import app.promise.android.ui.navigation.LocalTabSwipeHost
import app.promise.android.ui.theme.Alpha
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.rememberReduceMotion

@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val swipeHost = LocalTabSwipeHost.current
    when (val s = state) {
        is LoadState.Loading -> {
            HomeLoading()
        }
        is LoadState.Ready -> {
            TabSwipeContainer(
                currentIndex = swipeHost?.currentIndex ?: 0,
                tabCount = swipeHost?.tabCount ?: 4,
                enabled = swipeHost?.enabled == true,
                onSwipe = { direction -> swipeHost?.onSwipe(direction) },
                modifier = Modifier.fillMaxSize(),
            ) {
                HomeContent(
                    model = s.value,
                    onOpenProfile = onOpenProfile,
                    onComplete = viewModel::completeCommitment,
                    onCheckIn = viewModel::checkInPractice,
                )
            }
        }
        is LoadState.Error -> {
            HomeError(
                message = s.kind.toUserMessage(),
                canRetry = s.canRetry,
                onRetry = viewModel::retry,
            )
        }
        else -> {
            HomeLoading()
        }
    }
}

@Composable
private fun HomeLoading() {
    val colors = PromiseThemeColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.inset),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = colors.accent,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Loading today…",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun HomeError(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.inset),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
        if (canRetry) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = TouchTarget.min),
            ) {
                Text("Try again", color = colors.accent)
            }
        }
    }
}

@Composable
private fun HomeContent(
    model: HomeUiModel,
    onOpenProfile: () -> Unit,
    onComplete: (String) -> Unit,
    onCheckIn: (String) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val reduceMotion = rememberReduceMotion()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.inset),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(Spacing.lg))
            HomeHeader(
                greeting = model.greeting,
                userName = model.userName,
                dateLabel = model.dateLabel,
                initials = model.initials,
                onOpenProfile = onOpenProfile,
            )
            Spacer(modifier = Modifier.height(Spacing.md + Spacing.xxs))
            PromiseHairlineDivider()
            Spacer(modifier = Modifier.height(Spacing.lg))
        }
        item {
            Text(
                text = "Today’s commitments",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        if (model.commitments.isEmpty()) {
            item {
                Text(
                    text = "Nothing due today.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "Open Commitments when you’re ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.section))
            }
        } else {
            items(model.commitments, key = { it.id }) { commitment ->
                CommitmentTodayRow(
                    commitment = commitment,
                    reduceMotion = reduceMotion,
                    onComplete = { onComplete(commitment.id) },
                )
            }
            item { Spacer(modifier = Modifier.height(Spacing.section)) }
        }
        item {
            Text(
                text = "Practices",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        if (model.practices.isEmpty()) {
            item {
                Text(
                    text = "No active practices yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        } else {
            items(model.practices, key = { it.id }) { practice ->
                PracticeRow(
                    practice = practice,
                    reduceMotion = reduceMotion,
                    onCheckIn = { onCheckIn(practice.id) },
                )
            }
            item { Spacer(modifier = Modifier.height(Spacing.xxl)) }
        }
    }
}

@Composable
fun HomeHeader(
    greeting: String,
    userName: String,
    dateLabel: String,
    initials: String,
    onOpenProfile: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PromiseGreetingText(
                text = greeting,
                animateIn = true,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = userName,
                style = MaterialTheme.typography.displayLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .size(TouchTarget.min)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onOpenProfile)
                .semantics {
                    contentDescription = "Open profile for $userName"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
fun CommitmentTodayRow(
    commitment: HomeCommitment,
    onComplete: () -> Unit,
    reduceMotion: Boolean = false,
) {
    val colors = PromiseThemeColors.current
    val animModifier = if (reduceMotion) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.standardTween(Motion.CompletionMs))
    }
    Row(
        modifier = animModifier.padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onComplete,
            modifier = Modifier
                .size(TouchTarget.min)
                .semantics {
                    contentDescription = "Complete ${commitment.title}"
                },
        ) {
            Icon(
                imageVector = if (commitment.isCompleted) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (commitment.isCompleted) colors.success else colors.textSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = commitment.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary.copy(
                    alpha = if (commitment.isCompleted) Alpha.CompletedTitle else 1f,
                ),
                textDecoration = if (commitment.isCompleted) TextDecoration.LineThrough else null,
            )
            Spacer(modifier = Modifier.height(Spacing.hairlineGap))
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
                    text = commitment.dueLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
    PromiseHairlineDivider()
}

@Composable
fun PracticeRow(
    practice: HomePractice,
    onCheckIn: () -> Unit,
    reduceMotion: Boolean = false,
) {
    val colors = PromiseThemeColors.current
    val animModifier = if (reduceMotion) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.standardTween(Motion.CompletionMs))
    }
    Column(
        modifier = animModifier.padding(vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = practice.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.hairlineGap))
                val streak = if (practice.streakDays > 0) {
                    " · ${practice.streakDays}-day streak"
                } else {
                    ""
                }
                Text(
                    text = practice.progressLabel + streak,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            TextButton(
                onClick = onCheckIn,
                enabled = !practice.checkedInToday,
                modifier = Modifier
                    .heightIn(min = TouchTarget.min)
                    .semantics {
                        contentDescription = if (practice.checkedInToday) {
                            "${practice.title} checked in"
                        } else {
                            "Check in ${practice.title}"
                        }
                    },
            ) {
                Text(
                    text = if (practice.checkedInToday) "Done" else "Check in",
                    color = if (practice.checkedInToday) colors.success else colors.accent,
                )
            }
        }
    }
}
