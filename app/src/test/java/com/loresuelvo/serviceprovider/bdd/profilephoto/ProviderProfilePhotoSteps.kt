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
}
