package app.promise.android.domain

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val session: StateFlow<SessionState>

    suspend fun login(email: String, password: String)

    suspend fun register(name: String, email: String, password: String)

    suspend fun restoreSession()

    suspend fun logout()

    suspend fun logoutAll()
}
