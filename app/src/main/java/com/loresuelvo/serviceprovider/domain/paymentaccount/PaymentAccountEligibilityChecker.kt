package com.loresuelvo.serviceprovider.domain.paymentaccount

interface PaymentAccountEligibilityChecker {
    suspend fun checkEligibility(): PaymentAccountEligibility
}
