package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.api.ApiProviderActivityRepository
import com.loresuelvo.serviceprovider.data.api.ProviderActivityApi
import com.loresuelvo.serviceprovider.domain.statistics.ProviderActivityRepository
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

@Module
@InstallIn(SingletonComponent::class)
abstract class StatisticsModule {
    @Binds @Singleton
    abstract fun bindRepository(impl: ApiProviderActivityRepository): ProviderActivityRepository

    companion object {
        @Provides @Singleton
        fun provideApi(retrofit: Retrofit): ProviderActivityApi = createActivityApi(retrofit)

        internal fun createActivityApi(retrofit: Retrofit): ProviderActivityApi = retrofit.newBuilder().apply { converterFactories().clear() }
            .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = true; coerceInputValues = false }
                .asConverterFactory("application/json".toMediaType()))
            .build().create(ProviderActivityApi::class.java)
        @Provides
        fun provideUseCase(repository: ProviderActivityRepository) = GetProviderActivityUseCase(repository)
        @Provides @Singleton
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
