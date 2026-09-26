package com.loresuelvo.serviceprovider.acceptance.auth

import com.loresuelvo.serviceprovider.di.AuthModule
import com.loresuelvo.serviceprovider.di.RepositoryModule
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.platform.auth.BrowserAuthenticationLauncher
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZone
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZoneRepository
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZonesOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountAuthorizationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibility
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibilityChecker
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProviderSignupBrowserAuthenticationLauncher : BrowserAuthenticationLauncher {

    var nextOutcome: AuthenticationOutcome = AuthenticationOutcome.Cancelled
    var signupCalls: Int = 0
        private set

    override fun launch(
        activityContext: android.content.Context,
        action: AuthenticationAction,
        onResult: (AuthenticationOutcome) -> Unit,
    ) {
        if (action == AuthenticationAction.Signup) signupCalls += 1
        onResult(nextOutcome)
    }
}

class ProviderSignupSessionStore : AuthSessionStore {

    private val state = MutableStateFlow<AuthSession?>(null)
    override val sessionFlow: StateFlow<AuthSession?> = state

    override fun getSession(): AuthSession? = state.value

    override fun saveSession(session: AuthSession) {
        state.value = session
    }

    override fun clearSession() {
        state.value = null
    }
}

class ProviderSignupCategoryRepository : CategoryRepository {

    var categories: List<Category> = listOf(
        Category(id = 1, name = "Plomería"),
        Category(id = 2, name = "Electricidad"),
    )

    override suspend fun getCategories(): CategoriesOutcome =
        CategoriesOutcome.Success(categories)
}

class ProviderSignupCoverageZoneRepository : CoverageZoneRepository {
    override suspend fun getCoverageZones(): CoverageZonesOutcome = CoverageZonesOutcome.Success(
        listOf(CoverageZone(id = 1, name = "Comuna 1", boundaryPlaceId = "place-1")),
    )
}

class ProviderSignupProviderRepository : ProviderRepository {

    var outcome: RegistrationOutcome = RegistrationOutcome.Success(providerId = 1)
    var registerCalls: Int = 0
        private set
    var lastCommand: ProviderRegistrationCommand? = null
        private set

    override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome {
        registerCalls += 1
        lastCommand = command
        return outcome
    }

    var getProfileOutcome: GetProviderProfileOutcome =
        GetProviderProfileOutcome.Failure.NotFound

    override suspend fun getProfile(providerId: Int): GetProviderProfileOutcome =
        getProfileOutcome
}

class ProviderSignupCurrentAccountRepository : CurrentAccountRepository {
    var calls = 0
        private set
    var outcome: CurrentAccountOutcome = CurrentAccountOutcome.Failure.NotFound

    override suspend fun getCurrentAccount(): CurrentAccountOutcome {
        calls++
        return outcome
    }
}

class ProviderSignupJobRequestRepository : JobRequestRepository {
    override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> =
        ActivityLoadOutcome.Success(emptyList())

    override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome =
        AcceptJobRequestOutcome.Failure.Invalid
}

class ProviderSignupWorkOrderRepository : WorkOrderRepository {
    override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> =
        ActivityLoadOutcome.Success(emptyList())
}

class ProviderSignupConversationRepository : ConversationRepository {
    var outcome: ConversationsOutcome = ConversationsOutcome.Success(emptyList())
    var detailOutcome: ConversationDetailOutcome = ConversationDetailOutcome.Success(
        detail = ConversationDetail(
            id = 42,
            status = ConversationStatus.Active,
            counterpart = ConversationCounterpart(
                id = 7,
                name = "Ana",
                surname = "Pérez",
                profilePhotoUrl = null,
            ),
            messages = emptyList(),
            updatedOnEpochMillis = 1L,
        ),
    )

    override suspend fun getConversations(): ConversationsOutcome =
        outcome

    override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
        detailOutcome

    override suspend fun sendMessage(
        conversationId: Int,
        content: String,
    ): SendMessageOutcome =
        TODO("Not exercised by the signup acceptance test.")
}

class ProviderSignupServiceProposalRepository : ServiceProposalRepository {
    val created = mutableListOf<ValidatedServiceProposal>()
    var outcome: CreateServiceProposalOutcome = CreateServiceProposalOutcome.Created(9)
    var pending: kotlinx.coroutines.CompletableDeferred<CreateServiceProposalOutcome>? = null

    override suspend fun list() = com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome.Success(emptyList())

    override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
        created += proposal
        return pending?.await() ?: outcome
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AuthModule::class],
)
object ProviderSignupAuthTestModule {

    @Provides
    @Singleton
    fun provideProviderSignupBrowserAuthenticationLauncher(): ProviderSignupBrowserAuthenticationLauncher =
        ProviderSignupBrowserAuthenticationLauncher()

