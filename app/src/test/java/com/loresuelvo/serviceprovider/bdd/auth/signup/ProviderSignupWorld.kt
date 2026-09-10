package com.loresuelvo.serviceprovider.bdd.auth.signup

import android.content.Context
import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.LogoutOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

/**
 * Deterministic world for 01-PSU.
 *
 * This world exercises the app-owned boundary only: selecting signup calls
 * [WelcomeViewModel.signup], which delegates to [AuthProvider.signup]. The
 * fake deliberately does not model an Auth0 tenant or invent a database
 * connection identifier. That part of the acceptance contract stays pending
 * until the tenant configuration is supplied and verified.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSignupWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val context = mockk<Context>(relaxed = true)
    private val authProvider = RecordingAuthProvider()
    private val getCategories = GetCategoriesUseCase(
        object : CategoryRepository {
            override suspend fun getCategories(): CategoriesOutcome =
                CategoriesOutcome.Failure.Network(IllegalStateException("categories not in signup boundary"))
        },
    )

    private lateinit var viewModel: WelcomeViewModel

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun seedNoLocalSession() {
        viewModel = WelcomeViewModel(authProvider, getCategories)
    }

    fun selectSignup() {
        check(::viewModel.isInitialized) { "Signup scenario must seed its local session first" }
        viewModel.signup(context)
        scheduler.advanceUntilIdle()
    }

    fun assertSignupDelegated() {
        assertEquals(1, authProvider.signupCalls)
    }

    /**
     * The provider port accepts only an Activity context. There is no
     * password argument or password storage in the app-owned signup boundary;
     * tenant-side credential collection is outside this deterministic fake.
     */
    fun assertNoPasswordHandledByApp() {
        val signupMethods = AuthProvider::class.java.methods.filter { it.name == "signup" }
        assertEquals(1, signupMethods.size)
        val signupParameterTypes = signupMethods.single().parameterTypes
        assertEquals(Context::class.java, signupParameterTypes.first())
        assertFalse(
            "The provider port must not receive a password argument",
            signupParameterTypes.any { it.name.contains("password", ignoreCase = true) },
        )
        assertFalse(
            "The authentication outcome must not expose a password",
            AuthenticationOutcome::class.java.declaredFields.any { it.name.contains("password", ignoreCase = true) },
        )
    }

    fun signupCalls(): Int = authProvider.signupCalls

    override fun close() {
        Dispatchers.resetMain()
    }

    private class RecordingAuthProvider : AuthProvider {

        var signupCalls: Int = 0
            private set

        override suspend fun login(context: Context): AuthenticationOutcome =
            AuthenticationOutcome.Cancelled

        override suspend fun signup(context: Context): AuthenticationOutcome {
            signupCalls += 1
            return AuthenticationOutcome.Cancelled
        }

        override suspend fun loginWithGoogle(context: Context): AuthenticationOutcome =
            AuthenticationOutcome.Cancelled

        override suspend fun logout(context: Context): LogoutOutcome =
            LogoutOutcome.Cancelled
    }
}
