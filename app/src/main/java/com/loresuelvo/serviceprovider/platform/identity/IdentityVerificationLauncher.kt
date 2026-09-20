package com.loresuelvo.serviceprovider.platform.identity

import android.app.Activity
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult

interface IdentityVerificationLauncher {
    fun attach(activity: Activity)
    fun detach(activity: Activity)
    fun launch(
        credential: IdentityVerificationCredential,
        onResult: (IdentityVerificationResult) -> Unit,
    )
}
