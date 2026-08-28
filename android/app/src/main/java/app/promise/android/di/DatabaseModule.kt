package app.promise.android.di

import android.content.Context
import androidx.room.Room
import app.promise.android.data.local.db.CommitmentDao
import app.promise.android.data.local.db.FeedCacheDao
import app.promise.android.data.local.db.GoalDao
import app.promise.android.data.local.db.OutboxDao
import app.promise.android.data.local.db.PromiseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePromiseDatabase(
        @ApplicationContext context: Context,
    ): PromiseDatabase {
        return Room.databaseBuilder(
            context,
            PromiseDatabase::class.java,
            "promise_local.db",
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideCommitmentDao(database: PromiseDatabase): CommitmentDao {
        return database.commitmentDao()
    }

    @Provides
    fun provideGoalDao(database: PromiseDatabase): GoalDao {
        return database.goalDao()
    }

    @Provides
    fun provideOutboxDao(database: PromiseDatabase): OutboxDao {
        return database.outboxDao()
    }

    @Provides
    fun provideFeedCacheDao(database: PromiseDatabase): FeedCacheDao {
        return database.feedCacheDao()
    }
}
