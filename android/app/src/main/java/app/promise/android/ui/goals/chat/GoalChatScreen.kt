package app.promise.android.ui.goals.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.domain.ChatMessage
import app.promise.android.domain.ChatMessageDeliveryStatus
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalChatScreen(
    onBack: () -> Unit,
    viewModel: GoalChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PromiseThemeColors.current
    var inputText by remember { mutableStateOf("") }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
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
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.goal?.title ?: "Goal Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val participantCount = state.goal?.participants?.size ?: 0
                        Text(
                            text = if (participantCount > 1) "$participantCount participants" else "Shared Goal conversation",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.inset, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= GoalChatViewModel.MAX_MESSAGE_LENGTH) inputText = it },
                        placeholder = {
                            Text(
                                "Send a message…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp)
                            .semantics { contentDescription = "Message input" },
                        shape = RoundedCornerShape(Radius.lg),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    val canSend = inputText.trim().isNotBlank()
                    IconButton(
                        onClick = {
                            val textToSend = inputText
                            inputText = ""
                            viewModel.sendMessage(textToSend)
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(TouchTarget.min)
                            .semantics { contentDescription = "Send message" },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            tint = if (canSend) colors.accent else colors.textSecondary.copy(alpha = 0.4f),
                        )
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
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.align(Alignment.Center),
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
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        TextButton(onClick = viewModel::loadInitial) {
                            Text("Retry", color = colors.accent)
                        }
                    }
                }
                state.messages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.inset),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = "Group Conversation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = "A private space for participants to encourage each other.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.inset,
                            vertical = Spacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        items(
                            items = state.messages,
                            key = { it.id },
                        ) { message ->
                            val isMe = message.sender.id.isNotBlank() && message.sender.id == currentUserId
                            ChatMessageBubble(
                                message = message,
                                isMe = isMe,
                                onRetry = { viewModel.retryMessage(message.id) },
                            )
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
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    onRetry: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = Radius.md,
            topEnd = Radius.md,
            bottomStart = Radius.md,
            bottomEnd = Spacing.xxs,
        )
    } else {
        RoundedCornerShape(
            topStart = Radius.md,
            topEnd = Radius.md,
            bottomStart = Spacing.xxs,
            bottomEnd = Radius.md,
        )
    }

    val bubbleColor = if (isMe) {
        colors.primaryControl
    } else {
        colors.surfaceMuted
    }

    val textColor = if (isMe) {
        colors.onPrimaryControl
    } else {
        colors.textPrimary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment,
    ) {
        if (!isMe && message.sender.name.isNotBlank()) {
            Text(
                text = message.sender.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
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
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                    )
                    when (message.deliveryStatus) {
                        ChatMessageDeliveryStatus.SENDING -> {
                            Spacer(modifier = Modifier.width(Spacing.xxs))
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.dp,
                                color = textColor.copy(alpha = 0.7f),
                            )
                        }
                        ChatMessageDeliveryStatus.FAILED -> {
                            Spacer(modifier = Modifier.width(Spacing.xxs))
                            Row(
                                modifier = Modifier.clickable(onClick = onRetry),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Warning,
                                    contentDescription = "Retry sending message",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    "Retry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        ChatMessageDeliveryStatus.SENT -> Unit
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
