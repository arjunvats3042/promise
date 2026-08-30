package app.promise.android.ui.theme

import android.content.Context
import app.promise.android.widget.PromiseWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class ThemeController @Inject constructor(
    private val store: ThemeStore,
    @param:ApplicationContext private val context: Context,
) {
    private val _mode = MutableStateFlow(
        ThemeResolution.resolve(
            userSet = store.isUserSet(),
            stored = store.readMode(),
            systemDark = false,
        ),
    )
    val mode: StateFlow<PromiseThemeMode> = _mode.asStateFlow()

    /** Call when system dark flag is known; ignored once the user has chosen. */
    fun syncSystem(systemDark: Boolean) {
        if (store.isUserSet()) return
        val resolved = ThemeResolution.resolve(
            userSet = false,
            stored = null,
            systemDark = systemDark,
        )
        if (_mode.value != resolved) {
            _mode.value = resolved
            refreshWidgets()
        }
    }

    fun setMode(mode: PromiseThemeMode) {
        store.writeMode(mode)
        _mode.value = mode
        refreshWidgets()
    }

    fun toggle() {
        setMode(
            when (_mode.value) {
                PromiseThemeMode.Light -> PromiseThemeMode.Dark
                PromiseThemeMode.Dark -> PromiseThemeMode.Light
            },
        )
    }

    private fun refreshWidgets() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                PromiseWidgetUpdater.updateAllWidgetsTheme(context)
            }
        }
    }
}
