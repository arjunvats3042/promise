package app.promise.android.data.search

import retrofit2.http.GET
import retrofit2.http.Query

interface SearchApi {
    @GET("search/")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "all",
    ): GlobalSearchResponseDto
}
