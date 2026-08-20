package app.promise.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.data.remote.LocalNetworkPermission
import app.promise.android.domain.AuthRepository
import app.promise.android.domain.SessionState
import app.promise.android.ui.haptics.PromiseHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val localNetworkPermission: LocalNetworkPermission,
    private val haptics: PromiseHaptics,
) : ViewModel() {
    val session: StateFlow<SessionState> = authRepository.session

    private val _restoreNeedsRetry = MutableStateFlow(false)
    val restoreNeedsRetry: StateFlow<Boolean> = _restoreNeedsRetry.asStateFlow()

    private val _localNetwork = MutableStateFlow(resolveInitialLocalNetworkState())
    val localNetwork: StateFlow<LocalNetworkAccessState> = _localNetwork.asStateFlow()

    init {
        if (canTalkToApi()) {
            viewModelScope.launch { restore() }
        }
    }

    fun retryRestore() {
        viewModelScope.launch { restore() }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            haptics.confirm()
        }
    }

    fun onLocalNetworkPermissionResult(granted: Boolean) {
        if (granted) {
            _localNetwork.value = LocalNetworkAccessState.Granted
            viewModelScope.launch { restore() }
        } else {
            _localNetwork.value = LocalNetworkAccessState.Denied
        }
    }

    fun retryLocalNetworkPermission() {
        when {
            !localNetworkPermission.requiresAccess() -> {
                _localNetwork.value = LocalNetworkAccessState.NotRequired
                viewModelScope.launch { restore() }
            }
            localNetworkPermission.isGranted() -> {
                _localNetwork.value = LocalNetworkAccessState.Granted
                viewModelScope.launch { restore() }
            }
            else -> _localNetwork.value = LocalNetworkAccessState.NeedsRequest
        }
    }

    private fun resolveInitialLocalNetworkState(): LocalNetworkAccessState {
        if (!localNetworkPermission.requiresAccess()) {
            return LocalNetworkAccessState.NotRequired
        }
        return if (localNetworkPermission.isGranted()) {
            LocalNetworkAccessState.Granted
        } else {
            LocalNetworkAccessState.NeedsRequest
        }
    }

    private fun canTalkToApi(): Boolean {
        return when (_localNetwork.value) {
            LocalNetworkAccessState.NotRequired,
            LocalNetworkAccessState.Granted,
            -> true
            LocalNetworkAccessState.NeedsRequest,
            LocalNetworkAccessState.Denied,
            -> false
        }
    }

    private suspend fun restore() {
        if (!canTalkToApi()) return
        _restoreNeedsRetry.value = false
        authRepository.restoreSession()
        _restoreNeedsRetry.value = session.value is SessionState.Restoring
    }
}
