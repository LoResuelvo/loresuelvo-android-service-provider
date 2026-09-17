package com.loresuelvo.serviceprovider.bdd.conversation

import android.net.Uri
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.loresuelvo.serviceprovider.ui.screens.conversation.ChatListItem
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState

internal class ProviderConversationSteps {

    private lateinit var world: ProviderConversationWorld

    @Before
    fun setUp() {
        world = ProviderConversationWorld()
        // Cucumber instantiates this class via reflection; the step
        // methods are dispatched on the same thread that calls
        // `setUp`, so Robolectric's runtime is online here. The
        // synthetic URI is the same value the gallery / camera
        // launchers would hand to `onMediaPicked(uri)`.
        world.seedMediaUri(Uri.parse("content://media/picker/0"))
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

    @Given("que la conversación {int} está abierta con {int} mensajes previos")
    fun conversationIsOpenWithPriorMessages(
        @Suppress("UNUSED_PARAMETER") conversationId: Int,
        priorCount: Int,
    ) {
        // The "previous" tally maps directly to the detail's messages.
        // Each scenario uses an explicit split (1/2 for 03-PCC, 03/06
        // defaults) via the more specific Gherkin step above.
        val consumer = priorCount / 2
        val provider = priorCount - consumer
        world.givenDetailWithMessages(
            consumerCount = consumer,
            providerCount = provider,
        )
        world.whenOpeningConversation()
    }

    @Given("que la API aceptará el envío de un nuevo mensaje con contenido {string}")
    fun apiWillAcceptSendWithContent(content: String) {
        // The server-persisted id is fixed at 99 so the world can
        // assert the bubble replacement by stable id.
        world.givenSendWillSucceed(serverMessageId = 99, prompt = content)
        // Pause the send on a gate so the optimistic pending
        // bubble is observable BEFORE the server confirms (the
        // `Then` step asserts the pending state explicitly).
        world.pauseSendOnGate()
    }

    @Given("que el próximo envío del prestador fallará por red")
    fun nextSendWillFailByNetwork() {
        world.givenSendWillFailWithNetwork()
        world.pauseSendOnGate()
    }

    @Given("que la conversación {int} está abierta con una burbuja pendiente en fallo por red")
    fun conversationHasAFailedPendingBubble(@Suppress("UNUSED_PARAMETER") conversationId: Int) {
        // Set up an empty conversation, configure the first send
        // to fail, fire it (paused on the gate), then leave the
        // world with one failed bubble in place.
        world.givenEmptyDetail()
        world.whenOpeningConversation()
        world.givenSendWillFailWithNetwork()
        world.pauseSendOnGate()
        world.whenTyping("Mañana a las 10")
        world.whenTappingSend()
        world.releaseSendGate()
    }

    @Given("que el reintento del envío tendrá éxito")
    fun retryWillSucceed() {
        // After the failed send above, swap to a successful outcome
        // for the next call. The retry step itself uses a fresh
        // gate so we can observe the optimistic-then-confirmed
        // transition in two assertions.
        world.givenSendWillSucceed(serverMessageId = 7, prompt = "Mañana a las 10")
        world.pauseSendOnGate()
    }

    @Given("que el reintento del upload tendrá éxito")
    fun imageRetryWillSucceed() {
        world.givenSendWillSucceedWithMedia(serverMessageId = 7)
        world.pauseSendOnGate()
    }

    @When("el prestador navega a la ruta de la conversación {int}")
    fun providerOpensConversation(@Suppress("UNUSED_PARAMETER") conversationId: Int) =
        world.whenOpeningConversation()

    @When("el prestador escribe {string} en el input y selecciona Enviar")
    fun providerTypesAndSends(content: String) {
        world.whenTyping(content)
        world.whenTappingSend()
    }

    @When("el prestador escribe solo espacios en el input")
    fun providerTypesOnlySpaces() {
        world.whenTyping("   ")
    }

    @When("el prestador selecciona Reintentar en esa burbuja")
    fun providerRetriesTheFailedBubble() {
        world.whenTappingRetryOnFailedBubble()
    }

    @Then("la pantalla muestra los {int} mensajes en orden cronológico con el remitente y el avatar correctos")
    fun screenShowsMessagesInOrder(expectedCount: Int) {
        world.thenReadyStateRendersAllMessagesInOrder(
            expectedConsumerCount = expectedCount / 2,
            expectedProviderCount = expectedCount - (expectedCount / 2),
        )
    }

    @Then("la pantalla no muestra burbujas ni un estado de error")
    fun screenShowsNoBubblesAndNoError() {
        world.thenNoBubblesAndNoError()
    }

    @Then("la pantalla agrega optimistamente una burbuja pendiente con ese texto")
    fun screenAddsOptimisticPendingBubble() {
        val ready = world.readReadyStateOrNull()
            ?: error("expected Ready, got ${world.readState()}")
        val pending = ready.items.filterIsInstance<ChatListItem.LocalPending>()
        assertEquals(
            "expected exactly one pending bubble, got ${ready.items}",
            1,
            pending.size,
        )
        assertTrue(
            "pending bubble must have non-empty content, got '${pending.single().content}'",
            pending.single().content.isNotBlank(),
        )
        assertEquals(true, ready.sending)
    }

    @Then("al confirmarse el envío la burbuja pendiente se reemplaza por la versión persistida por el servidor con id estable y timestamp autoritativo")
    fun bubbleReplacedByServerConfirmedWithStableIdAndTimestamp() {
        // Release the send gate so the in-flight round-trip can
        // resolve into the server-persisted message.
        world.releaseSendGate()
        // The server-persisted id is fixed at 99 by the upstream
        // `apiWillAcceptSendWithContent` step.
        world.thenBubbleReplacedByServerConfirmed(serverMessageId = 99)
    }

    @Then("al fallar el envío la burbuja permanece con un indicador de fallo y un botón Reintentar")
    fun failedBubbleKeepsAFailureIndicatorAndRetryButton() {
        // Release the send gate so the in-flight round-trip can
        // resolve into the typed failure.
        world.releaseSendGate()
        val state = world.readState()
        assertTrue(
            "expected Ready, got $state",
            state is ProviderConversationUiState.Ready,
        )
        val ready = state as ProviderConversationUiState.Ready
        val failed = ready.items
            .filterIsInstance<ChatListItem.LocalFailed>()
        assertEquals(
            "expected exactly one failed bubble, got ${ready.items}",
            1,
            failed.size,
        )
        assertEquals(false, ready.sending)
    }

    @Then("el envío se ejecuta una sola vez")
    fun sendFiresExactlyOnce() {
        world.thenOnlyOneSendWasFired()
    }

    @Then("al confirmarse la burbuja pendiente se reemplaza por la versión persistida por el servidor")
    fun retryPendingBubbleIsReplacedByServerConfirmed() {
        // Release the send gate so the retry round-trip can
        // resolve into the server-persisted message.
        world.releaseSendGate()
        world.thenBubbleReplacedByServerConfirmed(serverMessageId = 7)
    }

    @Then("el botón Enviar permanece deshabilitado")
    fun sendButtonRemainsDisabled() {
        world.thenSendButtonIsDisabled()
    }

    @Then("ningún envío se dispara")
    fun noSendIsFired() {
        world.thenNoSendWasFired()
    }

    @And("el header exhibe el nombre completo del consumidor como título")
    fun headerShowsCounterpartName() {
        world.thenHeaderShowsCounterpartFullName()
    }

    @And("el input bar está vacío y habilitado")
    fun inputBarIsEmptyAndEnabled() {
        world.thenInputBarIsEmptyAndEnabled()
    }

    @And("el input bar vuelve a quedar vacío y habilitado")
    fun inputBarIsClearedAndReady() {
        world.thenInputBarIsEmptyAndEnabled()
    }

    // =====================================================================
    // US-B — Adjuntar imágenes a un mensaje
    // =====================================================================

    @Given("que el prestador tiene una imagen JPEG adjunta como preview")
    fun providerHasImagePending() {
        world.givenMediaPickerReturns(
            bytes = byteArrayOf(1, 2, 3, 4),
            mimeType = "image/jpeg",
            originalName = "kitchen.jpg",
        )
        world.whenProviderPicksMedia()
    }

    @When("el prestador selecciona una imagen JPEG de su galería")
    fun providerPicksImageFromGallery() {
        // Same flow as `providerHasImagePending` — the BDD World
        // is gallery-vs-camera agnostic; the route decides which
        // launcher fires and both end at `onMediaPicked(uri)`.
        world.givenMediaPickerReturns(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalName = "kitchen.jpg",
        )
        world.whenProviderPicksMedia()
    }

    @When("el prestador toma una foto JPEG con la cámara del dispositivo")
    fun providerTakesPhotoFromCamera() {
        world.givenMediaPickerReturns(
            bytes = byteArrayOf(9, 9, 9),
            mimeType = "image/jpeg",
            originalName = "capture.jpg",
        )
        world.whenProviderPicksMedia()
    }

    @When("el prestador selecciona Enviar con una imagen adjunta")
    fun providerSendsWithImage() {
        world.whenTappingSend()
    }

    @Given("que el próximo upload de imagen del prestador fallará por red")
    fun nextImageUploadWillFail() {
        world.givenSendWillFailWithNetwork()
        world.pauseSendOnGate()
    }

    @Given("que la conversación {int} está abierta con una burbuja pendiente de imagen en fallo por red")
    fun conversationHasFailedImageBubble(@Suppress("UNUSED_PARAMETER") conversationId: Int) {
        world.givenEmptyDetail()
        world.whenOpeningConversation()
        world.givenMediaPickerReturns(
            bytes = byteArrayOf(1),
            mimeType = "image/jpeg",
            originalName = "x.jpg",
        )
        world.whenProviderPicksMedia()
        world.givenSendWillFailWithNetwork()
        world.pauseSendOnGate()
        world.whenTappingSend()
        world.releaseSendGate()
    }

    @Then("la pantalla muestra una preview de esa imagen en la barra del input")
    fun screenShowsImagePreview() {
        world.thenStagedMediaBytes(expected = byteArrayOf(1, 2, 3, 4))
    }

    @And("el botón Enviar queda habilitado con esa imagen adjunta")
    fun sendButtonIsEnabledWithImage() {
        val ready = world.readReadyStateOrNull()
            ?: error("expected Ready, got ${world.readState()}")
        assertTrue(
            "expected send to be enabled when media is staged",
            ready.pendingMedia != null && !ready.sending,
        )
    }

    @Given("que el servidor confirma el upload y la persistencia del mensaje")
    fun serverConfirmsImageUpload() {
        world.givenSendWillSucceedWithMedia(serverMessageId = 99)
        world.pauseSendOnGate()
    }

    @Then("la pantalla agrega optimistamente una burbuja pendiente con la miniatura de esa imagen")
    fun screenAddsOptimisticPendingImageBubble() {
        val ready = world.readReadyStateOrNull()
            ?: error("expected Ready, got ${world.readState()}")
        val pending = ready.items.filterIsInstance<ChatListItem.LocalPending>()
        assertEquals(
            "expected exactly one pending image bubble, got ${ready.items}",
            1,
            pending.size,
        )
        assertTrue(
            "expected pending bubble to carry the staged image",
            pending.single().pendingMedia != null,
        )
    }

    @Then("la preview local se descarta y el input bar queda vacío")
    fun previewIsDiscardedAndInputBarIsCleared() {
        world.thenNoStagedMedia()
    }

    @Then("la burbuja pendiente se reemplaza por la versión persistida con id estable y url de descarga")
    fun pendingImageBubbleReplacedByConfirmed() {
        // The BDD world configures the next successful send to
        // return id 99 with the image URL echoed back; the media id
        // is generated by the faked sendMediaMessage flow.
        world.thenImageBubbleConfirmed(mediaId = "file-uuid-99")
    }

    @Then("al fallar el upload la burbuja permanece con un indicador de fallo y un botón Reintentar")
    fun failedImageBubbleKeepsFailureIndicator() {
        world.thenBubbleReplacedByLocalFailed(expectedContent = "")
    }

    @Then("al confirmarse la burbuja pendiente se reemplaza por la versión persistida con url de descarga")
    fun retryFailedImageBubbleReplacedByConfirmed() {
        world.thenImageBubbleConfirmed(mediaId = "file-uuid-7")
    }

    @Then("la pantalla muestra la miniatura de esa imagen en su burbuja con el contenido accesible correcto")
    fun screenShowsReceivedImageBubble() {
        val ready = world.readReadyStateOrNull()
            ?: error("expected Ready, got ${world.readState()}")
        val confirmed = ready.items
            .filterIsInstance<ChatListItem.ServerConfirmed>()
            .singleOrNull()
            ?: error("expected a single ServerConfirmed bubble, got ${ready.items}")
        val media = confirmed.message.media
            ?: error("expected the bubble to carry an image, got ${confirmed.message}")
        assertEquals("image/jpeg", media.mimeType)
    }
}
