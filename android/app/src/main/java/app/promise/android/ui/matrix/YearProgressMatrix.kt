package app.promise.android.ui.matrix

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun YearProgressCard(
    onClick: () -> Unit,
    onShareClick: () -> Unit = onClick,
    timeZoneId: String = "Asia/Kolkata",
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    val progressInfo = remember(timeZoneId) { YearProgressCalculator.calculate(timeZoneId) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick),
        color = colors.surfaceMuted,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
        ) {
            // Header: Year badge & Current Day count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                    Text(
                        text = "${progressInfo.year} ROADMAP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 1.2.sp,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = "DAY ${progressInfo.currentDayOfYear} / ${progressInfo.totalDays}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        letterSpacing = 0.5.sp,
                    )
                    Icon(
                        imageVector = Icons.Outlined.IosShare,
                        contentDescription = "Share",
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onShareClick),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Main Stat: Progress percent & days left
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "${progressInfo.percentElapsed}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "${progressInfo.daysRemaining} days remaining in ${progressInfo.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // 365-Dot Minimalist Matrix
            YearDotMatrix(
                currentDayOfYear = progressInfo.currentDayOfYear,
                totalDays = progressInfo.totalDays,
                accentColor = colors.accent,
                pastColor = colors.textPrimary.copy(alpha = 0.75f),
                futureColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                todayGlowColor = colors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
        }
    }
}

@Composable
fun YearDotMatrix(
    currentDayOfYear: Int,
    totalDays: Int,
    accentColor: Color,
    pastColor: Color,
    futureColor: Color,
    todayGlowColor: Color,
    modifier: Modifier = Modifier,
    onDaySelected: ((Int) -> Unit)? = null,
) {
    // Pulse animation for today's active dot
    val infiniteTransition = rememberInfiniteTransition(label = "todayPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val rows = 7
    val cols = (totalDays + rows - 1) / rows

    Canvas(
        modifier = modifier
            .pointerInput(onDaySelected) {
                if (onDaySelected != null) {
                    detectTapGestures { offset ->
                        val cellW = size.width / cols
                        val cellH = size.height / rows
                        val c = (offset.x / cellW).toInt().coerceIn(0, cols - 1)
                        val r = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
                        val dayIndex = c * rows + r + 1
                        if (dayIndex in 1..totalDays) {
                            onDaySelected(dayIndex)
                        }
                    }
                }
            },
    ) {
        val width = size.width
        val height = size.height

        val cellWidth = width / cols
        val cellHeight = height / rows
        val dotRadius = minOf(cellWidth, cellHeight) * 0.28f

        for (day in 1..totalDays) {
            val dayZero = day - 1
            val col = dayZero / rows
            val row = dayZero % rows

            val cx = col * cellWidth + cellWidth / 2f
            val cy = row * cellHeight + cellHeight / 2f

            when {
                day < currentDayOfYear -> {
                    // Past Day: Filled solid dot
                    drawCircle(
                        color = pastColor,
                        radius = dotRadius,
                        center = Offset(cx, cy),
                    )
                }
                day == currentDayOfYear -> {
                    // Today: Pulsing halo + Accent filled core
                    drawCircle(
                        color = todayGlowColor.copy(alpha = pulseAlpha),
                        radius = dotRadius * pulseScale * 1.5f,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = accentColor,
                        radius = dotRadius * 1.15f,
                        center = Offset(cx, cy),
                    )
                }
                else -> {
                    // Future Day: Hollow subtle circle
                    drawCircle(
                        color = futureColor,
                        radius = dotRadius * 0.85f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
            }
        }
    }
}
