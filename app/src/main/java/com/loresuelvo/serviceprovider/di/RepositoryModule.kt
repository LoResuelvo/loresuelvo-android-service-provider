package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.api.ApiCategoryRepository
import com.loresuelvo.serviceprovider.data.api.ApiConversationRepository
import com.loresuelvo.serviceprovider.data.api.ApiCoverageZoneRepository
import com.loresuelvo.serviceprovider.data.api.ApiCurrentAccountRepository
import com.loresuelvo.serviceprovider.data.api.ApiJobRequestRepository
import com.loresuelvo.serviceprovider.data.api.ApiProviderRepository
import com.loresuelvo.serviceprovider.data.api.ApiWorkOrderRepository
import com.loresuelvo.serviceprovider.data.auth.EncryptedAuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZoneRepository
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds every `domain.XxxRepository` port to its production
 * `data/.../XxxRepositoryImpl`. Add a new port by:
 *   1. declaring `interface XxxRepository` in `domain/;
 *   2. writing `class XxxRepositoryImpl @Inject constructor(...) : XxxRepository` in `data/;
 *   3. adding a `@Binds fun bindXxxRepository(impl: XxxRepositoryImpl): XxxRepository`
 *      line here.
 *
 * `AuthSessionStore` is registered here because it plays the role of a
 * persistent repository from the provider-flow perspective. Its
 * `EncryptedSharedPreferences` lives in `data/auth/` (see
 * `SessionStoreModule`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: ApiCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(impl: ApiConversationRepository): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindCoverageZoneRepository(impl: ApiCoverageZoneRepository): CoverageZoneRepository

    @Binds
    @Singleton
    abstract fun bindCurrentAccountRepository(impl: ApiCurrentAccountRepository): CurrentAccountRepository

    @Binds
    @Singleton
    abstract fun bindJobRequestRepository(impl: ApiJobRequestRepository): JobRequestRepository

    @Binds
    @Singleton
    abstract fun bindWorkOrderRepository(impl: ApiWorkOrderRepository): WorkOrderRepository

    @Binds
    @Singleton
    abstract fun bindProviderRepository(impl: ApiProviderRepository): ProviderRepository

    @Binds
    @Singleton
    abstract fun bindAuthSessionStore(impl: EncryptedAuthSessionStore): AuthSessionStore

    @Binds
    @Singleton
    abstract fun bindPaymentAccountRepository(
        impl: com.loresuelvo.serviceprovider.data.api.ApiPaymentAccountRepository,
    ): com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository

    @Binds
    @Singleton
    abstract fun bindPaymentAccountEligibilityChecker(
        impl: com.loresuelvo.serviceprovider.data.api.DefaultPaymentAccountEligibilityChecker,
    ): com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibilityChecker
}
