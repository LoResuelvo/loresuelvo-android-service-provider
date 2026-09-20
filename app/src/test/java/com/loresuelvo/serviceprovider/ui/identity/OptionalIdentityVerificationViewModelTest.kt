package com.loresuelvo.serviceprovider.ui.identity

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionalIdentityVerificationViewModelTest {
    @Test
    fun `later navigates once`() = runTest {
        val viewModel = OptionalIdentityVerificationViewModel()

        viewModel.effects.test {
            viewModel.later()
            viewModel.later()

            assertEquals(OptionalIdentityVerificationEffect.NavigateToMercadoPago, awaitItem())
            expectNoEvents()
        }
    }
}
