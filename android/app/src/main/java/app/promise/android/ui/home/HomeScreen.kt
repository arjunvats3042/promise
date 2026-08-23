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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.LoadState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomePractice
import app.promise.android.ui.ai.ThoughtParserSheet
import app.promise.android.ui.ai.WeeklyInsightsCard
import app.promise.android.ui.components.PromiseFeedSkeleton
import app.promise.android.ui.components.PromiseGreetingText
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.components.PromiseModalSheet
import app.promise.android.ui.theme.Alpha
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.rememberReduceMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    onOpenCommitment: (String) -> Unit = {},
    onOpenPractice: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weeklyInsightsState by viewModel.weeklyInsightsState.collectAsStateWithLifecycle()
    val dailyMotivationState by viewModel.dailyMotivationState.collectAsStateWithLifecycle()
    var countPractice by remember { mutableStateOf<HomePractice?>(null) }
    var showThoughtParser by remember { mutableStateOf(false) }
    var dismissedInvites by remember { mutableStateOf(false) }

    val pendingInvites = (state as? LoadState.Ready)?.value?.pendingInvites.orEmpty()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onVisible()
                dismissedInvites = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val s = state) {
        is LoadState.Loading -> {
            PromiseFeedSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding(),
            )
        }
        is LoadState.Ready -> {
            PullToRefreshBox(
                isRefreshing = s.isRefreshing,
                onRefresh = { viewModel.refresh(fromPull = true, force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                HomeContent(
                    model = s.value,
                    weeklyInsightsState = weeklyInsightsState,
                    dailyMotivationState = dailyMotivationState,
                    onOpenProfile = onOpenProfile,
                    onOpenCommitment = onOpenCommitment,
                    onOpenPractice = onOpenPractice,
                    onOpenSearch = onOpenSearch,
                    onOpenThoughtParser = { showThoughtParser = true },
                    onOpenInvites = { dismissedInvites = false },
                    onComplete = viewModel::completeCommitment,
                    onCheckIn = { practice ->
                        if (practice.trackingKind == GoalTrackingKind.COUNT && !practice.checkedInToday) {
                            countPractice = practice
                        } else {
                            viewModel.checkInPractice(practice.id)
                        }
                    },
                    onRetryCommitments = viewModel::retryCommitments,
                    onRetryPractices = viewModel::retryPractices,
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
            PromiseFeedSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding(),
            )
        }
    }

    if (!dismissedInvites && pendingInvites.isNotEmpty()) {
        GoalInvitePopupDialog(
            invites = pendingInvites,
            onAccept = { goalId ->
                viewModel.acceptInvitation(goalId)
            },
            onDecline = { goalId ->
                viewModel.declineInvitation(goalId)
            },
            onDismiss = {
                dismissedInvites = true
            },
        )
    }

    countPractice?.let { practice ->
        HomeCountCheckInSheet(
            practice = practice,
            onDismiss = { countPractice = null },
            onSubmit = { input ->
                viewModel.checkInPractice(practice.id, input)
                countPractice = null
            },
        )
    }

    if (showThoughtParser) {
        ThoughtParserSheet(
            onDismiss = { showThoughtParser = false },
            onParseThought = { thought -> viewModel.parseThought(thought) },
            onCreateCommitment = { input -> viewModel.createCommitmentFromThought(input) },
            onCreateGoal = { input -> viewModel.createGoalFromThought(input) },
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
    weeklyInsightsState: WeeklyInsightsUiState,
    dailyMotivationState: DailyMotivationUiState,
    onOpenProfile: () -> Unit,
    onOpenCommitment: (String) -> Unit,
    onOpenPractice: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenThoughtParser: () -> Unit = {},
    onOpenInvites: () -> Unit = {},
    onComplete: (String) -> Unit,
    onCheckIn: (HomePractice) -> Unit,
    onRetryCommitments: () -> Unit,
    onRetryPractices: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val reduceMotion = rememberReduceMotion()

    val personalPractices = remember(model.practices) {
        model.practices.filter { !it.isShared }
    }
    val sharedPractices = remember(model.practices) {
        model.practices.filter { it.isShared }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.inset),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // 1. HEADER
        item {
            Spacer(modifier = Modifier.height(Spacing.xl))
            HomeHeader(
                greeting = model.greeting,
                userName = model.userName,
                dateLabel = model.dateLabel,
                initials = model.initials,
                onOpenProfile = onOpenProfile,
                onOpenSearch = onOpenSearch,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            PromiseHairlineDivider()
            Spacer(modifier = Modifier.height(Spacing.md))
            TodayThoughtSection(state = dailyMotivationState)
            Spacer(modifier = Modifier.height(Spacing.sectionGap))
        }

        // Shared Goal Invites Banner if pending
        if (model.pendingInvites.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(colors.accent.copy(alpha = 0.14f))
                        .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(Radius.md))
                        .clickable(onClick = onOpenInvites)
                        .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🤝 Shared Goal Invite (${model.pendingInvites.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Text(
                                text = model.pendingInvites.first().let { "${it.inviterName} invited you to \"${it.title}\"" },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = onOpenInvites) {
                            Text("Review", color = colors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sectionGap))
            }
        }

        // Email Verification notice if needed
        if (!model.emailVerified) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(colors.warning.copy(alpha = 0.12f))
                        .border(1.dp, colors.warning.copy(alpha = 0.35f), RoundedCornerShape(Radius.md))
                        .clickable(onClick = onOpenProfile)
                        .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
                ) {
                    Column {
                        Text(
                            text = "Verify your email address",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.cardTitleBottom))
                        Text(
                            text = "Tap to visit Profile & Security to send a verification link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sectionGap))
            }
        }

        // 2. TODAY (Today's Commitments)
        item {
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.sectionHeaderBottom))
        }

        when {
            model.commitmentsError != null -> {
                item {
                    SectionError(
                        message = model.commitmentsError.toUserMessage(),
                        onRetry = onRetryCommitments,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sectionGap))
                }
            }
            model.commitments.isEmpty() -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(colors.surfaceMuted)
                            .padding(Spacing.cardPadding),
                    ) {
                        Text(
                            text = "Nothing due today.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.cardTitleBottom))
                        Text(
                            text = "Open Commitments when you’re ready to schedule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sectionGap))
                }
            }
            else -> {
                items(model.commitments, key = { it.id }) { commitment ->
                    CommitmentTodayRow(
                        commitment = commitment,
                        reduceMotion = reduceMotion,
                        onOpen = { onOpenCommitment(commitment.id) },
                        onComplete = { onComplete(commitment.id) },
                    )
                }
                item { Spacer(modifier = Modifier.height(Spacing.sectionGap)) }
            }
        }

        // 3. YOUR PRACTICE (Personal Practices)
        item {
            Text(
                text = "Your Practice",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.sectionHeaderBottom))
        }

        when {
            model.practicesError != null -> {
                item {
                    SectionError(
                        message = model.practicesError.toUserMessage(),
                        onRetry = onRetryPractices,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sectionGap))
                }
            }
            personalPractices.isEmpty() -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(colors.surfaceMuted)
                            .padding(Spacing.cardPadding),
                    ) {
                        Text(
                            text = "No active personal practices yet.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.cardTitleBottom))
                        Text(
                            text = "Create goals in the Goals tab to build daily consistency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sectionGap))
                }
            }
            else -> {
                items(personalPractices, key = { it.id }) { practice ->
                    PracticeRow(
                        practice = practice,
                        reduceMotion = reduceMotion,
                        onOpen = { onOpenPractice(practice.id) },
                        onCheckIn = { onCheckIn(practice) },
                    )
                }
                item { Spacer(modifier = Modifier.height(Spacing.sectionGap)) }
            }
        }

        // 4. SHARED (Shared Goals / Practices)
        if (sharedPractices.isNotEmpty()) {
            item {
                Text(
                    text = "Shared Practice",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.sectionHeaderBottom))
            }
            items(sharedPractices, key = { it.id }) { practice ->
                PracticeRow(
                    practice = practice,
                    reduceMotion = reduceMotion,
                    onOpen = { onOpenPractice(practice.id) },
                    onCheckIn = { onCheckIn(practice) },
                )
            }
            item { Spacer(modifier = Modifier.height(Spacing.sectionGap)) }
        }

        // 5. AI ASSISTANTS
        item {
            Text(
                text = "AI Assistant",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.sectionHeaderBottom))

            Surface(
                onClick = onOpenThoughtParser,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(Radius.md))
                    .semantics { contentDescription = "Turn thought into promises" },
                color = colors.surfaceMuted,
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Thought → Promise",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.cardTitleBottom))
                        Text(
                            text = "Turn a brain dump into commitments & goals using AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
            WeeklyInsightsCard(state = weeklyInsightsState)
            Spacer(modifier = Modifier.height(Spacing.xxxl))
        }
    }
}

