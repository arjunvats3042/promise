package app.promise.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.rememberReduceMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Multi-Layered Harmonic Silk Background Animation.
 * Delicately renders 3 intertwined flowing ribbons with subtle gradient aura
 * that periodically converge into the signature Promise loop/check.
 */
@Composable
fun LivingPromiseBackground(
    modifier: Modifier = Modifier,
    isGathering: Boolean = false,
) {
    val colors = PromiseThemeColors.current
    val accent = colors.accent
    val reduceMotion = rememberReduceMotion()

    // Rapid, smooth gather transition when user taps login (~350ms acceleration)
    val gatherPulse by animateFloatAsState(
        targetValue = if (isGathering) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "gatherPulse",
    )

    if (reduceMotion) {
        StaticHarmonicSilkRibbon(
            modifier = modifier,
            accent = accent,
        )
    } else {
        AnimatedHarmonicSilkRibbon(
            modifier = modifier,
            accent = accent,
            gatherPulse = gatherPulse,
        )
    }
}

@Composable
private fun AnimatedHarmonicSilkRibbon(
    modifier: Modifier,
    accent: Color,
    gatherPulse: Float,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "harmonicSilkCycle")

    // 7.5-second ambient rhythmic cycle
    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
        ),
        label = "cycleProgress",
    )

    // Breathing luminance pulse (~3.5s period)
    val breatheProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3750, easing = LinearEasing),
        ),
        label = "breatheProgress",
    )

    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val path3 = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        if (width <= 0f || height <= 0f) return@Canvas

        // Effective progress blended with gather pulse
        val effectiveProgress = if (gatherPulse > 0f) {
            (cycleProgress * (1f - gatherPulse) + 0.62f * gatherPulse).coerceIn(0f, 1f)
        } else {
            cycleProgress
        }

        // Morph factor into checkmark loop (peaks around progress 0.45 - 0.72)
        val checkmorphFactor = when {
            effectiveProgress in 0.42f..0.58f -> ((effectiveProgress - 0.42f) / 0.16f)
            effectiveProgress in 0.58f..0.72f -> 1.0f - ((effectiveProgress - 0.58f) / 0.14f)
            else -> 0.0f
        }
        val morph = maxOf(checkmorphFactor, gatherPulse).coerceIn(0f, 1f)

        val breathe = sin((breatheProgress * 2f * Math.PI).toFloat()) * 0.5f + 0.5f

        // Draw ambient atmospheric glow in the focal area
        val glowCenter = Offset(
            x = width * lerp(0.40f, 0.48f, morph),
            y = height * lerp(0.42f, 0.45f, morph),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = (0.06f + morph * 0.05f + breathe * 0.02f)),
                    Color.Transparent,
                ),
                center = glowCenter,
                radius = width * 0.55f,
            ),
            radius = width * 0.55f,
            center = glowCenter,
        )

        val angle = (effectiveProgress * 2f * Math.PI).toFloat()

        // ----------------------------------------------------
        // Ribbon 1: Primary Silk Wave (Strongest Lead Ribbon)
        // ----------------------------------------------------
        val wave1 = sin(angle.toDouble()).toFloat() * (height * 0.065f)
        path1.reset()
        val r1StartX = width * -0.10f
        val r1StartY = height * 0.36f + wave1 * 0.6f

        val r1C1X = width * 0.24f
        val r1C1Y = height * 0.22f + wave1

        val r1C2X = width * 0.44f
        val r1C2Y = height * 0.56f - wave1

        val r1EndX = width * 1.12f
        val r1EndY = height * 0.38f + wave1 * 0.8f

        // Check target points for Ribbon 1
        val r1TargetC1X = width * 0.28f
        val r1TargetC1Y = height * 0.44f
        val r1TargetC2X = width * 0.42f
        val r1TargetC2Y = height * 0.52f
        val r1TargetEndX = width * 0.66f
        val r1TargetEndY = height * 0.32f

        path1.moveTo(r1StartX, r1StartY)
        path1.cubicTo(
            lerp(r1C1X, r1TargetC1X, morph),
            lerp(r1C1Y, r1TargetC1Y, morph),
            lerp(r1C2X, r1TargetC2X, morph),
            lerp(r1C2Y, r1TargetC2Y, morph),
            lerp(r1EndX, r1TargetEndX, morph),
            lerp(r1EndY, r1TargetEndY, morph),
        )

        // ----------------------------------------------------
        // Ribbon 2: Harmonic Secondary Wave (Slight Phase Offset)
        // ----------------------------------------------------
        val wave2 = cos((angle * 1.15f).toDouble()).toFloat() * (height * 0.055f)
        path2.reset()
        val r2StartX = width * -0.08f
        val r2StartY = height * 0.33f - wave2 * 0.5f

        val r2C1X = width * 0.28f
        val r2C1Y = height * 0.26f - wave2

        val r2C2X = width * 0.48f
        val r2C2Y = height * 0.51f + wave2

        val r2EndX = width * 1.08f
        val r2EndY = height * 0.42f - wave2 * 0.7f

        // Check target points for Ribbon 2 (closely wraps Ribbon 1)
        val r2TargetC1X = width * 0.30f
        val r2TargetC1Y = height * 0.46f
        val r2TargetC2X = width * 0.44f
        val r2TargetC2Y = height * 0.50f
        val r2TargetEndX = width * 0.64f
        val r2TargetEndY = height * 0.34f

        path2.moveTo(r2StartX, r2StartY)
        path2.cubicTo(
            lerp(r2C1X, r2TargetC1X, morph),
            lerp(r2C1Y, r2TargetC1Y, morph),
            lerp(r2C2X, r2TargetC2X, morph),
            lerp(r2C2Y, r2TargetC2Y, morph),
            lerp(r2EndX, r2TargetEndX, morph),
            lerp(r2EndY, r2TargetEndY, morph),
        )

        // ----------------------------------------------------
        // Ribbon 3: Ambient Echo Wave (Gentle Deep Trail)
        // ----------------------------------------------------
        val wave3 = sin((angle * 0.85f + 1.2f).toDouble()).toFloat() * (height * 0.045f)
        path3.reset()
        val r3StartX = width * -0.12f
        val r3StartY = height * 0.40f + wave3 * 0.7f

        val r3C1X = width * 0.20f
        val r3C1Y = height * 0.18f + wave3 * 0.8f

        val r3C2X = width * 0.40f
        val r3C2Y = height * 0.60f - wave3 * 0.8f

        val r3EndX = width * 1.15f
        val r3EndY = height * 0.35f + wave3

        // Check target points for Ribbon 3
        val r3TargetC1X = width * 0.26f
        val r3TargetC1Y = height * 0.42f
        val r3TargetC2X = width * 0.40f
        val r3TargetC2Y = height * 0.54f
        val r3TargetEndX = width * 0.68f
        val r3TargetEndY = height * 0.30f

        path3.moveTo(r3StartX, r3StartY)
        path3.cubicTo(
            lerp(r3C1X, r3TargetC1X, morph),
            lerp(r3C1Y, r3TargetC1Y, morph),
            lerp(r3C2X, r3TargetC2X, morph),
            lerp(r3C2Y, r3TargetC2Y, morph),
            lerp(r3EndX, r3TargetEndX, morph),
            lerp(r3EndY, r3TargetEndY, morph),
        )

        // Render Ribbon 3 (Echo)
        drawSilkStroke(
            path = path3,
            accent = accent,
            strokeWidth = 1.2.dp.toPx(),
            glowWidth = 12.dp.toPx(),
            lineAlpha = (0.07f + morph * 0.04f).coerceIn(0.04f, 0.14f),
            glowAlpha = (0.025f + morph * 0.02f).coerceIn(0.015f, 0.06f),
        )

        // Render Ribbon 2 (Harmonic)
        drawSilkStroke(
            path = path2,
            accent = accent,
            strokeWidth = 1.6.dp.toPx(),
            glowWidth = 16.dp.toPx(),
            lineAlpha = (0.11f + morph * 0.05f).coerceIn(0.08f, 0.18f),
            glowAlpha = (0.035f + morph * 0.03f).coerceIn(0.02f, 0.08f),
        )

        // Render Ribbon 1 (Primary)
        drawSilkStroke(
            path = path1,
            accent = accent,
            strokeWidth = 2.2.dp.toPx(),
            glowWidth = 22.dp.toPx(),
            lineAlpha = (0.16f + morph * 0.08f).coerceIn(0.12f, 0.26f),
            glowAlpha = (0.05f + morph * 0.04f).coerceIn(0.03f, 0.10f),
        )
    }
}

