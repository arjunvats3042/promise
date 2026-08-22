package app.promise.android.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

@Serializable
data class NotificationPreferencesDto(
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("commitments_due_soon") val commitmentsDueSoon: Boolean,
    @SerialName("commitments_due_now") val commitmentsDueNow: Boolean,
    @SerialName("commitments_overdue") val commitmentsOverdue: Boolean,
    @SerialName("goals_today_practice") val goalsTodayPractice: Boolean,
    @SerialName("goals_streak_protection") val goalsStreakProtection: Boolean,
    @SerialName("quiet_hours_enabled") val quietHoursEnabled: Boolean,
    @SerialName("quiet_hours_start") val quietHoursStart: String? = "22:00:00",
    @SerialName("quiet_hours_end") val quietHoursEnd: String? = "08:00:00",
    @SerialName("morning_anchor_time") val morningAnchorTime: String? = "09:00:00",
    @SerialName("evening_anchor_time") val eveningAnchorTime: String? = "20:00:00",
)

@Serializable
data class NotificationPreferencesPatchDto(
    @SerialName("enabled") val enabled: Boolean? = null,
    @SerialName("commitments_due_soon") val commitmentsDueSoon: Boolean? = null,
    @SerialName("commitments_due_now") val commitmentsDueNow: Boolean? = null,
    @SerialName("commitments_overdue") val commitmentsOverdue: Boolean? = null,
    @SerialName("goals_today_practice") val goalsTodayPractice: Boolean? = null,
    @SerialName("goals_streak_protection") val goalsStreakProtection: Boolean? = null,
    @SerialName("quiet_hours_enabled") val quietHoursEnabled: Boolean? = null,
    @SerialName("quiet_hours_start") val quietHoursStart: String? = null,
    @SerialName("quiet_hours_end") val quietHoursEnd: String? = null,
    @SerialName("morning_anchor_time") val morningAnchorTime: String? = null,
    @SerialName("evening_anchor_time") val eveningAnchorTime: String? = null,
)

interface NotificationPreferencesApi {
    @GET("notifications/preferences/")
    suspend fun getPreferences(): Response<NotificationPreferencesDto>

    @PATCH("notifications/preferences/")
    suspend fun updatePreferences(@Body patch: NotificationPreferencesPatchDto): Response<NotificationPreferencesDto>
}
