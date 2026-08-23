package app.promise.android.data.network

import app.promise.android.domain.NotificationHistoryItem
import app.promise.android.domain.NotificationPreferences
import app.promise.android.domain.NotificationPreferencesPatch
import app.promise.android.domain.NotificationPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPreferencesRepositoryImpl @Inject constructor(
    private val api: NotificationPreferencesApi,
) : NotificationPreferencesRepository {

    override suspend fun getPreferences(): Result<NotificationPreferences> {
        return try {
            val response = api.getPreferences()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to load preferences: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePreferences(patch: NotificationPreferencesPatch): Result<NotificationPreferences> {
        return try {
            val response = api.updatePreferences(
                NotificationPreferencesPatchDto(
                    enabled = patch.enabled,
                    commitmentsDueSoon = patch.commitmentsDueSoon,
                    commitmentsDueNow = patch.commitmentsDueNow,
                    commitmentsOverdue = patch.commitmentsOverdue,
                    goalsDailyReminder = patch.goalsTodayPractice,
                    goalsDailyReminderTime = patch.morningAnchorTime,
                    goalsEveningReminder = patch.goalsStreakProtection,
                    goalsEveningReminderTime = patch.eveningAnchorTime,
                    sharedGoalsActivity = patch.sharedGoalsActivity,
                    sharedGoalsChat = patch.sharedGoalsChat,
                    weeklyDigestEnabled = patch.weeklyDigestEnabled,
                    quietHoursEnabled = patch.quietHoursEnabled,
                    quietHoursStart = patch.quietHoursStart,
                    quietHoursEnd = patch.quietHoursEnd,
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.toDomain())
            } else {
                Result.failure(Exception("Failed to update preferences: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getNotificationHistory(page: Int): Result<List<NotificationHistoryItem>> {
        return try {
            val response = api.getHistory(page)
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.results.map { dto ->
                    NotificationHistoryItem(
                        id = dto.id,
                        entityType = dto.entityType,
                        entityId = dto.entityId,
                        eventType = dto.eventType,
                        category = dto.category,
                        title = dto.title,
                        body = dto.body,
                        deepLink = dto.deepLink,
                        status = dto.status,
                        scheduledFor = dto.scheduledFor,
                        dispatchedAt = dto.dispatchedAt,
                        createdAt = dto.createdAt,
                    )
                }
                Result.success(items)
            } else {
                Result.failure(Exception("Failed to load notification history: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun triggerTestNotification(): Result<Unit> {
        return try {
            val response = api.triggerTestNotification()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to trigger test notification: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun NotificationPreferencesDto.toDomain(): NotificationPreferences {
        return NotificationPreferences(
            enabled = enabled,
            commitmentsDueSoon = commitmentsDueSoon,
            commitmentsDueNow = commitmentsDueNow,
            commitmentsOverdue = commitmentsOverdue,
            goalsTodayPractice = goalsDailyReminder,
            goalsStreakProtection = goalsEveningReminder,
            sharedGoalsActivity = sharedGoalsActivity,
            sharedGoalsChat = sharedGoalsChat,
            weeklyDigestEnabled = weeklyDigestEnabled,
            quietHoursEnabled = quietHoursEnabled,
            quietHoursStart = quietHoursStart ?: "22:00:00",
            quietHoursEnd = quietHoursEnd ?: "08:00:00",
            morningAnchorTime = goalsDailyReminderTime ?: "08:30:00",
            eveningAnchorTime = goalsEveningReminderTime ?: "20:30:00",
        )
    }
}
