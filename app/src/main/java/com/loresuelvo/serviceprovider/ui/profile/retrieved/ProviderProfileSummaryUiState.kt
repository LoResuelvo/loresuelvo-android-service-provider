package com.loresuelvo.serviceprovider.ui.profile.retrieved

data class ProviderProfileSummaryUiState(
    val isLoading: Boolean = false,
    val providerId: Int? = null,
    val name: String = "",
    val surname: String = "",
    val profilePhotoUrl: String? = null,
    val error: String? = null,
)
