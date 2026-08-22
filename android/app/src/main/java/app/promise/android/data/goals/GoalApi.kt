package app.promise.android.data.goals

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GoalApi {
    @GET("goals/")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("recurrence_kind") recurrenceKind: String? = null,
        @Query("tracking_kind") trackingKind: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): GoalPageDto

    @GET("goals/{id}/")
    suspend fun get(@Path("id") id: String): GoalDto

    @POST("goals/")
    suspend fun create(@Body body: CreateGoalRequest): GoalDto

    @POST("goals/{id}/pause/")
    suspend fun pause(@Path("id") id: String): GoalDto

    @POST("goals/{id}/resume/")
    suspend fun resume(@Path("id") id: String): GoalDto

    @POST("goals/{id}/complete/")
    suspend fun complete(@Path("id") id: String): GoalDto

    @POST("goals/{id}/cancel/")
    suspend fun cancel(@Path("id") id: String): GoalDto

    @GET("goals/{id}/check-ins/")
    suspend fun listCheckIns(
        @Path("id") id: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): GoalCheckInPageDto

    @POST("goals/{id}/check-ins/")
    suspend fun checkIn(@Path("id") id: String, @Body body: CheckInRequest): GoalCheckInDto

    @GET("goals/{id}/participants/")
    suspend fun listParticipants(@Path("id") id: String): List<GoalParticipantDto>

    @POST("goals/{id}/participants/")
    suspend fun inviteParticipant(
        @Path("id") id: String,
        @Body body: InviteParticipantRequest,
    ): GoalParticipantDto

    @POST("goals/{id}/participants/accept/")
    suspend fun acceptInvitation(@Path("id") id: String): GoalDto

    @POST("goals/{id}/participants/decline/")
    suspend fun declineInvitation(@Path("id") id: String): GoalParticipantDto

    @DELETE("goals/{id}/participants/{userId}/")
    suspend fun removeParticipant(
        @Path("id") id: String,
        @Path("userId") userId: String,
    ): GoalParticipantDto

    @POST("goals/{id}/leave/")
    suspend fun leave(@Path("id") id: String): GoalParticipantDto

    @GET("goals/{id}/chat/messages/")
    suspend fun listChatMessages(
        @Path("id") id: String,
        @Query("limit") limit: Int = 50,
        @Query("before_created_at") beforeCreatedAt: String? = null,
        @Query("before_id") beforeId: String? = null,
    ): List<ChatMessageDto>

    @POST("goals/{id}/chat/messages/")
    suspend fun sendChatMessage(
        @Path("id") id: String,
        @Body body: SendChatMessageRequest,
    ): ChatMessageDto

    @POST("goals/{id}/chat/read/")
    suspend fun markChatRead(
        @Path("id") id: String,
        @Body body: MarkChatReadRequest,
    ): GoalChatReadStateDto

    @GET("goals/{id}/chat/summary/")
    suspend fun getChatSummary(
        @Path("id") id: String,
    ): GoalChatSummaryDto

    @GET("goals/{id}/activity/")
    suspend fun listActivity(
        @Path("id") id: String,
        @Query("limit") limit: Int = 20,
        @Query("before_created_at") beforeCreatedAt: String? = null,
        @Query("before_id") beforeId: String? = null,
    ): List<GoalActivityItemDto>
}
