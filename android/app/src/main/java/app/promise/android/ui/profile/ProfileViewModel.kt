package app.promise.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.AuthRepository
import app.promise.android.domain.SecurityEventItem
import app.promise.android.domain.User
import app.promise.android.domain.UserSession
import app.promise.android.ui.haptics.PromiseHaptics
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
    private val notificationPreferencesRepository: app.promise.android.domain.NotificationPreferencesRepository,
    private val appEventBus: AppEventBus,
    authSession: AuthSession,
) : ViewModel() {
    val mode: StateFlow<PromiseThemeMode> = themeController.mode
    val user: StateFlow<User?> = authSession.user

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    private val _preferences = MutableStateFlow<app.promise.android.domain.NotificationPreferences?>(null)
    val preferences: StateFlow<app.promise.android.domain.NotificationPreferences?> = _preferences.asStateFlow()

    private val _notificationHistory = MutableStateFlow<List<app.promise.android.domain.NotificationHistoryItem>>(emptyList())
    val notificationHistory: StateFlow<List<app.promise.android.domain.NotificationHistoryItem>> = _notificationHistory.asStateFlow()

    private val _sessions = MutableStateFlow<List<UserSession>>(emptyList())
    val sessions: StateFlow<List<UserSession>> = _sessions.asStateFlow()

    private val _securityEvents = MutableStateFlow<List<SecurityEventItem>>(emptyList())
    val securityEvents: StateFlow<List<SecurityEventItem>> = _securityEvents.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadPreferences()
        loadNotificationHistory()
        loadSessions()
        loadSecurityEvents()
    }

    fun loadPreferences() {
        viewModelScope.launch {
            val result = notificationPreferencesRepository.getPreferences()
            result.onSuccess {
                _preferences.value = it
            }
        }
    }

    fun loadNotificationHistory() {
        viewModelScope.launch {
            val result = notificationPreferencesRepository.getNotificationHistory()
            result.onSuccess {
                _notificationHistory.value = it
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

    fun loadSecurityEvents() {
        viewModelScope.launch {
            try {
                _securityEvents.value = authRepository.getSecurityEvents()
            } catch (_: Throwable) {}
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            try {
                authRepository.revokeSession(sessionId)
                haptics.confirm()
                loadSessions()
                loadSecurityEvents()
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
                loadSecurityEvents()
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to sign out other sessions: ${t.message}"
                haptics.error()
            }
        }
    }

    fun unlinkGoogle(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.unlinkGoogle()
                haptics.confirm()
                loadSecurityEvents()
                onSuccess()
            } catch (t: Throwable) {
                haptics.error()
                onError(t.message ?: "Failed to unlink Google account")
            }
        }
    }

    fun requestEmailChange(newEmail: String, currentPassword: String?, onResult: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val detail = authRepository.requestEmailChange(newEmail, currentPassword)
                haptics.confirm()
                loadSecurityEvents()
                onResult(detail)
            } catch (t: Throwable) {
                haptics.error()
                onError(t.message ?: "Failed to request email change")
            }
        }
    }

    fun confirmEmailChange(token: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.confirmEmailChange(token)
                haptics.confirm()
                loadSecurityEvents()
                onSuccess()
            } catch (t: Throwable) {
                haptics.error()
                onError(t.message ?: "Failed to confirm email change")
            }
        }
    }

    fun updatePreferences(patch: app.promise.android.domain.NotificationPreferencesPatch) {
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
                _errorMessage.value = "Failed to update notification preferences"
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

    fun requestEmailVerification(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val detail = authRepository.requestEmailVerification()
                haptics.confirm()
                onResult(detail)
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to send verification email: ${t.message}"
                haptics.error()
            }
        }
    }

    fun changePassword(oldPass: String, newPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.changePassword(oldPass, newPass)
                haptics.confirm()
                loadSecurityEvents()
                onSuccess()
            } catch (t: Throwable) {
                haptics.error()
                onError(t.message ?: "Failed to change password")
            }
        }
    }

    fun setPassword(newPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.setPassword(newPass)
                haptics.confirm()
                loadSecurityEvents()
                onSuccess()
            } catch (t: Throwable) {
                haptics.error()
                onError(t.message ?: "Failed to set password")
            }
        }
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
