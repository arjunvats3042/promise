package app.promise.android.di

import app.promise.android.data.local.TokenStore
import app.promise.android.data.network.AccessTokenInterceptor
import app.promise.android.data.network.AuthApi
import app.promise.android.data.network.AuthRetryInterceptor
import app.promise.android.data.network.AuthSession
import app.promise.android.data.network.JsonAcceptInterceptor
import app.promise.android.data.network.NetworkJson
import app.promise.android.data.network.SessionRefresher
import app.promise.android.data.network.UnitConverterFactory
import app.promise.android.data.remote.ApiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = NetworkJson.json

    @Provides
    @Singleton
    @PublicHttp
    fun providePublicOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(ApiConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(JsonAcceptInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideSessionRefresher(
        @PublicApi publicApi: AuthApi,
        tokenStore: TokenStore,
        session: AuthSession,
    ): SessionRefresher {
        return SessionRefresher(publicApi, tokenStore, session)
    }

    @Provides
    @Singleton
    fun provideAuthedOkHttpClient(
        @PublicHttp publicClient: OkHttpClient,
        session: AuthSession,
        refresher: SessionRefresher,
    ): OkHttpClient {
        return publicClient.newBuilder()
            .addInterceptor(AccessTokenInterceptor(session))
            .addInterceptor(AuthRetryInterceptor(session, refresher))
            .build()
    }

    @Provides
    @Singleton
    @PublicApi
    fun providePublicAuthApi(@PublicHttp publicClient: OkHttpClient, json: Json): AuthApi {
        return createRetrofit(publicClient, json).create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    @AuthedApi
    fun provideAuthedAuthApi(authedClient: OkHttpClient, json: Json): AuthApi {
        return createRetrofit(authedClient, json).create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDeviceApi(authedClient: OkHttpClient, json: Json): app.promise.android.data.network.DeviceApi {
        return createRetrofit(authedClient, json).create(app.promise.android.data.network.DeviceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNotificationPreferencesApi(authedClient: OkHttpClient, json: Json): app.promise.android.data.network.NotificationPreferencesApi {
        return createRetrofit(authedClient, json).create(app.promise.android.data.network.NotificationPreferencesApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSyncApi(authedClient: OkHttpClient, json: Json): app.promise.android.data.sync.SyncApi {
        return createRetrofit(authedClient, json).create(app.promise.android.data.sync.SyncApi::class.java)
    }

    private fun createRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val baseUrl = ApiConfig.baseUrl.ifBlank { "https://invalid.local/api/v1/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
