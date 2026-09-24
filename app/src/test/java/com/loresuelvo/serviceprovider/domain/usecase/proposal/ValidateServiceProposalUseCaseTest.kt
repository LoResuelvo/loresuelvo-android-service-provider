package com.loresuelvo.serviceprovider.domain.usecase.proposal

import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ValidateServiceProposalUseCaseTest {
    private val validate = ValidateServiceProposalUseCase()
    private val utc = TimeZone.getTimeZone("UTC")
    private val base = ServiceProposalDraft(7, "123,45", "2026-10-01", "10:00", "  Inspect sink  ", "45")
    private val now = epoch("2026-09-29", "10:00", utc)

    @Test fun validDraftNormalizesAmountAndReason() {
        val result = validate(base, now, utc) as ProposalValidationOutcome.Valid
        assertEquals("123.45", result.proposal.amountPesos)
        assertEquals("Inspect sink", result.proposal.reason)
        assertEquals(7, result.proposal.consumerId)
    }

    @Test fun invalidFieldsAreIndependent() {
        val invalid = base.copy(consumerId = 0, amount = "0", date = "2026-02-30",
            time = "25:00", reason = " \t ", duration = "14")
        assertEquals(setOf(ProposalValidationError.Consumer, ProposalValidationError.Amount,
            ProposalValidationError.Date, ProposalValidationError.Time,
            ProposalValidationError.Reason, ProposalValidationError.Duration), errors(invalid))
    }

    @Test fun eachMissingRequiredFieldReportsItsOwnError() {
        assertTrue(ProposalValidationError.Amount in errors(base.copy(amount = "")))
        assertTrue(ProposalValidationError.Date in errors(base.copy(date = "")))
        assertTrue(ProposalValidationError.Time in errors(base.copy(time = "")))
        assertTrue(ProposalValidationError.Reason in errors(base.copy(reason = " ")))
        assertTrue(ProposalValidationError.Duration in errors(base.copy(duration = "")))
    }

    @Test fun amountRejectsMalformedNegativeFractionAndOverflow() {
        listOf("", "0", "-1", "1.234", "1,234", "1.2.3", "1,2.3", "1e3", "92233720368547759")
            .forEach { assertTrue("amount=$it", ProposalValidationError.Amount in errors(base.copy(amount = it))) }
        val maximum = validate(base.copy(amount = "92233720368547758,07"), now, utc)
            as ProposalValidationOutcome.Valid
        assertEquals("92233720368547758.07", maximum.proposal.amountPesos)
        assertTrue(ProposalValidationError.Amount in errors(base.copy(amount = "92233720368547758.08")))
    }

    @Test fun durationRequiresWholeMinutesWithinRange() {
        listOf("", "14", "1441", "15.5", "15,5", "-15", "+15")
            .forEach { assertTrue("duration=$it", ProposalValidationError.Duration in errors(base.copy(duration = it))) }
        listOf("15", "1440").forEach {
            assertTrue(ProposalValidationError.Duration !in errors(base.copy(duration = it)))
        }
    }

    @Test fun leadTimeIsStrictlyGreaterThanTwentyFourHours() {
        val scheduled = epoch("2026-10-01", "10:00", utc)
        assertTrue(ProposalValidationError.LeadTime in errors(base, scheduled - 86_400_000L))
        assertTrue(ProposalValidationError.LeadTime in errors(base, scheduled - 86_399_999L))
        assertTrue(ProposalValidationError.LeadTime !in errors(base, scheduled - 86_400_001L))
    }

    @Test fun calendarUsesCapturedZoneAndRejectsDstGap() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val draft = base.copy(date = "2026-03-08", time = "02:30")
        assertTrue(ProposalValidationError.Time in errors(draft, epoch("2026-03-01", "10:00", utc), zone))
        val rollover = base.copy(date = "2026-10-01", time = "00:30")
        val result = validate(rollover, now, zone) as ProposalValidationOutcome.Valid
        assertEquals(epoch("2026-10-01", "04:30", utc), result.proposal.scheduledEpochMillis)
    }

    @Test fun dstOverlapRequiresExplicitOffset() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val draft = base.copy(date = "2026-11-01", time = "01:30")
        val error = errors(draft, now, zone).single() as ProposalValidationError.AmbiguousTime
        assertEquals(listOf(-300, -240), error.offsetsMinutes)
        val first = validate(draft.copy(selectedOffsetMinutes = -240), now, zone) as ProposalValidationOutcome.Valid
        val second = validate(draft.copy(selectedOffsetMinutes = -300), now, zone) as ProposalValidationOutcome.Valid
        assertEquals(3_600_000L, second.proposal.scheduledEpochMillis - first.proposal.scheduledEpochMillis)
    }

    @Test fun halfHourDstGapAndOverlapAreHandledWithoutAssumingOneHour() {
        val zone = TimeZone.getTimeZone("Australia/Lord_Howe")
        assertTrue(ProposalValidationError.Time in errors(
            base.copy(date = "2026-10-04", time = "02:15"), now, zone))
        val draft = base.copy(date = "2027-04-04", time = "01:45")
        val overlap = errors(draft, now, zone).single() as ProposalValidationError.AmbiguousTime
        assertEquals(listOf(630, 660), overlap.offsetsMinutes)
        val earlier = validate(draft.copy(selectedOffsetMinutes = 660), now, zone) as ProposalValidationOutcome.Valid
        val later = validate(draft.copy(selectedOffsetMinutes = 630), now, zone) as ProposalValidationOutcome.Valid
        assertEquals(1_800_000L, later.proposal.scheduledEpochMillis - earlier.proposal.scheduledEpochMillis)
    }

    private fun errors(draft: ServiceProposalDraft, at: Long = now, zone: TimeZone = utc) =
        when (val result = validate(draft, at, zone)) {
            is ProposalValidationOutcome.Invalid -> result.errors
            is ProposalValidationOutcome.Valid -> emptySet()
        }

    private fun epoch(date: String, time: String, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            clear()
            val d = date.split('-').map(String::toInt)
            val t = time.split(':').map(String::toInt)
            set(d[0], d[1] - 1, d[2], t[0], t[1])
        }.timeInMillis
}
