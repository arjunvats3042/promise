package app.promise.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocalReminderEntryPoint {
        fun notificationManager(): PromiseNotificationManager
        fun commitmentDao(): app.promise.android.data.local.db.CommitmentDao
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: ""
        val entityType = intent.getStringExtra(EXTRA_ENTITY_TYPE) ?: "COMMITMENT"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Promise Reminder"
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: "commitment.due_now"
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: NotificationChannels.CHANNEL_COMMITMENT_ALERTS
        val priority = intent.getStringExtra(EXTRA_PRIORITY) ?: "high"
        val deepLink = intent.getStringExtra(EXTRA_DEEP_LINK) ?: "promise://commitments"

        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, LocalReminderEntryPoint::class.java)
        val notificationManager = entryPoint.notificationManager()
        val commitmentDao = entryPoint.commitmentDao()

        scope.launch {
            // Only fire if the commitment is still open/pending (not completed or cancelled)
            if (entityId.isNotEmpty() && entityType == "COMMITMENT") {
                val entity = commitmentDao.getById(entityId)
                if (entity != null && entity.status == "COMPLETED") {
                    return@launch
                }
            }

            val data = PromiseNotificationData(
                reminderId = reminderId,
                identityKey = "$entityType:$entityId:$eventType",
                entityType = entityType,
                entityId = entityId,
                eventType = eventType,
                title = title,
                body = body,
                deepLink = deepLink,
                channelId = channelId,
                priority = priority,
            )
            notificationManager.show(data)
        }
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "app.promise.android.ACTION_SHOW_REMINDER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_ENTITY_ID = "extra_entity_id"
        const val EXTRA_ENTITY_TYPE = "extra_entity_type"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_EVENT_TYPE = "extra_event_type"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_PRIORITY = "extra_priority"
        const val EXTRA_DEEP_LINK = "extra_deep_link"
    }
}
