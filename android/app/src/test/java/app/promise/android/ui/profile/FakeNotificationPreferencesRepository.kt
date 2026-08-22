package app.promise.android.ui.profile

import app.promise.android.domain.NotificationPreferences
import app.promise.android.domain.NotificationPreferencesPatch
import app.promise.android.domain.NotificationPreferencesRepository

class FakeNotificationPreferencesRepository(
    var initialPreferences: NotificationPreferences = NotificationPreferences(
        enabled = true,
        commitmentsDueSoon = true,
        commitmentsDueNow = true,
        commitmentsOverdue = true,
        goalsTodayPractice = true,
        goalsStreakProtection = true,
        quietHoursEnabled = true,
        quietHoursStart = "22:00:00",
        quietHoursEnd = "08:00:00",
        morningAnchorTime = "09:00:00",
        eveningAnchorTime = "20:00:00",
    ),
    var shouldFailUpdate: Boolean = false,
) : NotificationPreferencesRepository {

    var current = initialPreferences
    var lastPatch: NotificationPreferencesPatch? = null

    override suspend fun getPreferences(): Result<NotificationPreferences> {
        return Result.success(current)
    }

    override suspend fun updatePreferences(patch: NotificationPreferencesPatch): Result<NotificationPreferences> {
        lastPatch = patch
        if (shouldFailUpdate) {
            return Result.failure(Exception("Network error"))
        }
        current = current.copy(
            enabled = patch.enabled ?: current.enabled,
            commitmentsDueSoon = patch.commitmentsDueSoon ?: current.commitmentsDueSoon,
            commitmentsDueNow = patch.commitmentsDueNow ?: current.commitmentsDueNow,
            commitmentsOverdue = patch.commitmentsOverdue ?: current.commitmentsOverdue,
            goalsTodayPractice = patch.goalsTodayPractice ?: current.goalsTodayPractice,
            goalsStreakProtection = patch.goalsStreakProtection ?: current.goalsStreakProtection,
            quietHoursEnabled = patch.quietHoursEnabled ?: current.quietHoursEnabled,
            quietHoursStart = patch.quietHoursStart ?: current.quietHoursStart,
            quietHoursEnd = patch.quietHoursEnd ?: current.quietHoursEnd,
            morningAnchorTime = patch.morningAnchorTime ?: current.morningAnchorTime,
            eveningAnchorTime = patch.eveningAnchorTime ?: current.eveningAnchorTime,
        )
        return Result.success(current)
    }
}
