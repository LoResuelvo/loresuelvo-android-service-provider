package com.loresuelvo.serviceprovider.domain.account

import com.loresuelvo.serviceprovider.domain.category.Category

/**
 * The authenticated account returned by `/me`. [Consumer] is a role marker,
 * not a cached consumer profile: the provider app only needs the distinction
 * to fail closed before entering provider-only Home.
 */
sealed interface CurrentAccount {
    data class Provider(
        val id: Int,
        val name: String,
        val surname: String,
        val email: String,
        val category: Category,
        val profilePhotoUrl: String?,
    ) : CurrentAccount

    data object Consumer : CurrentAccount
}
