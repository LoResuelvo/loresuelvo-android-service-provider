package com.loresuelvo.serviceprovider.bdd.profilephoto

import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

class ProviderProfilePhotoSteps {

    private val world = ProviderProfilePhotoWorld()

    @After
    fun teardown() = world.close()


    @Dado("que el prestador completó nombre, apellido y seleccionó un rubro")
    fun prestadorCompletoDatosValidos() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.fillValidProfileData()
    }

    @Cuando("el prestador selecciona desde el dispositivo una foto JPEG legible de menos de 5 MiB")
    fun prestadorSeleccionaFotoJpegLegible() {
        world.selectValidJpegPhoto()
    }

    @Entonces("aparece una vista previa de esa foto en la misma página que el nombre, apellido y rubro")
    fun apareceVistaPreviaEnMismaPagina() {
        world.assertPhotoPreviewDisplayed()
    }

    @Y("los datos existentes del formulario permanecen sin cambios")
    fun datosExistentesPermanecenSinCambios() {
        world.assertProfileDataPreserved()
    }

    @Y("el prestador puede cargar o reemplazar la foto sin abandonar la página")
    fun prestadorPuedeCargarOReemplazarFoto() {
        world.assertUploadOrReplaceAvailable()
    }


    @Dado("el prestador abrió el selector del dispositivo desde el formulario")
    fun prestadorAbrioSelectorDesdeFormulario() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.fillValidProfileData()
        world.selectValidJpegPhoto()
    }

    @Cuando("el prestador cancela el selector sin elegir un archivo")
    fun prestadorCancelaSelectorSinElegirArchivo() {
        world.cancelPhotoSelection()
    }

    @Entonces("el prestador regresa al mismo formulario sin un error de validación")
    fun prestadorRegresaAlMismoFormularioSinErrorValidacion() {
        world.assertNoPhotoValidationError()
    }

    @Y("la selección anterior y su estado de confirmación permanecen sin cambios")
    fun seleccionAnteriorYEstadoConfirmacionPermanecenSinCambios() {
        world.assertPhotoPreviewDisplayed()
        world.assertUploadOrReplaceAvailable()
    }

    @Y("los demás datos del formulario permanecen sin cambios")
    fun losDemasDatosDelFormularioPermanecenSinCambios() {
        world.assertProfileDataPreserved()
    }


    @Dado("que el prestador está en el formulario de perfil")
    fun prestadorEstaEnFormularioPerfil() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
    }

    @Y("una imagen legible tiene el formato {string} y tamaño {int} bytes")
    fun imagenLegibleFormatoYTamano(formato: String, tamanoBytes: Int) {
        world.arrangeImageWithFormatAndSize(formato, tamanoBytes.toLong())
    }

    @Cuando("el prestador selecciona esa imagen desde el dispositivo")
    fun prestadorSeleccionaEsaImagen() {
        world.selectArrangedImage()
    }

    @Entonces("la imagen es aceptada y aparece su vista previa")
    fun imagenEsAceptadaYApareceVistaPrevia() {
        world.assertImageAcceptedWithPreview()
    }

    @Y("la acción para cargar la foto queda disponible")
    fun accionCargarFotoQuedaDisponible() {
        world.assertUploadActionAvailable()
    }


    @Dado("el prestador tiene una foto seleccionada, confirmada o con una carga fallida en el formulario")
    fun prestadorTieneFotoSeleccionadaOConfirmada() {
        world.arrangePhotoSelectedOrConfirmed()
    }

    @Y("el prestador todavía no completó el registro")
    fun prestadorTodaviaNoCompletoRegistro() {
        world.assertRegistrationNotCompleted()
    }

    @Cuando("el prestador selecciona otra foto válida mediante el control de reemplazo")
    fun prestadorSeleccionaOtraFotoValida() {
        world.replaceWithAnotherValidPhoto()
    }

    @Entonces("la vista previa muestra la nueva foto en el mismo formulario")
    fun vistaPreviaMuestraNuevaFoto() {
        world.assertNewPhotoPreviewDisplayed()
    }

    @Y("la nueva foto debe cargarse y confirmarse antes del registro")
    fun nuevaFotoDebeCargarseYConfirmarseAntesDelRegistro() {
        world.assertPhotoMustBeUploadedAndConfirmed()
    }

    @Y("el nombre, apellido, rubro y las zonas de cobertura seleccionadas permanecen sin cambios")
    fun datosPermanecenSinCambios() {
        world.assertProfileDataPreserved()
    }


    @Dado("el prestador tiene una foto válida seleccionada en el formulario")
    fun prestadorTieneFotoValidaSeleccionada() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.fillValidProfileData()
        world.selectValidJpegPhoto()
    }

    @Y("un nuevo archivo presenta la condición inválida {string}")
    fun nuevoArchivoPresentaCondicionInvalida(condicion: String) {
        world.arrangeInvalidCondition(condicion)
    }

    @Cuando("el prestador selecciona ese archivo")
    fun prestadorSeleccionaEseArchivo() {
        world.selectArrangedInvalidFile()
    }

    @Entonces("aparece un mensaje amigable de validación de la foto en el formulario")
    fun apareceMensajeAmigableValidacionFoto() {
        world.assertPhotoErrorMessageDisplayed()
    }

    @Y("no se intenta cargar el archivo inválido")
    fun noSeIntentaCargarArchivoInvalido() {
        world.assertNoUploadAttempted()
    }

    @Y("la foto válida anterior permanece seleccionada")
    fun fotoValidaAnteriorPermaneceSeleccionada() {
        world.assertOriginalPhotoStillSelected()
    }


    @Dado("el prestador ingresó sus datos personales y seleccionó un rubro")
    fun prestadorIngresoDatosPersonalesYRubro() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.fillValidProfileData()
    }

    @Y("hay una foto legible seleccionada en el estado {string}")
    fun hayFotoLegibleSeleccionadaEnEstado(estado: String) {
        world.arrangePhotoState(estado)
    }

    @Cuando("se recrea la pantalla mientras se conserva la instancia que administra su estado")
    fun recreaPantallaConservandoInstancia() {
        world.recreateScreenPreservingViewModel()
    }

    @Entonces("se muestran los mismos datos del formulario y la vista previa de la foto")
    fun muestranMismosDatosYVistaPreviaFoto() {
        world.assertProfileDataAndPhotoPreviewPreserved()
    }

    @Y("se conserva el estado previo de confirmación de la foto")
    fun conservaEstadoPrevioConfirmacionFoto() {
        world.assertUploadOrReplaceAvailable()
    }

    @Y("no se inicia automáticamente una carga ni un registro")
    fun noIniciaAutomaticamenteCargaNiRegistro() {
        world.assertNoAutomaticUploadOrRegistration()
    }

    // --- 06-PPH Steps ---

    @Dado("hay una foto válida seleccionada en el formulario")
    fun hayFotoValidaSeleccionadaEnFormulario() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.fillValidProfileData()
        world.selectValidJpegPhoto()
    }

    @Y("el prestador tiene una sesión autenticada válida")
    fun prestadorTieneSesionAutenticadaValida() {
        world.seedAuthenticatedSession()
    }

    @Y("la carga de la foto finalizará correctamente")
    fun cargaFotoFinalizaraCorrectamente() {
        world.configurePhotoUploadSuccess()
    }

    @Cuando("el prestador selecciona la acción para cargar la foto")
    fun prestadorSeleccionaAccionCargarFoto() {
        world.triggerPhotoUpload()
    }

    @Entonces("el formulario muestra el progreso de carga de forma accesible")
    fun formularioMuestraProgresoCargaDeFormaAccesible() {
        world.assertUploadProgressDisplayed()
    }

    @Y("la foto actual queda lista para el registro únicamente después de una confirmación exitosa")
    fun fotoActualQuedaListaParaRegistroDespuesDeConfirmacion() {
        world.assertPhotoReadyForRegistration()
    }

    @Y("el formulario no se envía")
    fun formularioNoSeEnvia() {
        world.assertRegistrationNotCompleted()
    }

    // --- 07-PPH Steps ---

    @Dado("la operación {string} está en curso")
    fun operacionEstaEnCurso(operacion: String) {
        world.arrangeOperationInProgress(operacion)
    }

    @Cuando("el prestador repite una acción de carga o registro")
    fun prestadorRepiteAccionCargaORegistro() {
        world.repeatUploadOrRegistrationAction()
    }

    @Entonces("no se inicia una operación duplicada")
    fun noSeIniciaOperacionDuplicada() {
        world.assertNoDuplicateOperation()
    }

    @Y("los controles de carga, reemplazo y registro que interfieren con la operación permanecen deshabilitados")
    fun controlesInterfierenPermanecenDeshabilitados() {
        world.assertControlsRemainDisabled()
    }

    @Y("el formulario continúa mostrando el progreso de la operación")
    fun formularioContinuaMostrandoProgreso() {
        world.assertOperationProgressContinues()
    }

    // --- 08-PPH Steps ---

    @Y("la carga de una foto válida está en curso")
    fun cargaFotoValidaEnCurso() {
        world.selectValidJpegPhoto()
    }

    @Y("la etapa {string} devuelve la falla recuperable {string}")
    fun etapaDevuelveFallaRecuperable(etapa: String, falla: String) {
        world.arrangeRecoverableFailure(etapa, falla)
    }

    @Cuando("el intento de carga finaliza con esa falla")
    fun intentoCargaFinalizaConEsaFalla() {
        world.triggerPhotoUpload()
    }

    @Entonces("el prestador permanece en el mismo formulario con un mensaje amigable de error de foto")
    fun permaneceEnMismoFormularioConMensajeAmigableErrorFoto() {
        world.assertRemainsOnFormWithFriendlyPhotoError()
    }

    @Y("se preservan el nombre, apellido y la selección de rubro")
    fun sePreservanNombreApellidoYRubro() {
        world.assertProfileDataPreserved()
    }

    @Y("se conserva la foto seleccionada para reintentar o reemplazarla")
    fun conservaFotoSeleccionadaParaReintentarOReemplazar() {
        world.assertPhotoRetainedForRetryOrReplace()
    }

    // --- 09-PPH Steps ---

    @Dado("el formulario muestra una falla recuperable de carga de foto")
    fun formularioMuestraFallaRecuperableCargaFoto() {
        world.arrangeRecoverablePhotoFailure()
    }

    @Y("el reintento será exitoso")
    fun reintentoSeraExitoso() {
        world.configureRetrySuccess()
    }

    @Cuando("el prestador selecciona la acción para reintentar la carga de la foto")
    fun prestadorSeleccionaAccionReintentarCargaFoto() {
        world.triggerPhotoUpload()
    }

    @Entonces("se reanuda el progreso de carga de la imagen seleccionada actualmente")
    fun reanudaProgresoCargaImagenActual() {
        world.assertUploadProgressResumed()
    }

    @Y("una confirmación exitosa deja esa imagen lista para el registro")
    fun confirmacionExitosaDejaImagenListaParaRegistro() {
        world.assertPhotoReadyForRegistration()
    }

    // --- 10-PPH Steps ---

    @Dado("que el prestador ingresó nombre, apellido válidos y seleccionó un rubro")
    fun prestadorIngresoNombreApellidoValidosYRubro() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.fillValidProfileData()
    }

    @Y("se cumple la condición de dependencia {string}")
    fun cumpleCondicionDependencia(condicion: String) {
        world.arrangeDependencyCondition(condicion)
    }

    @Cuando("el prestador solicita el registro")
    fun prestadorSolicitaRegistro() {
        world.requestRegistration()
    }

    @Y("el formulario identifica el requisito de foto o cobertura incompleto mediante un mensaje amigable")
    fun formularioIdentificaRequisitoIncompleto() {
        world.assertIncompleteRequirementIdentified()
    }

    @Y("se conservan los datos actuales del formulario")
    fun conservanDatosActualesFormulario() {
        world.assertProfileDataPreserved()
    }
}
