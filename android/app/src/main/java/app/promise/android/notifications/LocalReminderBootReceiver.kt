package app.promise.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.DuePrecision
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalReminderBootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun commitmentDao(): app.promise.android.data.local.db.CommitmentDao
        fun localReminderScheduler(): LocalReminderScheduler
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.MY_PACKAGE_REPLACED") {
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, BootEntryPoint::class.java)
        val dao = entryPoint.commitmentDao()
        val scheduler = entryPoint.localReminderScheduler()

        scope.launch {
            try {
                val openEntities = dao.getOpenWithDueDate()
                for (entity in openEntities) {
                    val commitment = app.promise.android.domain.Commitment(
                        id = entity.id,
                        title = entity.title,
                        description = entity.description,
                        status = when (entity.status.uppercase()) {
                            "COMPLETED" -> CommitmentStatus.COMPLETED
                            "CANCELLED" -> CommitmentStatus.CANCELLED
                            "SNOOZED" -> CommitmentStatus.SNOOZED
                            "WAITING" -> CommitmentStatus.WAITING
                            else -> CommitmentStatus.PENDING
                        },
                        dueAt = entity.dueAt,
                        duePrecision = try { DuePrecision.valueOf(entity.duePrecision) } catch (_: Exception) { DuePrecision.NONE },
                        source = "LOCAL",
                        snoozedUntil = null,
                        completedAt = entity.completedAt,
                        cancelledAt = null,
                        createdAt = "",
                        updatedAt = "",
                        isOverdue = entity.isOverdue,
                    )
                    scheduler.scheduleCommitmentReminder(commitment)
                }
            } catch (_: Throwable) {
            }
        }
    }
}
