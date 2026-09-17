package com.loresuelvo.serviceprovider.domain.file

/**
 * Business purpose for an uploaded file on the LoResuelvo platform.
 * Mirrors the backend's
 * `internal/domain/file/file.go` `Purpose` constants one-to-one.
 */
enum class FilePurpose {
    PROFILE_PHOTO,
    CONVERSATION_MESSAGE_IMAGE,
    CONVERSATION_MESSAGE_AUDIO,
    CONVERSATION_MESSAGE_VIDEO,
}
