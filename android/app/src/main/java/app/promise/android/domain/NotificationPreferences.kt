package app.promise.android.domain

data class NotificationPreferences(
    val enabled: Boolean,
    val commitmentsDueSoon: Boolean,
    val commitmentsDueNow: Boolean,
    val commitmentsOverdue: Boolean,
    val goalsTodayPractice: Boolean,
    val goalsStreakProtection: Boolean,
    val sharedGoalsActivity: Boolean = true,
    val sharedGoalsChat: Boolean = true,
    val weeklyDigestEnabled: Boolean = false,
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
    val sharedGoalsActivity: Boolean? = null,
    val sharedGoalsChat: Boolean? = null,
    val weeklyDigestEnabled: Boolean? = null,
    val quietHoursEnabled: Boolean? = null,
    val quietHoursStart: String? = null,
    val quietHoursEnd: String? = null,
    val morningAnchorTime: String? = null,
    val eveningAnchorTime: String? = null,
)

data class NotificationHistoryItem(
    val id: String,
    val entityType: String,
    val entityId: String,
    val eventType: String,
    val category: String,
    val title: String,
    val body: String,
    val deepLink: String,
    val status: String,
    val scheduledFor: String,
    val dispatchedAt: String?,
    val createdAt: String,
)

interface NotificationPreferencesRepository {
    suspend fun getPreferences(): Result<NotificationPreferences>
    suspend fun updatePreferences(patch: NotificationPreferencesPatch): Result<NotificationPreferences>
    suspend fun getNotificationHistory(page: Int = 1): Result<List<NotificationHistoryItem>>
}
