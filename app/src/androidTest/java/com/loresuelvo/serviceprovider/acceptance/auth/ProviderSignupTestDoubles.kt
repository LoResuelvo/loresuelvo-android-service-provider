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
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Deterministic route/platform replacement for the provider signup test. It
 * returns a pure result without starting a browser or contacting a tenant.
 */
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

/** In-memory store that mirrors the production singleton session contract. */
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

/** Category repository fake for UI tests. */
class ProviderSignupCategoryRepository : CategoryRepository {

    var categories: List<Category> = listOf(
        Category(id = 1, name = "Plomería"),
        Category(id = 2, name = "Electricidad"),
    )

    override suspend fun getCategories(): CategoriesOutcome =
        CategoriesOutcome.Success(categories)
}

/** Provider registration repository fake for UI tests. */
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
    fun provideProviderRepository(): ProviderSignupProviderRepository = ProviderSignupProviderRepository()

    @Provides
    @Singleton
    fun provideProviderRepositoryBinding(
        implementation: ProviderSignupProviderRepository,
    ): ProviderRepository = implementation
}
