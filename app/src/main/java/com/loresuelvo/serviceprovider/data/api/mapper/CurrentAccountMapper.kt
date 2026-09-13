package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.CurrentAccountDto
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount

internal fun CurrentAccountDto.toDomain(): CurrentAccount = when (role.lowercase()) {
    "provider" -> CurrentAccount.Provider(
        id = id,
        name = name,
        surname = surname,
        email = email,
        category = requireNotNull(category).toDomain(),
        profilePhotoUrl = profilePhoto?.url,
    )
    "consumer" -> CurrentAccount.Consumer
    else -> error("Unsupported current account role")
}
