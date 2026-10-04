package com.loresuelvo.serviceprovider.bdd.conversion

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase
import com.loresuelvo.serviceprovider.ui.statistics.*
import io.cucumber.java.After
import io.cucumber.java.es.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderConversionSteps {
    private val scheduler = TestCoroutineScheduler()
    private val repository = ConversionTestRepository()
    private val store = ViewModelStore()
    private val vm: ProviderConversionViewModel
    init {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        vm = ProviderConversionViewModel(GetProviderConversionUseCase(repository), ActivityTestSessionStore(),
            Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC))
        store.put("conversion", vm)
    }
    @After fun close() { store.clear(); scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun result() = (vm.uiState.value as ProviderConversionUiState.Ready).conversion
    private fun open() { vm.open(); scheduler.advanceUntilIdle(); assertEquals(1, repository.queries.size) }
    @Dado("que emití 20 propuestas en los últimos 30 días")
    fun issuedCohort() { repository.outcome = ConversionOutcome.Success(conversionFixture()) }
    @Dado("de esas propuestas 12 se contrataron, 9 tienen finalización informada y 8 se pagaron por completo")
    fun milestones() { repository.outcome = ConversionOutcome.Success(conversionFixture()) }
    @Cuando("abro Conversión de propuestas desde Actividad") fun openFromActivity() = open()
    @Entonces("veo las cuatro etapas con sus cantidades y porcentajes sobre las 20 propuestas")
    fun stages() {
        assertEquals(ProposalStages(20, 12, 9, 8), result().proposals.stages)
        assertEquals(listOf(60.0, 45.0, 40.0), with(result().proposals.rates) {
            listOf(contracted.cohort.percentage, reported.cohort.percentage, paid.cohort.percentage)
        })
        assertEquals(20L, result().proposals.rates.paid.cohort.denominator)
    }
    @Entonces("puedo consultar cuánto avanzó cada etapa respecto de la anterior y cuántas propuestas representa")
    fun advances() {
        vm.expandAdvances(true); assertTrue(vm.advancesExpanded.value)
        assertEquals(listOf(ConversionRatio(12, 20, 60.0), ConversionRatio(9, 12, 75.0), ConversionRatio(8, 9, 88.89)),
            with(result().proposals.rates) { listOf(contracted.previousStage, reported.previousStage, paid.previousStage) })
    }
    @Entonces("veo 8 propuestas sin contratación observada sin considerarlas rechazadas")
    fun uncontracted() { assertEquals(8L, result().proposals.uncontracted) }
    @Entonces("veo cuándo se consultó la información y que las propuestas todavía pueden avanzar")
    fun observation() {
        assertEquals(Instant.parse("2026-10-04T12:00:00.123456789Z"), result().observedAt)
        assertEquals("America/Argentina/Buenos_Aires", result().period.timeZone)
    }
    @Dado("que emití {int} propuestas en el período y ninguna se contrató")
    fun noAdvances(issued: Int) { repository.outcome = ConversionOutcome.Success(conversionFixture(issued.toLong(), true)) }
    @Cuando("consulto su conversión") fun consult() = open()
    @Entonces("veo {int} propuestas emitidas y cero en las etapas siguientes")
    fun emptyStages(issued: Int) { assertEquals(ProposalStages(issued.toLong(), 0, 0, 0), result().proposals.stages) }
    @Entonces("el porcentaje de contratación se muestra como {string}")
    fun contractingPercentage(label: String) {
        if (label == "no disponible") assertNull(result().proposals.rates.contracted.cohort.percentage)
        else assertEquals(0.0, result().proposals.rates.contracted.cohort.percentage!!, 0.0)
    }
    @Entonces("los avances entre etapas sin propuestas de partida figuran como no disponibles")
    fun unavailable() { assertNull(result().proposals.rates.reported.previousStage.percentage); assertNull(result().proposals.rates.paid.previousStage.percentage) }
    @Dado("que recibí 5 solicitudes en el período, acepté 3 y tengo 2 pendientes")
    fun requests() { repository.outcome = ConversionOutcome.Success(conversionFixture(0, true)) }
    @Dado("todavía no emití propuestas durante ese período") fun noProposals() = requests()
    @Cuando("consulto la conversión de mis propuestas") fun consultProposals() = open()
    @Entonces("sigo viendo mis 5 solicitudes, las 3 aceptadas y las 2 pendientes")
    fun requestCounts() { assertEquals(RequestAcceptance(5, 3, 2, ConversionRatio(3, 5, 60.0)), result().requests) }
    @Entonces("veo una aceptación del 60 por ciento, correspondiente a 3 de 5 solicitudes")
    fun acceptance() { assertEquals(ConversionRatio(3, 5, 60.0), result().requests.acceptanceRate) }
    @Entonces("las solicitudes aparecen separadas de las propuestas")
    fun separateCohorts() { assertEquals(0L, result().proposals.stages.issued); assertEquals(5L, result().requests.received) }
    @Entonces("aceptar una solicitud no se presenta como una contratación")
    fun notContracting() { assertEquals(3L, result().requests.accepted); assertEquals(0L, result().proposals.stages.contracted) }
}
