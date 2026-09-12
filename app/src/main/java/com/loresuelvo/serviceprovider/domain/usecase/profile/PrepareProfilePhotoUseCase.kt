package com.loresuelvo.serviceprovider.domain.usecase.profile

import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import javax.inject.Inject

/**
 * Use case to prepare and validate a selected profile photo source.
 */
class PrepareProfilePhotoUseCase @Inject constructor(
    private val preparer: ProfilePhotoPreparer,
) {
    suspend operator fun invoke(source: String): PhotoValidationOutcome =
        preparer.preparePhoto(source)
}
