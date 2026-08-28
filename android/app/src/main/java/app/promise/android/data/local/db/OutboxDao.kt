package app.promise.android.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE status != 'IN_FLIGHT' ORDER BY createdAtEpochMs ASC")
    suspend fun getPendingActions(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(action: OutboxEntity)

    @Query("DELETE FROM outbox WHERE actionId = :actionId")
    suspend fun delete(actionId: String): Int

    @Query("UPDATE outbox SET status = :status, retryCount = retryCount + 1 WHERE actionId = :actionId")
    suspend fun updateStatus(actionId: String, status: String): Int

    @Query("UPDATE outbox SET status = 'PENDING' WHERE status = 'IN_FLIGHT'")
    suspend fun resetInFlight(): Int

    @Query("DELETE FROM outbox")
    suspend fun clearAll(): Int
}
