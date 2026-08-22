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
