package com.loresuelvo.serviceprovider.ui.identity

import app.cash.turbine.test
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.identity.StartIdentityVerificationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OptionalIdentityVerificationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeIdentityVerificationRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `later navigates once`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.later()
            viewModel.later()

            assertEquals(OptionalIdentityVerificationEffect.NavigateToMercadoPago, awaitItem())
            expectNoEvents()
        }
        assertEquals(0, repository.calls)
    }

    @Test
    fun `verify now requests once and emits one credential launch`() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.verifyNow()
            viewModel.verifyNow()
            assertTrue(viewModel.uiState.value.loading)
            advanceUntilIdle()

            assertEquals("temporary-token", (awaitItem() as OptionalIdentityVerificationEffect.LaunchVerification).credential.token)
            expectNoEvents()
        }
        assertEquals(1, repository.calls)
    }

    @Test
    fun `completion navigates once without another backend request`() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.verifyNow()
            advanceUntilIdle()
            awaitItem()
            viewModel.onVerificationResult(IdentityVerificationResult.Completed)
            viewModel.onVerificationResult(IdentityVerificationResult.Completed)

            assertEquals(OptionalIdentityVerificationEffect.NavigateToMercadoPago, awaitItem())
            expectNoEvents()
        }
        assertEquals(1, repository.calls)
    }

    @Test
    fun `cancellation and failures restore both actions with safe feedback`() = runTest(dispatcher.scheduler) {
        val cases = listOf(
            IdentityVerificationResult.Cancelled to IdentityVerificationFeedback.Cancelled,
            IdentityVerificationResult.PermissionDenied to IdentityVerificationFeedback.PermissionDenied,
            IdentityVerificationResult.Failed to IdentityVerificationFeedback.Failed,
        )

        cases.forEach { (result, feedback) ->
            val viewModel = viewModel()
            viewModel.effects.test {
                viewModel.verifyNow()
                advanceUntilIdle()
                awaitItem()
                viewModel.onVerificationResult(result)

                assertFalse(viewModel.uiState.value.loading)
                assertEquals(feedback, viewModel.uiState.value.feedback)
                expectNoEvents()
            }
        }
    }

    private fun viewModel() = OptionalIdentityVerificationViewModel(
        StartIdentityVerificationUseCase(repository),
    )

    private class FakeIdentityVerificationRepository : IdentityVerificationRepository {
        var calls = 0
        override suspend fun start(): StartIdentityVerificationOutcome {
            calls += 1
            return StartIdentityVerificationOutcome.Success(
                IdentityVerificationCredential("temporary-token"),
            )
        }
    }
}
