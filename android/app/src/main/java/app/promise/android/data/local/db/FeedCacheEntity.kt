package app.promise.android.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "feed_cache")
data class FeedCacheEntity(
    @PrimaryKey val id: String = "primary_feed",
    val dailyQuoteText: String? = null,
    val dailyQuoteAuthor: String? = null,
    val lastSyncedAtEpochMs: Long = 0L,
)

@Dao
interface FeedCacheDao {
    @Query("SELECT * FROM feed_cache WHERE id = 'primary_feed' LIMIT 1")
    fun observeCache(): Flow<FeedCacheEntity?>

    @Query("SELECT * FROM feed_cache WHERE id = 'primary_feed' LIMIT 1")
    suspend fun getCache(): FeedCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCache(cache: FeedCacheEntity)

    @Query("DELETE FROM feed_cache")
    suspend fun clearAll(): Int
}
