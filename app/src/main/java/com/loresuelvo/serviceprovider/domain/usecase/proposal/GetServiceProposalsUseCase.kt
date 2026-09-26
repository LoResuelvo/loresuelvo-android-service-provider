package com.loresuelvo.serviceprovider.domain.usecase.proposal

import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import javax.inject.Inject

class GetServiceProposalsUseCase @Inject constructor(
    private val repository: ServiceProposalRepository,
) {
    suspend operator fun invoke(): ServiceProposalListOutcome = when (val result = repository.list()) {
        is ServiceProposalListOutcome.Success -> result.copy(proposals = result.proposals.sortedWith(
            compareByDescending<ServiceProposalSummary> { it.createdOnEpochMillis }.thenByDescending { it.id },
        ))
        is ServiceProposalListOutcome.Failure -> result
    }
}
