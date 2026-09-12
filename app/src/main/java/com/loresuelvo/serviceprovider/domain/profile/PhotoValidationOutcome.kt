package com.loresuelvo.serviceprovider.domain.profile

/**
 * Typed outcomes for profile photo inspection and preparation.
 */
sealed interface PhotoValidationOutcome {
    data class Valid(val photo: SelectedProfilePhoto) : PhotoValidationOutcome

    sealed interface Invalid : PhotoValidationOutcome {
        data object UnsupportedFormat : Invalid
        data object ExceedsMaxSize : Invalid
        data object EmptyFile : Invalid
        data object Unreadable : Invalid
        data object CorruptContent : Invalid
    }
}
