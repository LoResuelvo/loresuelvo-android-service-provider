package com.loresuelvo.serviceprovider.platform.paymentaccount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentAccountReturnLinkParserTest {

    private val parser = PaymentAccountReturnLinkParser()

    @Test
    fun should_parse_success_result_hint() {
        val url = "https://test.loresuelvo.com.ar/provider/register/mercado-pago?result=success"
        assertEquals(PaymentAccountReturnHint.Success, parser.parse(url))
    }

    @Test
    fun should_parse_cancelled_result_hint() {
        val url = "https://test.loresuelvo.com.ar/provider/register/mercado-pago?result=cancelled"
        assertEquals(PaymentAccountReturnHint.Cancelled, parser.parse(url))
    }

    @Test
    fun should_return_null_for_unrecognized_result() {
        val url = "https://test.loresuelvo.com.ar/provider/register/mercado-pago?result=unknown"
        assertNull(parser.parse(url))
    }

    @Test
    fun should_return_null_for_different_path() {
        val url = "https://test.loresuelvo.com.ar/different/path?result=success"
        assertNull(parser.parse(url))
    }

    @Test
    fun should_return_null_for_non_https_scheme() {
        val url = "http://test.loresuelvo.com.ar/provider/register/mercado-pago?result=success"
        assertNull(parser.parse(url))
    }

    @Test
    fun should_return_null_for_malformed_url() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("not a url"))
    }

    @Test
    fun should_respect_custom_configured_return_path() {
        val customConfig = PaymentAccountConfig(returnPath = "/custom/return/path")
        val customParser = PaymentAccountReturnLinkParser(customConfig)
        val url = "https://test.loresuelvo.com.ar/custom/return/path?result=success"
        assertEquals(PaymentAccountReturnHint.Success, customParser.parse(url))
        assertNull(customParser.parse("https://test.loresuelvo.com.ar/provider/register/mercado-pago?result=success"))
    }
}
