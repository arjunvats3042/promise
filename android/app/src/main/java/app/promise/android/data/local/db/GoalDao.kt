package app.promise.android.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE status = 'ACTIVE' ORDER BY title ASC")
    fun observeActive(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY title ASC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GoalEntity>)

    @Query("UPDATE goals SET checkedInToday = 1, currentStreak = currentStreak + 1, periodValue = periodValue + 1, isSynced = 0, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun recordCheckIn(id: String, now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM goals WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteStale(validIds: List<String>): Int

    @Query("DELETE FROM goals")
    suspend fun clearAll(): Int
}
