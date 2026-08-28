package app.promise.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

object Motion {
    const val PressMs = 90
    const val MicroMs = 120
    const val FilterChangeMs = 160
    const val ListInsertMs = 180
    const val CompletionMs = 180
    const val TabMs = 200
    const val ScreenPushMs = 210
    const val SheetOpenMs = 220
    const val SheetCloseMs = 200
    const val ThemeMs = 220
    const val GreetingInitialDelayMs = 180
    const val GreetingTypePerCharMs = 52
    const val GreetingDeletePerCharMs = 28
    const val GreetingChangeHoldMs = 900
    const val GreetingChangeGapMs = 120
    const val CheckInFeedbackMs = 180
    const val ReducedMotionFadeMs = 100

    val TabSlideDp = 16.dp
    val ListInsertRiseDp = 8.dp

    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val ExitEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val EnterEasing: Easing = FastOutSlowInEasing
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    fun <T> standardTween(durationMs: Int) = tween<T>(
        durationMillis = durationMs,
        easing = StandardEasing,
    )

    fun <T> exitTween(durationMs: Int) = tween<T>(
        durationMillis = durationMs,
        easing = ExitEasing,
    )

    fun <T> snappySpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> bouncySpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> gentleSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )
}

/**
 * Applies a subtle, tactile spring compression scale effect on press (0.97f).
 */
fun Modifier.pressScale(
    targetScale: Float = 0.97f,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Clickable modifier that combines subtle tactile spring scale and standard ripple.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    targetScale: Float = 0.97f,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bouncy-click-scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

