package app.promise.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.promise.android.data.local.db.OutboxDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@HiltWorker
class PromiseSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val outboxDao: OutboxDao,
    private val syncApi: SyncApi,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pending = outboxDao.getPendingActions()
        if (pending.isEmpty()) {
            return Result.success()
        }

        val json = Json { ignoreUnknownKeys = true }
        val actionDtos = pending.map { item ->
            val payloadObj = runCatching {
                if (item.payloadJson.isNotBlank() && item.payloadJson != "{}") {
                    json.parseToJsonElement(item.payloadJson).jsonObject
                } else null
            }.getOrNull()

            SyncActionItemDto(
                actionId = item.actionId,
                actionType = item.actionType,
                entityId = item.entityId,
                payload = payloadObj,
                clientTimestamp = item.clientTimestampIso,
            )
        }

        return try {
            val response = syncApi.syncOutbox(SyncBatchRequestDto(actionDtos))
            if (response.isSuccessful && response.body() != null) {
                val batchResult = response.body()!!
                for (res in batchResult.results) {
                    if (res.status == "APPLIED" || res.status == "ALREADY_PROCESSED") {
                        outboxDao.delete(res.actionId)
                    } else {
                        outboxDao.updateStatus(res.actionId, "FAILED")
                    }
                }
                Result.success()
            } else if (response.code() in 400..499) {
                // Client error, do not retry endlessly
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
