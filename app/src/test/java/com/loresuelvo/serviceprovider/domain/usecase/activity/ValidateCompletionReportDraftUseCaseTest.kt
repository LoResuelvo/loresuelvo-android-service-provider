package com.loresuelvo.serviceprovider.domain.usecase.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateCompletionReportDraftUseCaseTest {
    private val validate = ValidateCompletionReportDraftUseCase()

    @Test fun rejects_empty_or_whitespace_description() {
        assertEquals(CompletionDraftValidation.Invalid.DescriptionRequired,
            validate("", listOf("file-1")))
        assertEquals(CompletionDraftValidation.Invalid.DescriptionRequired,
            validate(" \n\t ", listOf("file-1")))
    }

    @Test fun requires_one_to_three_photos() {
        assertEquals(CompletionDraftValidation.Invalid.PhotoRequired, validate("Done", emptyList()))
        assertEquals(CompletionDraftValidation.Invalid.TooManyPhotos,
            validate("Done", listOf("1", "2", "3", "4")))
    }

    @Test fun requires_each_selected_photo_to_have_a_confirmed_file_id() {
        assertEquals(CompletionDraftValidation.Invalid.UnconfirmedPhoto,
            validate("Done", listOf(null)))
        assertEquals(CompletionDraftValidation.Invalid.UnconfirmedPhoto,
            validate("Done", listOf("file-1", " ")))
    }

    @Test fun rejects_repeated_confirmed_file_ids() {
        assertEquals(CompletionDraftValidation.Invalid.DuplicatePhotoIds,
            validate("Done", listOf("file-1", "file-1")))
    }

    @Test fun returns_trimmed_description_and_ordered_ids_for_valid_one_or_three_photos() {
        assertEquals(CompletionDraftValidation.Valid("Done", listOf("file-1")),
            validate("  Done \n", listOf("file-1")))
        assertEquals(CompletionDraftValidation.Valid("Done", listOf("1", "2", "3")),
            validate("Done", listOf("1", "2", "3")))
    }
}
