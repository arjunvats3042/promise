package app.promise.android.data.network

import app.promise.android.domain.SecurityEventItem
import app.promise.android.domain.User
import app.promise.android.domain.UserSession
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthApi {
    @POST("auth/login/")
    suspend fun login(@Body body: LoginRequest): AuthSessionResponse

    @POST("auth/register/")
    suspend fun register(@Body body: RegisterRequest): AuthSessionResponse

    @POST("auth/google/")
    suspend fun googleAuth(@Body body: GoogleAuthRequest): AuthSessionResponse

    @POST("auth/google/link/")
    suspend fun linkGoogle(@Body body: GoogleLinkRequest): MeResponse

    @POST("auth/google/unlink/")
    suspend fun unlinkGoogle(): MeResponse

    @POST("auth/verify-email/request/")
    suspend fun verifyEmailRequest(): MessageDetailResponse

    @POST("auth/verify-email/confirm/")
    suspend fun verifyEmailConfirm(@Body body: VerifyEmailConfirmRequest): MeResponse

    @POST("auth/email/change/")
    suspend fun emailChangeRequest(@Body body: EmailChangeRequest): MessageDetailResponse

    @POST("auth/email/change/confirm/")
    suspend fun emailChangeConfirm(@Body body: EmailChangeConfirmRequest): MeResponse

    @POST("auth/password-reset/request/")
    suspend fun passwordResetRequest(@Body body: PasswordResetRequest): MessageDetailResponse

    @POST("auth/password-reset/confirm/")
    suspend fun passwordResetConfirm(@Body body: PasswordResetConfirmRequest): MeResponse

    @POST("auth/set-password/")
    suspend fun setPassword(@Body body: SetPasswordRequest): MeResponse

    @POST("auth/change-password/")
    suspend fun changePassword(@Body body: ChangePasswordRequest): MeResponse

    @POST("auth/delete-account/")
    suspend fun deleteAccount(): Response<Unit>

    @POST("auth/refresh/")
    suspend fun refresh(@Body body: RefreshRequest): TokensResponse

    @POST("auth/logout/")
    suspend fun logout(@Body body: RefreshRequest): Response<Unit>

    @POST("auth/logout-all/")
    suspend fun logoutAll(): Response<Unit>

    @GET("auth/sessions/")
    suspend fun getSessions(): SessionsResponse

    @DELETE("auth/sessions/{sessionId}/")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): Response<Unit>

    @POST("auth/sessions/revoke-all/")
    suspend fun revokeAllSessions(@Body body: RevokeAllSessionsRequest): Response<Unit>

    @GET("auth/security-events/")
    suspend fun getSecurityEvents(): SecurityEventsResponse

    @GET("auth/me/")
    suspend fun me(): MeResponse
}

fun UserDto.toDomain(): User {
    return User(
        id = id,
        email = email,
        name = name,
        timezone = timezone,
        emailVerified = emailVerified,
        hasPassword = hasPassword,
        googleLinked = googleLinked,
        createdAt = createdAt,
    )
}

fun AuthSessionDto.toDomain(): UserSession {
    return UserSession(
        id = id,
        deviceName = deviceName.ifBlank { "Unknown Device" },
        platform = platform,
        lastUsedAt = lastUsedAt,
        createdAt = createdAt,
        isCurrent = isCurrent,
    )
}

fun SecurityEventDto.toDomain(): SecurityEventItem {
    return SecurityEventItem(
        id = id,
        eventType = eventType,
        deviceName = deviceName,
        platform = platform,
        createdAt = createdAt,
    )
}
