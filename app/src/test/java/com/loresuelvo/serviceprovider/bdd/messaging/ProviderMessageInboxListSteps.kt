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

    @When("la bandeja termina de cargar")
    fun inboxFinishesLoading() = world.loadInbox()

    @Then("muestra todas las conversaciones en el orden recibido desde la API")
    fun inboxPreservesApiOrder() = world.assertApiOrder()

    @And("cada fila muestra el nombre completo del consumidor y su foto o sus iniciales como alternativa")
    fun rowsShowConsumerIdentity() = world.assertConsumerIdentity()

    @And("cada fila muestra el extracto disponible del último mensaje y una fecha u hora relativa")
    fun rowsShowPreviewAndTime() = world.assertPreviewAndTime()
}
