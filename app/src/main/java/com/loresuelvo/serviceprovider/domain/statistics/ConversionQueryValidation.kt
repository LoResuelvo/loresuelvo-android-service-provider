package com.loresuelvo.serviceprovider.domain.statistics

import java.time.Duration
import java.time.Instant

enum class ConversionQueryError { INCOMPLETE, REVERSED, FUTURE, TOO_LONG }

fun ConversionQuery.validationError(now: Instant): ConversionQueryError? {
    if (from == null && to == null) return null
    val start = from?.toInstant() ?: return ConversionQueryError.INCOMPLETE
    val finish = to?.toInstant() ?: return ConversionQueryError.INCOMPLETE
    return when {
        start >= finish -> ConversionQueryError.REVERSED
        finish > now -> ConversionQueryError.FUTURE
        Duration.between(start, finish) > Duration.ofDays(365) -> ConversionQueryError.TOO_LONG
        else -> null
    }
}
