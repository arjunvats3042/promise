package app.promise.android.ui.profile

import app.promise.android.data.network.AuthSession
import app.promise.android.ui.haptics.FakePromiseHaptics
import app.promise.android.ui.theme.InMemoryThemeStore
import app.promise.android.ui.theme.PromiseThemeMode
import app.promise.android.ui.theme.ThemeController
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileViewModelTest {
    @Test
    fun setModeEmitsLightHapticOncePerChange() {
        val haptics = FakePromiseHaptics()
        val vm = ProfileViewModel(ThemeController(InMemoryThemeStore()), haptics, AuthSession())
        vm.setMode(PromiseThemeMode.Light)
        assertEquals(emptyList<String>(), haptics.events)
        vm.setMode(PromiseThemeMode.Dark)
        assertEquals(listOf("light"), haptics.events)
        vm.setMode(PromiseThemeMode.Dark)
        assertEquals(listOf("light"), haptics.events)
    }
}
