package com.loresuelvo.serviceprovider.domain.profile

/**
 * Port for inspecting, validating, and staging a user-selected profile photo.
 * Pure contract independent of Android platform types.
 */
interface ProfilePhotoPreparer {
    suspend fun preparePhoto(source: String): PhotoValidationOutcome
    suspend fun cleanPhoto(photo: SelectedProfilePhoto)
}
