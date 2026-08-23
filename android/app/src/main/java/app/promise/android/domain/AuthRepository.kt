package app.promise.android.domain

import kotlinx.coroutines.flow.StateFlow

data class UserSession(
    val id: String,
    val deviceName: String,
    val platform: String,
    val lastUsedAt: String,
    val createdAt: String,
    val isCurrent: Boolean,
)

data class SecurityEventItem(
    val id: String,
    val eventType: String,
    val deviceName: String,
    val platform: String,
    val createdAt: String,
)

interface AuthRepository {
    val session: StateFlow<SessionState>

    suspend fun login(email: String, password: String)

    suspend fun register(name: String, email: String, password: String)

    suspend fun googleLogin(idToken: String)

    suspend fun linkGoogle(idToken: String)

    suspend fun unlinkGoogle()

    suspend fun requestEmailVerification(): String

    suspend fun confirmEmailVerification(token: String)

    suspend fun requestEmailChange(newEmail: String, currentPassword: String? = null): String

    suspend fun confirmEmailChange(token: String)

    suspend fun requestPasswordReset(email: String): String

    suspend fun confirmPasswordReset(token: String, password: String)

    suspend fun setPassword(password: String)

    suspend fun changePassword(oldPassword: String, newPassword: String)

    suspend fun deleteAccount()

    suspend fun restoreSession()

    suspend fun logout()

    suspend fun logoutAll()

    suspend fun getSessions(): List<UserSession>

    suspend fun revokeSession(sessionId: String)

    suspend fun revokeAllSessions(exceptCurrent: Boolean = true)

    suspend fun getSecurityEvents(): List<SecurityEventItem>

    suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String = "image/jpeg"): User

    suspend fun deleteProfilePhoto(): User
}
