package com.loresuelvo.serviceprovider.ui.proposals

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
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
    @Test fun `load after returning fetches fresh status and preserves tab`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            var status = ServiceProposalStatus.Pending
            val repository = object : ServiceProposalRepository {
                override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
                    error("Creation is outside this test")
                override suspend fun list(): ServiceProposalListOutcome {
                    calls++
                    return ServiceProposalListOutcome.Success(listOf(ServiceProposalSummary(
                        12, 93, 1500050, 1L, "Inspect sink", 45, status, 1L,
                        ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
                        ServiceProposalBookingTerms("ARS", 1500050, 1000, 1499050, 500, 100, 400,
                            1100, 1499450, 1500550, 1L),
                    )))
                }
            }
            val viewModel = ServiceProposalListViewModel(GetServiceProposalsUseCase(repository))
            store.put("list", viewModel)
            advanceUntilIdle()
            assertEquals(listOf(12), viewModel.uiState.value.visibleProposals.map { it.id })
            viewModel.onResume()
            assertEquals(1, calls)
            status = ServiceProposalStatus.Accepted
            viewModel.onResume()
            viewModel.onResume()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals(ProposalTab.Pending, viewModel.uiState.value.selectedTab)
            assertEquals(emptyList<ServiceProposalSummary>(), viewModel.uiState.value.visibleProposals)
            assertEquals(ServiceProposalStatus.Accepted,
                viewModel.uiState.value.proposalInConversation(93)?.status)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

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
