package app.promise.android.di

import app.promise.android.data.commitments.CommitmentApi
import app.promise.android.data.commitments.CommitmentRepositoryImpl
import app.promise.android.data.network.NetworkJson
import app.promise.android.data.network.UnitConverterFactory
import app.promise.android.data.remote.ApiConfig
import app.promise.android.domain.CommitmentRepository
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
abstract class CommitmentBindModule {
    @Binds
    @Singleton
    abstract fun bindCommitmentRepository(impl: CommitmentRepositoryImpl): CommitmentRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CommitmentProvideModule {
    @Provides
    @Singleton
    fun provideCommitmentApi(authedClient: OkHttpClient, json: Json): CommitmentApi {
        val baseUrl = ApiConfig.baseUrl.ifBlank { "https://invalid.local/api/v1/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authedClient)
            .addConverterFactory(UnitConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CommitmentApi::class.java)
    }
}