@Composable
private fun SectionError(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(Spacing.xxs))
    TextButton(
        onClick = onRetry,
        modifier = Modifier.heightIn(min = TouchTarget.min),
    ) {
        Text("Try again", color = colors.accent)
    }
}

@Composable
private fun TodayThoughtSection(
    state: DailyMotivationUiState,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current

    when (state) {
        is DailyMotivationUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(colors.surfaceMuted.copy(alpha = 0.5f))
                    .padding(horizontal = Spacing.cardPadding, vertical = Spacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.surfaceRaised),
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.surfaceRaised.copy(alpha = 0.6f)),
                    )
                }
            }
        }
        is DailyMotivationUiState.Error -> {
            // Hidden gracefully when error
        }
        is DailyMotivationUiState.Success -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(colors.surfaceMuted)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(Radius.md))
                    .padding(horizontal = Spacing.cardPadding, vertical = Spacing.sm + 2.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Today's Thought: ${state.quote.quote}"
                    },
            ) {
                Column {
                    Text(
                        text = "TODAY'S THOUGHT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary,
                        letterSpacing = 1.0.sp,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = "“${state.quote.quote}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCountCheckInSheet(
    practice: HomePractice,
    onDismiss: () -> Unit,
    onSubmit: (CheckInInput) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var valueText by remember {
        mutableStateOf(practice.periodValue?.toString() ?: practice.targetValue?.toString() ?: "")
    }
    PromiseModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.md)) {
            Text(
                text = practice.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Today’s check-in",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            OutlinedTextField(
                value = valueText,
                onValueChange = { valueText = it.filter { ch -> ch.isDigit() } },
                singleLine = true,
                label = { Text("Value") },
                shape = RoundedCornerShape(Radius.sm),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    cursorColor = colors.accent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Check-in value" },
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Button(
                onClick = {
                    val value = valueText.toIntOrNull() ?: return@Button
                    onSubmit(
                        CheckInInput(
                            status = GoalCheckInStatus.COMPLETED,
                            value = value,
                        ),
                    )
                },
                enabled = valueText.toIntOrNull() != null,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.min),
            ) {
                Text("Save")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = colors.textSecondary)
            }
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
    onOpenSearch: () -> Unit = {},
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
        IconButton(
            onClick = onOpenSearch,
            modifier = Modifier
                .size(TouchTarget.min)
                .semantics { contentDescription = "Search" },
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.xs))
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
    onOpen: () -> Unit = {},
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
    val statusText = if (commitment.isCompleted) "Completed" else if (commitment.isOverdue) "Overdue" else "Due"
    val commitmentDesc = "${commitment.title}, $statusText, ${commitment.dueLabel}"

    Row(
        modifier = animModifier.padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onComplete,
            modifier = Modifier
                .size(TouchTarget.min)
                .semantics {
                    contentDescription = if (commitment.isCompleted) {
                        "${commitment.title} completed"
                    } else {
                        "Mark ${commitment.title} completed"
                    }
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
        Spacer(modifier = Modifier.width(Spacing.xs))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    contentDescription = commitmentDesc
                },
        ) {
            Text(
                text = commitment.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary.copy(
                    alpha = if (commitment.isCompleted) Alpha.CompletedTitle else 1f,
                ),
                textDecoration = if (commitment.isCompleted) TextDecoration.LineThrough else null,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
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
                    text = if (commitment.isOverdue && !commitment.dueLabel.startsWith("Overdue", ignoreCase = true)) {
                        "Overdue · ${commitment.dueLabel}"
                    } else {
                        commitment.dueLabel
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (commitment.isOverdue) colors.warning else colors.textSecondary,
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
    onOpen: () -> Unit = {},
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
    val practiceDesc = buildString {
        append(practice.title)
        if (practice.isShared) append(", Shared practice")
        if (practice.streakDays > 0) append(", ${practice.streakDays} day streak")
        append(", ${practice.progressLabel}")
        if (practice.checkedInToday) append(", Checked in today")
    }

    Column(
        modifier = animModifier.padding(vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .semantics(mergeDescendants = true) {
                        contentDescription = practiceDesc
                    },
            ) {
                Text(
                    text = practice.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = practice.progressLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    if (practice.streakDays > 0) {
                        app.promise.android.ui.components.PromiseStreakBadge(
                            streakText = "${practice.streakDays}d",
                        )
                    }
                    if (practice.isShared) {
                        Text(
                            text = "· Shared",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                if (practice.isShared && (practice.unreadChatCount > 0 || practice.latestChatMessage != null)) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (practice.unreadChatCount > 0) colors.accent.copy(alpha = 0.15f) else colors.surfaceMuted)
                            .border(
                                width = 1.dp,
                                color = if (practice.unreadChatCount > 0) colors.accent.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent,
                                shape = RoundedCornerShape(Radius.sm),
                            )
                            .clickable(onClick = onOpen)
                            .padding(horizontal = Spacing.xs + 2.dp, vertical = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "💬",
                            fontSize = 11.sp,
                        )
                        if (practice.unreadChatCount > 0) {
                            Text(
                                text = "${practice.unreadChatCount} new",
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
                            text = practice.latestChatMessage?.let { "${it.senderName}: ${it.text}" } ?: "Group chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (practice.unreadChatCount > 0) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (practice.progressFraction > 0f) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    app.promise.android.ui.components.PromiseLinearProgressBar(
                        progress = practice.progressFraction,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
            TextButton(
                onClick = onCheckIn,
                enabled = !practice.checkedInToday,
                modifier = Modifier
                    .heightIn(min = TouchTarget.min)
                    .semantics {
                        contentDescription = if (practice.checkedInToday) {
                            "${practice.title} checked in today"
                        } else {
                            "Check in ${practice.title}"
                        }
                    },
            ) {
                Text(
                    text = if (practice.checkedInToday) "Done" else "Check in",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (practice.checkedInToday) colors.success else colors.accent,
                )
            }
        }
    }
    PromiseHairlineDivider()
}
