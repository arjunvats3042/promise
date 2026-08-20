package app.promise.android.data.goals

import app.promise.android.data.commitments.pageNumberFromNext
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalPeriodUnit
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import retrofit2.http.Body
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
}

fun GoalDto.toDomain(): Goal {
    return Goal(
        id = id,
        title = title,
        description = description,
        status = status.toGoalStatus(),
        timezone = timezone,
        startDate = startDate,
        endDate = endDate,
        recurrenceKind = recurrenceKind.toRecurrenceKind(),
        weekdays = weekdays,
        periodUnit = periodUnit?.toPeriodUnit(),
        timesPerPeriod = timesPerPeriod,
        trackingKind = trackingKind.toTrackingKind(),
        targetValue = targetValue,
        targetUnit = targetUnit,
        source = source,
        pausedAt = pausedAt,
        completedAt = completedAt,
        cancelledAt = cancelledAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isEnded = isEnded,
        progress = progress.toDomain(),
        currentStreak = currentStreak,
    )
}

fun GoalProgressDto.toDomain(): GoalProgress {
    return GoalProgress(
        currentPeriod = currentPeriod.toDomain(),
        weekProgress = weekProgress.toDomain(),
        consistencyPercent = consistencyPercent,
    )
}

fun GoalPeriodCountsDto.toDomain(): GoalPeriodCounts {
    return GoalPeriodCounts(
        required = required,
        completed = completed,
        value = value,
        targetValue = targetValue,
    )
}

fun GoalCheckInDto.toDomain(): GoalCheckIn {
    return GoalCheckIn(
        id = id,
        periodDate = periodDate,
        status = status.toCheckInStatus(),
        value = value,
        note = note,
        checkedAt = checkedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun String.toGoalStatus(): GoalStatus =
    runCatching { GoalStatus.valueOf(this) }.getOrDefault(GoalStatus.ACTIVE)

fun String.toRecurrenceKind(): GoalRecurrenceKind =
    runCatching { GoalRecurrenceKind.valueOf(this) }.getOrDefault(GoalRecurrenceKind.DAILY)

fun String.toPeriodUnit(): GoalPeriodUnit =
    runCatching { GoalPeriodUnit.valueOf(this) }.getOrDefault(GoalPeriodUnit.WEEK)

fun String.toTrackingKind(): GoalTrackingKind =
    runCatching { GoalTrackingKind.valueOf(this) }.getOrDefault(GoalTrackingKind.BINARY)

fun String.toCheckInStatus(): GoalCheckInStatus =
    runCatching { GoalCheckInStatus.valueOf(this) }.getOrDefault(GoalCheckInStatus.COMPLETED)

fun goalPageNumberFromNext(next: String?): Int? = pageNumberFromNext(next)
