package com.loresuelvo.serviceprovider.platform.paymentaccount

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAuthorizationUrlValidatorTest {

    private val validator = PaymentAuthorizationUrlValidator(PaymentAccountConfig())

    @Test
    fun should_accept_valid_https_urls_on_allowed_domains() {
        assertTrue(validator.isValid("https://mercadopago.com/oauth"))
        assertTrue(validator.isValid("https://auth.mercadopago.com/authorization?client_id=123"))
        assertTrue(validator.isValid("https://auth.mercadopago.com.ar/authorization?client_id=123"))
    }

    @Test
    fun should_reject_invalid_schemes_and_user_info() {
        assertFalse(validator.isValid("http://auth.mercadopago.com/authorization"))
        assertFalse(validator.isValid("javascript:alert(1)"))
        assertFalse(validator.isValid("intent://auth.mercadopago.com"))
        assertFalse(validator.isValid("https://user:pass@auth.mercadopago.com/authorization"))
    }

    @Test
    fun should_reject_domain_suffix_collision_and_unrelated_domains() {
        assertFalse(validator.isValid("https://evilmercadopago.com/authorization"))
        assertFalse(validator.isValid("https://fakemercadopago.com.ar/authorization"))
        assertFalse(validator.isValid("https://attacker.com/authorization"))
    }

    @Test
    fun should_reject_malformed_urls() {
        assertFalse(validator.isValid(""))
        assertFalse(validator.isValid("not a valid url"))
        assertFalse(validator.isValid("https://"))
    }

    @Test
    fun should_respect_custom_allowed_domains_in_config() {
        val customValidator = PaymentAuthorizationUrlValidator(
            PaymentAccountConfig(allowedDomains = setOf("sandbox.example.com")),
        )
        assertTrue(customValidator.isValid("https://sandbox.example.com/oauth"))
        assertFalse(customValidator.isValid("https://auth.mercadopago.com/oauth"))
    }
}
