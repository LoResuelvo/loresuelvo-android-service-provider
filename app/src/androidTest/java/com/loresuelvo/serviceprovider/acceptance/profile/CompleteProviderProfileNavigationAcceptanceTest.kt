package com.loresuelvo.serviceprovider.acceptance.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupAuthProvider
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupCategoryRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupProviderRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end device acceptance test for the provider profile completion flow (US-35.1).
 *
 * Verifies the full user flow from Welcome signup to Profile form completion
 * and navigation to the Mercado Pago step on device/emulator.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CompleteProviderProfileNavigationAcceptanceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var authProvider: ProviderSignupAuthProvider
    private lateinit var sessionStore: ProviderSignupSessionStore
    private lateinit var categoryRepository: ProviderSignupCategoryRepository
    private lateinit var providerRepository: ProviderSignupProviderRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        val entryPoint = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext(),
            CompleteProviderProfileTestEntryPoint::class.java,
        )
        authProvider = entryPoint.authProvider() as ProviderSignupAuthProvider
        sessionStore = entryPoint.sessionStore()
        categoryRepository = entryPoint.categoryRepository() as ProviderSignupCategoryRepository
        providerRepository = entryPoint.providerRepository() as ProviderSignupProviderRepository

        categoryRepository.categories = listOf(
            Category(id = 1, name = "Plomería"),
            Category(id = 2, name = "Gas"),
        )
    }

    @Test
    fun completing_profile_form_navigates_to_mercadopago_linking_step() {
        val session = AuthSession(
            user = User(
                id = "auth0|provider-device",
                email = "device.provider@loresuelvo.test",
            ),
            accessToken = "device-access-token",
        )
        authProvider.nextOutcome = AuthenticationOutcome.Success(session)

        // 1. Start from Welcome and trigger signup
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.welcome_register))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // 2. Observe Profile screen heading
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.provider_profile_title))
            .assertIsDisplayed()

        // 3. Fill in name and surname
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.provider_profile_name_label))
            .performTextInput("Carlos")

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.provider_profile_surname_label))
            .performTextInput("Gómez")

        // 4. Select category from dropdown
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.provider_profile_category_label))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Plomería")
            .performClick()
        composeTestRule.waitForIdle()

        // 5. Submit form
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.provider_profile_submit))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // 6. Observe navigation to Mercado Pago placeholder
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.mercadopago_placeholder_title))
            .assertIsDisplayed()

        // 7. Verify backend repository was invoked with correct command
        assertEquals(1, providerRepository.registerCalls)
        assertEquals("device.provider@loresuelvo.test", providerRepository.lastCommand?.email)
        assertEquals("Carlos", providerRepository.lastCommand?.name)
        assertEquals("Gómez", providerRepository.lastCommand?.surname)
        assertEquals(1, providerRepository.lastCommand?.categoryId)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CompleteProviderProfileTestEntryPoint {

    fun authProvider(): AuthProvider

    fun sessionStore(): ProviderSignupSessionStore

    fun categoryRepository(): ProviderSignupCategoryRepository

    fun providerRepository(): ProviderSignupProviderRepository
}
