package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.api.ApiIdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IdentityVerificationModule {
    @Binds
    @Singleton
    abstract fun bindIdentityVerificationRepository(
        implementation: ApiIdentityVerificationRepository,
    ): IdentityVerificationRepository
}
