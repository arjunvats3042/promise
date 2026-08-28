package app.promise.android.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun enqueueSync(force: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PromiseSyncWorker>()
            .setConstraints(constraints)
            .build()

        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            policy,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "promise_outbox_sync"
    }
}
