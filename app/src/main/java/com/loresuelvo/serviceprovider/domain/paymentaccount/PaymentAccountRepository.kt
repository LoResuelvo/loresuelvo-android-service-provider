package com.loresuelvo.serviceprovider.domain.paymentaccount

interface PaymentAccountRepository {
    suspend fun getStatus(): PaymentAccountStatusOutcome
}
