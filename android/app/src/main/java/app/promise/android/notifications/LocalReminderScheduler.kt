package app.promise.android.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface LocalReminderScheduler {
    fun scheduleCommitmentReminder(commitment: Commitment)
    fun cancelCommitmentReminder(commitmentId: String)
    fun rescheduleAll(commitments: List<Commitment>)
}

@Singleton
class LocalReminderSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocalReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun scheduleCommitmentReminder(commitment: Commitment) {
        if (commitment.status == CommitmentStatus.COMPLETED || commitment.status == CommitmentStatus.CANCELLED) {
            cancelCommitmentReminder(commitment.id)
            return
        }

        val rawDue = commitment.dueAt?.takeIf { it.isNotBlank() } ?: return
        val dueEpochMs = parseIsoToEpochMs(rawDue) ?: return
        val now = System.currentTimeMillis()

        if (dueEpochMs <= now) {
            // Already past due
            return
        }

        // 1. Schedule exact due time reminder ("Due now")
        val dueIntent = Intent(context, LocalReminderReceiver::class.java).apply {
            action = LocalReminderReceiver.ACTION_SHOW_REMINDER
            putExtra(LocalReminderReceiver.EXTRA_REMINDER_ID, "local_${commitment.id}_due")
            putExtra(LocalReminderReceiver.EXTRA_ENTITY_ID, commitment.id)
            putExtra(LocalReminderReceiver.EXTRA_ENTITY_TYPE, "COMMITMENT")
            putExtra(LocalReminderReceiver.EXTRA_TITLE, commitment.title)
            putExtra(LocalReminderReceiver.EXTRA_BODY, "Your commitment is due now.")
            putExtra(LocalReminderReceiver.EXTRA_EVENT_TYPE, "commitment.due_now")
            putExtra(LocalReminderReceiver.EXTRA_CHANNEL_ID, NotificationChannels.CHANNEL_COMMITMENT_ALERTS)
            putExtra(LocalReminderReceiver.EXTRA_PRIORITY, "high")
            putExtra(LocalReminderReceiver.EXTRA_DEEP_LINK, "promise://commitment/${commitment.id}")
        }

        val requestCodeDue = computeRequestCode(commitment.id, 0)
        val pendingIntentDue = PendingIntent.getBroadcast(
            context,
            requestCodeDue,
            dueIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        scheduleAlarm(dueEpochMs, pendingIntentDue)

        // 2. Schedule advance reminder (15 minutes prior) if due > 30 minutes away
        val advanceEpochMs = dueEpochMs - 15 * 60 * 1000
        if (advanceEpochMs > now) {
            val advanceIntent = Intent(context, LocalReminderReceiver::class.java).apply {
                action = LocalReminderReceiver.ACTION_SHOW_REMINDER
                putExtra(LocalReminderReceiver.EXTRA_REMINDER_ID, "local_${commitment.id}_15m")
                putExtra(LocalReminderReceiver.EXTRA_ENTITY_ID, commitment.id)
                putExtra(LocalReminderReceiver.EXTRA_ENTITY_TYPE, "COMMITMENT")
                putExtra(LocalReminderReceiver.EXTRA_TITLE, commitment.title)
                putExtra(LocalReminderReceiver.EXTRA_BODY, "Due in 15 minutes.")
                putExtra(LocalReminderReceiver.EXTRA_EVENT_TYPE, "commitment.due_soon")
                putExtra(LocalReminderReceiver.EXTRA_CHANNEL_ID, NotificationChannels.CHANNEL_COMMITMENT_REMINDERS)
                putExtra(LocalReminderReceiver.EXTRA_PRIORITY, "normal")
                putExtra(LocalReminderReceiver.EXTRA_DEEP_LINK, "promise://commitment/${commitment.id}")
            }

            val requestCodeAdvance = computeRequestCode(commitment.id, 1)
            val pendingIntentAdvance = PendingIntent.getBroadcast(
                context,
                requestCodeAdvance,
                advanceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            scheduleAlarm(advanceEpochMs, pendingIntentAdvance)
        }
    }

    override fun cancelCommitmentReminder(commitmentId: String) {
        val dueIntent = Intent(context, LocalReminderReceiver::class.java).apply {
            action = LocalReminderReceiver.ACTION_SHOW_REMINDER
        }
        val pDue = PendingIntent.getBroadcast(
            context,
            computeRequestCode(commitmentId, 0),
            dueIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pDue != null) {
            alarmManager?.cancel(pDue)
            pDue.cancel()
        }

        val pAdvance = PendingIntent.getBroadcast(
            context,
            computeRequestCode(commitmentId, 1),
            dueIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pAdvance != null) {
            alarmManager?.cancel(pAdvance)
            pAdvance.cancel()
        }
    }

    override fun rescheduleAll(commitments: List<Commitment>) {
        for (c in commitments) {
            scheduleCommitmentReminder(c)
        }
    }

    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (alarmManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun computeRequestCode(id: String, offset: Int): Int {
        // Constrain to safe bounds: mask to positive, modulo to prevent overflow on * 10
        val safeHash = (id.hashCode() and 0x7FFFFFFF) % 100_000_000
        return safeHash * 10 + offset
    }

    private fun parseIsoToEpochMs(isoString: String): Long? {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
