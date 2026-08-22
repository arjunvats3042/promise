package app.promise.android.di

import app.promise.android.data.network.UnitConverterFactory
import app.promise.android.data.remote.ApiConfig
import app.promise.android.data.search.SearchApi
import app.promise.android.data.search.SearchRepositoryImpl
import app.promise.android.domain.SearchRepository
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
abstract class SearchBindModule {
    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SearchProvideModule {
    @Provides
    @Singleton
    fun provideSearchApi(authedClient: OkHttpClient, json: Json): SearchApi {
        val baseUrl = ApiConfig.baseUrl.ifBlank { "https://invalid.local/api/v1/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authedClient)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SearchApi::class.java)
    }
}
