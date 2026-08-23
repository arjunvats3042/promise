package app.promise.android.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {

    @Binds
    @Singleton
    abstract fun bindAppUpdateManager(
        impl: FirebaseAppDistributionUpdateManager,
    ): AppUpdateManager
}
