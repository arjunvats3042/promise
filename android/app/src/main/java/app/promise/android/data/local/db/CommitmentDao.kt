package app.promise.android.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments WHERE status IN ('PENDING', 'WAITING', 'SNOOZED') ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, title ASC")
    fun observeOpen(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE status = 'COMPLETED' ORDER BY completedAt DESC, updatedAtEpochMs DESC")
    fun observeCompleted(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CommitmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommitmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CommitmentEntity>)

    @Query("UPDATE commitments SET status = 'COMPLETED', completedAt = :completedAt, isSynced = 0, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markCompleted(id: String, completedAt: String, now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM commitments WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM commitments WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteStale(validIds: List<String>): Int

    @Query("DELETE FROM commitments")
    suspend fun clearAll(): Int
}
