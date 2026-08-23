package app.promise.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.promise.android.domain.DeviceRegistrationRepository
import app.promise.android.ui.components.AppUpdateDialog
import app.promise.android.ui.navigation.DeepLinkRouter
import app.promise.android.ui.navigation.PromiseNavHost
import app.promise.android.ui.theme.AccentSession
import app.promise.android.ui.theme.PromiseTheme
import app.promise.android.ui.theme.ThemeController
import app.promise.android.update.AppUpdateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeController: ThemeController
    @Inject lateinit var accentSession: AccentSession
    @Inject lateinit var appUpdateManager: AppUpdateManager
    @Inject lateinit var deviceRegistrationRepository: DeviceRegistrationRepository
    @Inject lateinit var deepLinkRouter: DeepLinkRouter

    private val handler = Handler(Looper.getMainLooper())
    private var isFirstResume = true

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            lifecycleScope.launch {
                deviceRegistrationRepository.syncDeviceRegistration()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial deep link or notification payload
        handleIncomingIntent(intent)

        // Request notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Sync FCM token on app launch
        lifecycleScope.launch {
            deviceRegistrationRepository.syncDeviceRegistration()
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) {
                themeController.syncSystem(systemDark)
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val uri = intent.dataString
        if (!uri.isNullOrBlank()) {
            deepLinkRouter.routeUri(uri)
            return
        }
        val entityType = intent.getStringExtra("entity_type")
        val entityId = intent.getStringExtra("entity_id")
        val eventType = intent.getStringExtra("event_type") ?: ""
        if (!entityType.isNullOrBlank() && !entityId.isNullOrBlank()) {
            deepLinkRouter.routeEntity(entityType, entityId, eventType)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "MainActivity onResume")

        // Delay the first check to let the Activity fully settle and the Firebase
        // App Distribution SDK register this Activity via ActivityLifecycleCallbacks.
        // Subsequent resumes (background → foreground) call immediately.
        val delayMs = if (isFirstResume) FIRST_CHECK_DELAY_MS else 0L
        isFirstResume = false

        handler.removeCallbacksAndMessages(UPDATE_CHECK_TOKEN)
        handler.postAtTime(
            {
                if (!isDestroyed && !isFinishing) {
                    Log.i(TAG, "calling AppUpdateManager.checkForUpdate")
                    appUpdateManager.checkForUpdate()
                }
            },
            UPDATE_CHECK_TOKEN,
            android.os.SystemClock.uptimeMillis() + delayMs,
        )
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(UPDATE_CHECK_TOKEN)
    }

    companion object {
        private const val TAG = "PromiseAppUpdate"
        private const val FIRST_CHECK_DELAY_MS = 1500L
        private val UPDATE_CHECK_TOKEN = Any()
    }
}

