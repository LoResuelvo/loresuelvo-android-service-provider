package com.loresuelvo.serviceprovider.bdd.identity

import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome

class FakeIdentityVerificationRepository : IdentityVerificationRepository {
    var calls = 0
    var outcome: StartIdentityVerificationOutcome = StartIdentityVerificationOutcome.Failure.Network

    override suspend fun start(): StartIdentityVerificationOutcome {
        calls += 1
        return outcome
    }
}
