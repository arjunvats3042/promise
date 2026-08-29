package app.promise.android.di

import app.promise.android.data.network.DeviceRegistrationRepositoryImpl
import app.promise.android.domain.DeviceRegistrationRepository
import app.promise.android.notifications.NoOpNotificationRouter
import app.promise.android.notifications.NotificationRouter
import app.promise.android.notifications.PromiseNotificationManager
import app.promise.android.notifications.PromiseNotificationManagerImpl
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

    @Binds
    @Singleton
    abstract fun bindPromiseNotificationManager(impl: PromiseNotificationManagerImpl): PromiseNotificationManager

    @Binds
    @Singleton
    abstract fun bindDeviceRegistrationRepository(impl: DeviceRegistrationRepositoryImpl): DeviceRegistrationRepository

    @Binds
    @Singleton
    abstract fun bindLocalReminderScheduler(
        impl: app.promise.android.notifications.LocalReminderSchedulerImpl,
    ): app.promise.android.notifications.LocalReminderScheduler

    @Binds
    @Singleton
    abstract fun bindNotificationPreferencesRepository(
        impl: app.promise.android.data.network.NotificationPreferencesRepositoryImpl,
    ): app.promise.android.domain.NotificationPreferencesRepository
}
