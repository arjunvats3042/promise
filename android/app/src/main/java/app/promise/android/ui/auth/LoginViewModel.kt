package app.promise.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.toErrorKind
import app.promise.android.data.network.ApiException
import app.promise.android.data.remote.LocalNetworkPermission
import app.promise.android.domain.AuthRepository
import app.promise.android.ui.haptics.PromiseHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val localNetworkPermission: LocalNetworkPermission,
    private val haptics: PromiseHaptics,
) : ViewModel() {
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    fun onEmailChange(value: String) {
        _email.value = value
        if (_action.value is ActionState.Failed) {
            _action.value = ActionState.Idle
        }
    }

    fun onPasswordChange(value: String) {
        _password.value = value
        if (_action.value is ActionState.Failed) {
            _action.value = ActionState.Idle
        }
    }

    fun onLocalNetworkGranted() {
        val failed = _action.value as? ActionState.Failed ?: return
        if (failed.kind == ErrorKind.LocalNetworkDenied) {
            _action.value = ActionState.Idle
        }
    }

    fun submit() {
        if (_action.value is ActionState.InFlight) return
        if (localNetworkPermission.requiresAccess() && !localNetworkPermission.isGranted()) {
            _action.value = ActionState.Failed(ErrorKind.LocalNetworkDenied)
            return
        }
        if (_email.value.isBlank() || _password.value.isEmpty()) {
            _action.value = ActionState.Failed(ErrorKind.Validation())
            haptics.error()
            return
        }
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                authRepository.login(_email.value, _password.value)
                _action.value = ActionState.Idle
                haptics.confirm()
            } catch (e: ApiException) {
                val kind = e.toErrorKind()
                _action.value = ActionState.Failed(kind)
                haptics.error()
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun submitGoogleLogin(idToken: String) {
        if (_action.value is ActionState.InFlight) return
        _action.value = ActionState.InFlight
        viewModelScope.launch {
            try {
                authRepository.googleLogin(idToken)
                _action.value = ActionState.Idle
                haptics.confirm()
            } catch (_: kotlinx.coroutines.CancellationException) {
                _action.value = ActionState.Idle
            } catch (e: ApiException) {
                val kind = e.toErrorKind()
                _action.value = ActionState.Failed(kind)
                haptics.error()
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun onGoogleSignInCancelled() {
        if (_action.value is ActionState.InFlight) {
            _action.value = ActionState.Idle
        }
    }

    fun onGoogleSignInFailed(errorKind: ErrorKind = ErrorKind.Unknown) {
        _action.value = ActionState.Failed(errorKind)
        haptics.error()
    }

    fun submitPasswordReset(email: String, onSent: (String) -> Unit) {
        if (email.isBlank()) {
            _action.value = ActionState.Failed(ErrorKind.Validation())
            haptics.error()
            return
        }
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                val message = authRepository.requestPasswordReset(email)
                _action.value = ActionState.Idle
                onSent(message)
                haptics.confirm()
            } catch (e: ApiException) {
                _action.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }
}
