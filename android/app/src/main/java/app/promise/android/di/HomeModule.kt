package app.promise.android.di

import app.promise.android.data.home.PreviewHomeRepository
import app.promise.android.domain.HomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: PreviewHomeRepository): HomeRepository
}
