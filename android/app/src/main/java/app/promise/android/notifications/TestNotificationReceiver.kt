package app.promise.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors

class TestNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TEST_NOTIFICATION = "app.promise.android.ACTION_TEST_NOTIFICATION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Promise Notification"
        val body = intent.getStringExtra("body") ?: "Here is your requested notification! ✨"
        val eventType = intent.getStringExtra("event_type") ?: "goal.today_practice"
        val entityType = intent.getStringExtra("entity_type") ?: "GOAL"
        val entityId = intent.getStringExtra("entity_id") ?: "test-goal-1"
        val reminderId = intent.getStringExtra("reminder_id") ?: "test-rem-${System.currentTimeMillis()}"
        val deepLink = intent.getStringExtra("deep_link") ?: "promise://home"

        val map = mapOf(
            "title" to title,
            "body" to body,
            "event_type" to eventType,
            "entity_type" to entityType,
            "entity_id" to entityId,
            "reminder_id" to reminderId,
            "deep_link" to deepLink,
        )

        val parsed = NotificationPayloadParser.parse(map) ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PromiseFirebaseMessagingService.FcmEntryPoint::class.java
        )
        entryPoint.notificationManager().show(parsed)
    }
}
