package app.promise.android.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.domain.AuthRepository
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.SessionState
import app.promise.android.widget.PromiseWidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@HiltWorker
class NotificationActionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val commitmentRepository: CommitmentRepository,
    private val goalRepository: GoalRepository,
    private val authRepository: AuthRepository,
    private val appEventBus: AppEventBus,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val entityId = inputData.getString(KEY_ENTITY_ID) ?: return Result.failure()
        val snoozeMinutes = inputData.getInt(KEY_SNOOZE_MINUTES, 60)
        val replyText = inputData.getString(KEY_REPLY_TEXT)

        if (authRepository.session.value !is SessionState.Authenticated) {
            try {
                authRepository.restoreSession()
            } catch (_: Exception) {}
        }

        val session = authRepository.session.value
        if (session !is SessionState.Authenticated) {
            return Result.failure()
        }

        return try {
            when (action) {
                NotificationActionReceiver.ACTION_COMPLETE_COMMITMENT -> {
                    commitmentRepository.complete(entityId)
                    appEventBus.emit(AppMutationEvent.CommitmentCompleted(entityId))
                    PromiseWidgetUpdater.fetchAndPushWidgetData(appContext)
                    Result.success()
                }
                NotificationActionReceiver.ACTION_SNOOZE_COMMITMENT -> {
                    val snoozedUntil = Instant.now().plus(snoozeMinutes.toLong(), ChronoUnit.MINUTES).toString()
                    commitmentRepository.snooze(entityId, snoozedUntil)
                    appEventBus.emit(AppMutationEvent.CommitmentSnoozed(entityId))
                    PromiseWidgetUpdater.fetchAndPushWidgetData(appContext)
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
                    appEventBus.emit(AppMutationEvent.GoalCheckedIn(entityId))
                    PromiseWidgetUpdater.fetchAndPushWidgetData(appContext)
                    Result.success()
                }
                NotificationActionReceiver.ACTION_REPLY_CHAT -> {
                    if (!replyText.isNullOrBlank()) {
                        val msg = goalRepository.sendChatMessage(entityId, replyText)
                        appEventBus.emit(AppMutationEvent.ChatMessageCreated(entityId, msg.id))
                        PromiseWidgetUpdater.fetchAndPushWidgetData(appContext)
                    }
                    Result.success()
                }
                else -> Result.failure()
            }
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            // Graceful handling for conflict/already completed/not found
            if (message.contains("409") || message.contains("404") || message.contains("ALREADY_COMPLETED")) {
                PromiseWidgetUpdater.fetchAndPushWidgetData(appContext)
                Result.success()
            } else if (runAttemptCount < 3) {
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
        const val KEY_REPLY_TEXT = "key_reply_text"
    }
}

