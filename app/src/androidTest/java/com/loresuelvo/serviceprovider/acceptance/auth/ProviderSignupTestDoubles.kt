package com.loresuelvo.serviceprovider.acceptance.auth

import android.content.Context
import com.loresuelvo.serviceprovider.di.AuthModule
import com.loresuelvo.serviceprovider.di.RepositoryModule
import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.LogoutOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
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
 * Deterministic Auth0 replacement for the provider signup navigation test.
 * The fake is provided as the production [AuthProvider] binding, allowing the
 * test to trigger the real Welcome/ViewModel/navigation graph without a
 * browser or a tenant.
 */
class ProviderSignupAuthProvider : AuthProvider {

    var nextOutcome: AuthenticationOutcome = AuthenticationOutcome.Cancelled
    var signupCalls: Int = 0
        private set

    override suspend fun login(context: Context): AuthenticationOutcome = nextOutcome

    override suspend fun signup(context: Context): AuthenticationOutcome {
        signupCalls += 1
        return nextOutcome
    }

    override suspend fun loginWithGoogle(context: Context): AuthenticationOutcome = nextOutcome

    override suspend fun logout(context: Context): LogoutOutcome = LogoutOutcome.Cancelled
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

/** The Welcome screen only needs a deterministic public-category response. */
class ProviderSignupCategoryRepository : CategoryRepository {

    override suspend fun getCategories(): CategoriesOutcome =
        CategoriesOutcome.Success(emptyList())
}

class ProviderSignupProviderRepository : ProviderRepository {

    override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome =
        RegistrationOutcome.Success(providerId = 1)
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AuthModule::class],
)
object ProviderSignupAuthTestModule {

    @Provides
    @Singleton
    fun provideProviderSignupAuthProvider(): ProviderSignupAuthProvider = ProviderSignupAuthProvider()

    @Provides
    @Singleton
    fun provideAuthProvider(
        implementation: ProviderSignupAuthProvider,
    ): AuthProvider = implementation
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
    fun provideCategoryRepository(): CategoryRepository = ProviderSignupCategoryRepository()

    @Provides
    @Singleton
    fun provideProviderRepository(): ProviderRepository = ProviderSignupProviderRepository()
}
