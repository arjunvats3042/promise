package app.promise.android.data.network

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
                    goalsTodayPractice = patch.goalsTodayPractice,
                    goalsStreakProtection = patch.goalsStreakProtection,
                    quietHoursEnabled = patch.quietHoursEnabled,
                    quietHoursStart = patch.quietHoursStart,
                    quietHoursEnd = patch.quietHoursEnd,
                    morningAnchorTime = patch.morningAnchorTime,
                    eveningAnchorTime = patch.eveningAnchorTime,
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

    private fun NotificationPreferencesDto.toDomain(): NotificationPreferences {
        return NotificationPreferences(
            enabled = enabled,
            commitmentsDueSoon = commitmentsDueSoon,
            commitmentsDueNow = commitmentsDueNow,
            commitmentsOverdue = commitmentsOverdue,
            goalsTodayPractice = goalsTodayPractice,
            goalsStreakProtection = goalsStreakProtection,
            quietHoursEnabled = quietHoursEnabled,
            quietHoursStart = quietHoursStart ?: "22:00:00",
            quietHoursEnd = quietHoursEnd ?: "08:00:00",
            morningAnchorTime = morningAnchorTime ?: "09:00:00",
            eveningAnchorTime = eveningAnchorTime ?: "20:00:00",
        )
    }
}
