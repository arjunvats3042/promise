package app.promise.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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

    fun <T> standardTween(durationMs: Int) = tween<T>(
        durationMillis = durationMs,
        easing = StandardEasing,
    )

    fun <T> exitTween(durationMs: Int) = tween<T>(
        durationMillis = durationMs,
        easing = ExitEasing,
    )
}
