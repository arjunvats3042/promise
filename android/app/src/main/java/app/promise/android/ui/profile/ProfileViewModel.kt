package app.promise.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.data.local.ProfilePhotoStore
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.AuthRepository
import app.promise.android.domain.DeviceRegistrationRepository
import app.promise.android.domain.NotificationPreferences
import app.promise.android.domain.NotificationPreferencesPatch
import app.promise.android.domain.NotificationPreferencesRepository
import app.promise.android.domain.User
import app.promise.android.domain.UserSession
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.navigation.DeepLinkRouter
import app.promise.android.ui.theme.PromiseThemeMode
import app.promise.android.ui.theme.ThemeController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val themeController: ThemeController,
    private val haptics: PromiseHaptics,
    private val authRepository: AuthRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val deviceRegistrationRepository: DeviceRegistrationRepository,
    private val appEventBus: AppEventBus,
    private val profilePhotoStore: ProfilePhotoStore,
    val deepLinkRouter: DeepLinkRouter,
    authSession: AuthSession,
) : ViewModel() {
    val mode: StateFlow<PromiseThemeMode> = themeController.mode
    val user: StateFlow<User?> = authSession.user
    val photoUri: StateFlow<String?> = profilePhotoStore.photoUri

    fun updateProfilePhoto(uri: android.net.Uri) {
        profilePhotoStore.savePhotoFromUri(uri)
        haptics.confirm()
        viewModelScope.launch {
            try {
                val bytes = profilePhotoStore.readBytesFromUri(uri)
                if (bytes != null && bytes.isNotEmpty()) {
                    authRepository.uploadProfilePhoto(bytes)
                }
            } catch (t: Throwable) {
                // Keep local cached photo even if offline
            }
        }
    }

    fun clearProfilePhoto() {
        profilePhotoStore.clearPhoto()
        haptics.light()
        viewModelScope.launch {
            try {
                authRepository.deleteProfilePhoto()
            } catch (_: Throwable) {
            }
        }
    }

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    private val _preferences = MutableStateFlow<NotificationPreferences?>(null)
    val preferences: StateFlow<NotificationPreferences?> = _preferences.asStateFlow()

    private val _sessions = MutableStateFlow<List<UserSession>>(emptyList())
    val sessions: StateFlow<List<UserSession>> = _sessions.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadPreferences()
        loadSessions()
    }

    fun loadPreferences() {
        viewModelScope.launch {
            val result = notificationPreferencesRepository.getPreferences()
            result.onSuccess {
                _preferences.value = it
            }
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            try {
                _sessions.value = authRepository.getSessions()
            } catch (_: Throwable) {}
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            try {
                authRepository.revokeSession(sessionId)
                haptics.confirm()
                loadSessions()
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to sign out session: ${t.message}"
                haptics.error()
            }
        }
    }

    fun revokeOtherSessions() {
        viewModelScope.launch {
            try {
                authRepository.revokeAllSessions(exceptCurrent = true)
                haptics.confirm()
                loadSessions()
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to sign out other sessions: ${t.message}"
                haptics.error()
            }
        }
    }

    fun updatePreferences(patch: NotificationPreferencesPatch) {
        val current = _preferences.value ?: return

        // 1. Optimistic update
        val updated = current.copy(
            enabled = patch.enabled ?: current.enabled,
            commitmentsDueSoon = patch.commitmentsDueSoon ?: current.commitmentsDueSoon,
            commitmentsDueNow = patch.commitmentsDueNow ?: current.commitmentsDueNow,
            commitmentsOverdue = patch.commitmentsOverdue ?: current.commitmentsOverdue,
            goalsTodayPractice = patch.goalsTodayPractice ?: current.goalsTodayPractice,
            goalsStreakProtection = patch.goalsStreakProtection ?: current.goalsStreakProtection,
            sharedGoalsActivity = patch.sharedGoalsActivity ?: current.sharedGoalsActivity,
            sharedGoalsChat = patch.sharedGoalsChat ?: current.sharedGoalsChat,
            weeklyDigestEnabled = patch.weeklyDigestEnabled ?: current.weeklyDigestEnabled,
            quietHoursEnabled = patch.quietHoursEnabled ?: current.quietHoursEnabled,
            quietHoursStart = patch.quietHoursStart ?: current.quietHoursStart,
            quietHoursEnd = patch.quietHoursEnd ?: current.quietHoursEnd,
            morningAnchorTime = patch.morningAnchorTime ?: current.morningAnchorTime,
            eveningAnchorTime = patch.eveningAnchorTime ?: current.eveningAnchorTime,
        )
        _preferences.value = updated
        haptics.light()

        // 2. Immediate backend PATCH
        viewModelScope.launch {
            val result = notificationPreferencesRepository.updatePreferences(patch)
            result.onSuccess {
                _preferences.value = it
                appEventBus.emit(AppMutationEvent.NotificationPreferencesChanged)
            }.onFailure {
                // 3. Rollback on failure
                _preferences.value = current
                _errorMessage.value = "Couldn't update notification preferences. Try again."
                haptics.error()
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setMode(mode: PromiseThemeMode) {
        if (themeController.mode.value == mode) return
        themeController.setMode(mode)
        haptics.light()
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoggingOut.value = true
            try {
                authRepository.deleteAccount()
                haptics.confirm()
                onComplete()
            } catch (_: Throwable) {
                _isLoggingOut.value = false
            }
        }
    }

    fun logoutAll(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoggingOut.value = true
            try {
                authRepository.logoutAll()
                haptics.confirm()
                onComplete()
            } finally {
                _isLoggingOut.value = false
            }
        }
    }
}
