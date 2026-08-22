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
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val localNetworkPermission: LocalNetworkPermission,
    private val haptics: PromiseHaptics,
) : ViewModel() {
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    fun onNameChange(value: String) {
        _name.value = value
        clearFailed()
    }

    fun onEmailChange(value: String) {
        _email.value = value
        clearFailed()
    }

    fun onPasswordChange(value: String) {
        _password.value = value
        clearFailed()
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
        val trimmedName = _name.value.trim()
        val trimmedEmail = _email.value.trim()
        val password = _password.value
        if (trimmedName.isEmpty() || trimmedEmail.isEmpty() || password.isEmpty()) {
            _action.value = ActionState.Failed(ErrorKind.Validation())
            haptics.error()
            return
        }
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                authRepository.register(trimmedName, trimmedEmail, password)
                _action.value = ActionState.Idle
                haptics.confirm()
            } catch (e: ApiException) {
                val kind = e.toErrorKind()
                _action.value = ActionState.Failed(kind)
                if (kind is ErrorKind.Validation || kind == ErrorKind.EmailAlreadyExists) {
                    haptics.error()
                }
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
            }
        }
    }

    fun submitGoogleLogin(idToken: String) {
        if (_action.value is ActionState.InFlight) return
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                authRepository.googleLogin(idToken)
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

    private fun clearFailed() {
        if (_action.value is ActionState.Failed) {
            _action.value = ActionState.Idle
        }
    }
}
