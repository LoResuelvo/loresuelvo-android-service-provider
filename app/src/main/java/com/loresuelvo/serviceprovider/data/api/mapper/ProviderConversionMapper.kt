package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.*
import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun ProviderConversionDto.toDomain(): ProviderConversion {
    val from = utcInstant(period.from)
    val to = utcInstant(period.to)
    val observed = utcInstant(observedAt)
    require(period.timeZone == "America/Argentina/Buenos_Aires")
    require(from < to && to <= observed && Duration.between(from, to) <= Duration.ofDays(365))
    val stages = proposals.stages
    require(stages.issued >= stages.contracted && stages.contracted >= stages.reported &&
        stages.reported >= stages.paid && stages.paid >= 0)
    require(proposals.uncontracted == stages.issued - stages.contracted)
    require(requests.received >= 0 && requests.accepted >= 0 && requests.pending >= 0 &&
        requests.accepted <= requests.received && requests.pending == requests.received - requests.accepted)
    val rates = proposals.rates
    return ProviderConversion(ConversionPeriod(from, to, period.timeZone), observed,
        ProposalConversion(ProposalStages(stages.issued, stages.contracted, stages.reported, stages.paid),
            ProposalRates(rates.contracted.domain(stages.contracted, stages.issued, stages.issued),
                rates.reported.domain(stages.reported, stages.issued, stages.contracted),
                rates.paid.domain(stages.paid, stages.issued, stages.reported)), proposals.uncontracted),
        RequestAcceptance(requests.received, requests.accepted, requests.pending,
            requests.acceptanceRate.domain(requests.accepted, requests.received)))
}
private fun utcInstant(value: String): Instant = OffsetDateTime.parse(value).also {
    require(it.offset == ZoneOffset.UTC)
}.toInstant()
private fun ConversionStageRatesDto.domain(numerator: Long, cohortBase: Long, previousBase: Long) =
    ConversionStageRates(cohort.domain(numerator, cohortBase), previousStage.domain(numerator, previousBase))
private fun ConversionRatioDto.domain(expectedNumerator: Long, expectedDenominator: Long): ConversionRatio {
    require(numerator == expectedNumerator && denominator == expectedDenominator)
    require(numerator >= 0 && numerator <= denominator)
    require(if (denominator == 0L) percentage == null else
        percentage != null && percentage.isFinite() && percentage in 0.0..100.0)
    return ConversionRatio(numerator, denominator, percentage)
}
