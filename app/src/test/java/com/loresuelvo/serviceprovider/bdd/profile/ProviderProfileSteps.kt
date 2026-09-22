package com.loresuelvo.serviceprovider.bdd.profile

import com.loresuelvo.serviceprovider.bdd.paymentaccount.ConnectMercadoPagoWorld
import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

internal class ProviderProfileSteps {

    private val world = ProviderProfileWorld()
    private var paymentWorld: ConnectMercadoPagoWorld? = null

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

    @Given("que la consulta de mis datos {string}")
    fun accountQueryHasSituation(situation: String) = world.configureAccountSituation(situation)

    @When("abro Perfil")
    fun providerOpensProfile() = world.openProfile()

    @Then("veo {string}")
    fun profileShowsAccountResult(result: String) = world.assertAccountResult(result)

    @And("las acciones de conexión no están disponibles todavía")
    fun connectionActionsAreUnavailable() = world.assertConnectionActionsUnavailable()

    @Given("que no se pudieron cargar mis datos y veo la opción de reintentar")
    fun profileLoadFailedWithRetry() = world.configureFailedProfileWithRetry()

    @And("el servicio vuelve a estar disponible")
    fun accountServiceReturns() = world.restoreAccountService()

    @When("selecciono Reintentar")
    fun providerRetriesProfile() = world.selectRetry()

    @Then("veo mis datos actualizados")
    fun profileShowsUpdatedData() = world.assertUpdatedProvider()

    @And("desaparece el error")
    fun profileErrorDisappears() = world.assertErrorGone()

    @Given("que mi sesión venció")
    fun providerSessionExpired() = world.expireSessionAtAccountLookup()

    @When("intento consultar Perfil")
    fun providerConsultsExpiredProfile() = world.openProfile()

    @Then("se me informa que debo volver a iniciar sesión")
    fun providerMustSignInAgain() = world.assertReLoginRequired()

    @And("mis datos privados dejan de estar visibles")
    fun privateProfileDataDisappears() = world.assertPrivateDataGone()

    @Given("que el servicio informa mi estado de identidad")
    fun serviceReportsIdentityStatus() = world.configureIdentityStatus()

    @When("consulto Perfil")
    fun providerConsultsProfile() = world.openProfile()

    @Then("veo ese estado de identidad")
    fun profileShowsIdentityStatus() = world.assertIdentityStatus()

    @And("veo la fecha de aprobación si existe")
    fun profileShowsApprovalDate() = world.assertIdentityApprovalDate()

    @And("no hay una acción para iniciar o reintentar la identificación")
    fun profileHasNoIdentityAction() = world.assertNoIdentityAction()

    @Given("que todavía no verifiqué mi identidad ni conecté Mercado Pago")
    fun providerHasPendingConnections() = world.configurePendingConnections()

    @Then("Mercado Pago aparece pendiente y puedo abrir su flujo de conexión")
    fun profileShowsPendingPaymentConnection() = world.assertPendingPaymentConnection()

    @And("Google Calendar aparece como {string} sin una acción de conexión")
    fun calendarIsComingSoon(label: String) {
        require(label == "Próximamente")
        world.assertCalendarDoesNotBlockProfile()
    }

    @And("puedo seguir usando Inicio y Mensajes")
    fun primaryTabsRemainAvailable() = world.assertPrimaryTabs()

    @Given("que abrí el flujo de conexión de Mercado Pago desde Perfil")
    fun connectionFlowOpenedFromProfile() {
        world.openProfile()
        world.assertPendingPaymentConnection()
        payment().arrangeProviderCanConnect()
    }

    @And("mi cuenta de Mercado Pago todavía no está conectada")
    fun paymentAccountIsNotConnected() = payment().assertAccountStatusPendingDisplayed()

    @When("selecciono Conectar con Mercado Pago")
    fun providerStartsPaymentAuthorization() = payment().selectConnectMercadoPago()

    @Then("se abre la autorización oficial en el navegador")
    fun officialAuthorizationOpens() = payment().assertAuthorizationUrlOpenedInBrowser()

    @And("la app no me solicita credenciales de Mercado Pago")
    fun applicationDoesNotAskForPaymentCredentials() =
        payment().assertNoCredentialsRequestedWithinApp()

    @After
    fun tearDown() {
        paymentWorld?.close()
        world.close()
    }

    private fun payment(): ConnectMercadoPagoWorld =
        paymentWorld ?: ConnectMercadoPagoWorld().also { paymentWorld = it }
}
