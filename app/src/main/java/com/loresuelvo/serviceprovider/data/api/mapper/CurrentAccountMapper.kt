package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.CurrentAccountDto
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

internal fun CurrentAccountDto.toDomain(): CurrentAccount = when (role.lowercase()) {
    "provider" -> CurrentAccount.Provider(
        id = id,
        name = name,
        surname = surname,
        email = email,
        category = requireNotNull(category).toDomain(),
        profilePhotoUrl = profilePhoto?.url,
        identityVerificationStatus = identityVerificationStatus.toIdentityStatus(),
        identityVerifiedOn = identityVerifiedOn.toVerifiedOnMillis(),
    )
    "consumer" -> CurrentAccount.Consumer
    else -> error("Unsupported current account role")
}

private fun String?.toIdentityStatus(): IdentityVerificationStatus = when (this?.lowercase(Locale.ROOT)) {
    "unverified" -> IdentityVerificationStatus.Unverified
    "not_started" -> IdentityVerificationStatus.NotStarted
    "in_progress" -> IdentityVerificationStatus.InProgress
    "awaiting_user" -> IdentityVerificationStatus.AwaitingUser
    "in_review" -> IdentityVerificationStatus.InReview
    "approved" -> IdentityVerificationStatus.Approved
    "declined" -> IdentityVerificationStatus.Declined
    "resubmitted" -> IdentityVerificationStatus.Resubmitted
    "abandoned" -> IdentityVerificationStatus.Abandoned
    "expired" -> IdentityVerificationStatus.Expired
    "kyc_expired" -> IdentityVerificationStatus.KycExpired
    else -> IdentityVerificationStatus.Unavailable
}

private val verifiedOnPattern = Regex(
    """^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-](?:[01]\d|2[0-3]):[0-5]\d)$""",
)

private fun String?.toVerifiedOnMillis(): Long? {
    val match = this?.let(verifiedOnPattern::matchEntire) ?: return null
    val timestamp = match.groupValues[1] + match.groupValues[3]
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).apply {
        isLenient = false
    }
    val position = ParsePosition(0)
    val seconds = parser.parse(timestamp, position) ?: return null
    if (position.index != timestamp.length) return null
    val fraction = match.groupValues[2]
    val millis = fraction.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
    return seconds.time + millis
}
