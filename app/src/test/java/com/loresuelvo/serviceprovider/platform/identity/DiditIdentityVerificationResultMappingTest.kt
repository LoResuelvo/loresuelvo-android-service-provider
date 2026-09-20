package com.loresuelvo.serviceprovider.platform.identity

import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import me.didit.sdk.SessionData
import me.didit.sdk.VerificationError
import me.didit.sdk.VerificationResult
import me.didit.sdk.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DiditIdentityVerificationResultMappingTest {
    private val session = SessionData("session-id", VerificationStatus.PENDING)

    @Test
    fun `completion summary is reduced to neutral completion`() {
        assertEquals(
            IdentityVerificationResult.Completed,
            VerificationResult.Completed(session).toIdentityVerificationResult(),
        )
    }

    @Test
    fun `cancellation is reduced without session data`() {
        assertEquals(
            IdentityVerificationResult.Cancelled,
            VerificationResult.Cancelled(session).toIdentityVerificationResult(),
        )
    }

    @Test
    fun `camera denial is distinct and other failures are generic`() {
        assertEquals(
            IdentityVerificationResult.PermissionDenied,
            VerificationResult.Failed(VerificationError.CameraAccessDenied, session)
                .toIdentityVerificationResult(),
        )
        assertEquals(
            IdentityVerificationResult.Failed,
            VerificationResult.Failed(VerificationError.SessionExpired, session)
                .toIdentityVerificationResult(),
        )
    }
}
