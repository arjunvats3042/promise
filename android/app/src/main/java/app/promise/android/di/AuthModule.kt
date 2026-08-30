package app.promise.android.di

import android.content.Context
import app.promise.android.data.local.AndroidKeystoreRefreshKeyProvider
import app.promise.android.data.local.SecureRefreshTokenStore
import app.promise.android.data.local.TokenStore
import app.promise.android.data.network.AuthApi
import app.promise.android.data.network.AuthRepositoryImpl
import app.promise.android.data.network.AuthSession
import app.promise.android.data.network.SessionRefresher
import app.promise.android.domain.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore {
        val dir = File(context.noBackupFilesDir, "auth")
        return SecureRefreshTokenStore(
            file = File(dir, "refresh.bin"),
            keyProvider = AndroidKeystoreRefreshKeyProvider(),
        )
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        @PublicApi publicApi: AuthApi,
        @AuthedApi authedApi: AuthApi,
        tokenStore: TokenStore,
        userSessionStore: app.promise.android.data.local.UserSessionStore,
        session: AuthSession,
        refresher: SessionRefresher,
        deviceRegistrationRepository: app.promise.android.domain.DeviceRegistrationRepository,
    ): AuthRepository {
        return AuthRepositoryImpl(
            context = context,
            publicApi = publicApi,
            authedApi = authedApi,
            tokenStore = tokenStore,
            userSessionStore = userSessionStore,
            memory = session,
            refresher = refresher,
            deviceRegistrationRepository = deviceRegistrationRepository,
        )
    }
}
