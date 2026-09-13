package com.loresuelvo.serviceprovider.domain.usecase.paymentaccount

import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountAuthorizationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import javax.inject.Inject

class RequestPaymentAccountAuthorizationUseCase @Inject constructor(
    private val repository: PaymentAccountRepository,
) {
    suspend operator fun invoke(): PaymentAccountAuthorizationOutcome =
        repository.requestAuthorization()
}
