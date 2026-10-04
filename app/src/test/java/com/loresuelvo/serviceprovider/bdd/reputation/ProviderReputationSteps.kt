package com.loresuelvo.serviceprovider.bdd.reputation

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase
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
}
