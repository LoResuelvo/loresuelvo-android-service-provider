package com.loresuelvo.serviceprovider.domain.account

sealed interface IdentityVerificationStatus {
    val availableAction: IdentityVerificationAction?
        get() = when (this) {
            Unverified, NotStarted, AwaitingUser -> IdentityVerificationAction.Start
            Declined, Abandoned, Expired, KycExpired -> IdentityVerificationAction.Retry
            Unavailable, InProgress, InReview, Resubmitted, Approved -> null
        }

    data object Unavailable : IdentityVerificationStatus
    data object Unverified : IdentityVerificationStatus
    data object NotStarted : IdentityVerificationStatus
    data object InProgress : IdentityVerificationStatus
    data object AwaitingUser : IdentityVerificationStatus
    data object InReview : IdentityVerificationStatus
    data object Approved : IdentityVerificationStatus
    data object Declined : IdentityVerificationStatus
    data object Resubmitted : IdentityVerificationStatus
    data object Abandoned : IdentityVerificationStatus
    data object Expired : IdentityVerificationStatus
    data object KycExpired : IdentityVerificationStatus
}

enum class IdentityVerificationAction { Start, Retry }
