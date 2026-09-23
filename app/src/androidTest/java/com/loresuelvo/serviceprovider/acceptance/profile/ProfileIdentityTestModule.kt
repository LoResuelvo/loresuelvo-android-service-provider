package com.loresuelvo.serviceprovider.acceptance.profile

import android.app.Activity
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import com.loresuelvo.serviceprovider.platform.identity.IdentityVerificationLauncher
import javax.inject.Inject
import javax.inject.Singleton

// Installed only by ProfileIdentityNavigationTest through its nested module.
@Singleton
class ProfileIdentityTestRepository @Inject constructor() : IdentityVerificationRepository {
    var calls = 0
        private set
    override suspend fun start(): StartIdentityVerificationOutcome {
        calls++
        return StartIdentityVerificationOutcome.Success(IdentityVerificationCredential("device-test-session"))
    }
}

@Singleton
class ProfileIdentityTestLauncher @Inject constructor() : IdentityVerificationLauncher {
    var calls = 0
        private set
    private var callback: ((IdentityVerificationResult) -> Unit)? = null
    override fun attach(activity: Activity) = Unit
    override fun detach(activity: Activity) = Unit
    override fun launch(credential: IdentityVerificationCredential, onResult: (IdentityVerificationResult) -> Unit) {
        check(credential.token == "device-test-session")
        calls++
        callback = onResult
    }
    fun finish(result: IdentityVerificationResult) {
        checkNotNull(callback).also { callback = null }.invoke(result)
    }
}
