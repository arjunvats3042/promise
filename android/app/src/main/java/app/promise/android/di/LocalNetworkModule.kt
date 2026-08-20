package app.promise.android.di

import app.promise.android.data.remote.AndroidLocalNetworkPermission
import app.promise.android.data.remote.LocalNetworkPermission
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalNetworkModule {
    @Binds
    @Singleton
    abstract fun bindLocalNetworkPermission(
        impl: AndroidLocalNetworkPermission,
    ): LocalNetworkPermission
}
