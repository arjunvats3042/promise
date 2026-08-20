package app.promise.android.di

import app.promise.android.ui.haptics.AndroidPromiseHaptics
import app.promise.android.ui.haptics.PromiseHaptics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HapticsModule {
    @Binds
    @Singleton
    abstract fun bindPromiseHaptics(impl: AndroidPromiseHaptics): PromiseHaptics
}
