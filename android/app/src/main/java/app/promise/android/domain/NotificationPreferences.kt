package app.promise.android.domain

data class NotificationPreferences(
    val enabled: Boolean,
    val commitmentsDueSoon: Boolean,
    val commitmentsDueNow: Boolean,
    val commitmentsOverdue: Boolean,
    val goalsTodayPractice: Boolean,
    val goalsStreakProtection: Boolean,
    val quietHoursEnabled: Boolean,
    val quietHoursStart: String,
    val quietHoursEnd: String,
    val morningAnchorTime: String,
    val eveningAnchorTime: String,
)

data class NotificationPreferencesPatch(
    val enabled: Boolean? = null,
    val commitmentsDueSoon: Boolean? = null,
    val commitmentsDueNow: Boolean? = null,
    val commitmentsOverdue: Boolean? = null,
    val goalsTodayPractice: Boolean? = null,
    val goalsStreakProtection: Boolean? = null,
    val quietHoursEnabled: Boolean? = null,
    val quietHoursStart: String? = null,
    val quietHoursEnd: String? = null,
    val morningAnchorTime: String? = null,
    val eveningAnchorTime: String? = null,
)

interface NotificationPreferencesRepository {
    suspend fun getPreferences(): Result<NotificationPreferences>
    suspend fun updatePreferences(patch: NotificationPreferencesPatch): Result<NotificationPreferences>
}
