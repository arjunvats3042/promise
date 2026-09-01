package app.promise.android.ui.goals.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.domain.ChatMessage
import app.promise.android.domain.ChatMessageDeliveryStatus
import app.promise.android.domain.GoalStatus
import app.promise.android.ui.components.AvatarSize
import app.promise.android.ui.components.PromiseAvatar
import app.promise.android.ui.components.PromiseAvatarStack
import app.promise.android.ui.components.PromiseCardSurface
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.home.HomeViewModel
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.bouncyClickable
import app.promise.android.ui.theme.pressScale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalChatScreen(
    onBack: () -> Unit,
    viewModel: GoalChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PromiseThemeColors.current
    var inputText by remember { mutableStateOf("") }
    var showSummarySheet by remember { mutableStateOf(false) }
    val currentUserId = viewModel.currentUserId()
    val listState = rememberLazyListState()

    // Trigger loading older messages when near top of list (end of reverse list)
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.isPaginating) {
            viewModel.loadOlder()
        }
    }

    val newestMessageId = state.messages.firstOrNull()?.id
    LaunchedEffect(newestMessageId) {
        if (newestMessageId != null && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = Elevation.hairline,
            ) {
                Column {
                    AnimatedVisibility(
                        visible = state.isSearchOpen,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleSearch(false) },
                                modifier = Modifier.size(TouchTarget.min),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Close search",
                                    tint = colors.textPrimary,
                                )
                            }
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                placeholder = {
                                    Text(
                                        "Search in conversation…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary,
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Radius.pill),
                                trailingIcon = {
                                    if (state.searchQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = "Clear",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = colors.surfaceMuted,
                                    unfocusedContainerColor = colors.surfaceMuted,
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                ),
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !state.isSearchOpen,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(TouchTarget.min)
                                    .semantics { contentDescription = "Back" },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                )
                            }

                            Spacer(modifier = Modifier.width(Spacing.xxs))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.goal?.title ?: "Goal Room",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    // Live pulse indicator
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1200),
                                            repeatMode = RepeatMode.Reverse,
                                        ),
                                        label = "pulseAlpha",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent.copy(alpha = pulseAlpha)),
                                    )

                                    val participants = state.goal?.participants.orEmpty()
                                    val subtitle = when {
                                        participants.size > 1 -> "${participants.size} members"
                                        state.goal != null -> "Shared accountability"
                                        else -> "Connecting…"
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }

                            // AI Summary button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(colors.accent.copy(alpha = 0.12f))
                                    .border(
                                        1.dp,
                                        colors.accent.copy(alpha = 0.28f),
                                        RoundedCornerShape(Radius.pill),
                                    )
                                    .pressScale(0.95f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = androidx.compose.material3.ripple(color = colors.accent),
                                        onClick = { showSummarySheet = true },
                                    )
                                    .padding(horizontal = Spacing.sm + 2.dp, vertical = Spacing.xs)
                                    .semantics { contentDescription = "Summarize conversation with AI" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
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
                                        text = "Digest",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.accent,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(Spacing.xs))

                            IconButton(
                                onClick = { viewModel.toggleSearch(true) },
                                modifier = Modifier.size(TouchTarget.min),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search chat",
                                    tint = colors.textPrimary,
                                )
                            }
                        }
                    }

                    PromiseHairlineDivider()
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = Elevation.hairline,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Column {
                    PromiseHairlineDivider()

                    val isGoalActive = state.goal == null || state.goal?.status == GoalStatus.ACTIVE
                    if (isGoalActive && (state.goal?.canSendChat != false)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            // Floating multi-line pill input
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        RoundedCornerShape(Radius.xl),
                                    ),
                                shape = RoundedCornerShape(Radius.xl),
                                color = colors.surfaceMuted,
                            ) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = {
                                        if (it.length <= GoalChatViewModel.MAX_MESSAGE_LENGTH) {
                                            inputText = it
                                        }
                                    },
                                    placeholder = {
                                        Text(
                                            "Send encouragement or update…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.textSecondary,
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 46.dp, max = 120.dp)
                                        .semantics { contentDescription = "Message input" },
                                    shape = RoundedCornerShape(Radius.xl),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary,
                                        cursorColor = colors.accent,
                                    ),
                                )
                            }

                            Spacer(modifier = Modifier.width(Spacing.sm))

                            // Tactile send button
                            val canSend = inputText.trim().isNotBlank()
                            val sendBg = if (canSend) colors.accent else colors.surfaceMuted
                            val sendFg = if (canSend) Color.White else colors.textSecondary.copy(alpha = 0.4f)

                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(sendBg)
                                    .pressScale(targetScale = 0.90f, enabled = canSend)
                                    .clickable(
                                        enabled = canSend,
                                        onClick = {
                                            val textToSend = inputText
                                            inputText = ""
                                            viewModel.sendMessage(textToSend)
                                        },
                                    )
                                    .semantics { contentDescription = "Send message" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = null,
                                    tint = sendFg,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                        val statusText = when (state.goal?.status) {
                            GoalStatus.PAUSED -> "This goal is paused. Chat is in read-only mode."
                            GoalStatus.COMPLETED -> "Goal accomplished! Conversation archived."
                            GoalStatus.CANCELLED -> "Goal cancelled. Conversation closed."
                            else -> "Conversation is closed for inactive goals."
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.inset, vertical = Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading && state.messages.isEmpty() -> {
                    app.promise.android.ui.components.PromiseListSkeleton(
                        itemCount = 5,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                state.errorMessage != null && state.messages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.inset),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.errorMessage ?: "Failed to load messages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        TextButton(onClick = viewModel::loadInitial) {
                            Text("Retry", color = colors.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                state.messages.isEmpty() -> {
                    // Evocative empty state
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = Spacing.screenHorizontal),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.12f))
                                .border(1.dp, colors.accent.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Forum,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = "Shared Accountability Room",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "A private space to share check-in proof, celebrate streaks, and stay disciplined together.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )

                        Spacer(modifier = Modifier.height(Spacing.lg))

                        // Quick starter prompts
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            listOf("Let's stay disciplined", "Checked in for today", "Checking in").forEach { prompt ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.pill))
                                        .background(colors.surfaceMuted)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            RoundedCornerShape(Radius.pill),
                                        )
                                        .clickable { inputText = prompt }
                                        .padding(horizontal = Spacing.sm + 2.dp, vertical = Spacing.xs),
                                ) {
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.screenHorizontal,
                            vertical = Spacing.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        itemsIndexed(
                            items = state.messages,
                            key = { _, msg -> msg.id },
                        ) { index, message ->
                            val isMe = message.sender.id.isNotBlank() && message.sender.id == currentUserId

                            // Check consecutive clustering
                            val isNextSameSender = if (index > 0) {
                                val nextMsg = state.messages[index - 1]
                                nextMsg.sender.id == message.sender.id &&
                                    areWithinMinutes(message.createdAt, nextMsg.createdAt, 5)
                            } else false

                            val isPrevSameSender = if (index < state.messages.size - 1) {
                                val prevMsg = state.messages[index + 1]
                                prevMsg.sender.id == message.sender.id &&
                                    areWithinMinutes(prevMsg.createdAt, message.createdAt, 5)
                            } else false

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            ) {
                                ChatMessageBubble(
                                    message = message,
                                    isMe = isMe,
                                    showSenderHeader = !isMe && !isPrevSameSender,
                                    showAvatar = !isMe && !isNextSameSender,
                                    onRetry = { viewModel.retryMessage(message.id) },
                                )

                                // Check if we should show date separator above this message
                                val shouldShowDateHeader = if (index == state.messages.size - 1) {
                                    true // oldest message always gets a date header
                                } else {
                                    val olderMsg = state.messages[index + 1]
                                    !isSameDay(message.createdAt, olderMsg.createdAt)
                                }

                                if (shouldShowDateHeader) {
                                    Spacer(modifier = Modifier.height(Spacing.sm))
                                    ChatDateSeparator(dateIso = message.createdAt)
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                }
                            }
                        }

                        if (state.isPaginating) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.sm),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search Overlay
            if (state.isSearchOpen) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (state.isSearching) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
                        }
                    } else if (state.searchQuery.isNotBlank() && state.searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No messages found matching \"${state.searchQuery}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    } else if (state.searchQuery.isBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Type to search within this room",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            item {
                                Text(
                                    text = "${state.searchResults.size} results found",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            itemsIndexed(state.searchResults, key = { _, it -> it.id }) { _, result ->
                                PromiseCardSurface(
                                    onClick = {
                                        viewModel.toggleSearch(false)
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = result.sender.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.accent,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = formatShortDate(result.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = result.snippet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSummarySheet) {
        app.promise.android.ui.ai.ChatSummarySheet(
            goalId = viewModel.goalId,
            onDismiss = { showSummarySheet = false },
            onFetchSummary = { id -> viewModel.summarizeChat(id) },
        )
    }
}

@Composable
private fun ChatDateSeparator(dateIso: String) {
    val colors = PromiseThemeColors.current
    val label = formatHeaderDate(dateIso)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(colors.surfaceMuted)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    RoundedCornerShape(Radius.pill),
                )
                .padding(horizontal = Spacing.md, vertical = 3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    showSenderHeader: Boolean,
    showAvatar: Boolean,
    onRetry: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val alignment = if (isMe) Alignment.End else Alignment.Start

    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 4.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 4.dp,
            bottomEnd = 18.dp,
        )
    }

    val bubbleBg = if (isMe) colors.primaryControl else colors.surfaceMuted
    val textFg = if (isMe) colors.onPrimaryControl else colors.textPrimary
    val metaFg = if (isMe) colors.onPrimaryControl.copy(alpha = 0.65f) else colors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMe) {
            if (showAvatar) {
                val initials = HomeViewModel.initialsFor(message.sender.name.ifBlank { "User" })
                PromiseAvatar(
                    initials = initials,
                    photoPath = message.sender.avatarUrl,
                    size = AvatarSize.SM,
                    modifier = Modifier.padding(end = Spacing.xs, bottom = 2.dp),
                )
            } else {
                Spacer(modifier = Modifier.width(28.dp + Spacing.xs))
            }
        }

        Column(horizontalAlignment = alignment) {
            if (showSenderHeader && message.sender.name.isNotBlank()) {
                Text(
                    text = message.sender.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                    modifier = Modifier.padding(start = Spacing.xs, bottom = 2.dp),
                )
            }

            val timeStr = formatTimestamp(message.createdAt)
            val accessibilityLabel = when {
                message.deliveryStatus == ChatMessageDeliveryStatus.FAILED -> "Message failed to send: ${message.body}. Tap to retry."
                message.deliveryStatus == ChatMessageDeliveryStatus.SENDING -> "Sending message: ${message.body}"
                isMe -> "Your message sent at $timeStr: ${message.body}"
                else -> "Message from ${message.sender.name} at $timeStr: ${message.body}"
            }

            Surface(
                shape = bubbleShape,
                color = bubbleBg,
                border = if (!isMe) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                } else null,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = accessibilityLabel
                    },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textFg,
                        lineHeight = 20.sp,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = metaFg,
                        )
                        when (message.deliveryStatus) {
                            ChatMessageDeliveryStatus.SENDING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(9.dp),
                                    strokeWidth = 1.2.dp,
                                    color = metaFg,
                                )
                            }
                            ChatMessageDeliveryStatus.SENT -> {
                                if (isMe) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = "Sent",
                                        tint = metaFg,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                            ChatMessageDeliveryStatus.FAILED -> {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.xs))
                                        .clickable(onClick = onRetry)
                                        .padding(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Warning,
                                        contentDescription = "Retry sending message",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "Retry",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(isoString: String): String {
    return runCatching {
        val instant = Instant.parse(isoString)
        val time = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        DateTimeFormatter.ofPattern("HH:mm").format(time)
    }.getOrDefault("")
}

private fun formatShortDate(isoString: String): String {
    return runCatching {
        val instant = Instant.parse(isoString)
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        DateTimeFormatter.ofPattern("MMM d").format(date)
    }.getOrDefault("")
}

private fun formatHeaderDate(isoString: String): String {
    return runCatching {
        val instant = Instant.parse(isoString)
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now(ZoneId.systemDefault())
        when (ChronoUnit.DAYS.between(date, today)) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> DateTimeFormatter.ofPattern("EEE, MMM d").format(date)
        }
    }.getOrDefault("")
}

private fun isSameDay(iso1: String, iso2: String): Boolean {
    return runCatching {
        val d1 = Instant.parse(iso1).atZone(ZoneId.systemDefault()).toLocalDate()
        val d2 = Instant.parse(iso2).atZone(ZoneId.systemDefault()).toLocalDate()
        d1 == d2
    }.getOrDefault(true)
}

private fun areWithinMinutes(iso1: String, iso2: String, minutes: Long): Boolean {
    return runCatching {
        val i1 = Instant.parse(iso1)
        val i2 = Instant.parse(iso2)
        kotlin.math.abs(ChronoUnit.MINUTES.between(i1, i2)) <= minutes
    }.getOrDefault(false)
}

