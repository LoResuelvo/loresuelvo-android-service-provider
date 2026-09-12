package com.loresuelvo.serviceprovider.domain.file

sealed interface PresignUploadOutcome {
    data class Success(val result: PresignUploadResult) : PresignUploadOutcome
    sealed interface Failure : PresignUploadOutcome {
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int, val message: String) : Failure
        data class Unauthorized(val message: String) : Failure
    }
}
