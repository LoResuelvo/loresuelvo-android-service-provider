package com.loresuelvo.serviceprovider.domain.usecase.proposal

import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import javax.inject.Inject

class CreateServiceProposalUseCase @Inject constructor(
    private val repository: ServiceProposalRepository,
) {
    suspend operator fun invoke(proposal: ValidatedServiceProposal) = repository.create(proposal)
}
