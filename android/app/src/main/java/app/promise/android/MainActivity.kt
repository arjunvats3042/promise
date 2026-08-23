package app.promise.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.ui.components.AppUpdateDialog
import app.promise.android.ui.navigation.PromiseNavHost
import app.promise.android.ui.theme.AccentSession
import app.promise.android.ui.theme.PromiseTheme
import app.promise.android.ui.theme.ThemeController
import app.promise.android.update.AppUpdateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeController: ThemeController
    @Inject lateinit var accentSession: AccentSession
    @Inject lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) {
                themeController.syncSystem(systemDark)
            }
            LaunchedEffect(Unit) {
                appUpdateManager.checkForUpdate()
            }
            val mode by themeController.mode.collectAsStateWithLifecycle()
            val sessionAccent by accentSession.accent.collectAsStateWithLifecycle()
            val updateUiState by appUpdateManager.updateUiState.collectAsStateWithLifecycle()

            PromiseTheme(mode = mode, sessionAccent = sessionAccent) {
                PromiseNavHost()
                AppUpdateDialog(
                    uiState = updateUiState,
                    onUpdate = { appUpdateManager.startUpdate() },
                    onDismiss = { appUpdateManager.dismissForSession() },
                )
            }
        }
    }
}
