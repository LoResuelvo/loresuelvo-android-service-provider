package com.loresuelvo.serviceprovider.domain.usecase.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.ui.statistics.ConversionTestRepository
import java.time.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GetProviderConversionUseCaseTest {
    private val now = Instant.parse("2026-10-04T12:00:00.123456789Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    @Test fun `direct callers cannot send partial zero reversed future or over 365 elapsed day ranges`() = runTest {
        val repository = ConversionTestRepository()
        val useCase = GetProviderConversionUseCase(repository, clock)
        val end = now.atOffset(ZoneOffset.UTC)
        listOf(ConversionQuery(end, null), ConversionQuery(null, end), ConversionQuery(end, end),
            ConversionQuery(end.plusNanos(1), end), ConversionQuery(end.minusDays(1), end.plusNanos(1)),
            ConversionQuery(end.minusDays(365).minusNanos(1), end)).forEach {
            assertEquals(ConversionOutcome.Failure.InvalidQuery, useCase(it))
        }
        assertTrue(repository.queries.isEmpty())
    }
    @Test fun `omitted and precise positive offset 365 day and nanosecond ranges pass unchanged`() = runTest {
        val repository = ConversionTestRepository()
        val useCase = GetProviderConversionUseCase(repository, clock)
        val end = now.atOffset(ZoneOffset.ofHours(3))
        val queries = listOf(ConversionQuery(), ConversionQuery(end.minusDays(365), end),
            ConversionQuery(end.minusNanos(1), end))
        queries.forEach { assertTrue(useCase(it) is ConversionOutcome.Success) }
        assertEquals(queries, repository.queries)
    }
}
