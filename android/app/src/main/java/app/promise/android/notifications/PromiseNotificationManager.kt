package app.promise.android.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import app.promise.android.MainActivity
import app.promise.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface PromiseNotificationManager {
    fun show(data: PromiseNotificationData)
    fun update(data: PromiseNotificationData)
    fun cancel(notificationId: Int)
    fun cancelByReminderId(reminderId: String)
    fun cancelByEntity(entityType: String, entityId: String)
}

@Singleton
class PromiseNotificationManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PromiseNotificationManager {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // In-memory mapping of reminderId/entity to notificationId for fast cancellation
    private val reminderToNotificationId = ConcurrentHashMap<String, Int>()
    private val entityToNotificationId = ConcurrentHashMap<String, Int>()

    override fun show(data: PromiseNotificationData) {
        NotificationChannels.ensureChannels(context)

        val notificationId = data.notificationId
        if (data.reminderId.isNotEmpty()) {
            reminderToNotificationId[data.reminderId] = notificationId
        }
        if (data.entityId.isNotEmpty()) {
            entityToNotificationId["${data.entityType}:${data.entityId}"] = notificationId
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(Intent.ACTION_VIEW, Uri.parse(data.deepLink), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val groupKey = if (data.entityType == "GOAL") "app.promise.GOALS" else "app.promise.COMMITMENTS"

        val builder = NotificationCompat.Builder(context, data.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setPriority(
                if (data.priority == "high") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )

        // Attach action buttons based on entity and event type
        when (data.entityType) {
            "COMMITMENT" -> {
                val completeIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId * 10 + 1,
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_COMPLETE_COMMITMENT
                        putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, data.entityId)
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                        putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, data.reminderId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val snoozeIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId * 10 + 2,
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_SNOOZE_COMMITMENT
                        putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, data.entityId)
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                        putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, data.reminderId)
                        putExtra(NotificationActionReceiver.EXTRA_SNOOZE_MINUTES, 60)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "Complete", completeIntent)
                builder.addAction(0, "Snooze 1h", snoozeIntent)
            }
            "GOAL" -> {
                val checkInIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId * 10 + 3,
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_CHECKIN_GOAL
                        putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, data.entityId)
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                        putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, data.reminderId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "Check in", checkInIntent)
            }
        }

        notificationManager.notify(notificationId, builder.build())
    }

    override fun update(data: PromiseNotificationData) {
        show(data)
    }

    override fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    override fun cancelByReminderId(reminderId: String) {
        val notificationId = reminderToNotificationId.remove(reminderId)
        if (notificationId != null) {
            notificationManager.cancel(notificationId)
        }
    }

    override fun cancelByEntity(entityType: String, entityId: String) {
        val notificationId = entityToNotificationId.remove("${entityType.uppercase()}:$entityId")
        if (notificationId != null) {
            notificationManager.cancel(notificationId)
        }
    }
}
