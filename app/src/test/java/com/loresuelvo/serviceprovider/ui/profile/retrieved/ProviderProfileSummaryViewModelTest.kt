package com.loresuelvo.serviceprovider.ui.profile.retrieved

import com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome
import com.loresuelvo.serviceprovider.domain.provider.ProviderProfile
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.provider.GetProviderProfileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderProfileSummaryViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private val repository = FakeProviderRepository()
    private val getProviderProfile = GetProviderProfileUseCase(repository)

    private lateinit var viewModel: ProviderProfileSummaryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ProviderProfileSummaryViewModel(getProviderProfile)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadProfile updates UI state with retrieved profile photo URL and details on success`() = runTest(scheduler) {
        val profile = ProviderProfile(
            id = 42,
            name = "Carlos",
            surname = "Gómez",
            profilePhotoUrl = "https://cdn.loresuelvo.test/profile/carlos.jpg",
        )
        repository.profileOutcome = GetProviderProfileOutcome.Success(profile)

        viewModel.loadProfile(42)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.error)
        assertEquals(42, state.providerId)
        assertEquals("Carlos", state.name)
        assertEquals("Gómez", state.surname)
        assertEquals("https://cdn.loresuelvo.test/profile/carlos.jpg", state.profilePhotoUrl)
    }

    @Test
    fun `loadProfile updates UI state with error when retrieval fails`() = runTest(scheduler) {
        repository.profileOutcome = GetProviderProfileOutcome.Failure.Network(IOException("Network error"))

        viewModel.loadProfile(42)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("Failed to load provider profile", state.error)
        assertNull(state.profilePhotoUrl)
    }

    private class FakeProviderRepository : ProviderRepository {
        var profileOutcome: GetProviderProfileOutcome = GetProviderProfileOutcome.Success(
            ProviderProfile(1, "Carlos", "Gómez", null),
        )

        override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome =
            RegistrationOutcome.Success(1)

        override suspend fun getProfile(providerId: Int): GetProviderProfileOutcome = profileOutcome
    }
}
