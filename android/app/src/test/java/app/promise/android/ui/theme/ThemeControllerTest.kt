package app.promise.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeControllerTest {
    @Test
    fun unsetFollowsSystem() {
        assertEquals(
            PromiseThemeMode.Dark,
            ThemeResolution.resolve(userSet = false, stored = null, systemDark = true),
        )
        assertEquals(
            PromiseThemeMode.Light,
            ThemeResolution.resolve(userSet = false, stored = null, systemDark = false),
        )
    }

    @Test
    fun userChoiceWinsOverSystem() {
        assertEquals(
            PromiseThemeMode.Light,
            ThemeResolution.resolve(
                userSet = true,
                stored = PromiseThemeMode.Light,
                systemDark = true,
            ),
        )
    }

    @Test
    fun setModePersistsAndIgnoresLaterSystemSync() {
        val store = InMemoryThemeStore()
        val controller = ThemeController(store)
        controller.syncSystem(systemDark = true)
        assertEquals(PromiseThemeMode.Dark, controller.mode.value)
        controller.setMode(PromiseThemeMode.Light)
        assertEquals(PromiseThemeMode.Light, controller.mode.value)
        controller.syncSystem(systemDark = true)
        assertEquals(PromiseThemeMode.Light, controller.mode.value)
    }

    @Test
    fun toggleRoundsTrip() {
        val controller = ThemeController(InMemoryThemeStore())
        controller.setMode(PromiseThemeMode.Light)
        controller.toggle()
        assertEquals(PromiseThemeMode.Dark, controller.mode.value)
        controller.toggle()
        assertEquals(PromiseThemeMode.Light, controller.mode.value)
    }
}