@Composable
private fun StaticHarmonicSilkRibbon(
    modifier: Modifier,
    accent: Color,
) {
    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val path3 = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        if (width <= 0f || height <= 0f) return@Canvas

        path1.reset()
        path1.moveTo(width * -0.05f, height * 0.38f)
        path1.cubicTo(width * 0.28f, height * 0.44f, width * 0.42f, height * 0.50f, width * 0.65f, height * 0.33f)

        path2.reset()
        path2.moveTo(width * -0.04f, height * 0.35f)
        path2.cubicTo(width * 0.30f, height * 0.46f, width * 0.44f, height * 0.48f, width * 0.63f, height * 0.35f)

        path3.reset()
        path3.moveTo(width * -0.06f, height * 0.41f)
        path3.cubicTo(width * 0.26f, height * 0.42f, width * 0.40f, height * 0.52f, width * 0.67f, height * 0.31f)

        drawSilkStroke(path3, accent, 1.0.dp.toPx(), 10.dp.toPx(), 0.06f, 0.02f)
        drawSilkStroke(path2, accent, 1.4.dp.toPx(), 14.dp.toPx(), 0.09f, 0.03f)
        drawSilkStroke(path1, accent, 2.0.dp.toPx(), 20.dp.toPx(), 0.14f, 0.05f)
    }
}

private fun DrawScope.drawSilkStroke(
    path: Path,
    accent: Color,
    strokeWidth: Float,
    glowWidth: Float,
    lineAlpha: Float,
    glowAlpha: Float,
) {
    // 1. Soft Outer Glow
    drawPath(
        path = path,
        color = accent.copy(alpha = glowAlpha),
        style = Stroke(
            width = glowWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // 2. Focused Crisp Line
    drawPath(
        path = path,
        color = accent.copy(alpha = lineAlpha),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
