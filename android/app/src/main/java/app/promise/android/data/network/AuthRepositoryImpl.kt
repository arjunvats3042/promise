package app.promise.android.data.network

import app.promise.android.core.AppLog
import app.promise.android.data.local.TokenStore
import app.promise.android.domain.AuthRepository
import app.promise.android.domain.SessionState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepositoryImpl(
    private val publicApi: AuthApi,
    private val authedApi: AuthApi,
    private val tokenStore: TokenStore,
    private val memory: AuthSession,
    private val refresher: SessionRefresher,
    private val deviceRegistrationRepository: app.promise.android.domain.DeviceRegistrationRepository? = null,
) : AuthRepository {
    private val _session = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    override suspend fun login(email: String, password: String) {
        try {
            val response = publicApi.login(LoginRequest(email = email.trim(), password = password))
            AppLog.d(TAG, "login HTTP ok; applying session")
            acceptAuthenticated(response)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppLog.d(TAG, "login failed: ${t.javaClass.simpleName}/${(t as? ApiException)?.code ?: "n/a"}")
            throw t.toApiException()
        }
    }

    override suspend fun register(name: String, email: String, password: String) {
        try {
            val response = publicApi.register(
                RegisterRequest(
                    email = email.trim(),
                    password = password,
                    name = name.trim(),
                ),
            )
            AppLog.d(TAG, "register HTTP ok; applying session")
            acceptAuthenticated(response)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppLog.d(TAG, "register failed: ${t.javaClass.simpleName}/${(t as? ApiException)?.code ?: "n/a"}")
            throw t.toApiException()
        }
    }

    override suspend fun restoreSession() {
        _session.value = SessionState.Restoring
        val stored = tokenStore.readRefreshToken()
        if (stored.isNullOrBlank()) {
            memory.clear()
            _session.value = SessionState.Unauthenticated
            return
        }
        val refreshed = try {
            refresher.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
        if (!refreshed) {
            if (tokenStore.readRefreshToken() == null) {
                memory.clear()
                _session.value = SessionState.Unauthenticated
            }
            return
        }
        try {
            val me = authedApi.me()
            val user = me.user.toDomain()
            memory.setUser(user)
            _session.value = SessionState.Authenticated(user)
            deviceRegistrationRepository?.getStoredFcmToken()?.let { token ->
                try { deviceRegistrationRepository.registerDevice(token) } catch (_: Throwable) {}
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val error = t.toApiException()
            if (error.isSessionEnded) {
                tokenStore.clear()
                memory.clear()
                _session.value = SessionState.Unauthenticated
            }
        }
    }

    override suspend fun logout() {
        val refresh = tokenStore.readRefreshToken()
        try {
            deviceRegistrationRepository?.unregisterDevice()
            if (refresh != null) {
                authedApi.logout(RefreshRequest(refresh))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        } finally {
            tokenStore.clear()
            memory.clear()
            _session.value = SessionState.Unauthenticated
        }
    }

    override suspend fun logoutAll() {
        try {
            deviceRegistrationRepository?.unregisterDevice()
            authedApi.logoutAll()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        } finally {
            tokenStore.clear()
            memory.clear()
            _session.value = SessionState.Unauthenticated
        }
    }

    private suspend fun acceptAuthenticated(response: AuthSessionResponse) {
        AppLog.d(TAG, "auth response accepted; persisting session")
        try {
            persistTokens(response.tokens)
            AppLog.d(TAG, "refresh token persisted")
        } catch (t: Throwable) {
            AppLog.d(TAG, "persist failed: ${t.javaClass.simpleName}")
            throw t
        }
        val user = response.user.toDomain()
        memory.setUser(user)
        _session.value = SessionState.Authenticated(user)
        AppLog.d(TAG, "session authenticated")
        deviceRegistrationRepository?.getStoredFcmToken()?.let { token ->
            try { deviceRegistrationRepository.registerDevice(token) } catch (_: Throwable) {}
        }
    }

    private suspend fun persistTokens(tokens: TokensDto) {
        tokenStore.saveRefreshToken(tokens.refreshToken)
        memory.setAccessToken(tokens.accessToken)
    }

    private companion object {
        const val TAG = "PromiseAuth"
    }
}
