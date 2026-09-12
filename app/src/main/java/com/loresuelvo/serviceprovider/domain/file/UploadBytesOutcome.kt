package com.loresuelvo.serviceprovider.domain.file

sealed interface UploadBytesOutcome {
    data object Success : UploadBytesOutcome
    sealed interface Failure : UploadBytesOutcome {
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int, val message: String) : Failure
        data class Unauthorized(val message: String) : Failure
    }
}
