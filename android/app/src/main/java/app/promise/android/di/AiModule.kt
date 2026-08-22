package app.promise.android.di

import app.promise.android.data.ai.AiApi
import app.promise.android.data.ai.AiRepositoryImpl
import app.promise.android.data.network.UnitConverterFactory
import app.promise.android.data.remote.ApiConfig
import app.promise.android.domain.AiRepository
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
abstract class AiBindModule {
    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AiProvideModule {
    @Provides
    @Singleton
    fun provideAiApi(authedClient: OkHttpClient, json: Json): AiApi {
        val baseUrl = ApiConfig.baseUrl.ifBlank { "https://invalid.local/api/v1/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authedClient)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AiApi::class.java)
    }
}
