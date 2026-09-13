package com.loresuelvo.serviceprovider.bdd.home

import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

internal class ProviderHomeSteps {

    private val world = ProviderHomeWorld()

    @After
    fun tearDown() = world.close()

    @Given("que existe una sesión local vigente y la API devolverá un perfil completo con rol provider")
    fun sessionWillResolveProvider() = world.configureProviderProfileWithLoading()

    @When("el prestador abre la aplicación")
    fun providerOpensApplication() = world.openApplication()

    @Then("la app muestra un estado de carga hasta resolver el perfil autenticado")
    fun appShowsLoadingUntilProfileResolution() = world.assertLoadingThenHome()

    @And("navega directamente a Home sin mostrar Welcome ni el registro profesional momentáneamente")
    fun appNavigatesDirectlyToHome() = world.assertLoadingThenHome()

    @Given("que la API devuelve el nombre, apellido, rubro y foto del prestador autenticado")
    fun apiReturnsProviderIdentity() = world.configureProviderProfile()

    @When("la app muestra Home")
    fun appShowsHome() = world.openHomeActivity()

    @Then("Home muestra el nombre completo, el rubro y la foto del prestador")
    fun homeShowsProviderIdentity() = world.assertProviderIdentity()

    @And("la foto tiene una alternativa accesible con las iniciales si no puede cargarse")
    fun photoHasInitialsFallback() = world.assertInitialsFallbackIsSupported()

    @Given("que existe una sesión local pero la API responde 404 al consultar el perfil autenticado")
    fun apiReturnsMissingProfile() = world.configureMissingProfile()

    @When("la app resuelve el destino privado inicial")
    fun appResolvesPrivateDestination() = world.resolvePrivateDestination()

    @Then("navega al registro profesional sin mostrar Home")
    fun appNavigatesToProfessionalRegistration() = world.assertIncompleteProfile()

    @And("conserva la sesión para completar el registro")
    fun appKeepsSessionForRegistration() = world.assertSessionRetained()

    @Given("que existe una sesión local y la API devuelve un perfil con rol consumer")
    fun apiReturnsConsumerAccount() = world.configureConsumerAccount()

    @Then("impide el acceso a Home y muestra un mensaje amigable indicando que la cuenta no corresponde a un prestador")
    fun appShowsAccountMismatch() = world.assertAccountMismatch()

    @And("ofrece volver a Welcome cerrando la sesión local")
    fun appOffersWelcomeAfterClearingSession() = world.returnToWelcome()

    @Given("que existe una sesión local pero la API rechaza el token con 401")
    fun apiRejectsToken() = world.configureExpiredSession()

    @Then("elimina la sesión local y muestra Welcome")
    fun appClearsSessionAndShowsWelcome() = world.assertExpiredSessionWelcome()

    @And("ninguna pantalla privada permanece accesible mediante Atrás")
    fun noPrivateScreenRemainsOnBack() = world.assertExpiredSessionWelcome()

    @Given("que existe una sesión local y la consulta del perfil falla por red o por un error 5xx")
    fun profileQueryFailsTemporarily() = world.configureTemporaryFailure()

    @Then("muestra un mensaje amigable con una acción para reintentar")
    fun appShowsRetryableEntryError() = world.assertRetryableEntryError()

    @And("conserva la sesión sin mostrar Welcome, el registro profesional ni Home")
    fun appKeepsSessionWithoutPrivateDestination() = world.assertRetryableEntryError()

    @Given("que la recuperación del perfil falló temporalmente y la siguiente consulta devolverá un prestador completo")
    fun nextProfileQueryWillSucceed() {
        world.configureRetryableProfileRecovery()
        world.resolvePrivateDestination()
    }

    @When("el prestador selecciona Reintentar")
    fun providerRetriesProfileResolution() = world.retryEntry()

    @Then("la app consulta nuevamente el perfil autenticado y muestra Home")
    fun appRetriesAndShowsHome() = world.assertEntryRetryShowsHome()

    @And("no solicita una nueva autenticación")
    fun appDoesNotRequestAuthentication() = world.assertSessionRetained()

    @Given("que el registro profesional finalizó y el prestador completó o decidió omitir el paso opcional de Mercado Pago")
    fun onboardingFinishedBeforeHome() = world.configureOnboardingContinuation()

    @When("el prestador continúa a Home")
    fun providerContinuesToHome() = world.refreshAfterOnboarding()

    @Then("la app vuelve a consultar el perfil autenticado antes de mostrar Home")
    fun appRefreshesProfileBeforeHome() = world.assertOnboardingRefreshShowsHome()

    @And("el registro profesional y Mercado Pago no quedan accesibles mediante Atrás")
    fun onboardingDestinationsAreReplaced() = world.assertOnboardingRefreshShowsHome()

    @Given("que la API devuelve solicitudes pendientes y trabajos agendados del prestador autenticado")
    fun apiReturnsProviderActivity() = world.configureActivityData()

    @When("la app termina de cargar la actividad de Home")
    fun appFinishesLoadingActivity() = world.openHomeActivity()

    @Then("cada solicitud muestra el consumidor, el título y la descripción disponibles")
    fun requestsShowAvailableFields() = world.assertActivityLoaded()

    @And("cada trabajo muestra el consumidor, la descripción y la fecha y hora programadas")
    fun workShowsAvailableFields() = world.assertActivityLoaded()

    @And("el resumen muestra las cantidades reales de solicitudes pendientes y trabajos agendados")
    fun activitySummaryUsesRealCounts() = world.assertActivityLoaded()

    @And("Home ofrece accesos visibles a Solicitudes, Trabajos agendados y Mercado Pago")
    fun homeOffersActivityActions() = world.assertActivityActionsVisible()

    @Given("que la API no devuelve solicitudes pendientes ni trabajos agendados para el prestador")
    fun apiReturnsEmptyActivity() = world.configureEmptyActivity()

    @Then("muestra estados vacíos claros para Solicitudes y Trabajos agendados")
    fun appShowsEmptyActivityStates() = world.assertActivityEmpty()

    @And("muestra ambas cantidades en cero sin presentar un error ni inventar actividad")
    fun appShowsZeroActivityCounts() = world.assertActivityEmpty()

    @Given("que una sección de actividad falló temporalmente y la siguiente consulta devolverá datos")
    fun activitySectionWillRecover() = world.configureActivityRetry()

    @When("el prestador reintenta esa sección desde Home")
    fun providerRetriesActivitySection() {
        world.openHomeActivity()
        world.retryJobRequests()
    }

    @Then("la app actualiza la sección con la respuesta más reciente")
    fun appUpdatesActivitySection() = world.assertActivityRetrySucceeded()

    @And("conserva la identidad, la sesión y las demás secciones disponibles")
    fun appPreservesOtherActivityState() = world.assertActivityRetrySucceeded()

    @Given("que la app ya resolvió Home para una sesión vigente")
    fun appAlreadyResolvedHome() {
        world.configureProviderProfile()
        world.openApplication()
    }

    @When("la actividad rota o se recrea el proceso")
    fun activityIsRecreated() = world.recreateApplication()

    @Then("la app conserva un único destino Home y un estado de navegación coherente")
    fun appKeepsSingleHomeDestination() = world.assertSingleHomeAfterRecreation()

    @And("no agrega Welcome, el registro profesional ni otra Home al historial")
    fun appDoesNotAddDuplicateDestinations() = world.assertSingleHomeAfterRecreation()
}
