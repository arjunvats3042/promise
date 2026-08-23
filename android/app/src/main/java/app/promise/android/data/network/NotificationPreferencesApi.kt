package app.promise.android.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query

@Serializable
data class NotificationPreferencesDto(
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("commitments_due_soon") val commitmentsDueSoon: Boolean = true,
    @SerialName("commitments_due_now") val commitmentsDueNow: Boolean = true,
    @SerialName("commitments_overdue") val commitmentsOverdue: Boolean = true,
    @SerialName("goals_daily_reminder") val goalsDailyReminder: Boolean = true,
    @SerialName("goals_daily_reminder_time") val goalsDailyReminderTime: String? = "08:30:00",
    @SerialName("goals_evening_reminder") val goalsEveningReminder: Boolean = true,
    @SerialName("goals_evening_reminder_time") val goalsEveningReminderTime: String? = "20:30:00",
    @SerialName("shared_goals_activity") val sharedGoalsActivity: Boolean = true,
    @SerialName("shared_goals_chat") val sharedGoalsChat: Boolean = true,
    @SerialName("weekly_digest_enabled") val weeklyDigestEnabled: Boolean = false,
    @SerialName("quiet_hours_enabled") val quietHoursEnabled: Boolean = true,
    @SerialName("quiet_hours_start") val quietHoursStart: String? = "22:00:00",
    @SerialName("quiet_hours_end") val quietHoursEnd: String? = "08:00:00",
)

@Serializable
data class NotificationPreferencesPatchDto(
    @SerialName("enabled") val enabled: Boolean? = null,
    @SerialName("commitments_due_soon") val commitmentsDueSoon: Boolean? = null,
    @SerialName("commitments_due_now") val commitmentsDueNow: Boolean? = null,
    @SerialName("commitments_overdue") val commitmentsOverdue: Boolean? = null,
    @SerialName("goals_daily_reminder") val goalsDailyReminder: Boolean? = null,
    @SerialName("goals_daily_reminder_time") val goalsDailyReminderTime: String? = null,
    @SerialName("goals_evening_reminder") val goalsEveningReminder: Boolean? = null,
    @SerialName("goals_evening_reminder_time") val goalsEveningReminderTime: String? = null,
    @SerialName("shared_goals_activity") val sharedGoalsActivity: Boolean? = null,
    @SerialName("shared_goals_chat") val sharedGoalsChat: Boolean? = null,
    @SerialName("weekly_digest_enabled") val weeklyDigestEnabled: Boolean? = null,
    @SerialName("quiet_hours_enabled") val quietHoursEnabled: Boolean? = null,
    @SerialName("quiet_hours_start") val quietHoursStart: String? = null,
    @SerialName("quiet_hours_end") val quietHoursEnd: String? = null,
)

@Serializable
data class NotificationHistoryItemDto(
    @SerialName("id") val id: String,
    @SerialName("entity_type") val entityType: String = "",
    @SerialName("entity_id") val entityId: String = "",
    @SerialName("event_type") val eventType: String = "",
    @SerialName("category") val category: String = "SYSTEM",
    @SerialName("title") val title: String = "Promise Notification",
    @SerialName("body") val body: String = "",
    @SerialName("deep_link") val deepLink: String = "promise://home",
    @SerialName("status") val status: String = "DISPATCHED",
    @SerialName("scheduled_for") val scheduledFor: String = "",
    @SerialName("dispatched_at") val dispatchedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class NotificationHistoryResponseDto(
    @SerialName("count") val count: Int? = null,
    @SerialName("next") val next: String? = null,
    @SerialName("previous") val previous: String? = null,
    @SerialName("results") val results: List<NotificationHistoryItemDto> = emptyList(),
)

interface NotificationPreferencesApi {
    @GET("notifications/preferences/")
    suspend fun getPreferences(): Response<NotificationPreferencesDto>

    @PATCH("notifications/preferences/")
    suspend fun updatePreferences(@Body patch: NotificationPreferencesPatchDto): Response<NotificationPreferencesDto>

    @GET("notifications/history/")
    suspend fun getHistory(@Query("page") page: Int = 1): Response<NotificationHistoryResponseDto>
}
