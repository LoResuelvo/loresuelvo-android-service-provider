package com.loresuelvo.serviceprovider.domain.paymentaccount

sealed interface PaymentAccountEligibility {
    data object Eligible : PaymentAccountEligibility
    sealed interface Ineligible : PaymentAccountEligibility {
        data object IncompleteProfile : Ineligible
        data object NotAProvider : Ineligible
    }
}
