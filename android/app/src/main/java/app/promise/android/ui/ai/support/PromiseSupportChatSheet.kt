package app.promise.android.ui.ai.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromiseSupportChatSheet(
    onDismiss: () -> Unit,
    initialQuery: String? = null,
    viewModel: PromiseSupportViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = PromiseThemeColors.current
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.sendMessage(initialQuery)
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.isGenerating) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        dragHandle = null,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. TOP APP BAR
            SupportHeader(
                onReset = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    viewModel.clearConversation()
                },
                onClose = onDismiss,
            )

            // 2. CONVERSATION STREAM
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    if (message.role == "user") {
                        UserChatBubble(text = message.text)
                    } else {
                        if (message.isLoading) {
                            AssistantLoadingBubble()
                        } else {
                            AssistantChatCard(
                                item = message,
                                onFollowupClick = { followupQuery ->
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.sendMessage(followupQuery)
                                },
                            )
                        }
                    }
                }
            }

            // 3. BOTTOM INPUT DOCK
            SupportInputDock(
                inputText = uiState.inputText,
                isGenerating = uiState.isGenerating,
                onTextChange = viewModel::updateInputText,
                onSend = {
                    focusManager.clearFocus()
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    viewModel.sendMessage()
                },
            )
        }
    }
}

@Composable
private fun SupportHeader(
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Luminous Concierge Badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.accent.copy(alpha = 0.25f),
                                Color(0xFF8B5CF6).copy(alpha = 0.35f),
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(colors.accent, Color(0xFFA78BFA))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column {
                Text(
                    text = "Promise Concierge",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "Online · App Knowledge Guide",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(TouchTarget.min),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Reset Conversation",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(TouchTarget.min),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun UserChatBubble(text: String) {
    val colors = PromiseThemeColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.sm, bottomStart = Radius.lg, bottomEnd = Radius.lg),
            color = colors.accent.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = text,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun AssistantChatCard(
    item: SupportChatItem,
    onFollowupClick: (String) -> Unit,
) {
    val colors = PromiseThemeColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.lg, bottomStart = Radius.lg, bottomEnd = Radius.lg),
            color = colors.cardBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // If off topic guardrail triggered, show friendly pill
                if (item.isOffTopic) {
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = "✦ Promise Focus Guide",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Render Markdown-styled content
                FormattedMarkdownContent(text = item.text)
            }
        }

        // Suggested Follow-up Question Chips
        if (item.followups.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item.followups.forEach { followup ->
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = colors.chipBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .pressScale(0.95f)
                            .clickable { onFollowupClick(followup) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "💬",
                                fontSize = 11.sp,
                            )
                            Text(
                                text = followup,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                color = colors.accent,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattedMarkdownContent(text: String) {
    val colors = PromiseThemeColors.current
    val lines = text.lines()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
            } else if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
                val bulletContent = line.substring(2).trim()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = "•",
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = bulletContent.replace("**", "").replace("*", ""),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        lineHeight = 20.sp,
                    )
                }
            } else if (line.startsWith("1.") || line.startsWith("2.") || line.startsWith("3.")) {
                val prefix = line.take(2)
                val stepContent = line.substring(2).trim()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = prefix,
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stepContent.replace("**", "").replace("*", ""),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        lineHeight = 20.sp,
                    )
                }
            } else {
                Text(
                    text = line.replace("**", ""),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontWeight = if (line.startsWith("**") || line.endsWith("**")) FontWeight.Bold else FontWeight.Normal,
                    color = colors.textPrimary,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun AssistantLoadingBubble() {
    val colors = PromiseThemeColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "support_typing")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3"
    )

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.cardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.accent.copy(alpha = dot1Alpha)))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.accent.copy(alpha = dot2Alpha)))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.accent.copy(alpha = dot3Alpha)))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Concierge thinking...",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SupportInputDock(
    inputText: String,
    isGenerating: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    Surface(
        color = colors.cardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = "Ask anything about Promise...",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(Radius.pill),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedContainerColor = colors.surfaceMuted,
                    unfocusedContainerColor = colors.surfaceMuted,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank() && !isGenerating) onSend() }),
                modifier = Modifier.weight(1f),
            )

            // Send button
            val canSend = inputText.isNotBlank() && !isGenerating
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) Brush.linearGradient(listOf(colors.accent, Color(0xFF8B5CF6)))
                        else Brush.linearGradient(listOf(colors.surfaceMuted, colors.surfaceMuted))
                    )
                    .pressScale(0.92f, enabled = canSend),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Color.Black else colors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
