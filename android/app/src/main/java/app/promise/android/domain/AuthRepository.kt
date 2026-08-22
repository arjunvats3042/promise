package app.promise.android.domain

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val session: StateFlow<SessionState>

    suspend fun login(email: String, password: String)

    suspend fun register(name: String, email: String, password: String)

    suspend fun googleLogin(idToken: String)

    suspend fun requestEmailVerification(): String

    suspend fun confirmEmailVerification(token: String)

    suspend fun requestPasswordReset(email: String): String

    suspend fun confirmPasswordReset(token: String, password: String)

    suspend fun setPassword(password: String)

    suspend fun changePassword(oldPassword: String, newPassword: String)

    suspend fun deleteAccount()

    suspend fun restoreSession()

    suspend fun logout()

    suspend fun logoutAll()
}
