package app.promise.android.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.promise.android.domain.AuthRepository
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.SessionState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@HiltWorker
class NotificationActionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val commitmentRepository: CommitmentRepository,
    private val goalRepository: GoalRepository,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val entityId = inputData.getString(KEY_ENTITY_ID) ?: return Result.failure()
        val snoozeMinutes = inputData.getInt(KEY_SNOOZE_MINUTES, 60)

        val session = authRepository.session.value
        if (session !is SessionState.Authenticated) {
            return Result.failure()
        }

        return try {
            when (action) {
                NotificationActionReceiver.ACTION_COMPLETE_COMMITMENT -> {
                    commitmentRepository.complete(entityId)
                    Result.success()
                }
                NotificationActionReceiver.ACTION_SNOOZE_COMMITMENT -> {
                    val snoozedUntil = Instant.now().plus(snoozeMinutes.toLong(), ChronoUnit.MINUTES).toString()
                    commitmentRepository.snooze(entityId, snoozedUntil)
                    Result.success()
                }
                NotificationActionReceiver.ACTION_CHECKIN_GOAL -> {
                    val today = LocalDate.now().toString()
                    goalRepository.checkIn(
                        entityId,
                        CheckInInput(
                            status = GoalCheckInStatus.COMPLETED,
                            periodDate = today,
                        ),
                    )
                    Result.success()
                }
                else -> Result.failure()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_ACTION = "key_action"
        const val KEY_ENTITY_ID = "key_entity_id"
        const val KEY_SNOOZE_MINUTES = "key_snooze_minutes"
    }
}
