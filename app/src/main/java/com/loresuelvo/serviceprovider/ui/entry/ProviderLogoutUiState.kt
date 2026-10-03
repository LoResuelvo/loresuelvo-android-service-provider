package com.loresuelvo.serviceprovider.ui.entry

/** Non-secret logout state owned outside the private navigation graph. */
data class ProviderLogoutUiState(
    val confirmationVisible: Boolean = false,
    val processing: Boolean = false,
    val localRemovalPending: Boolean = false,
    val externalLogoutPending: Boolean = false,
    val launchId: Long? = null,
)
