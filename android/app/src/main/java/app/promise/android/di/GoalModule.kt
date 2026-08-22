package app.promise.android.di

import app.promise.android.data.goals.GoalApi
import app.promise.android.data.goals.GoalRepositoryImpl
import app.promise.android.data.network.UnitConverterFactory
import app.promise.android.data.remote.ApiConfig
import app.promise.android.domain.GoalRepository
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

import app.promise.android.data.realtime.GoalChatRealtimeClient
import app.promise.android.data.realtime.GoalChatRealtimeClientImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class GoalBindModule {
    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository

    @Binds
    @Singleton
    abstract fun bindGoalChatRealtimeClient(impl: GoalChatRealtimeClientImpl): GoalChatRealtimeClient
}

@Module
@InstallIn(SingletonComponent::class)
object GoalProvideModule {
    @Provides
    @Singleton
    fun provideGoalApi(authedClient: OkHttpClient, json: Json): GoalApi {
        val baseUrl = ApiConfig.baseUrl.ifBlank { "https://invalid.local/api/v1/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authedClient)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GoalApi::class.java)
    }
}
