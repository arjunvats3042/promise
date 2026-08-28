package app.promise.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    val initialAngle: Float,
    val speed: Float,
    val radius: Float,
    val color: Color,
    val rotationSpeed: Float,
    val isCircle: Boolean,
)

@Composable
fun PromiseCelebrationBurst(
    trigger: Boolean,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!trigger) return

    val progress = remember { Animatable(0f) }
    val particles = remember {
        val colors = listOf(
            Color(0xFFF59E0B), // Amber gold
            Color(0xFF10B981), // Emerald
            Color(0xFF3B82F6), // Accent blue
            Color(0xFF8B5CF6), // Purple
            Color(0xFFEC4899), // Rose
            Color(0xFFFDE047), // Yellow
        )
        val rnd = Random(System.currentTimeMillis())
        List(32) {
            val angle = rnd.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 180f + rnd.nextFloat() * 260f
            val radius = 4f + rnd.nextFloat() * 6f
            val color = colors[rnd.nextInt(colors.size)]
            val rotSpeed = -180f + rnd.nextFloat() * 360f
            val isCircle = rnd.nextBoolean()
            Particle(angle, speed, radius, color, rotSpeed, isCircle)
        }
    }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = LinearEasing),
        )
        onFinished()
    }

    val t = progress.value
    if (t < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val alpha = (1f - t * t).coerceIn(0f, 1f)

            particles.forEach { p ->
                val distance = p.speed * t
                val gravity = 120f * t * t
                val x = center.x + distance * cos(p.initialAngle)
                val y = center.y + distance * sin(p.initialAngle) + gravity

                val particleColor = p.color.copy(alpha = alpha)

                rotate(degrees = p.rotationSpeed * t, pivot = Offset(x, y)) {
                    if (p.isCircle) {
                        drawCircle(
                            color = particleColor,
                            radius = p.radius * (1f - t * 0.3f),
                            center = Offset(x, y),
                        )
                    } else {
                        drawRect(
                            color = particleColor,
                            topLeft = Offset(x - p.radius, y - p.radius),
                            size = androidx.compose.ui.geometry.Size(p.radius * 2f, p.radius * 1.4f),
                        )
                    }
                }
            }
        }
    }
}
