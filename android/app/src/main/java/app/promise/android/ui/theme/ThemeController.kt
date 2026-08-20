package app.promise.android.ui.theme

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ThemeController @Inject constructor(
    private val store: ThemeStore,
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
        _mode.value = ThemeResolution.resolve(
            userSet = false,
            stored = null,
            systemDark = systemDark,
        )
    }

    fun setMode(mode: PromiseThemeMode) {
        store.writeMode(mode)
        _mode.value = mode
    }

    fun toggle() {
        setMode(
            when (_mode.value) {
                PromiseThemeMode.Light -> PromiseThemeMode.Dark
                PromiseThemeMode.Dark -> PromiseThemeMode.Light
            },
        )
    }
}
