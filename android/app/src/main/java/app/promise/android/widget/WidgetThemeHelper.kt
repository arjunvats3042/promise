package app.promise.android.widget

import android.content.Context
import android.content.res.Configuration

object WidgetThemeHelper {
    private const val THEME_PREFS_NAME = "promise_theme"
    private const val KEY_USER_SET = "user_set"
    private const val KEY_MODE = "mode"

    /**
     * Resolves whether widgets should render in dark theme.
     * Prioritizes user's in-app theme preference (Light / Dark),
     * and falls back to system configuration if not explicitly set.
     */
    fun isDarkTheme(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_USER_SET, false)) {
                val mode = prefs.getString(KEY_MODE, null)
                if (mode == "Dark") return true
                if (mode == "Light") return false
            }
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } catch (_: Throwable) {
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
