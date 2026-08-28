package app.promise.android.data.sync

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncApi {
    @POST("sync/outbox/")
    suspend fun syncOutbox(
        @Body request: SyncBatchRequestDto,
    ): Response<SyncBatchResponseDto>
}
