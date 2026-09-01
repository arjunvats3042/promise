package app.promise.android.ui.ai.support

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.domain.SupportActionChip
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.pressScale
import app.promise.android.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PromiseSupportChatSheet(
    onDismiss: () -> Unit,
    originX: Float = 0.9f,
    originY: Float = 0.8f,
    onAction: (String) -> Unit = {},
    initialQuery: String? = null,
    viewModel: PromiseSupportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = PromiseThemeColors.current
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    val animProgress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var completedTypewriterIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    // Mark initial messages as already animated on sheet open
    LaunchedEffect(Unit) {
        if (uiState.messages.isNotEmpty()) {
            completedTypewriterIds = completedTypewriterIds + uiState.messages.map { it.id }
        }
    }

    fun dismissWithMacAnimation(postAction: (() -> Unit)? = null) {
        if (isDismissing) return
        isDismissing = true
        coroutineScope.launch {
            if (reduceMotion) {
                animProgress.snapTo(0f)
            } else {
                animProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            onDismiss()
            postAction?.invoke()
        }
    }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            animProgress.snapTo(1f)
        } else {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    BackHandler(enabled = true) {
        dismissWithMacAnimation()
    }

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

    // Full Screen macOS-Style Expansion Overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 1. Scrim Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f * animProgress.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissWithMacAnimation() },
                )
        )

        // 2. MacBook Zoom Window (Emerges from and Collapses back into the Orb's exact position)
        val currentScale = if (reduceMotion) 1f else (0.12f + 0.88f * animProgress.value)
        val currentAlpha = animProgress.value.coerceIn(0f, 1f)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                    alpha = currentAlpha
                    transformOrigin = TransformOrigin(originX, originY)
                    clip = true
                    shadowElevation = 20.dp.toPx() * animProgress.value
                }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(
                    1.dp,
                    colors.outlineStrong.copy(alpha = 0.6f * animProgress.value),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ),
            color = colors.cardBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Top Sheet Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.textSecondary.copy(alpha = 0.28f))
                    )
                }

                // 1. TOP APP BAR
                SupportHeader(
                    onReset = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        completedTypewriterIds = emptySet()
                        viewModel.clearConversation()
                    },
                    onClose = { dismissWithMacAnimation() },
                )

                // Hairline separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outlineStrong.copy(alpha = 0.35f))
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
                                    isAlreadyAnimated = completedTypewriterIds.contains(message.id),
                                    onAnimationComplete = {
                                        completedTypewriterIds = completedTypewriterIds + message.id
                                    },
                                    onFollowupClick = { followupQuery ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.sendMessage(followupQuery)
                                    },
                                    onActionClick = { actionType ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dismissWithMacAnimation {
                                            onAction(actionType)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // 3. BESPOKE HUMAN-CRAFTED TYPING DOCK
                SupportInputDock(
                    inputText = uiState.inputText,
                    isGenerating = uiState.isGenerating,
                    onTextChange = viewModel::updateInputText,
                    onSend = {
                        focusManager.clearFocus()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.sendMessage()
                    },
                )
            }
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
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.accent.copy(alpha = 0.22f),
                                Color(0xFF8B5CF6).copy(alpha = 0.32f),
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(colors.accent.copy(alpha = 0.8f), Color(0xFFA78BFA))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(19.dp),
                )
            }

            Column {
                Text(
                    text = "Promise Concierge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "Online · App Knowledge Guide",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        fontSize = 11.5.sp,
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
                modifier = Modifier
                    .size(TouchTarget.min)
                    .pressScale(0.92f),
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
                modifier = Modifier
                    .size(TouchTarget.min)
                    .pressScale(0.92f),
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
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 4.dp
            ),
            color = colors.accent.copy(alpha = 0.16f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                colors.accent.copy(alpha = 0.40f)
            ),
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AssistantChatCard(
    item: SupportChatItem,
    isAlreadyAnimated: Boolean = false,
    onAnimationComplete: () -> Unit = {},
    onFollowupClick: (String) -> Unit,
    onActionClick: (String) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val reduceMotion = rememberReduceMotion()

    // Smooth Typewriter character reveal - runs strictly ONCE per new assistant response
    var revealedChars by remember(item.id, item.text) {
        mutableIntStateOf(if (reduceMotion || isAlreadyAnimated) item.text.length else 0)
    }

    LaunchedEffect(item.id, item.text, isAlreadyAnimated) {
        if (!reduceMotion && !isAlreadyAnimated && revealedChars < item.text.length) {
            while (revealedChars < item.text.length) {
                revealedChars = (revealedChars + 5).coerceAtMost(item.text.length)
                delay(12)
            }
            onAnimationComplete()
        } else if (!isAlreadyAnimated) {
            onAnimationComplete()
        }
    }

    val visibleText = item.text.take(revealedChars)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = colors.surfaceRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineStrong.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        revealedChars = item.text.length
                        onAnimationComplete()
                    }
                ),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (item.isOffTopic) {
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(text = "✦", fontSize = 11.sp, color = Color(0xFFF59E0B))
                            Text(
                                text = "Promise App Guide",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Render Markdown-styled content with smooth typewriter
                FormattedMarkdownContent(text = visibleText)
            }
        }

        // 1-Tap Action Deep-Link Badges
        if (item.actionChips.isNotEmpty() && revealedChars >= item.text.length * 0.4f) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item.actionChips.forEach { action ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.accent.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(
                                    colors.accent.copy(alpha = 0.8f),
                                    Color(0xFF8B5CF6).copy(alpha = 0.6f)
                                )
                            )
                        ),
                        modifier = Modifier
                            .pressScale(0.94f)
                            .clickable { onActionClick(action.actionType) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }

        // Suggested Follow-up Question Chips
        if (item.followups.isNotEmpty() && revealedChars >= item.text.length * 0.6f) {
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
                        color = colors.surfaceMuted,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            colors.outlineStrong.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .pressScale(0.95f)
                            .clickable { onFollowupClick(followup) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "✦",
                                fontSize = 10.sp,
                                color = colors.accent,
                            )
                            Text(
                                text = followup,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp,
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

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
            } else if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
                val bulletContent = line.substring(2).trim()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                    Text(
                        text = bulletContent.replace("**", "").replace("*", ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            } else if (line.startsWith("1.") || line.startsWith("2.") || line.startsWith("3.") || line.startsWith("4.")) {
                val prefix = line.take(2)
                val stepContent = line.substring(2).trim()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.accent.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = prefix.replace(".", ""),
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    Text(
                        text = stepContent.replace("**", "").replace("*", ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            } else {
                Text(
                    text = line.replace("**", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (line.startsWith("**") || line.endsWith("**")) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (line.startsWith("**") || line.endsWith("**")) colors.textPrimary else colors.textPrimary.copy(alpha = 0.95f),
                    fontSize = 14.5.sp,
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
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, delayMillis = 180, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, delayMillis = 360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3"
    )

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineStrong.copy(alpha = 0.4f)),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(colors.accent.copy(alpha = dot1Alpha)))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(colors.accent.copy(alpha = dot2Alpha)))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(colors.accent.copy(alpha = dot3Alpha)))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Concierge thinking…",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                fontSize = 12.sp,
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
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> colors.accent.copy(alpha = 0.65f)
            inputText.isNotBlank() -> colors.outlineStrong
            else -> colors.outlineStrong.copy(alpha = 0.45f)
        },
        animationSpec = Motion.standardTween(Motion.FilterChangeMs),
        label = "dock_border"
    )

    val canSend = inputText.isNotBlank() && !isGenerating

    val infiniteTransition = rememberInfiniteTransition(label = "generating_spin")
    val spinnerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin"
    )

    val buttonBrush = when {
        canSend -> Brush.linearGradient(listOf(colors.accent, Color(0xFF8B5CF6)))
        isGenerating -> SolidColor(colors.surfaceRaised)
        else -> SolidColor(Color.Transparent)
    }

    // Soft glassmorphic bottom dock
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.cardBackground.copy(alpha = 0.85f),
                        colors.cardBackground,
                    )
                )
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        // Ergonomic Pill Capsule Container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceMuted)
                .border(1.dp, borderColor, RoundedCornerShape(26.dp))
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "✦",
                color = if (isFocused) colors.accent else colors.textSecondary.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )

            BasicTextField(
                value = inputText,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) onSend()
                    }
                ),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Ask anything about Promise…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary.copy(alpha = 0.55f),
                            fontSize = 14.5.sp,
                        )
                    }
                    innerTextField()
                }
            )

            // Right Action Button (Inside Capsule)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(buttonBrush)
                    .border(
                        1.dp,
                        if (canSend) Color.Transparent else colors.outlineStrong.copy(alpha = 0.35f),
                        CircleShape
                    )
                    .pressScale(0.90f, enabled = canSend)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = canSend,
                        onClick = onSend,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isGenerating) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Generating",
                        tint = colors.accent,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(spinnerAngle),
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color.Black else colors.textSecondary.copy(alpha = 0.45f),
                        modifier = Modifier
                            .size(17.dp)
                            .padding(start = if (canSend) 2.dp else 0.dp),
                    )
                }
            }
        }
    }
}
