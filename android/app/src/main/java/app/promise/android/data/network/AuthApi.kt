package app.promise.android.data.network

import app.promise.android.domain.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login/")
    suspend fun login(@Body body: LoginRequest): AuthSessionResponse

    @POST("auth/register/")
    suspend fun register(@Body body: RegisterRequest): AuthSessionResponse

    @POST("auth/refresh/")
    suspend fun refresh(@Body body: RefreshRequest): TokensResponse

    @POST("auth/logout/")
    suspend fun logout(@Body body: RefreshRequest): Response<Unit>

    @POST("auth/logout-all/")
    suspend fun logoutAll(): Response<Unit>

    @GET("auth/me/")
    suspend fun me(): MeResponse
}

fun UserDto.toDomain(): User {
    return User(
        id = id,
        email = email,
        name = name,
        timezone = timezone,
        createdAt = createdAt,
    )
}
