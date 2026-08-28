package app.promise.android.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class NotificationActionReceiver : BroadcastReceiver() {

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

        // 2. Enqueue background work via WorkManager
        val inputData = Data.Builder()
            .putString(NotificationActionWorker.KEY_ACTION, action)
            .putString(NotificationActionWorker.KEY_ENTITY_ID, entityId)
            .putInt(NotificationActionWorker.KEY_SNOOZE_MINUTES, snoozeMinutes)
            .apply {
                if (!replyText.isNullOrBlank()) {
                    putString(NotificationActionWorker.KEY_REPLY_TEXT, replyText)
                }
            }
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationActionWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
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

