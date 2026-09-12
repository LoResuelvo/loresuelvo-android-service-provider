package com.loresuelvo.serviceprovider.domain.file

sealed interface ConfirmUploadOutcome {
    data class Success(val file: ConfirmedFile) : ConfirmUploadOutcome
    sealed interface Failure : ConfirmUploadOutcome {
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int, val message: String) : Failure
        data class Unauthorized(val message: String) : Failure
    }
}
