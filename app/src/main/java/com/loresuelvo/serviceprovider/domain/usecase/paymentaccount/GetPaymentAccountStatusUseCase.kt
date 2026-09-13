package com.loresuelvo.serviceprovider.domain.usecase.paymentaccount

import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPaymentAccountStatusUseCase @Inject constructor(
    private val repository: PaymentAccountRepository,
) {
    suspend operator fun invoke(): PaymentAccountStatusOutcome = repository.getStatus()
}
