package app.promise.android.ui.profile

import androidx.lifecycle.ViewModel
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.User
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.theme.PromiseThemeMode
import app.promise.android.ui.theme.ThemeController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val themeController: ThemeController,
    private val haptics: PromiseHaptics,
    authSession: AuthSession,
) : ViewModel() {
    val mode: StateFlow<PromiseThemeMode> = themeController.mode
    val user: StateFlow<User?> = authSession.user

    fun setMode(mode: PromiseThemeMode) {
        if (themeController.mode.value == mode) return
        themeController.setMode(mode)
        haptics.light()
    }
}
