package app.promise.android.data.users

import retrofit2.http.GET
import retrofit2.http.Query

interface UserApi {
    @GET("users/lookup/")
    suspend fun lookupByEmail(@Query("email") email: String): LookupUserDto
}
