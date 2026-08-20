package app.promise.android.ui.theme

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsThemeStore @Inject constructor(
    @ApplicationContext context: Context,
) : ThemeStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isUserSet(): Boolean = prefs.getBoolean(KEY_USER_SET, false)

    override fun readMode(): PromiseThemeMode? {
        val raw = prefs.getString(KEY_MODE, null) ?: return null
        return runCatching { PromiseThemeMode.valueOf(raw) }.getOrNull()
    }

    override fun writeMode(mode: PromiseThemeMode) {
        prefs.edit()
            .putBoolean(KEY_USER_SET, true)
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "promise_theme"
        const val KEY_USER_SET = "user_set"
        const val KEY_MODE = "mode"
    }
}