    @Provides
    @Singleton
    fun provideBrowserAuthenticationLauncher(
        implementation: ProviderSignupBrowserAuthenticationLauncher,
    ): BrowserAuthenticationLauncher = implementation
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class],
)
object ProviderSignupRepositoryTestModule {

    @Provides
    @Singleton
    fun provideSessionStore(): ProviderSignupSessionStore = ProviderSignupSessionStore()

    @Provides
    @Singleton
    fun provideAuthSessionStore(
        implementation: ProviderSignupSessionStore,
    ): AuthSessionStore = implementation

    @Provides
    @Singleton
    fun provideCategoryRepository(): ProviderSignupCategoryRepository = ProviderSignupCategoryRepository()

    @Provides
    @Singleton
    fun provideCategoryRepositoryBinding(
        implementation: ProviderSignupCategoryRepository,
    ): CategoryRepository = implementation

    @Provides
    @Singleton
    fun provideCoverageZoneRepository(): CoverageZoneRepository = ProviderSignupCoverageZoneRepository()

    @Provides
    @Singleton
    fun provideProviderRepository(): ProviderSignupProviderRepository = ProviderSignupProviderRepository()

    @Provides
    @Singleton
    fun provideProviderRepositoryBinding(
        implementation: ProviderSignupProviderRepository,
    ): ProviderRepository = implementation

    @Provides
    @Singleton
    fun provideCurrentAccountRepository(): ProviderSignupCurrentAccountRepository =
        ProviderSignupCurrentAccountRepository()

    @Provides
    @Singleton
    fun provideCurrentAccountRepositoryBinding(
        implementation: ProviderSignupCurrentAccountRepository,
    ): CurrentAccountRepository = implementation

    @Provides
    @Singleton
    fun provideJobRequestRepository(): ProviderSignupJobRequestRepository =
        ProviderSignupJobRequestRepository()

    @Provides
    @Singleton
    fun provideJobRequestRepositoryBinding(
        implementation: ProviderSignupJobRequestRepository,
    ): JobRequestRepository = implementation

    @Provides
    @Singleton
    fun provideWorkOrderRepository(): ProviderSignupWorkOrderRepository =
        ProviderSignupWorkOrderRepository()

    @Provides
    @Singleton
    fun provideWorkOrderRepositoryBinding(
        implementation: ProviderSignupWorkOrderRepository,
    ): WorkOrderRepository = implementation

    @Provides
    @Singleton
    fun provideConversationRepository(): ProviderSignupConversationRepository =
        ProviderSignupConversationRepository()

    @Provides
    @Singleton
    fun provideConversationRepositoryBinding(
        implementation: ProviderSignupConversationRepository,
    ): ConversationRepository = implementation

    @Provides
    @Singleton
    fun provideServiceProposalRepository(): ProviderSignupServiceProposalRepository =
        ProviderSignupServiceProposalRepository()

    @Provides
    @Singleton
    fun provideServiceProposalRepositoryBinding(
        implementation: ProviderSignupServiceProposalRepository,
    ): ServiceProposalRepository = implementation

    @Provides
    @Singleton
    fun providePaymentAccountRepository(): ProviderSignupPaymentAccountRepository = ProviderSignupPaymentAccountRepository()

    @Provides
    @Singleton
    fun providePaymentAccountRepositoryBinding(
        implementation: ProviderSignupPaymentAccountRepository,
    ): PaymentAccountRepository = implementation

    @Provides
    @Singleton
    fun providePaymentAccountEligibilityChecker(): ProviderSignupPaymentAccountEligibilityChecker = ProviderSignupPaymentAccountEligibilityChecker()

    @Provides
    @Singleton
    fun providePaymentAccountEligibilityCheckerBinding(
        implementation: ProviderSignupPaymentAccountEligibilityChecker,
    ): PaymentAccountEligibilityChecker = implementation
}

class ProviderSignupPaymentAccountRepository : PaymentAccountRepository {
    var outcome: PaymentAccountStatusOutcome = PaymentAccountStatusOutcome.Failure.Unauthorized
    var authorizationOutcome: PaymentAccountAuthorizationOutcome =
        PaymentAccountAuthorizationOutcome.Success("https://auth.mercadopago.com/authorization?client_id=123")

    override suspend fun getStatus(): PaymentAccountStatusOutcome = outcome
    override suspend fun requestAuthorization(): PaymentAccountAuthorizationOutcome = authorizationOutcome
}

class ProviderSignupPaymentAccountEligibilityChecker : PaymentAccountEligibilityChecker {
    var eligibility: PaymentAccountEligibility = PaymentAccountEligibility.Eligible

    override suspend fun checkEligibility(): PaymentAccountEligibility = eligibility
}
