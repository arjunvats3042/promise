package app.promise.android.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CommitmentEntity::class,
        GoalEntity::class,
        OutboxEntity::class,
        FeedCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PromiseDatabase : RoomDatabase() {
    abstract fun commitmentDao(): CommitmentDao
    abstract fun goalDao(): GoalDao
    abstract fun outboxDao(): OutboxDao
    abstract fun feedCacheDao(): FeedCacheDao
}
