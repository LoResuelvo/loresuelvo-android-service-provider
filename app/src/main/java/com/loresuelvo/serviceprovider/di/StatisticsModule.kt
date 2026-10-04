package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.api.ApiCollectionTransactionsRepository
import com.loresuelvo.serviceprovider.data.api.CollectionTransactionsApi
import com.loresuelvo.serviceprovider.domain.statistics.CollectionTransactionsRepository
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetCollectionTransactionsUseCase
import com.loresuelvo.serviceprovider.data.api.ApiProviderCollectionsRepository
import com.loresuelvo.serviceprovider.data.api.ProviderCollectionsApi
import com.loresuelvo.serviceprovider.domain.statistics.ProviderCollectionsRepository
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderCollectionsUseCase
import com.loresuelvo.serviceprovider.data.api.ApiProviderActivityRepository
import com.loresuelvo.serviceprovider.data.api.ProviderActivityApi
import com.loresuelvo.serviceprovider.domain.statistics.ProviderActivityRepository
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase
import com.loresuelvo.serviceprovider.data.api.ApiProviderReputationRepository
import com.loresuelvo.serviceprovider.data.api.ProviderReputationApi
import com.loresuelvo.serviceprovider.domain.statistics.ProviderReputationRepository
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase
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
    abstract fun bindTransactionsRepository(impl: ApiCollectionTransactionsRepository):
        CollectionTransactionsRepository

    @Binds @Singleton
    abstract fun bindRepository(impl: ApiProviderActivityRepository): ProviderActivityRepository

    @Binds @Singleton
    abstract fun bindCollectionsRepository(impl: ApiProviderCollectionsRepository): ProviderCollectionsRepository

    @Binds @Singleton
    abstract fun bindReputationRepository(impl: ApiProviderReputationRepository):
        ProviderReputationRepository

    @Binds @Singleton
    abstract fun bindConversionRepository(impl: com.loresuelvo.serviceprovider.data.api.ApiProviderConversionRepository):
        com.loresuelvo.serviceprovider.domain.statistics.ProviderConversionRepository

    companion object {
        @Provides @Singleton
        fun provideConversionApi(retrofit: Retrofit): com.loresuelvo.serviceprovider.data.api.ProviderConversionApi =
            createConversionApi(retrofit)

        internal fun createConversionApi(retrofit: Retrofit): com.loresuelvo.serviceprovider.data.api.ProviderConversionApi =
            retrofit.newBuilder().apply { converterFactories().clear() }
                .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = true; coerceInputValues = false }
                    .asConverterFactory("application/json".toMediaType()))
                .build().create(com.loresuelvo.serviceprovider.data.api.ProviderConversionApi::class.java)

        @Provides
        fun provideConversionUseCase(repository: com.loresuelvo.serviceprovider.domain.statistics.ProviderConversionRepository, clock: Clock) =
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase(repository, clock)

        @Provides @Singleton
        fun provideReputationApi(retrofit: Retrofit): ProviderReputationApi =
            createReputationApi(retrofit)

        internal fun createReputationApi(retrofit: Retrofit): ProviderReputationApi =
            retrofit.newBuilder().apply { converterFactories().clear() }
                .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = true; coerceInputValues = false }
                    .asConverterFactory("application/json".toMediaType()))
                .build().create(ProviderReputationApi::class.java)

        @Provides
        fun provideReputationUseCase(repository: ProviderReputationRepository) =
            GetProviderReputationUseCase(repository)

        @Provides @Singleton
        fun provideTransactionsApi(retrofit: Retrofit): CollectionTransactionsApi =
            createTransactionsApi(retrofit)

        internal fun createTransactionsApi(retrofit: Retrofit): CollectionTransactionsApi =
            retrofit.newBuilder().apply { converterFactories().clear() }
                .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = true; coerceInputValues = false }
                    .asConverterFactory("application/json".toMediaType()))
                .build().create(CollectionTransactionsApi::class.java)

        @Provides
        fun provideTransactionsUseCase(repository: CollectionTransactionsRepository) =
            GetCollectionTransactionsUseCase(repository)

        @Provides @Singleton
        fun provideCollectionsApi(retrofit: Retrofit): ProviderCollectionsApi = createCollectionsApi(retrofit)

        internal fun createCollectionsApi(retrofit: Retrofit): ProviderCollectionsApi = retrofit.newBuilder().apply { converterFactories().clear() }
            .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = true; coerceInputValues = false }
                .asConverterFactory("application/json".toMediaType()))
            .build().create(ProviderCollectionsApi::class.java)
        @Provides
        fun provideCollectionsUseCase(repository: ProviderCollectionsRepository) = GetProviderCollectionsUseCase(repository)

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
