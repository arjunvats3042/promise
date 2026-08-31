package app.promise.android.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.domain.AuthRepository
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.SessionState
import app.promise.android.widget.PromiseWidgetUpdater
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class NotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionEntryPoint {
        fun commitmentRepository(): CommitmentRepository
        fun goalRepository(): GoalRepository
        fun authRepository(): AuthRepository
        fun appEventBus(): AppEventBus
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 60)

        // Check for direct reply text from RemoteInput
        val remoteInputResults = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInputResults?.getCharSequence(KEY_TEXT_REPLY)?.toString()

        // 1. Immediately dismiss/update notification from shade
        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notificationId)
        }

        // 2. Execute action directly via goAsync()
        val pendingResult = goAsync()
        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ActionEntryPoint::class.java,
                )
                val commitmentRepository = entryPoint.commitmentRepository()
                val goalRepository = entryPoint.goalRepository()
                val authRepository = entryPoint.authRepository()
                val appEventBus = entryPoint.appEventBus()

                if (authRepository.session.value !is SessionState.Authenticated) {
                    try {
                        authRepository.restoreSession()
                    } catch (_: Throwable) {}
                }

                when (action) {
                    ACTION_COMPLETE_COMMITMENT -> {
                        commitmentRepository.complete(entityId)
                        appEventBus.emit(AppMutationEvent.CommitmentCompleted(entityId))
                        PromiseWidgetUpdater.fetchAndPushWidgetData(context.applicationContext)
                    }
                    ACTION_SNOOZE_COMMITMENT -> {
                        val snoozedUntil = Instant.now().plus(snoozeMinutes.toLong(), ChronoUnit.MINUTES).toString()
                        commitmentRepository.snooze(entityId, snoozedUntil)
                        appEventBus.emit(AppMutationEvent.CommitmentSnoozed(entityId))
                        PromiseWidgetUpdater.fetchAndPushWidgetData(context.applicationContext)
                    }
                    ACTION_CHECKIN_GOAL -> {
                        val today = LocalDate.now().toString()
                        goalRepository.checkIn(
                            entityId,
                            CheckInInput(
                                status = GoalCheckInStatus.COMPLETED,
                                periodDate = today,
                            ),
                        )
                        appEventBus.emit(AppMutationEvent.GoalCheckedIn(entityId))
                        PromiseWidgetUpdater.fetchAndPushWidgetData(context.applicationContext)
                    }
                    ACTION_REPLY_CHAT -> {
                        if (!replyText.isNullOrBlank()) {
                            val msg = goalRepository.sendChatMessage(entityId, replyText)
                            appEventBus.emit(AppMutationEvent.ChatMessageCreated(entityId, msg.id))
                            PromiseWidgetUpdater.fetchAndPushWidgetData(context.applicationContext)
                        }
                    }
                }
            } catch (e: Throwable) {
                val msg = e.message.orEmpty()
                if (msg.contains("409") || msg.contains("404") || msg.contains("ALREADY_COMPLETED")) {
                    PromiseWidgetUpdater.fetchAndPushWidgetData(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE_COMMITMENT = "app.promise.android.ACTION_COMPLETE_COMMITMENT"
        const val ACTION_SNOOZE_COMMITMENT = "app.promise.android.ACTION_SNOOZE_COMMITMENT"
        const val ACTION_CHECKIN_GOAL = "app.promise.android.ACTION_CHECKIN_GOAL"
        const val ACTION_REPLY_CHAT = "app.promise.android.ACTION_REPLY_CHAT"

        const val EXTRA_ENTITY_ID = "extra_entity_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }
}

