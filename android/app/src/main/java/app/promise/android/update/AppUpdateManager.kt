package app.promise.android.update

import kotlinx.coroutines.flow.StateFlow

/**
 * Metadata for a newly detected release from Firebase App Distribution.
 */
data class AppReleaseInfo(
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String? = null,
)

/**
 * State of the update download process.
 */
sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Downloading(
        val progressPercent: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : AppUpdateDownloadState
    data object Downloaded : AppUpdateDownloadState
    data class Error(val userMessage: String) : AppUpdateDownloadState
    data object Cancelled : AppUpdateDownloadState
}

/**
 * UI State for in-app update prompt.
 */
sealed interface AppUpdateUiState {
    data object Hidden : AppUpdateUiState
    data class UpdateAvailable(
        val release: AppReleaseInfo,
        val downloadState: AppUpdateDownloadState = AppUpdateDownloadState.Idle,
    ) : AppUpdateUiState
}

/**
 * Abstraction for in-app update checking and installation.
 *
 * NOTE FOR GOOGLE PLAY COMPATIBILITY:
 * This abstraction allows Firebase App Distribution self-update functionality
 * to be safely swapped with Google Play In-App Updates or a No-Op implementation
 * without touching any application UI or business logic.
 */
interface AppUpdateManager {
    val updateUiState: StateFlow<AppUpdateUiState>

    /**
     * Checks for a newer release if not already checked in this app session.
     * Silent on no update / error.
     */
    fun checkForUpdate(force: Boolean = false)

    /**
     * User initiated the update. Begins downloading APK and triggers system installer.
     */
    fun startUpdate()

    /**
     * User dismissed the update prompt for the remainder of this app session.
     */
    fun dismissForSession()
}
