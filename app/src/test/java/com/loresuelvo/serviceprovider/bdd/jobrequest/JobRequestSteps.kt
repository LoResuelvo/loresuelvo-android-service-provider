package com.loresuelvo.serviceprovider.bdd.jobrequest

import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

internal class JobRequestSteps {

    private val world = JobRequestWorld()

    @After
    fun tearDown() = world.close()

    @Given("que Home muestra una solicitud de trabajo pendiente para el prestador")
    fun homeShowsPendingRequest() = world.givenPendingRequest()

    @Given("que el prestador está viendo una solicitud pendiente")
    fun providerSeesPendingRequest() = world.givenOpenedPendingRequest()

    @Given("que el prestador está viendo el detalle de una solicitud pendiente")
    fun providerSeesRequestDetail() = world.givenOpenedPendingRequest()

    @When("el prestador selecciona Ver solicitud")
    fun providerOpensRequest() = world.openDetail()

    @Then("la app muestra el nombre completo del consumidor, el título y la descripción completa de la solicitud")
    fun appShowsRequestDetails() = world.assertRequestDetails()

    @And("ofrece Continuar conversación")
    fun appOffersContinue() = world.assertAcceptAvailable()

    @Given("que el detalle de la solicitud incluye imágenes de contexto")
    fun detailIncludesImages() = world.givenRequestWithImages()

    @When("el prestador selecciona una miniatura")
    fun providerSelectsThumbnail() = world.selectThumbnail()

    @Then("la app muestra esa imagen en pantalla completa con una descripción accesible")
    fun appShowsFullScreenImage() = world.assertImageViewer()

    @And("permite cerrarla para volver al mismo detalle sin responder la solicitud")
    fun appClosesImageViewer() = world.closeImageViewerAndAssertDetail()

    @Given("que el detalle de la solicitud no incluye imágenes de contexto")
    fun detailHasNoImages() = world.givenRequestWithoutImages()

    @When("el prestador abre el detalle")
    fun providerOpensDetail() = world.openDetail()

    @Then("la app muestra todos los demás datos disponibles de la solicitud")
    fun appShowsAvailableRequestData() = world.assertRequestDetails()

    @And("no muestra una galería vacía ni una acción para abrir imágenes")
    fun appDoesNotShowEmptyGallery() = world.assertNoImageGallery()

    @When("selecciona Continuar conversación")
    fun providerSelectsContinue() = world.selectContinue()

    @When("el prestador selecciona Continuar conversación")
    fun providerSelectsContinueFromDetail() = world.selectContinue()

    @Then("la app muestra progreso y bloquea la acción mientras envía una única aceptación")
    fun appShowsAcceptanceProgress() = world.assertAcceptanceProgress()

    @And("la API confirma la solicitud con estado accepted")
    fun apiConfirmsAcceptance() = world.assertAcceptanceConfirmed()

    @And("la solicitud aceptada deja de aparecer entre las pendientes de Home")
    fun acceptedRequestLeavesHome() = world.assertAcceptedRequestRemovedFromHome()

    @Given("que la aceptación falló por red o por un error del servidor")
    fun acceptanceFailedTemporarily() = world.givenTemporaryAcceptanceFailure()

    @When("el prestador selecciona Reintentar")
    fun providerRetriesAcceptance() = world.retryAcceptance()

    @Then("la app envía nuevamente una sola aceptación")
    fun appRetriesAcceptanceOnce() = world.assertAcceptanceRetriedOnce()

    @And("conserva visibles los datos de la solicitud hasta recibir confirmación")
    fun appKeepsRequestData() = world.assertRequestDataVisible()

    @Given("que la API rechaza la aceptación porque la solicitud ya no está pendiente")
    fun acceptanceIsStale() = world.givenStaleAcceptance()

    @Then("la app informa que la solicitud ya no está disponible")
    fun appInformsRequestUnavailable() = world.assertRequestUnavailable()

    @And("no presenta la respuesta como exitosa")
    fun appDoesNotPresentSuccess() = world.assertNoAcceptanceSuccess()

    @Given("que la API aceptó la solicitud y activó la conversación vinculada")
    fun apiAcceptedConversation() = world.givenAcceptedConversation()

    @When("la app procesa la confirmación de aceptación")
    fun appProcessesAcceptanceConfirmation() = world.processAcceptanceConfirmation()

    @Then("navega inmediatamente a la ruta de conversación identificada por conversation_id")
    fun appNavigatesToConversation() = world.assertConversationPath()

    @And("la ruta muestra un destino temporal hasta que la US de chat entregue la conversación real")
    fun appShowsTemporaryConversationDestination() = world.assertTemporaryConversationDestination()

    @And("Atrás no vuelve al detalle de una solicitud ya respondida")
    fun backDoesNotReturnToRequestDetail() = world.assertDetailRemovedFromBackStack()

    @When("cierra el detalle o utiliza Atrás")
    fun providerClosesDetail() = world.closeDetail()

    @Then("regresa al mismo Home sin enviar una aceptación")
    fun appReturnsToHomeWithoutAcceptance() = world.assertHomeWithoutAcceptance()

    @And("la solicitud continúa visible entre las pendientes")
    fun requestRemainsPending() = world.assertRequestRemainsPending()

    @Given("que el prestador abrió una solicitud pendiente y todavía no la respondió")
    fun providerOpenedPendingRequest() = world.givenOpenedPendingRequest()

    @When("la actividad se recrea")
    fun activityIsRecreated() = world.recreateDetail()

    @Then("la app recupera el detalle usando el identificador de la solicitud")
    fun appRecoversDetail() = world.assertRecreatedDetail()

    @And("no repite una aceptación ni una navegación anterior")
    fun appDoesNotReplayAcceptance() = world.assertNoAcceptanceReplay()
}
