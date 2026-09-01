package app.promise.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

/**
 * The original signature Promise "P" brand monogram.
 */
@Composable
fun PromiseLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showWordmark: Boolean = false,
    wordmarkSubtitle: String? = null,
    animated: Boolean = false,
) {
    val colors = PromiseThemeColors.current

    val pulseScale by if (animated) {
        val transition = rememberInfiniteTransition(label = "logoPulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "logoPulseScale",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    if (showWordmark) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp),
        ) {
            PromiseLogoEmblem(
                size = size,
                primaryColor = colors.primaryControl,
                backgroundColor = colors.surfaceMuted,
                scale = pulseScale,
            )
            Column {
                Text(
                    text = "Promise",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp,
                )
                if (wordmarkSubtitle != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = wordmarkSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    } else {
        PromiseLogoEmblem(
            modifier = modifier,
            size = size,
            primaryColor = colors.primaryControl,
            backgroundColor = colors.surfaceMuted,
            scale = pulseScale,
        )
    }
}

/**
 * Precision rendering of the original Promise "P" Monogram.
 */
@Composable
fun PromiseLogoEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    primaryColor: Color,
    backgroundColor: Color,
    scale: Float = 1f,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(size * 0.24f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.size(size * 0.65f * scale),
        ) {
            val w = this.size.width
            val h = this.size.height

            // Dimensions calibrated directly from the canonical ic_launcher_foreground geometry (108x108 viewport)
            val scaleFactor = w / 108f

            // 1. Vertical bar (M34,28h12v52h-12z)
            val barLeft = 24f * scaleFactor
            val barTop = 20f * scaleFactor
            val barWidth = 16f * scaleFactor
            val barHeight = 68f * scaleFactor

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.5f * scaleFactor, 2.5f * scaleFactor),
            )

            // 2. Upper "P" loop (M34,28h32c8.8,0 16,7.2 16,16c0,8.8 -7.2,16 -16,16h-20z)
            val loopPath = Path().apply {
                val loopLeft = barLeft
                val loopTop = barTop
                val loopRight = loopLeft + (42f * scaleFactor)
                val loopBottom = loopTop + (42f * scaleFactor)
                val cornerRadius = 21f * scaleFactor

                moveTo(loopLeft, loopTop)
                lineTo(loopRight - cornerRadius, loopTop)
                arcTo(
                    rect = Rect(
                        left = loopRight - (cornerRadius * 2),
                        top = loopTop,
                        right = loopRight,
                        bottom = loopBottom,
                    ),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                lineTo(loopLeft, loopBottom)
                close()
            }

            drawPath(
                path = loopPath,
                color = primaryColor,
            )
        }
    }
}
