package com.loresuelvo.serviceprovider.domain.account

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityVerificationStatusTest {
    @Test
    fun `only actionable profile statuses permit starting or retrying verification`() {
        val expectedActions = mapOf(
            IdentityVerificationStatus.Unverified to IdentityVerificationAction.Start,
            IdentityVerificationStatus.NotStarted to IdentityVerificationAction.Start,
            IdentityVerificationStatus.AwaitingUser to IdentityVerificationAction.Start,
            IdentityVerificationStatus.Declined to IdentityVerificationAction.Retry,
            IdentityVerificationStatus.Abandoned to IdentityVerificationAction.Retry,
            IdentityVerificationStatus.Expired to IdentityVerificationAction.Retry,
            IdentityVerificationStatus.KycExpired to IdentityVerificationAction.Retry,
            IdentityVerificationStatus.InProgress to null,
            IdentityVerificationStatus.InReview to null,
            IdentityVerificationStatus.Resubmitted to null,
            IdentityVerificationStatus.Approved to null,
            IdentityVerificationStatus.Unavailable to null,
        )

        expectedActions.forEach { (status, expected) ->
            assertEquals(status.toString(), expected, status.availableAction)
        }
    }
}
