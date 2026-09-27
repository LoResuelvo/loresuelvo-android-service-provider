package com.loresuelvo.serviceprovider.domain.usecase.activity

import javax.inject.Inject

sealed interface CompletionDraftValidation {
    data class Valid(val description: String, val confirmedFileIds: List<String>) : CompletionDraftValidation

    sealed interface Invalid : CompletionDraftValidation {
        data object DescriptionRequired : Invalid
        data object PhotoRequired : Invalid
        data object TooManyPhotos : Invalid
        data object UnconfirmedPhoto : Invalid
        data object DuplicatePhotoIds : Invalid
    }
}

class ValidateCompletionReportDraftUseCase @Inject constructor() {
    operator fun invoke(description: String, confirmedFileIds: List<String?>): CompletionDraftValidation {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) return CompletionDraftValidation.Invalid.DescriptionRequired
        if (confirmedFileIds.isEmpty()) return CompletionDraftValidation.Invalid.PhotoRequired
        if (confirmedFileIds.size > 3) return CompletionDraftValidation.Invalid.TooManyPhotos
        if (confirmedFileIds.any { it.isNullOrBlank() })
            return CompletionDraftValidation.Invalid.UnconfirmedPhoto

        val ids = confirmedFileIds.filterNotNull()
        if (ids.distinct().size != ids.size) return CompletionDraftValidation.Invalid.DuplicatePhotoIds
        return CompletionDraftValidation.Valid(trimmed, ids)
    }
}
