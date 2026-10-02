package com.loresuelvo.serviceprovider.domain.conversation

/** Creates a private output URI for a delegated camera capture. */
interface CameraOutput {
    fun createCameraOutputUri(): String
}
