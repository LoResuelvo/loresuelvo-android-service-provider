package com.loresuelvo.serviceprovider.bdd.coverage

import com.loresuelvo.serviceprovider.bdd.profile.CompleteProviderProfileWorld
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZone
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

class ProviderCoverageZonesSteps {
    private lateinit var world: CompleteProviderProfileWorld

    @Before
    fun setUp() {
        world = CompleteProviderProfileWorld()
    }

    @After
    fun tearDown() = world.close()

    @Dado("un prestador autenticado sin perfil completo y una carga de zonas pendiente")
    fun pendingCoverageLoad() {
        world.seedAuthenticatedSession()
        world.configurePendingCoverageZones()
    }

    @Cuando("el prestador abre el formulario de perfil")
    fun openProfileForm() = world.navigateToProfileDestination()

    @Entonces("la sección de cobertura muestra el progreso de carga")
    fun coverageLoadingIsVisible() = world.assertCoverageZonesLoading()

    @Y("ninguna zona queda seleccionada implícitamente")
    fun noZoneIsImplicitlySelected() = world.assertNoCoverageZoneSelected()

    @Y("el registro no puede enviarse hasta que el catálogo esté disponible")
    fun registrationIsBlocked() = world.assertRegistrationBlockedWhileCoverageLoads()

    @Y("los demás campos del perfil permanecen disponibles")
    fun profileFieldsRemainEditable() = world.assertProfileFieldsRemainEditable()

    @Dado("que la API devolverá zonas disponibles con identificadores y nombres legibles")
    fun availableCoverageZones() {
        world.configureCoverageZones(
            listOf(
                CoverageZone(14, "Comuna 14", "place-14"),
                CoverageZone(6, "Comuna 6", "place-6"),
            ),
        )
        world.configurePendingCoverageZones()
        world.navigateToProfileDestination()
    }

    @Cuando("finaliza la carga del catálogo de zonas")
    fun finishCoverageLoad() = world.finishCoverageLoad()

    @Entonces("cada nombre disponible aparece una sola vez en el orden del servidor")
    fun namesAppearInServerOrder() =
        world.assertCoverageZoneNamesInOrder(listOf("Comuna 14", "Comuna 6"))

    @Y("los nombres se muestran en lugar de los identificadores o referencias del mapa")
    fun readableNamesAreExposed() =
        world.assertCoverageZoneNamesInOrder(listOf("Comuna 14", "Comuna 6"))

    @Y("ninguna zona queda seleccionada inicialmente en ningún entorno")
    fun noInitialSelection() = world.assertNoCoverageZoneSelected()

    @Dado("que la carga de zonas devolverá una {string}")
    fun coverageFailure(failure: String) {
        world.configureCoverageFailure(server = failure == "falla del servidor")
        world.configurePendingCoverageZones()
        world.navigateToProfileDestination()
    }

    @Entonces("aparece un mensaje amigable y una acción para reintentar")
    fun friendlyRetryableError() = world.assertCoverageZonesError()

    @Y("no se solicita el registro del prestador")
    fun providerIsNotRegistered() = world.assertRegistrationBlockedWhileCoverageLoads()

    @Y("los demás datos del formulario permanecen sin cambios")
    fun otherFormDataIsPreserved() = world.assertProfileFieldsRemainEditable()

    @Dado("que hay un error de catálogo visible y el formulario contiene datos")
    fun coverageErrorWithProfileData() = world.arrangeCoverageErrorWithProfileData()

    @Y("el siguiente intento devolverá zonas disponibles")
    fun successfulRetry() = world.configureSuccessfulCoverageRetry()

    @Cuando("el prestador reintenta la carga de zonas")
    fun retryCoverageZones() = world.retryCoverageZones()

    @Entonces("se muestra el progreso y luego las zonas disponibles")
    fun loadingThenAvailableZones() = world.assertCoverageZonesReady()

    @Y("se conservan nombre, apellido, rubro y foto confirmada")
    fun completeProfileDataIsPreserved() = world.assertCompleteProfileDataPreserved()

    @Dado("que la API devolverá un catálogo de zonas vacío")
    fun emptyCoverageCatalog() {
        world.configureEmptyCoverageZones()
        world.configurePendingCoverageZones()
        world.navigateToProfileDestination()
    }

    @Entonces("aparece un mensaje amigable y una acción para volver a cargar")
    fun emptyCatalogIsVisible() = world.assertCoverageZonesEmpty()

    @Y("el registro permanece bloqueado")
    fun registrationRemainsBlocked() = world.assertRegistrationBlockedWhileCoverageLoads()

    @Y("no se agrega ningún identificador predeterminado")
    fun noDefaultCoverageId() = world.assertNoCoverageZoneSelected()

    @Dado("un catálogo disponible con ninguna o una zona seleccionada")
    fun readyCoverageSelection() = world.arrangeReadyCoverageSelection()

    @Cuando("el prestador marca otra zona disponible")
    fun checkAnotherZone() = world.checkCoverageZone(14)

    @Entonces("la zona elegida queda seleccionada una sola vez")
    fun selectedOnce() = world.assertSelectedCoverageZones(listOf(6, 14))

    @Y("las selecciones anteriores permanecen")
    fun priorSelectionsRemain() = world.assertSelectedCoverageZones(listOf(6, 14))

    @Y("se admiten zonas no contiguas")
    fun nonContiguousZonesAreAllowed() = world.assertSelectedCoverageZones(listOf(6, 14))
}
