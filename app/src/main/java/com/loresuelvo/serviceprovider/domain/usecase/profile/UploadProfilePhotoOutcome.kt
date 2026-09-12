package com.loresuelvo.serviceprovider.domain.usecase.profile

import com.loresuelvo.serviceprovider.domain.file.ConfirmedFile

sealed interface UploadProfilePhotoOutcome {
    data class Success(val confirmedFile: ConfirmedFile) : UploadProfilePhotoOutcome
    sealed interface Failure : UploadProfilePhotoOutcome {
        val message: String

        data class Network(
            override val message: String,
            val cause: Throwable? = null,
        ) : Failure

        data class Server(
            val code: Int,
            override val message: String,
        ) : Failure

        data class LocalFileError(
            override val message: String,
        ) : Failure

        data class Unauthorized(
            override val message: String,
        ) : Failure
    }
}
