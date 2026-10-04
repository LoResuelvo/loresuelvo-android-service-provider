package com.loresuelvo.serviceprovider.ui.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

enum class ConversionDateError { FORMAT, INCOMPLETE, REVERSED, FUTURE, TOO_LONG }
data class ConversionFilters(val fromDay: String = "", val throughDay: String = "",
    val dateError: ConversionDateError? = null)

internal val conversionZone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
internal fun conversionDates(filters: ConversionFilters, now: Instant): Pair<ConversionQuery?, ConversionDateError?> {
    if (filters.fromDay.isBlank() || filters.throughDay.isBlank()) return null to ConversionDateError.INCOMPLETE
    if (!filters.fromDay.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) ||
        !filters.throughDay.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return null to ConversionDateError.FORMAT
    val start: LocalDate
    val through: LocalDate
    try {
        start = LocalDate.parse(filters.fromDay)
        through = LocalDate.parse(filters.throughDay)
    } catch (_: DateTimeParseException) { return null to ConversionDateError.FORMAT }
    val today = now.atZone(conversionZone).toLocalDate()
    val finish = if (through == today) now.atZone(conversionZone).toOffsetDateTime()
        else through.plusDays(1).atStartOfDay(conversionZone).toOffsetDateTime()
    val query = ConversionQuery(start.atStartOfDay(conversionZone).toOffsetDateTime(), finish)
    return query to query.validationError(now)?.let { ConversionDateError.valueOf(it.name) }
}
