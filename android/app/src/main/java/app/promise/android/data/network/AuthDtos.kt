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
data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val timezone: String,
    @SerialName("created_at")
    val createdAt: String,
)
