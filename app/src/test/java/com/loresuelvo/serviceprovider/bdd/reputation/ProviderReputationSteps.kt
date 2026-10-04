package com.loresuelvo.serviceprovider.bdd.reputation

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.*
import com.loresuelvo.serviceprovider.ui.statistics.*
import io.cucumber.java.After
import io.cucumber.java.es.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderReputationSteps {
    private val scheduler = TestCoroutineScheduler()
    private val repository = ReputationTestRepository()
    private val store = ViewModelStore()
    private val vm: ProviderReputationViewModel
    private var expected = reputationFixture()
    init {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        vm = ProviderReputationViewModel(GetProviderReputationUseCase(repository), ActivityTestSessionStore())
        store.put("reputation", vm)
    }
    @After fun close() { store.clear(); scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun result() = (vm.uiState.value as ProviderReputationUiState.Ready).reputation
    private fun open() { vm.open(); scheduler.advanceUntilIdle(); assertEquals(1, repository.calls) }

    @Dado("que tengo 30 trabajos pagados y 24 recibieron una reseña")
    fun paidReviewedWork() { repository.outcome = ReputationOutcome.Success(expected) }
    @Cuando("abro Reputación dentro de Mi desempeño") fun openReputation() = open()
    @Entonces("veo mi calificación promedio y las 24 reseñas que la respaldan")
    fun ratingSample() { assertEquals(4.63, result().averageRating!!, 0.0); assertEquals(24L, result().reviewCount) }
    @Entonces("veo cuántas calificaciones recibí de cada cantidad de estrellas")
    fun distribution() { assertEquals(expected.ratingDistribution, result().ratingDistribution) }
    @Entonces("veo que 24 de mis 30 trabajos pagados tienen reseña, con una cobertura del 80 por ciento")
    fun coverage() {
        assertEquals(24L, result().reviewedPaidOrders); assertEquals(30L, result().eligiblePaidOrders)
        assertEquals(80.0, result().coveragePercentage!!, 0.0)
    }
    @Entonces("se aclara que la información corresponde a toda mi trayectoria")
    fun lifetime() {
        assertTrue(result().reviewCount > result().reviews.size)
        assertEquals(24L, result().ratingDistribution.sumOf { it.count })
        // The paired Compose test proves the lifetime label for this production result.
    }
    @Dado("que tengo {int} trabajos pagados y ninguno recibió una reseña")
    fun noReviews(work: Int) {
        expected = reputationFixture(work.toLong(), empty = true)
        repository.outcome = ReputationOutcome.Success(expected)
    }
    @Cuando("consulto mi reputación") fun consult() = open()
    @Entonces("veo que todavía no tengo calificaciones") fun noRating() { assertNull(result().averageRating) }
    @Entonces("veo cero reseñas y cero calificaciones de cada cantidad de estrellas")
    fun zeroRatings() {
        assertEquals(0L, result().reviewCount)
        assertEquals((1..5).map { RatingCount(it, 0) }, result().ratingDistribution)
    }
    @Entonces("la cobertura se muestra como {string}")
    fun emptyCoverage(label: String) {
        if (label == "no disponible") assertNull(result().coveragePercentage)
        else assertEquals(0.0, result().coveragePercentage!!, 0.0)
    }
    @Dado("que recibí calificaciones con y sin comentario escrito") fun comments() = paidReviewedWork()
    @Cuando("consulto mis reseñas") fun consultReviews() = open()
    @Entonces("veo el trabajo y la calificación correspondientes a cada reseña")
    fun workRatings() { assertEquals(expected.reviews.map { it.workOrderId to it.rating }, result().reviews.map { it.workOrderId to it.rating }) }
    @Entonces("veo el comentario solamente cuando fue escrito")
    fun optionalComments() { assertEquals(listOf("Stored client comment", ""), result().reviews.map { it.description }) }
    @Entonces("no se agregan nombres, fechas ni opiniones que no fueron informados")
    fun actualContent() { assertEquals(expected.reviews, result().reviews) }
    @Entonces("no se presentan como las reseñas más recientes")
    fun orderMeaning() {
        assertEquals(listOf(184, 179), result().reviews.map { it.workOrderId })
        // The Screen explicitly explains work-number order rather than recency.
    }
    private var retained = emptyList<ReceivedReview>()
    private var activity: ProviderActivityViewModel? = null
    private var transactions: CollectionTransactionsViewModel? = null
    private fun settle() = scheduler.advanceUntilIdle()
    private fun followingPage() = expected.copy(reviews = listOf(expected.reviews.last(),
        ReceivedReview(172, 5, "Following actual comment")), nextCursor = null)

    @Dado("que estoy leyendo mis reseñas y quedan otras por mostrar")
    fun readingWithContinuation() {
        open(); retained = result().reviews; vm.rememberReadingPosition(9, 37)
        repository.outcome = ReputationOutcome.Success(followingPage())
    }
    @Cuando("elijo cargar más reseñas") fun loadMore() { vm.loadMore(); settle() }
    @Entonces("se agregan las siguientes sin repetir trabajos")
    fun appendedWithoutDuplicates() {
        assertEquals(listOf(184, 179, 172), result().reviews.map { it.workOrderId })
        assertEquals(result().reviews.size, result().reviews.distinctBy { it.workOrderId }.size)
    }
    @Entonces("conservo las reseñas anteriores y mi posición de lectura")
    fun retainRowsAndReading() {
        assertTrue(result().reviews.containsAll(retained)); assertEquals(9, vm.readingIndex); assertEquals(37, vm.readingOffset)
    }
    @Entonces("los indicadores siguen representando toda mi trayectoria")
    fun globalIndicators() { assertEquals(24L, result().reviewCount); assertEquals(expected.ratingDistribution, result().ratingDistribution) }
    @Dado("que falló {string} y se informó el problema sin mostrar resultados inventados")
    fun failedQuery(query: String) {
        if (query == "la carga de más reseñas") {
            open(); retained = result().reviews; repository.outcome = ReputationOutcome.Failure.Network
            vm.loadMore(); settle()
            assertEquals(ReputationOutcome.Failure.Network, (vm.uiState.value as ProviderReputationUiState.Ready).failure)
            assertEquals(retained, result().reviews)
        } else {
            repository.outcome = ReputationOutcome.Failure.Network; open()
            assertEquals(ProviderReputationUiState.Error(ReputationOutcome.Failure.Network), vm.uiState.value)
        }
    }
    @Dado("la información vuelve a estar disponible")
    fun availableAgain() { repository.outcome = ReputationOutcome.Success(if (retained.isEmpty()) expected else followingPage()) }
    @Cuando("elijo reintentar") fun retry() { vm.retry(); settle() }
    @Entonces("puedo continuar {string}")
    fun continuedReading(reading: String) {
        if (reading == "desde las siguientes reseñas") {
            assertEquals("opaque+/cursor=", repository.cursors.last()); appendedWithoutDuplicates()
        } else { assertNull(repository.cursors.last()); assertEquals(expected.reviews, result().reviews) }
    }
    @Entonces("conservo la información válida que ya estaba leyendo")
    fun retainedValidInformation() { assertTrue(result().reviews.containsAll(retained)) }
    @Dado("que ya cargué varias reseñas y recibí una nueva calificación")
    fun newRatingAfterPages() {
        readingWithContinuation(); loadMore()
        expected = expected.copy(calculatedAt = java.time.Instant.parse("2026-10-04T12:00:00Z"),
            averageRating = 4.64, reviewCount = 25, reviewedPaidOrders = 25, coveragePercentage = 83.33,
            ratingDistribution = expected.ratingDistribution.map { if (it.rating == 5) it.copy(count = 18) else it },
            reviews = listOf(ReceivedReview(200, 5, "New actual rating")))
        repository.outcome = ReputationOutcome.Success(expected)
    }
    @Cuando("actualizo mi reputación") fun refresh() { vm.refresh(); settle() }
    @Entonces("veo los indicadores actualizados y las primeras reseñas de la nueva consulta")
    fun updatedFirstPage() { assertEquals(expected, result()); assertNull(repository.cursors.last()); assertEquals(0, vm.readingIndex) }
    @Entonces("no se mezclan con las reseñas cargadas anteriormente")
    fun noMixedOldRows() { assertEquals(listOf(200), result().reviews.map { it.workOrderId }) }
    @Entonces("veo cuándo se consultó la información")
    fun calculatedTimestamp() { assertEquals(java.time.Instant.parse("2026-10-04T12:00:00Z"), result().calculatedAt) }
    @Dado("que estaba leyendo mis reseñas y fui a otra sección de Desempeño")
    fun otherPerformanceSection() {
        readingWithContinuation(); loadMore()
        val sessions = ActivityTestSessionStore()
        activity = ProviderActivityViewModel(GetProviderActivityUseCase(ActivityTestRepository()), sessions,
            java.time.Clock.fixed(expected.calculatedAt, java.time.ZoneOffset.UTC)).also { store.put("activity", it) }
        activity!!.selectGranularity(ActivityGranularity.WEEK); activity!!.comparePrevious(true)
        transactions = CollectionTransactionsViewModel(GetCollectionTransactionsUseCase(object : CollectionTransactionsRepository {
            override suspend fun getTransactions(query: CollectionTransactionsQuery) = CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Network)
        }), sessions).also { store.put("transactions", it) }
        transactions!!.selectPeriod(activityFixture().period); transactions!!.selectPurpose(CollectionPurpose.BOOKING_DEPOSIT)
        settle()
    }
    @Cuando("vuelvo a Reputación") fun returnToReputation() { vm.open(); settle() }
    @Entonces("retomo mi posición de lectura") fun resumedPosition() { assertEquals(9, vm.readingIndex); assertEquals(37, vm.readingOffset) }
    @Entonces("Actividad y Cobros conservan sus propias opciones")
    fun independentOptions() {
        assertEquals(ActivityGranularity.WEEK, activity!!.query.granularity); assertTrue(activity!!.query.comparePrevious)
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, transactions!!.uiState.value.purpose)
    }
    @Entonces("Reputación sigue mostrando toda mi trayectoria sin pedir un período")
    fun noPeriodForReputation() { globalIndicators(); assertEquals(listOf(null, "opaque+/cursor="), repository.cursors) }

}
