package com.loresuelvo.serviceprovider.domain.usecase.identity

import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import javax.inject.Inject

class StartIdentityVerificationUseCase @Inject constructor(
    private val repository: IdentityVerificationRepository,
) {
    suspend operator fun invoke(): StartIdentityVerificationOutcome = repository.start()
}
