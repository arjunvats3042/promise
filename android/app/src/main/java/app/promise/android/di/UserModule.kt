package app.promise.android.di

import app.promise.android.data.network.UnitConverterFactory
import app.promise.android.data.remote.ApiConfig
import app.promise.android.data.users.UserApi
import app.promise.android.data.users.UserRepositoryImpl
import app.promise.android.domain.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class UserBindModule {
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UserProvideModule {
    @Provides
    @Singleton
    fun provideUserApi(authedClient: OkHttpClient, json: Json): UserApi {
        val baseUrl = ApiConfig.baseUrl.ifBlank { "https://invalid.local/api/v1/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authedClient)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UserApi::class.java)
    }
}
