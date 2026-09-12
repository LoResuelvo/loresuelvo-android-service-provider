package com.loresuelvo.serviceprovider.domain.provider

/**
 * Command representing the provider profile registration payload.
 *
 * Captures name, surname, and category collected in US-35.1.
 * Fields contributed by other stories (profile photo and coverage zones)
 * are represented with default or placeholder parameters to preserve
 * architecture purity while deferring the actual combined endpoint call.
 */
data class ProviderRegistrationCommand(
    val email: String,
    val name: String,
    val surname: String,
    val categoryId: Int,
    val coverageZoneIds: List<Int> = emptyList(),
    val profilePhotoFileId: String = "",
)
