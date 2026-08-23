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

    override suspend fun googleLogin(idToken: String) {
        try {
            val response = publicApi.googleAuth(GoogleAuthRequest(idToken = idToken))
            AppLog.d(TAG, "google auth HTTP ok; applying session")
            acceptAuthenticated(response)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppLog.d(TAG, "google auth failed: ${t.javaClass.simpleName}/${(t as? ApiException)?.code ?: "n/a"}")
            throw t.toApiException()
        }
    }

    override suspend fun requestEmailVerification(): String {
        return try {
            val response = authedApi.verifyEmailRequest()
            response.detail
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun confirmEmailVerification(token: String) {
        try {
            val response = publicApi.verifyEmailConfirm(VerifyEmailConfirmRequest(token = token.trim()))
            val user = response.user.toDomain()
            memory.setUser(user)
            _session.value = SessionState.Authenticated(user)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun requestPasswordReset(email: String): String {
        return try {
            val response = publicApi.passwordResetRequest(PasswordResetRequest(email = email.trim()))
            response.detail
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun confirmPasswordReset(token: String, password: String) {
        try {
            val response = publicApi.passwordResetConfirm(
                PasswordResetConfirmRequest(token = token.trim(), password = password)
            )
            val user = response.user.toDomain()
            memory.setUser(user)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun setPassword(password: String) {
        try {
            val response = authedApi.setPassword(SetPasswordRequest(password = password))
            val user = response.user.toDomain()
            memory.setUser(user)
            _session.value = SessionState.Authenticated(user)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String) {
        try {
            val response = authedApi.changePassword(
                ChangePasswordRequest(oldPassword = oldPassword, newPassword = newPassword)
            )
            val user = response.user.toDomain()
            memory.setUser(user)
            _session.value = SessionState.Authenticated(user)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun deleteAccount() {
        try {
            deviceRegistrationRepository?.unregisterDevice()
            authedApi.deleteAccount()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        } finally {
            tokenStore.clear()
            memory.clear()
            _session.value = SessionState.Unauthenticated
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
            try {
                deviceRegistrationRepository?.syncDeviceRegistration()
            } catch (_: Throwable) {}
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

    override suspend fun linkGoogle(idToken: String) {
        try {
            val response = authedApi.linkGoogle(GoogleLinkRequest(idToken = idToken))
            val user = response.user.toDomain()
            memory.setUser(user)
            _session.value = SessionState.Authenticated(user)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun unlinkGoogle() {
        try {
            val response = authedApi.unlinkGoogle()
            val user = response.user.toDomain()
            memory.setUser(user)
            _session.value = SessionState.Authenticated(user)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun requestEmailChange(newEmail: String, currentPassword: String?): String {
        return try {
            val res = authedApi.emailChangeRequest(EmailChangeRequest(newEmail = newEmail.trim(), currentPassword = currentPassword))
            res.debugToken ?: res.detail
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun confirmEmailChange(token: String) {
        try {
            val response = authedApi.emailChangeConfirm(EmailChangeConfirmRequest(token = token.trim()))
            val user = response.user.toDomain()
            memory.setUser(user)
            _session.value = SessionState.Authenticated(user)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun getSessions(): List<app.promise.android.domain.UserSession> {
        return try {
            authedApi.getSessions().sessions.map { it.toDomain() }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun revokeSession(sessionId: String) {
        try {
            authedApi.revokeSession(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun revokeAllSessions(exceptCurrent: Boolean) {
        try {
            authedApi.revokeAllSessions(RevokeAllSessionsRequest(exceptCurrent = exceptCurrent))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun getSecurityEvents(): List<app.promise.android.domain.SecurityEventItem> {
        return try {
            authedApi.getSecurityEvents().results.map { it.toDomain() }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw t.toApiException()
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
        try {
            deviceRegistrationRepository?.syncDeviceRegistration()
        } catch (_: Throwable) {}
    }

    private suspend fun persistTokens(tokens: TokensDto) {
        tokenStore.saveRefreshToken(tokens.refreshToken)
        memory.setAccessToken(tokens.accessToken)
    }

    private companion object {
        const val TAG = "PromiseAuth"
    }
}
