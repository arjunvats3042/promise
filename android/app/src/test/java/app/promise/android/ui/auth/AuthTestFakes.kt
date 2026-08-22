package app.promise.android.ui.auth

import app.promise.android.data.network.ApiException
import app.promise.android.data.remote.LocalNetworkPermission
import app.promise.android.domain.AuthRepository
import app.promise.android.domain.SessionState
import app.promise.android.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeLocalNetworkPermission(
    private val requiresAccess: Boolean = false,
    var granted: Boolean = true,
) : LocalNetworkPermission {
    override fun requiresAccess(): Boolean = requiresAccess

    override fun isGranted(): Boolean = !requiresAccess || granted
}

internal class FakeAuthRepository(
    private val loginError: ApiException? = null,
    private val registerError: ApiException? = null,
) : AuthRepository {
    override val session: StateFlow<SessionState> =
        MutableStateFlow(SessionState.Restoring)
    var loggedInWith: Pair<String, String>? = null
    var registeredWith: Triple<String, String, String>? = null
    var restoreCalled: Boolean = false

    override suspend fun login(email: String, password: String) {
        loggedInWith = email to password
        loginError?.let { throw it }
        (session as MutableStateFlow).value = SessionState.Authenticated(
            User("1", email, "Ada", "UTC", "2026-01-01T00:00:00Z"),
        )
    }

    override suspend fun register(name: String, email: String, password: String) {
        registeredWith = Triple(name, email, password)
        registerError?.let { throw it }
        (session as MutableStateFlow).value = SessionState.Authenticated(
            User("1", email, name, "UTC", "2026-01-01T00:00:00Z"),
        )
    }

    override suspend fun googleLogin(idToken: String) {
        (session as MutableStateFlow).value = SessionState.Authenticated(
            User("1", "google@example.com", "Google User", "UTC", "2026-01-01T00:00:00Z"),
        )
    }

    override suspend fun requestEmailVerification(): String = "Verification email sent."

    override suspend fun confirmEmailVerification(token: String) = Unit

    override suspend fun requestPasswordReset(email: String): String = "Reset email sent."

    override suspend fun confirmPasswordReset(token: String, password: String) = Unit

    override suspend fun setPassword(password: String) = Unit

    override suspend fun changePassword(oldPassword: String, newPassword: String) = Unit

    override suspend fun deleteAccount() {
        (session as MutableStateFlow).value = SessionState.Unauthenticated
    }

    override suspend fun linkGoogle(idToken: String) = Unit

    override suspend fun unlinkGoogle() = Unit

    override suspend fun requestEmailChange(newEmail: String, currentPassword: String?): String = "Email change requested."

    override suspend fun confirmEmailChange(token: String) = Unit

    override suspend fun getSessions(): List<app.promise.android.domain.UserSession> = emptyList()

    override suspend fun revokeSession(sessionId: String) = Unit

    override suspend fun revokeAllSessions(exceptCurrent: Boolean) = Unit

    override suspend fun getSecurityEvents(): List<app.promise.android.domain.SecurityEventItem> = emptyList()

    override suspend fun restoreSession() {
        restoreCalled = true
        (session as MutableStateFlow).value = SessionState.Unauthenticated
    }

    override suspend fun logout() = Unit

    var logoutAllCalled: Boolean = false
    override suspend fun logoutAll() {
        logoutAllCalled = true
        (session as MutableStateFlow).value = SessionState.Unauthenticated
    }
}
