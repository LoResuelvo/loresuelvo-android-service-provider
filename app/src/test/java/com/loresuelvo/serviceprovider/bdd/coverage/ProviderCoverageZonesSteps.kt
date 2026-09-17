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

    @Dado("que el prestador seleccionó dos zonas")
    fun twoSelectedZones() = world.arrangeTwoSelectedCoverageZones()

    @Cuando("el prestador desmarca una zona")
    fun uncheckZone() = world.uncheckCoverageZone(14)

    @Entonces("solamente esa zona se elimina de la selección")
    fun onlyUncheckedZoneIsRemoved() = world.assertSelectedCoverageZones(listOf(6))

    @Y("la otra zona permanece seleccionada")
    fun otherZoneRemainsSelected() = world.assertSelectedCoverageZones(listOf(6))

    @Dado("que una zona ya está seleccionada")
    fun selectedZone() = world.arrangeReadyCoverageSelection()

    @Cuando("se recibe nuevamente el mismo evento de selección marcada")
    fun repeatSelectionEvent() = world.repeatCoverageZoneSelection(6)

    @Entonces("el identificador aparece una sola vez en la selección")
    fun selectedIdAppearsOnce() = world.assertSelectedCoverageZones(listOf(6))

    @Y("no puede enviarse duplicado al registro")
    fun duplicateCannotBeSubmitted() = world.assertCoverageSelectionHasNoDuplicates()

    @Dado("que el catálogo está disponible y los demás datos requeridos son válidos")
    fun validProfileWithoutCoverage() = world.arrangeValidProfileWithoutCoverageSelection()

    @Y("no hay ninguna zona seleccionada")
    fun noSelectedZone() = world.assertNoCoverageZoneSelected()

    @Cuando("el prestador envía el formulario")
    fun submitForm() = world.attemptSubmit()

    @Entonces("aparece una validación localizada junto a la sección de cobertura")
    fun missingCoverageValidation() = world.assertMissingCoverageZonesError()

    @Y("no se solicita el registro ni se navega a otra pantalla")
    fun registrationAndNavigationDoNotOccur() = world.assertRegistrationAndNavigationDidNotOccur()

    @Dado("un formulario válido con {string} proveniente del catálogo")
    fun validFormWithCoverageSelection(selection: String) =
        world.arrangeValidProfileWithCoverageSelection(selection)

    @Y("la API de registro responderá exitosamente")
    fun successfulRegistration() = world.configureSuccessfulRegistration()

    @Entonces("se envían una vez exactamente los identificadores seleccionados")
    fun exactSelectedIdsAreSentOnce() = world.assertExactCoverageSelectionRegisteredOnce()

    @Y("el prestador avanza a la vinculación de Mercado Pago")
    fun navigateToMercadoPago() = world.assertNavigatedToMercadoPago()

    @Y("el formulario no es accesible mediante navegación hacia atrás")
    fun profileIsRemovedFromBackStack() = world.assertProfileFormPoppedFromBackstack()

    @Dado("que un registro válido está en curso")
    fun registrationInProgress() = world.arrangeRegistrationInProgress()

    @Cuando("se reciben más acciones de envío o selección de cobertura")
    fun repeatSubmitAndSelection() = world.repeatSubmitAndCoverageSelection()

    @Entonces("existe solamente la solicitud de registro original")
    fun onlyOriginalRegistrationExists() = world.assertSingleRegistrationCall()

    @Y("su selección enviada permanece sin cambios")
    fun submittedSelectionRemainsUnchanged() = world.assertSubmittedCoverageSelectionUnchanged()

    @Y("los controles muestran el estado ocupado")
    fun controlsShowBusyState() = world.assertSubmitDisabledWithLoading()

    @Dado("un formulario válido cuya selección será rechazada por {string}")
    fun rejectedCoverageSelection(reason: String) = world.arrangeRejectedCoverageSelection(reason)

    @Entonces("aparece un mensaje localizado para corregir la cobertura")
    fun coverageCorrectionMessage() = world.assertCoverageRejectionVisible()

    @Y("el prestador permanece en el formulario con sus datos y foto confirmada")
    fun profileAndPhotoRemain() = world.assertProfileAndPhotoPreserved()

    @Y("puede corregir o volver a cargar la selección sin un reenvío automático")
    fun correctWithoutAutomaticSubmit() = world.correctCoverageWithoutAutomaticSubmit()

    @Dado("que un rechazo de cobertura está visible y existe una selección previa")
    fun visibleCoverageRejectionWithSelection() = world.arrangeVisibleCoverageRejectionWithSelection()

    @Y("la nueva carga del catálogo {string}")
    fun configureCatalogReload(result: String) = world.configureCoverageReload(result)

    @Cuando("el prestador vuelve a cargar las zonas")
    fun reloadCoverageZones() = world.retryCoverageZones()

    @Entonces("se conservan las selecciones que siguen disponibles")
    fun availableSelectionsRemain() = world.assertReloadedCoverageSelection()

    @Y("se informa cualquier selección eliminada cuando la carga es exitosa")
    fun removedSelectionsAreReported() = world.assertCoverageAdjustmentReportedWhenNeeded()

    @Y("se conservan los demás datos sin reenviar el formulario")
    fun profileRemainsWithoutResubmission() = world.assertProfilePreservedWithoutResubmission()

    @Y("el registro permanece bloqueado si la carga falla")
    fun registrationRemainsBlockedAfterFailure() = world.assertRegistrationBlockedAfterFailedReload()
}
