package app.promise.android.ui.theme

object ThemeResolution {
    fun resolve(
        userSet: Boolean,
        stored: PromiseThemeMode?,
        systemDark: Boolean,
    ): PromiseThemeMode {
        if (userSet && stored != null) return stored
        return if (systemDark) PromiseThemeMode.Dark else PromiseThemeMode.Light
    }
}

interface ThemeStore {
    fun isUserSet(): Boolean
    fun readMode(): PromiseThemeMode?
    fun writeMode(mode: PromiseThemeMode)
}

class InMemoryThemeStore(
    private var userSet: Boolean = false,
    private var mode: PromiseThemeMode? = null,
) : ThemeStore {
    override fun isUserSet(): Boolean = userSet
    override fun readMode(): PromiseThemeMode? = mode
    override fun writeMode(mode: PromiseThemeMode) {
        this.mode = mode
        userSet = true
    }
}
