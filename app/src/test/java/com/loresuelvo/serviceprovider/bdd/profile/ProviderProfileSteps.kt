package com.loresuelvo.serviceprovider.bdd.profile

import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

internal class ProviderProfileSteps {

    private val world = ProviderProfileWorld()

    @Given("que inicié sesión como prestador con mi perfil profesional completo")
    fun authenticatedProviderHasCompleteProfile() = world.configureAuthenticatedProvider()

    @Given("que estoy en {string}")
    fun providerIsOnTab(tab: String) = world.openTab(tab)

    @When("selecciono Perfil")
    fun providerSelectsProfile() = world.selectProfile()

    @Then("veo mi nombre, apellido, correo y rubro")
    fun profileShowsProviderIdentity() = world.assertProviderIdentity()

    @And("veo mi foto o un avatar alternativo si no está disponible")
    fun profileShowsPhotoOrAvatar() = world.assertPhotoOrAvatar()

    @And("Perfil queda seleccionado junto a Inicio y Mensajes")
    fun profileIsSelectedWithPrimaryTabs() = world.assertPrimaryTabs()

    @After
    fun tearDown() = world.close()
}
