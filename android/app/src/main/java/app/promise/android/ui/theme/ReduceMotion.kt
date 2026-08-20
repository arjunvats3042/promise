package app.promise.android.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

fun animatorDurationScale(context: Context): Float {
    return Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}

fun isReduceMotionEnabled(context: Context): Boolean {
    return animatorDurationScale(context) == 0f
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return isReduceMotionEnabled(context)
}
