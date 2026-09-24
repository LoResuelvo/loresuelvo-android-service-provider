package com.loresuelvo.serviceprovider.domain.usecase.proposal

import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalDraft
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import java.math.BigDecimal
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class ValidateServiceProposalUseCase @Inject constructor() {
    operator fun invoke(draft: ServiceProposalDraft, nowMillis: Long, zone: TimeZone): ProposalValidationOutcome {
        val errors = mutableSetOf<ProposalValidationError>()
        if (draft.consumerId <= 0) errors += ProposalValidationError.Consumer
        val amount = parseAmount(draft.amount) ?: run { errors += ProposalValidationError.Amount; null }
        val reason = draft.reason.trim().takeIf(String::isNotEmpty)
            ?: run { errors += ProposalValidationError.Reason; null }
        val duration = draft.duration.takeIf { it.matches(Regex("[0-9]+")) }
            ?.toIntOrNull()?.takeIf { it in 15..1440 }
            ?: run { errors += ProposalValidationError.Duration; null }
        val date = parseDate(draft.date)
        if (date == null) errors += ProposalValidationError.Date
        val time = parseTime(draft.time)
        if (time == null) errors += ProposalValidationError.Time
        var scheduled: Long? = null
        var offset: Int? = null
        if (date != null && time != null) {
            val localMillis = utcMillis(date[0], date[1], date[2], time[0], time[1])
            val offsets = listOf(-86_400_000L, 0L, 86_400_000L)
                .map { zone.getOffset(localMillis + it) / 60_000 }.distinct()
                .filter { candidate ->
                    val instant = localMillis - candidate * 60_000L
                    zone.getOffset(instant) / 60_000 == candidate &&
                        calendarFields(instant, zone) == date + time
                }.sorted()
            when {
                offsets.isEmpty() -> errors += ProposalValidationError.Time
                offsets.size > 1 && draft.selectedOffsetMinutes !in offsets ->
                    errors += ProposalValidationError.AmbiguousTime(offsets)
                draft.selectedOffsetMinutes != null && draft.selectedOffsetMinutes !in offsets ->
                    errors += ProposalValidationError.Time
                else -> {
                    offset = draft.selectedOffsetMinutes?.takeIf { it in offsets } ?: offsets.single()
                    scheduled = localMillis - offset * 60_000L
                    if (scheduled - nowMillis <= 86_400_000L) errors += ProposalValidationError.LeadTime
                }
            }
        }
        if (errors.isNotEmpty()) return ProposalValidationOutcome.Invalid(errors)
        return ProposalValidationOutcome.Valid(ValidatedServiceProposal(
            draft.consumerId, amount!!, scheduled!!, offset!!, reason!!, duration!!,
        ))
    }

    private fun parseAmount(input: String): String? {
        if (!input.matches(Regex("[0-9]+(?:[.,][0-9]{1,2})?"))) return null
        val amount = BigDecimal(input.replace(',', '.'))
        if (amount.signum() <= 0) return null
        try { amount.movePointRight(2).longValueExact() } catch (_: ArithmeticException) { return null }
        return amount.stripTrailingZeros().toPlainString()
    }

    private fun parseDate(input: String): List<Int>? {
        if (!input.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return null
        val fields = input.split('-').map(String::toInt)
        if (fields[0] !in 1..9999 || fields[1] !in 1..12 || fields[2] !in 1..31) return null
        return fields.takeIf {
            runCatching { utcMillis(it[0], it[1], it[2], 0, 0) }.isSuccess
        }
    }

    private fun parseTime(input: String): List<Int>? {
        if (!input.matches(Regex("[0-9]{2}:[0-9]{2}"))) return null
        return input.split(':').map(String::toInt).takeIf { it[0] in 0..23 && it[1] in 0..59 }
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            isLenient = false
            clear(); set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun calendarFields(instant: Long, zone: TimeZone): List<Int> =
        GregorianCalendar(zone, Locale.ROOT).apply { isLenient = false; timeInMillis = instant }.let {
            listOf(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH),
                it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))
        }
}
