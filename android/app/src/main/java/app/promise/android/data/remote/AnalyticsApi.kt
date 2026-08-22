package app.promise.android.data.remote

import app.promise.android.core.analytics.AnalyticsBatchPayload
import app.promise.android.core.analytics.AnalyticsIngestResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AnalyticsApi {
    @POST("api/v1/analytics/events/")
    suspend fun sendEventBatch(
        @Body payload: AnalyticsBatchPayload
    ): Response<AnalyticsIngestResponse>
}
