package app.promise.android.data.commitments

import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.DuePrecision
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommitmentApi {
    @GET("commitments/")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("is_overdue") isOverdue: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): CommitmentPageDto

    @GET("commitments/{id}/")
    suspend fun get(@Path("id") id: String): CommitmentDto

    @POST("commitments/")
    suspend fun create(@Body body: CreateCommitmentRequest): CommitmentDto

    @POST("commitments/{id}/complete/")
    suspend fun complete(@Path("id") id: String): CommitmentDto

    @POST("commitments/{id}/snooze/")
    suspend fun snooze(@Path("id") id: String, @Body body: SnoozeRequest): CommitmentDto

    @POST("commitments/{id}/unsnooze/")
    suspend fun unsnooze(@Path("id") id: String): CommitmentDto

    @POST("commitments/{id}/wait/")
    suspend fun wait(@Path("id") id: String): CommitmentDto

    @POST("commitments/{id}/cancel/")
    suspend fun cancel(@Path("id") id: String): CommitmentDto
}

fun CommitmentDto.toDomain(): Commitment {
    return Commitment(
        id = id,
        title = title,
        description = description,
        status = status.toCommitmentStatus(),
        dueAt = dueAt,
        duePrecision = duePrecision.toDuePrecision(),
        source = source,
        snoozedUntil = snoozedUntil,
        completedAt = completedAt,
        cancelledAt = cancelledAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isOverdue = isOverdue,
    )
}

fun String.toCommitmentStatus(): CommitmentStatus {
    return runCatching { CommitmentStatus.valueOf(this) }.getOrDefault(CommitmentStatus.PENDING)
}

fun String.toDuePrecision(): DuePrecision {
    return runCatching { DuePrecision.valueOf(this) }.getOrDefault(DuePrecision.NONE)
}

fun pageNumberFromNext(next: String?): Int? {
    if (next.isNullOrBlank()) return null
    val marker = "page="
    val idx = next.indexOf(marker)
    if (idx < 0) return null
    val start = idx + marker.length
    val end = next.indexOf('&', start).let { if (it < 0) next.length else it }
    return next.substring(start, end).toIntOrNull()
}
