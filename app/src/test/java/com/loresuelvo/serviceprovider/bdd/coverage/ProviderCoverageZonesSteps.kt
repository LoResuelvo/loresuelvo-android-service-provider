package com.loresuelvo.serviceprovider.bdd.coverage

import com.loresuelvo.serviceprovider.bdd.profile.CompleteProviderProfileWorld
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
}
