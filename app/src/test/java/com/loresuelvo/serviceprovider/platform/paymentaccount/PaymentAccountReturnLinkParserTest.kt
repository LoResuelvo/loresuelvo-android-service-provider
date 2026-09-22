package com.loresuelvo.serviceprovider.platform.paymentaccount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentAccountReturnLinkParserTest {

    private val parser = PaymentAccountReturnLinkParser(
        PaymentAccountConfig(returnHost = "return.example.test"),
    )

    @Test
    fun should_parse_success_result_hint() {
        val url = "https://return.example.test/provider/register/mercado-pago?result=success"
        assertEquals(PaymentAccountReturnHint.Success, parser.parse(url))
    }

    @Test
    fun should_parse_cancelled_result_hint() {
        val url = "https://return.example.test/provider/register/mercado-pago?result=cancelled"
        assertEquals(PaymentAccountReturnHint.Cancelled, parser.parse(url))
    }

    @Test
    fun should_return_null_for_unrecognized_result() {
        val url = "https://return.example.test/provider/register/mercado-pago?result=unknown"
        assertNull(parser.parse(url))
    }

    @Test
    fun should_return_null_for_different_path() {
        val url = "https://return.example.test/different/path?result=success"
        assertNull(parser.parse(url))
    }

    @Test
    fun should_return_null_for_non_https_scheme() {
        val url = "http://return.example.test/provider/register/mercado-pago?result=success"
        assertNull(parser.parse(url))
    }

    @Test
    fun should_return_null_for_malformed_url() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("not a url"))
    }

    @Test
    fun should_respect_custom_configured_return_path() {
        val customConfig = PaymentAccountConfig(
            returnHost = "return.example.test",
            returnPath = "/custom/return/path",
        )
        val customParser = PaymentAccountReturnLinkParser(customConfig)
        val url = "https://return.example.test/custom/return/path?result=success"
        assertEquals(PaymentAccountReturnHint.Success, customParser.parse(url))
        assertNull(customParser.parse("https://return.example.test/provider/register/mercado-pago?result=success"))
    }

    @Test
    fun rejects_links_without_a_configured_return_host() {
        assertNull(PaymentAccountReturnLinkParser().parse(
            "https://return.example.test/provider/register/mercado-pago?result=success",
        ))
    }

    @Test
    fun rejects_other_hosts_and_ambiguous_results() {
        assertNull(parser.parse(
            "https://return.example.test.evil.test/provider/register/mercado-pago?result=success",
        ))
        assertNull(parser.parse(
            "https://return.example.test@evil.test/provider/register/mercado-pago?result=success",
        ))
        assertNull(parser.parse(
            "https://return.example.test:8443/provider/register/mercado-pago?result=success",
        ))
        assertNull(parser.parse(
            "https://return.example.test/provider/register/mercado-pago?result=success&result=cancelled",
        ))
    }
}
