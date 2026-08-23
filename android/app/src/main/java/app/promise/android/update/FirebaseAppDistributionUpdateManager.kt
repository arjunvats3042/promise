package app.promise.android.update

import android.util.Log
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.google.firebase.appdistribution.OnProgressListener
import com.google.firebase.appdistribution.UpdateProgress
import com.google.firebase.appdistribution.UpdateStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of [AppUpdateManager] using Firebase App Distribution Android SDK.
 *
 * SAFETY & PRIVACY:
 * - Silent when no update is available or service is unreachable.
 * - Suppresses repeated alerts in the same app session once dismissed by the user.
 * - Never logs credentials, auth tokens, API keys, or private user content.
 *
 * GOOGLE PLAY COMPATIBILITY NOTE:
 * This component is intended for pre-release and Firebase App Distribution test builds.
 * For future Google Play production builds, replace or exclude this implementation in favor
 * of Play In-App Updates or a No-Op implementation.
 */
@Singleton
class FirebaseAppDistributionUpdateManager @Inject constructor() : AppUpdateManager {
    private val _updateUiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Hidden)
    override val updateUiState: StateFlow<AppUpdateUiState> = _updateUiState.asStateFlow()

    @Volatile
    private var hasCheckedThisSession: Boolean = false

    @Volatile
    private var hasDismissedThisSession: Boolean = false

    override fun checkForUpdate(force: Boolean) {
        if (hasCheckedThisSession && !force) return
        if (hasDismissedThisSession && !force) return
        hasCheckedThisSession = true

        try {
            val appDistribution = FirebaseAppDistribution.getInstance()
            appDistribution.checkForNewRelease()
                .addOnSuccessListener { release ->
                    if (release == null) {
                        Log.d(TAG, "No newer Firebase App Distribution release found")
                        return@addOnSuccessListener
                    }
                    Log.d(TAG, "New App Distribution release available: ${release.displayVersion} (${release.versionCode})")
                    if (!hasDismissedThisSession) {
                        _updateUiState.value = AppUpdateUiState.UpdateAvailable(
                            release = AppReleaseInfo(
                                versionName = release.displayVersion,
                                versionCode = release.versionCode,
                                releaseNotes = release.releaseNotes,
                            ),
                            downloadState = AppUpdateDownloadState.Idle,
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    if (exception is FirebaseAppDistributionException) {
                        Log.w(TAG, "App Distribution check completed with code: ${exception.errorCode}")
                    } else {
                        Log.w(TAG, "App Distribution release check unavailable")
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize App Distribution release check", e)
        }
    }

    override fun startUpdate() {
        val current = _updateUiState.value
        if (current !is AppUpdateUiState.UpdateAvailable) return

        _updateUiState.value = current.copy(
            downloadState = AppUpdateDownloadState.Downloading(
                progressPercent = 0f,
                bytesDownloaded = 0,
                totalBytes = 0,
            ),
        )

        try {
            val appDistribution = FirebaseAppDistribution.getInstance()
            appDistribution.updateApp()
                .addOnProgressListener(
                    OnProgressListener { progress: UpdateProgress ->
                        val total = progress.apkFileTotalBytes
                        val downloaded = progress.apkBytesDownloaded
                        val percent = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

                        val latest = _updateUiState.value
                        if (latest is AppUpdateUiState.UpdateAvailable) {
                            val downloadState = when (progress.updateStatus) {
                                UpdateStatus.DOWNLOADING -> AppUpdateDownloadState.Downloading(percent, downloaded, total)
                                UpdateStatus.DOWNLOADED -> AppUpdateDownloadState.Downloaded
                                UpdateStatus.UPDATE_CANCELED,
                                UpdateStatus.INSTALL_CANCELED -> AppUpdateDownloadState.Cancelled
                                UpdateStatus.DOWNLOAD_FAILED,
                                UpdateStatus.INSTALL_FAILED -> AppUpdateDownloadState.Error("Download failed. Please try again later.")
                                else -> latest.downloadState
                            }
                            _updateUiState.value = latest.copy(downloadState = downloadState)
                        }
                    },
                )
                .addOnFailureListener { exception ->
                    Log.w(TAG, "App Distribution update failed")
                    val updated = _updateUiState.value
                    if (updated is AppUpdateUiState.UpdateAvailable) {
                        _updateUiState.value = updated.copy(
                            downloadState = AppUpdateDownloadState.Error(
                                userMessage = "Unable to download update. Please try again later.",
                            ),
                        )
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error invoking updateApp()", e)
            val updated = _updateUiState.value
            if (updated is AppUpdateUiState.UpdateAvailable) {
                _updateUiState.value = updated.copy(
                    downloadState = AppUpdateDownloadState.Error(
                        userMessage = "Unable to download update. Please try again later.",
                    ),
                )
            }
        }
    }

    override fun dismissForSession() {
        hasDismissedThisSession = true
        _updateUiState.value = AppUpdateUiState.Hidden
    }

    companion object {
        private const val TAG = "PromiseAppUpdate"
    }
}
