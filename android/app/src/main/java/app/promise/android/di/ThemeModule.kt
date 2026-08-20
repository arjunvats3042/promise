package app.promise.android.di

import app.promise.android.ui.theme.SharedPrefsThemeStore
import app.promise.android.ui.theme.ThemeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeModule {
    @Binds
    @Singleton
    abstract fun bindThemeStore(impl: SharedPrefsThemeStore): ThemeStore
}
