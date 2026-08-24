package com.loresuelvo.serviceprovider.data.api

/**
 * Centralized HTTP client configuration. Keep all tunables here so
 * the [com.loresuelvo.serviceprovider.di.NetworkModule] doesn't have
 * to know magic numbers. Values are conservative defaults; revisit
 * when the API's SLA is known.
 */
internal object ApiConfig {
    /** TCP connect timeout (seconds). */
    const val CONNECT_TIMEOUT_SECONDS = 10L

    /** Socket read timeout (seconds). */
    const val READ_TIMEOUT_SECONDS = 30L

    /** Socket write timeout (seconds). */
    const val WRITE_TIMEOUT_SECONDS = 30L

    /** Total call timeout (seconds) — covers connect + read + write. */
    const val CALL_TIMEOUT_SECONDS = 60L
}