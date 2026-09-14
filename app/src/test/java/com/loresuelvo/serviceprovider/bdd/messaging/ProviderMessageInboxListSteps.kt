package com.loresuelvo.serviceprovider.bdd.messaging

import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

internal class ProviderMessageInboxListSteps {

    private lateinit var world: ProviderMessageInboxListWorld

    @Before
    fun setUp() {
        world = ProviderMessageInboxListWorld()
    }

    @After
    fun tearDown() = world.close()

    @Given("que la API devolverá conversaciones asociadas a la cuenta del prestador")
    fun apiReturnsProviderConversations() = world.configureConversations()

    @Given("que la API devolverá una conversación pendiente y una conversación activa")
    fun apiReturnsPendingAndActiveConversations() = world.configurePendingAndActiveConversations()

    @When("la bandeja termina de cargar")
    fun inboxFinishesLoading() = world.loadInbox()

    @When("la bandeja muestra ambas conversaciones")
    fun inboxShowsBothConversations() = world.loadInbox()

    @When("el prestador abre la bandeja de mensajes")
    fun providerOpensMessagesInbox() = world.openInboxWhileLoading()

    @Then("muestra todas las conversaciones en el orden recibido desde la API")
    fun inboxPreservesApiOrder() = world.assertApiOrder()

    @And("cada fila muestra el nombre completo del consumidor y su foto o sus iniciales como alternativa")
    fun rowsShowConsumerIdentity() = world.assertConsumerIdentity()

    @And("cada fila muestra el extracto disponible del último mensaje y una fecha u hora relativa")
    fun rowsShowPreviewAndTime() = world.assertPreviewAndTime()

    @Then("la conversación pendiente exhibe un distintivo Pendiente de aceptación")
    fun pendingConversationShowsBadge() = world.assertPendingConversation()

    @And("la conversación activa no exhibe ese distintivo")
    fun activeConversationHasNoBadge() = world.assertActiveConversation()

    @Then("muestra un estado vacío que explica que los mensajes aparecerán al recibir o aceptar solicitudes")
    fun inboxShowsEmptyState() = world.assertEmptyState()

    @And("no muestra una lista vacía ni un error")
    fun emptyStateHidesListAndError() = world.assertEmptyStateIsExclusive()

    @Given("que la API no devuelve conversaciones para la cuenta del prestador")
    fun apiReturnsNoConversations() = world.configureEmptyConversations()

    @Given("que la consulta de conversaciones permanece en curso")
    fun conversationsRequestRemainsInFlight() = world.configurePendingRequest()

    @Then("muestra un indicador de carga accesible hasta que la consulta finaliza")
    fun inboxShowsAccessibleLoading() = world.assertLoadingState()

    @And("no muestra simultáneamente contenido vacío, datos anteriores ni un error")
    fun loadingStateIsExclusive() = world.assertLoadingStateIsExclusive()
}
