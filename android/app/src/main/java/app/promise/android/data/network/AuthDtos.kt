package app.promise.android.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String,
)

@Serializable
data class AuthSessionResponse(
    val user: UserDto,
    val tokens: TokensDto,
)

@Serializable
data class TokensResponse(
    val tokens: TokensDto,
)

@Serializable
data class MeResponse(
    val user: UserDto,
)

@Serializable
data class TokensDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Int,
)

@Serializable
data class GoogleAuthRequest(
    @SerialName("id_token")
    val idToken: String,
    @SerialName("device_name")
    val deviceName: String = "",
    val platform: String = "android",
)

@Serializable
data class VerifyEmailConfirmRequest(
    val token: String,
)

@Serializable
data class PasswordResetRequest(
    val email: String,
)

@Serializable
data class PasswordResetConfirmRequest(
    val token: String,
    val password: String,
)

@Serializable
data class SetPasswordRequest(
    val password: String,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password")
    val oldPassword: String,
    @SerialName("new_password")
    val newPassword: String,
)

@Serializable
data class MessageDetailResponse(
    val detail: String,
    @SerialName("debug_token")
    val debugToken: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val timezone: String,
    @SerialName("email_verified")
    val emailVerified: Boolean = false,
    @SerialName("has_password")
    val hasPassword: Boolean = true,
    @SerialName("google_linked")
    val googleLinked: Boolean = false,
    @SerialName("created_at")
    val createdAt: String,
)
