package app.promise.android.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
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
    @param:ApplicationContext private val context: Context,
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
            entityToNotificationId["${data.entityType.uppercase()}:${data.entityId}"] = notificationId
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(Intent.ACTION_VIEW, Uri.parse(data.deepLink), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val groupKey = when {
            data.eventType == "goal.chat.message_created" || data.channelId == NotificationChannels.CHANNEL_COMMUNITY -> GROUP_CHAT
            data.entityType.equals("GOAL", ignoreCase = true) -> GROUP_GOALS
            data.entityType.equals("DIGEST", ignoreCase = true) || data.eventType == "digest.weekly" -> GROUP_DIGEST
            data.entityType.equals("SECURITY", ignoreCase = true) || data.entityType.equals("SYSTEM", ignoreCase = true) -> GROUP_SYSTEM
            else -> GROUP_COMMITMENTS
        }

        val subText = when (data.entityType.uppercase()) {
            "GOAL" -> if (data.streakCount > 0) "Daily Practice · 🔥 ${data.streakCount}d streak" else "Daily Habit"
            "COMMITMENT" -> "Zero-Overdue Tracker"
            "DIGEST" -> "Gemini AI Digest"
            "SECURITY" -> "Account Security"
            else -> "Promise"
        }

        val isUrgent = data.priority == "high" || data.eventType == "commitment.due_now"

        val builder = NotificationCompat.Builder(context, data.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(0xFF4F46E5.toInt()) // Promise Indigo accent
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setSubText(subText)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setPriority(
                if (isUrgent) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(
                when {
                    isUrgent -> NotificationCompat.CATEGORY_ALARM
                    data.eventType == "goal.chat.message_created" -> NotificationCompat.CATEGORY_MESSAGE
                    data.entityType.equals("GOAL", ignoreCase = true) -> NotificationCompat.CATEGORY_REMINDER
                    else -> NotificationCompat.CATEGORY_REMINDER
                }
            )

        // Enrich with BigTextStyle formatted body
        val bigText = when {
            data.eventType == "goal.chat.message_created" -> data.body
            data.entityType.equals("GOAL", ignoreCase = true) && data.streakCount > 0 -> "${data.body}\n🔥 Keep your ${data.streakCount}-day streak alive! Tap Check In below."
            data.eventType == "commitment.due_now" -> "🚨 Due right now: ${data.body}\nComplete it to maintain your zero-overdue status."
            data.eventType == "commitment.overdue" -> "⚠️ Overdue: ${data.body}\nAction required. Tap Complete or Snooze."
            else -> data.body
        }

        builder.setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle(data.title)
                .setSummaryText(subText)
                .bigText(bigText)
        )

        // Attach rich interactive action buttons based on entity and event type
        when (data.entityType.uppercase()) {
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
                builder.addAction(0, "✓ Complete", completeIntent)
                builder.addAction(0, "⏰ Snooze 1h", snoozeIntent)
            }
            "GOAL" -> {
                if (data.eventType == "goal.chat.message_created") {
                    // Direct Reply Action with RemoteInput
                    val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
                        .setLabel("Type a quick reply...")
                        .build()

                    val replyIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId * 10 + 4,
                        Intent(context, NotificationActionReceiver::class.java).apply {
                            action = NotificationActionReceiver.ACTION_REPLY_CHAT
                            putExtra(NotificationActionReceiver.EXTRA_ENTITY_ID, data.entityId)
                            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                        },
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        },
                    )

                    val replyAction = NotificationCompat.Action.Builder(
                        0,
                        "💬 Reply",
                        replyIntent,
                    ).addRemoteInput(remoteInput).build()

                    builder.addAction(replyAction)
                } else if (!data.eventType.startsWith("goal.participant.")) {
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
                    builder.addAction(0, "✓ Check In", checkInIntent)
                }
            }
            "DIGEST" -> {
                builder.addAction(0, "✨ View AI Insights", contentIntent)
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
        val key = "${entityType.uppercase()}:$entityId"
        val notificationId = entityToNotificationId.remove(key) ?: PromiseNotificationData.computeNotificationId(key)
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val GROUP_COMMITMENTS = "app.promise.COMMITMENTS"
        const val GROUP_GOALS = "app.promise.GOALS"
        const val GROUP_CHAT = "app.promise.CHAT"
        const val GROUP_DIGEST = "app.promise.DIGEST"
        const val GROUP_SYSTEM = "app.promise.SYSTEM"
    }
}

