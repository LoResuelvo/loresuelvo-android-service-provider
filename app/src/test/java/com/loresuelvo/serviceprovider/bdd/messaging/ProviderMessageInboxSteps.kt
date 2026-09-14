package com.loresuelvo.serviceprovider.bdd.messaging

import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

internal class ProviderMessageInboxSteps {

    private val world = ProviderMessageInboxWorld()

    @After
    fun tearDown() = world.reset()

    @Given("que el prestador autenticado se encuentra en Home")
    fun providerIsOnHome() = world.openHome()

    @When("selecciona Mensajes en la barra de navegación inferior")
    fun providerSelectsMessages() = world.selectMessages()

    @Then("la app muestra la bandeja de mensajes y marca Mensajes como destino seleccionado")
    fun appShowsMessagesAsSelected() = world.assertMessagesSelected()

    @And("la barra permanece disponible en Home y Mensajes sin mostrarse en destinos de detalle o autenticación")
    fun bottomBarVisibilityFollowsTopLevelRoutes() = world.assertBottomBarVisibility()

    @Given("que la bandeja muestra una conversación con un identificador válido")
    fun inboxShowsConversationWithValidId() = world.openMessagesWithConversation()

    @When("el prestador selecciona esa conversación")
    fun providerSelectsConversation() = world.selectConversation()

    @Then("la app navega una sola vez a la ruta de conversación identificada por conversation_id")
    fun appNavigatesToConversationRoute() = world.assertConversationRoute()

    @And("al volver regresa a la bandeja de mensajes conservada")
    fun backReturnsToRetainedInbox() = world.assertBackToInbox()
}
