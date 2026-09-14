package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
class ProviderRegistrationDefaultsModule {
    @Provides
    @Named("initialProviderCoverageZoneIds")
    fun provideInitialCoverageZoneIds(): List<Int> =
        if (BuildConfig.DEBUG && BuildConfig.FLAVOR == "dev") {
            // Temporary US-35.5 mock: Comuna 1 in a freshly seeded local API.
            listOf(1)
        } else {
            emptyList()
        }
}
