package com.loresuelvo.serviceprovider.ui.proposals

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceProposalListViewModelTest {
    @Test fun `repeated load while in flight issues one GET`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            val repository = object : ServiceProposalRepository {
                override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
                    error("Creation is outside this test")
                override suspend fun list(): ServiceProposalListOutcome {
                    calls++
                    return ServiceProposalListOutcome.Success(emptyList())
                }
            }
            val viewModel = ServiceProposalListViewModel(GetServiceProposalsUseCase(repository))
            store.put("list", viewModel)
            viewModel.load()
            viewModel.load()
            advanceUntilIdle()
            assertEquals(1, calls)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
