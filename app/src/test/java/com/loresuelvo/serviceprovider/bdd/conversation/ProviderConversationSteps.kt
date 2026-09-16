package com.loresuelvo.serviceprovider.bdd.conversation

import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

internal class ProviderConversationSteps {

    private lateinit var world: ProviderConversationWorld

    @Before
    fun setUp() {
        world = ProviderConversationWorld()
    }

    @After
    fun tearDown() = world.close()

    @Given("que la API devuelve el detalle de la conversación {int} con {int} mensajes \\(1 del consumidor, 2 del prestador\\)")
    fun apiReturnsDetailWithExplicitMixed(
        @Suppress("UNUSED_PARAMETER") conversationId: Int,
        @Suppress("UNUSED_PARAMETER") totalCount: Int,
    ) {
        // The Gherkin step writes a human-friendly description; the
        // numeric {int} is the total. We split 1/2 for the consumer
        // / provider tally to match scenario 01-PCC.
        world.givenDetailWithMessages(consumerCount = 1, providerCount = 2)
    }

    @Given("que la API devuelve el detalle de la conversación {int} con {int} mensajes")
    fun apiReturnsDetailWithMessages(
        @Suppress("UNUSED_PARAMETER") conversationId: Int,
        totalCount: Int,
    ) {
        // The single parametric matcher covers scenarios that
        // don't break down the consumer / provider split in the
        // Gherkin prose. Total 0 ⇒ empty detail (scenario 02-PCC).
        if (totalCount == 0) {
            world.givenEmptyDetail()
        } else {
            // Default split: half consumer, half provider. Each
            // scenario can pass the more specific step above when
            // the split matters for the assertion (01-PCC uses
            // the explicit 1/2 step).
            val consumer = totalCount / 2
            val provider = totalCount - consumer
            world.givenDetailWithMessages(
                consumerCount = consumer,
                providerCount = provider,
            )
        }
    }

    @When("el prestador navega a a la ruta de la conversación {int}")
    fun providerOpensConversationWithExtraA(
        @Suppress("UNUSED_PARAMETER") conversationId: Int,
    ) = world.whenOpeningConversation()

    @When("el prestador navega a la ruta de la conversación {int}")
    fun providerOpensConversation(@Suppress("UNUSED_PARAMETER") conversationId: Int) =
        world.whenOpeningConversation()

    @Then("la pantalla muestra los {int} mensajes en orden cronológico con el remitente y el avatar correctos")
    fun screenShowsMessagesInOrder(expectedCount: Int) {
        world.thenReadyStateRendersAllMessagesInOrder(
            expectedConsumerCount = expectedCount / 2,
            expectedProviderCount = expectedCount - (expectedCount / 2),
        )
    }

    @And("el header exhibe el nombre completo del consumidor como título")
    fun headerShowsCounterpartName() {
        world.thenHeaderShowsCounterpartFullName()
    }

    @And("el input bar está vacío y habilitado")
    fun inputBarIsEmptyAndEnabled() {
        world.thenInputBarIsEmptyAndEnabled()
    }

    @Then("la pantalla no muestra burbujas ni un estado de error")
    fun screenShowsNoBubblesAndNoError() {
        world.thenNoBubblesAndNoError()
    }
}
