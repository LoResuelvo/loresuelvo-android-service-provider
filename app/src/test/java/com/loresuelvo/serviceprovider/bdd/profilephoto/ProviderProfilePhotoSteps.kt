package com.loresuelvo.serviceprovider.bdd.profilephoto

import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

/**
 * Step definitions for US-35.4 provider profile photo acceptance scenarios.
 */
class ProviderProfilePhotoSteps {

    private val world = ProviderProfilePhotoWorld()

    @After
    fun teardown() = world.close()

    // --- 01-PPH ---

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

    // --- 03-PPH ---

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

    // --- 04-PPH ---

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
}
