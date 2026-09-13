package com.loresuelvo.serviceprovider.domain.account

import com.loresuelvo.serviceprovider.domain.category.Category

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
