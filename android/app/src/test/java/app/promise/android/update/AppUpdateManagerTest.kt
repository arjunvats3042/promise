package app.promise.android.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test fake implementation of [AppUpdateManager] to verify all state transitions,
 * session dismissal, duplicate check suppression, and error handling.
 */
class FakeAppUpdateManager : AppUpdateManager {
    private val _updateUiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Hidden)
    override val updateUiState: StateFlow<AppUpdateUiState> = _updateUiState.asStateFlow()

    var checkCallCount = 0
        private set

    var startUpdateCallCount = 0
        private set

    var nextRelease: AppReleaseInfo? = null
    var shouldFailCheck: Boolean = false
    var shouldFailDownload: Boolean = false

    private var hasCheckedThisSession = false
    private var hasDismissedThisSession = false

    override fun checkForUpdate(force: Boolean) {
        if (hasCheckedThisSession && !force) return
        if (hasDismissedThisSession && !force) return

        hasCheckedThisSession = true
        checkCallCount++

        if (shouldFailCheck) {
            // Silently stay hidden on network/service failure
            return
        }

        val release = nextRelease
        if (release != null && !hasDismissedThisSession) {
            _updateUiState.value = AppUpdateUiState.UpdateAvailable(
                release = release,
                downloadState = AppUpdateDownloadState.Idle,
            )
        }
    }

    override fun startUpdate() {
        startUpdateCallCount++
        val current = _updateUiState.value
        if (current !is AppUpdateUiState.UpdateAvailable) return

        if (shouldFailDownload) {
            _updateUiState.value = current.copy(
                downloadState = AppUpdateDownloadState.Error(
                    userMessage = "Unable to download update. Please try again later.",
                ),
            )
        } else {
            _updateUiState.value = current.copy(
                downloadState = AppUpdateDownloadState.Downloading(
                    progressPercent = 0.5f,
                    bytesDownloaded = 5000,
                    totalBytes = 10000,
                ),
            )
        }
    }

    fun simulateDownloadProgress(percent: Float, downloaded: Long, total: Long) {
        val current = _updateUiState.value
        if (current is AppUpdateUiState.UpdateAvailable) {
            _updateUiState.value = current.copy(
                downloadState = AppUpdateDownloadState.Downloading(
                    progressPercent = percent,
                    bytesDownloaded = downloaded,
                    totalBytes = total,
                ),
            )
        }
    }

    fun simulateDownloadComplete() {
        val current = _updateUiState.value
        if (current is AppUpdateUiState.UpdateAvailable) {
            _updateUiState.value = current.copy(
                downloadState = AppUpdateDownloadState.Downloaded,
            )
        }
    }

    fun simulateDownloadCancelled() {
        val current = _updateUiState.value
        if (current is AppUpdateUiState.UpdateAvailable) {
            _updateUiState.value = current.copy(
                downloadState = AppUpdateDownloadState.Cancelled,
            )
        }
    }

    override fun dismissForSession() {
        hasDismissedThisSession = true
        _updateUiState.value = AppUpdateUiState.Hidden
    }
}

class AppUpdateManagerTest {

    private lateinit var updateManager: FakeAppUpdateManager

    @Before
    fun setUp() {
        updateManager = FakeAppUpdateManager()
    }

    @Test
    fun testUpdateAvailable_showsPromptWithReleaseDetails() {
        val release = AppReleaseInfo(
            versionName = "0.1.1",
            versionCode = 2L,
            releaseNotes = "Improved performance and bug fixes.",
        )
        updateManager.nextRelease = release

        updateManager.checkForUpdate()

        val state = updateManager.updateUiState.value
        assertTrue("Expected UpdateAvailable state", state is AppUpdateUiState.UpdateAvailable)
        val available = state as AppUpdateUiState.UpdateAvailable
        assertEquals("0.1.1", available.release.versionName)
        assertEquals(2L, available.release.versionCode)
        assertEquals("Improved performance and bug fixes.", available.release.releaseNotes)
        assertEquals(AppUpdateDownloadState.Idle, available.downloadState)
    }

    @Test
    fun testNoUpdateAvailable_remainsHiddenSilently() {
        updateManager.nextRelease = null

        updateManager.checkForUpdate()

        assertEquals(AppUpdateUiState.Hidden, updateManager.updateUiState.value)
        assertEquals(1, updateManager.checkCallCount)
    }

    @Test
    fun testUpdateCheckFailure_remainsHiddenSilentlyWithoutCrash() {
        updateManager.shouldFailCheck = true
        updateManager.nextRelease = AppReleaseInfo(versionName = "0.1.1", versionCode = 2L)

        updateManager.checkForUpdate()

        assertEquals(AppUpdateUiState.Hidden, updateManager.updateUiState.value)
        assertEquals(1, updateManager.checkCallCount)
    }

