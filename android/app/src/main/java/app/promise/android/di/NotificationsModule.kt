package app.promise.android.di

import app.promise.android.notifications.NoOpNotificationRouter
import app.promise.android.notifications.NotificationRouter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    @Singleton
    abstract fun bindNotificationRouter(impl: NoOpNotificationRouter): NotificationRouter
}
