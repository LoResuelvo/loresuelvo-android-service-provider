package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.ConfirmFileRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.FileResponseDto
import com.loresuelvo.serviceprovider.data.api.dto.PresignFileRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.PresignFileResponseDto
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.ConfirmedFile
import com.loresuelvo.serviceprovider.domain.file.FilePurpose
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.PresignUploadResult

internal fun PresignUploadRequest.toDto(): PresignFileRequestDto =
    PresignFileRequestDto(
        originalName = originalName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        purpose = purposeToWire(purpose),
    )

internal fun ConfirmUploadRequest.toDto(): ConfirmFileRequestDto =
    ConfirmFileRequestDto(
        key = key,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
    )

internal fun PresignFileResponseDto.toDomain(): PresignUploadResult =
    PresignUploadResult(
        fileId = fileId,
        key = key,
        uploadUrl = uploadUrl,
        headers = headers,
    )

internal fun FileResponseDto.toDomain(): ConfirmedFile =
    ConfirmedFile(
        id = id,
        url = url,
        mimeType = mimeType,
        originalName = originalName,
    )

internal fun purposeToWire(purpose: FilePurpose): String =
    when (purpose) {
        FilePurpose.PROFILE_PHOTO -> "profile_photo"
    }

internal fun purposeFromWire(value: String): FilePurpose =
    when (value) {
        "profile_photo" -> FilePurpose.PROFILE_PHOTO
        else -> throw IllegalArgumentException("Unknown file purpose: $value")
    }
