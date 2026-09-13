package com.loresuelvo.serviceprovider.domain.usecase.paymentaccount

import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibility
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibilityChecker
import javax.inject.Inject

class CheckPaymentAccountEligibilityUseCase @Inject constructor(
    private val eligibilityChecker: PaymentAccountEligibilityChecker,
) {
    suspend operator fun invoke(): PaymentAccountEligibility =
        eligibilityChecker.checkEligibility()
}
