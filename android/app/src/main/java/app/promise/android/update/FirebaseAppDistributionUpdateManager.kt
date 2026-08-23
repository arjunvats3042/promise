package app.promise.android.update

import android.util.Log
import app.promise.android.BuildConfig
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.google.firebase.appdistribution.OnProgressListener
import com.google.firebase.appdistribution.UpdateProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of [AppUpdateManager] using Firebase App Distribution Android SDK basic flow.
 *
 * OFFICIAL BASIC FLOW:
 * Uses [FirebaseAppDistribution.updateIfNewReleaseAvailable] on Activity onResume:
 * - Checks whether tester alerts are enabled
 * - Prompts the tester to sign in if required
 * - Checks for a newer release
 * - Shows the official Firebase update prompt
 * - Downloads the APK with progress
 * - Prompts Android package installer
 *
 * SAFETY & PRIVACY:
 * - Never logs credentials, auth tokens, API keys, or private user content.
 * - Does not block app startup.
 * - Transient errors (network, auth canceled) do NOT permanently suppress checks.
 */
@Singleton
class FirebaseAppDistributionUpdateManager @Inject constructor() : AppUpdateManager {
    private val _updateUiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Hidden)
    override val updateUiState: StateFlow<AppUpdateUiState> = _updateUiState.asStateFlow()

    @Volatile
    private var isChecking: Boolean = false

    @Volatile
    private var hasDismissedThisSession: Boolean = false

    @Volatile
    private var hasCompletedCheckThisSession: Boolean = false

    override fun checkForUpdate(force: Boolean) {
        Log.i(TAG, "checkForUpdate entered")
        Log.i(TAG, "FirebaseAppDistributionUpdateManager check entered")

        if (isChecking) {
            Log.i(TAG, "Update check already in progress, skipping concurrent check.")
            return
        }
        if (hasDismissedThisSession && !force) {
            Log.i(TAG, "Update check skipped: user dismissed update for this session.")
            return
        }
        if (hasCompletedCheckThisSession && !force) {
            Log.i(TAG, "Update check skipped: already checked in this session.")
            return
        }

        isChecking = true
        Log.i(
            TAG,
            "Update check started. Current versionName=${BuildConfig.VERSION_NAME}, versionCode=${BuildConfig.VERSION_CODE}",
        )

        try {
            val appDistribution = FirebaseAppDistribution.getInstance()
            Log.i(TAG, "Tester state: isTesterSignedIn=${appDistribution.isTesterSignedIn}")

            Log.i(TAG, "calling updateIfNewReleaseAvailable")
            appDistribution.updateIfNewReleaseAvailable()
                .addOnProgressListener(
                    OnProgressListener { progress: UpdateProgress ->
                        val total = progress.apkFileTotalBytes
                        val downloaded = progress.apkBytesDownloaded
                        val percent = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                        Log.i(
                            TAG,
                            "Update download progress: status=${progress.updateStatus}, downloaded=$downloaded/$total bytes (${(percent * 100).toInt()}%)",
                        )
                    },
                )
                .addOnSuccessListener {
                    hasCompletedCheckThisSession = true
                    Log.i(TAG, "updateIfNewReleaseAvailable success")
                }
                .addOnFailureListener { exception ->
                    // Do NOT permanently lock the session on failure so tester can retry upon fixing network/signing in
                    val exClass = exception::class.java.simpleName
                    val statusName = if (exception is FirebaseAppDistributionException) {
                        exception.errorCode.name
                    } else {
                        "N/A"
                    }
                    val safeMessage = exception.message ?: "No details"
                    Log.w(
                        TAG,
                        "updateIfNewReleaseAvailable failure class=$exClass code=$statusName message=$safeMessage",
                    )
                }
                .addOnCompleteListener {
                    isChecking = false
                }
        } catch (e: Exception) {
            isChecking = false
            val exClass = e::class.java.simpleName
            val safeMessage = e.message ?: "No details"
            Log.w(TAG, "App Distribution initialization failure class=$exClass message=$safeMessage")
        }
    }

    override fun startUpdate() {
        try {
            Log.i(TAG, "startUpdate called")
            val appDistribution = FirebaseAppDistribution.getInstance()
            appDistribution.updateApp()
        } catch (e: Exception) {
            Log.w(TAG, "startUpdate failure: ${e::class.java.simpleName} - ${e.message}")
        }
    }

    override fun dismissForSession() {
        Log.i(TAG, "dismissForSession called")
        hasDismissedThisSession = true
        _updateUiState.value = AppUpdateUiState.Hidden
    }

    companion object {
        private const val TAG = "PromiseAppUpdate"
    }
}

