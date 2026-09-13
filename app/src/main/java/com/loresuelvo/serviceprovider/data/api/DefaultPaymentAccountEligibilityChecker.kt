package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibility
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibilityChecker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPaymentAccountEligibilityChecker @Inject constructor(
    private val sessionStore: AuthSessionStore,
) : PaymentAccountEligibilityChecker {

    override suspend fun checkEligibility(): PaymentAccountEligibility {
        val session = sessionStore.getSession() ?: return PaymentAccountEligibility.Ineligible.NotAProvider
        return PaymentAccountEligibility.Eligible
    }
}
