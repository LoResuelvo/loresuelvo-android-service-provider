package com.loresuelvo.serviceprovider.domain.account

interface CurrentAccountRepository {
    suspend fun getCurrentAccount(): CurrentAccountOutcome
}
