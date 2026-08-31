package app.promise.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val CHANNEL_COMMITMENT_ALERTS = "channel_commitment_alerts"
    const val CHANNEL_COMMITMENT_REMINDERS = "channel_commitment_reminders"
    const val CHANNEL_GOAL_REMINDERS = "channel_goal_reminders"
    const val CHANNEL_COMMUNITY = "channel_community"
    const val CHANNEL_SYSTEM = "channel_system"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_COMMITMENT_ALERTS,
                "Commitment Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Exact deadline alarms and urgent time-sensitive alerts"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_COMMITMENT_REMINDERS,
                "Commitment Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Upcoming deadline reminders, overdue reviews, and snooze alerts"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_GOAL_REMINDERS,
                "Practice & Habit Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily practice routines, streak protection, and habit check-ins"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_COMMUNITY,
                "Shared Goals & Community Chat",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Chat messages, participant updates, and group goal activity"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_SYSTEM,
                "Security & System",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "New device logins and essential account alerts"
                enableVibration(true)
            },
        )

        for (channel in channels) {
            manager.createNotificationChannel(channel)
        }
    }
}