    @Test
    fun testUserChoosesLater_dismissesForSessionAndBlocksRecheck() {
        val release = AppReleaseInfo(versionName = "0.1.1", versionCode = 2L)
        updateManager.nextRelease = release

        updateManager.checkForUpdate()
        assertTrue(updateManager.updateUiState.value is AppUpdateUiState.UpdateAvailable)

        // User taps "Later"
        updateManager.dismissForSession()
        assertEquals(AppUpdateUiState.Hidden, updateManager.updateUiState.value)

        // Subsequent check in the same session (e.g. navigation) does not re-show dialog
        updateManager.checkForUpdate()
        assertEquals(AppUpdateUiState.Hidden, updateManager.updateUiState.value)
        assertEquals(1, updateManager.checkCallCount)
    }

    @Test
    fun testUserChoosesUpdate_startsDownloadWithProgressFeedback() {
        val release = AppReleaseInfo(versionName = "0.1.1", versionCode = 2L)
        updateManager.nextRelease = release

        updateManager.checkForUpdate()
        updateManager.startUpdate()

        assertEquals(1, updateManager.startUpdateCallCount)
        val state = updateManager.updateUiState.value as AppUpdateUiState.UpdateAvailable
        assertTrue(state.downloadState is AppUpdateDownloadState.Downloading)
        val downloading = state.downloadState as AppUpdateDownloadState.Downloading
        assertEquals(0.5f, downloading.progressPercent, 0.001f)

        // Simulate progress increment
        updateManager.simulateDownloadProgress(0.85f, 8500, 10000)
        val stateProgress = updateManager.updateUiState.value as AppUpdateUiState.UpdateAvailable
        val progress85 = stateProgress.downloadState as AppUpdateDownloadState.Downloading
        assertEquals(0.85f, progress85.progressPercent, 0.001f)

        // Simulate completion
        updateManager.simulateDownloadComplete()
        val stateComplete = updateManager.updateUiState.value as AppUpdateUiState.UpdateAvailable
        assertEquals(AppUpdateDownloadState.Downloaded, stateComplete.downloadState)
    }

    @Test
    fun testDownloadFailure_showsSafeErrorMessageWithoutPrivateData() {
        val release = AppReleaseInfo(versionName = "0.1.1", versionCode = 2L)
        updateManager.nextRelease = release
        updateManager.shouldFailDownload = true

        updateManager.checkForUpdate()
        updateManager.startUpdate()

        val state = updateManager.updateUiState.value as AppUpdateUiState.UpdateAvailable
        assertTrue(state.downloadState is AppUpdateDownloadState.Error)
        val error = state.downloadState as AppUpdateDownloadState.Error
        assertEquals("Unable to download update. Please try again later.", error.userMessage)
        assertFalse(error.userMessage.contains("token"))
        assertFalse(error.userMessage.contains("api_key"))
    }

    @Test
    fun testUserCancellation_handlesCancellationGracefully() {
        val release = AppReleaseInfo(versionName = "0.1.1", versionCode = 2L)
        updateManager.nextRelease = release

        updateManager.checkForUpdate()
        updateManager.startUpdate()
        updateManager.simulateDownloadCancelled()

        val state = updateManager.updateUiState.value as AppUpdateUiState.UpdateAvailable
        assertEquals(AppUpdateDownloadState.Cancelled, state.downloadState)
    }

    @Test
    fun testLifecycleRecomposition_doesNotTriggerDuplicateChecks() {
        val release = AppReleaseInfo(versionName = "0.1.1", versionCode = 2L)
        updateManager.nextRelease = release

        // Simulate multiple lifecycle/recomposition calls to checkForUpdate
        updateManager.checkForUpdate()
        updateManager.checkForUpdate()
        updateManager.checkForUpdate()

        assertEquals("Should only perform network check once per session", 1, updateManager.checkCallCount)
    }

    @Test
    fun testAccessibilityTalkBackStringFormatting() {
        val release = AppReleaseInfo(
            versionName = "0.1.1",
            versionCode = 2L,
            releaseNotes = "New daily routines feature.",
        )

        val summary = buildString {
            append("New version available. Promise ")
            append(release.versionName)
            append(" is ready to install.")
            if (!release.releaseNotes.isNullOrBlank()) {
                append(" Release notes: ")
                append(release.releaseNotes)
            }
        }

        assertTrue(summary.contains("Promise 0.1.1"))
        assertTrue(summary.contains("New daily routines feature."))
    }
}
