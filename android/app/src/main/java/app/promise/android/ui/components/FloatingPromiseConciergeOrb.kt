package app.promise.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingPromiseConciergeOrb(
    onClick: (pivotX: Float, pivotY: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val orbSizeDp = 54.dp
    val marginPaddingDp = 16.dp
    val orbSizePx = with(density) { orbSizeDp.toPx() }
    val marginPx = with(density) { marginPaddingDp.toPx() }

    // Ambient Breathing / Glow Animation
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha"
    )
    val iconRotation by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "icon_rotation"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val minX = marginPx
        val maxX = (screenWidthPx - orbSizePx - marginPx).coerceAtLeast(minX)
        val minY = marginPx + with(density) { 60.dp.toPx() } // Below top app bar
        val maxY = (screenHeightPx - orbSizePx - marginPx - with(density) { 90.dp.toPx() }).coerceAtLeast(minY) // Above bottom bar

        val offsetX = remember { Animatable(maxX) }
        val offsetY = remember { Animatable(maxY * 0.72f) }
        var isDragging by remember { mutableStateOf(false) }

        // Keep orb on right edge initially when screen width resolves
        LaunchedEffect(screenWidthPx) {
            if (screenWidthPx > 0 && !isDragging) {
                offsetX.snapTo(maxX)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .pointerInput(screenWidthPx, screenHeightPx) {
                    var totalDragDistance = 0f
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            totalDragDistance = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            // Snap to nearest horizontal edge (Left or Right)
                            val snapTargetX = if (offsetX.value < screenWidthPx / 2f) minX else maxX
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = snapTargetX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            val snapTargetX = if (offsetX.value < screenWidthPx / 2f) minX else maxX
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = snapTargetX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragDistance += dragAmount.getDistance()
                            val newX = (offsetX.value + dragAmount.x).coerceIn(minX, maxX)
                            val newY = (offsetY.value + dragAmount.y).coerceIn(minY, maxY)
                            coroutineScope.launch {
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                            }
                        }
                    )
                }
                .size(orbSizeDp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Open Promise Concierge AI Chat"
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val pivotX = if (screenWidthPx > 0) ((offsetX.value + orbSizePx / 2f) / screenWidthPx).coerceIn(0.05f, 0.95f) else 0.9f
                        val pivotY = if (screenHeightPx > 0) ((offsetY.value + orbSizePx / 2f) / screenHeightPx).coerceIn(0.05f, 0.95f) else 0.8f
                        onClick(pivotX, pivotY)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            // 1. Ambient Glowing Halo
            if (!reduceMotion) {
                Box(
                    modifier = Modifier
                        .size(orbSizeDp * 1.35f)
                        .scale(if (isDragging) 1.25f else pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.accent.copy(alpha = glowAlpha),
                                    Color(0xFF8B5CF6).copy(alpha = glowAlpha * 0.5f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }

            // 2. Glassmorphic Luminous Core Orb
            Surface(
                modifier = Modifier
                    .size(orbSizeDp)
                    .scale(if (isDragging) 1.12f else 1.0f)
                    .shadow(
                        elevation = if (isDragging) 12.dp else 6.dp,
                        shape = CircleShape,
                        spotColor = colors.accent.copy(alpha = 0.4f),
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                colors.accent,
                                Color(0xFFA78BFA),
                                colors.accent.copy(alpha = 0.5f)
                            )
                        ),
                        shape = CircleShape
                    ),
                shape = CircleShape,
                color = colors.cardBackground.copy(alpha = 0.92f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Subtle dynamic gradient fill
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        colors.accent.copy(alpha = 0.22f),
                                        Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                        Color.Transparent,
                                    )
                                )
                            )
                    )

                    // Sparkling AI Spark Icon
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(if (!reduceMotion && !isDragging) iconRotation else 0f),
                    )

                    // Live Online Indicator Micro Dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                            .border(1.dp, colors.cardBackground, CircleShape)
                    )
                }
            }
        }
    }
}
