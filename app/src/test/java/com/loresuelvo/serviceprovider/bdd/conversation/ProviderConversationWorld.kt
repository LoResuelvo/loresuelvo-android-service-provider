package com.loresuelvo.serviceprovider.bdd.conversation

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.MediaReference
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationByIdUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMediaMessageUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMessageUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.ChatListItem
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * BDD world for the `provider-conversation.feature` scenarios
 * (US-A, Provider Chat Conversation — PCC).
 *
 * Drives the production [ProviderConversationViewModel] through a
 * fake [ConversationRepository] so each step observes the same
 * instance — no mocked assertions, no `Thread.sleep`, and no
 * disconnect between the `When` and the production behaviour it
 * exercises.
 *
 * Scenario coverage map:
 *  - 01-PCC / 02-PCC — opening a conversation with and without
 *    prior messages (Boundary 4).
 *  - 03-PCC — sending a text message (success).
 *  - 04-PCC — sending a text message that fails by network,
 *    leaving a persistent pending / failed bubble.
 *  - 05-PCC — retrying the failed send resolves the bubble.
 *  - 06-PCC — blank input keeps the send button disabled.
 */
// Cohesion review: this existing world owns one real conversation model and its shared fake ports.
// Text/image/audio acceptance shares the same composer lifecycle; the next seam is standalone port fixtures.
// PCA01–11 and bounded scheduler/owned-model teardown provide focused proof without a second state machine.
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProviderConversationWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val repository = FakeConversationRepository()

    private var microphoneGranted = false
    fun grantMicrophone() { microphoneGranted = true }
    private val recorder = BddAudioRecorder()
    private val player = BddAudioPlayer()

    private val conversationId: Int = 42

    private lateinit var viewModel: ProviderConversationViewModel

    /**
     * Snapshot of [sendCalls] captured at the moment the retry
     * action is initiated. The 05-PCC assertion ("el envío se
     * ejecuta una sola vez") scopes its check to the diff
     * between this baseline and the current count — keeping the
     * assertion robust against the cumulative count from prior
     * failed sends in the Given setup.
     */
    private var sendCallsAtRetryBaseline: Int = 0
    private var operation: String = "detalle"
    private val ownedModels = mutableListOf<ProviderConversationViewModel>()

    /**
     * Staged media for the US-B scenarios. The [FakeMediaReader]
     * resolves any URI to [stagedMedia] (the World's picker is
     * permissive so the BDD steps can drive any URI through the
     * VM regardless of the gallery / camera path).
     *
     * The synthetic URI is supplied by the Steps at `setUp()` time
     * — under plain JUnit `Uri.parse` returns null (Android
     * runtime stubs), so the Steps class is responsible for the
     * Android-side construction.
     */
    private var mediaPickerUri: String = "content://bdd/image"
    private var stagedMediaUri: String = mediaPickerUri
    private var stagedMedia: MediaUpload? = null
    private var readFailure: java.io.IOException? = null

    fun seedMediaUri(uri: android.net.Uri) {
        mediaPickerUri = uri.toString()
        stagedMediaUri = uri.toString()
    }

    init {
        Dispatchers.setMain(dispatcher)
    }

    // --- Given ----------------------------------------------------------

    fun givenDetailWithMessages(
        consumerCount: Int,
        providerCount: Int,
    ) {
        val messages = buildList {
            repeat(consumerCount) { i ->
                add(
                    ConversationMessage(
                        id = 100 + i,
                        sender = ConversationSender.Consumer,
                        content = "consumer-${i + 1}",
                        createdOnEpochMillis = (i + 1).toLong(),
                    ),
                )
            }
            repeat(providerCount) { i ->
                add(
                    ConversationMessage(
                        id = 200 + i,
                        sender = ConversationSender.Provider,
                        content = "provider-${i + 1}",
                        createdOnEpochMillis = (consumerCount + i + 1).toLong(),
                    ),
                )
            }
        }
        repository.detailOutcome = ConversationDetailOutcome.Success(detail(messages))
    }

    fun givenEmptyDetail() {
        repository.detailOutcome = ConversationDetailOutcome.Success(detail(emptyList()))
    }

    fun givenSendWillSucceed(serverMessageId: Int, prompt: String) {
        repository.sendOutcome = SendMessageOutcome.Success(
            message = ConversationMessage(
                id = serverMessageId,
                sender = ConversationSender.Provider,
                content = prompt,
                createdOnEpochMillis = 10_000L,
            ),
        )
    }

    /**
     * US-B variant of [givenSendWillSucceed]: the server returns a
     * media-bearing message. The id is used both for the persisted
     * `ConversationMessage.id` and to seed the synthetic
     * `MediaReference.Image.id` so the BDD assertions can pin both.
     */
    fun givenSendWillSucceedWithMedia(serverMessageId: Int, prompt: String = "") {
        repository.sendOutcome = SendMessageOutcome.Success(
            message = ConversationMessage(
                id = serverMessageId,
                sender = ConversationSender.Provider,
                content = prompt,
                createdOnEpochMillis = 10_000L,
            ),
        )
    }

    fun givenSendWillFailWithNetwork() {
        repository.sendOutcome = SendMessageOutcome.Failure.Network(
            cause = RuntimeException("offline"),
        )
    }

    /**
     * US-B: stage a media picker outcome so the next `onMediaPicked`
     * invocation resolves to the given bytes + metadata. The
     * synthetic `Uri` is the same key the World returns from
     * [mediaPickerUri].
     */
    fun givenMediaPickerReturns(
        bytes: ByteArray,
        mimeType: String,
        originalName: String,
    ) {
        stagedMediaUri = mediaPickerUri
        stagedMedia = MediaUpload.Image(
            bytes = bytes,
            mimeType = mimeType,
            originalName = originalName,
        )
    }

    fun whenProviderPicksMedia() {
        viewModel.onImagesPicked(listOf(stagedMediaUri))
        scheduler.advanceUntilIdle()
    }

    fun whenProviderClearsMedia() {
        viewModel.onClearStagedMedia()
        scheduler.advanceUntilIdle()
    }

    /**
     * Pause the next [sendMessage] round-trip on a [CompletableDeferred]
     * so the optimistic `LocalPending` bubble stays in the list
     * until the test calls [releaseSendGate]. Mirrors the inbox
     * world's `pendingCompletion` pattern — lets 03/04-PCC
     * observe the optimistic state BEFORE the server confirms or
     * fails.
     */
    fun pauseSendOnGate() {
        repository.sendGate = CompletableDeferred()
    }

    fun releaseSendGate() {
        repository.sendGate?.complete(Unit)
        scheduler.advanceUntilIdle()
    }

    // --- When -----------------------------------------------------------

    fun whenOpeningConversation() {
        viewModel = newViewModel()
        scheduler.advanceUntilIdle()
    }

    fun whenTyping(prompt: String) {
        viewModel.onPromptChange(prompt)
    }

    fun whenTappingSend() {
        viewModel.onSendClick()
        scheduler.advanceUntilIdle()
    }

    fun whenTappingRetryOnFailedBubble() {
        // Capture the sendCalls baseline so the Gherkin "el envío
        // se ejecuta una sola vez" assertion can verify that
        // tapping Retry fired exactly one new round-trip — without
        // coupling the test to the cumulative count from prior
        // failed sends.
        sendCallsAtRetryBaseline = repository.sendCalls
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        val failedKey = ready.items
            .filterIsInstance<ChatListItem.LocalFailed>()
            .single()
            .key
        viewModel.onRetrySendFailedBubble(failedKey)
        scheduler.advanceUntilIdle()
    }

    fun givenRestrictedStatus(status: String) {
        givenEmptyDetail()
        when (status) {
            "Loading" -> repository.pendingDetailCompletion = CompletableDeferred()
            "Error" -> repository.detailOutcome = ConversationDetailOutcome.Failure.Network(Exception("offline"))
            else -> {
                val detail = (repository.detailOutcome as ConversationDetailOutcome.Success).detail
                repository.detailOutcome = ConversationDetailOutcome.Success(detail.copy(status = when (status) {
                    "Pending" -> ConversationStatus.Pending
                    "Rejected" -> ConversationStatus.Rejected
                    else -> ConversationStatus.Unsupported("future")
                }))
            }
        }
        whenOpeningConversation()
    }

    fun whenAttemptingRestrictedActions() {
        whenTyping("Blocked")
        viewModel.onSendClick()
        viewModel.onImagesPicked(listOf("content://blocked"))
        viewModel.onStartRecording()
        viewModel.onRetrySendFailedBubble("missing")
        scheduler.advanceUntilIdle()
    }

    fun thenComposerDidNotStart() {
        assertEquals(0, repository.sendCalls)
        assertTrue(!viewModel.canStartComposerOperation())
        readReadyStateOrNull()?.let {
            assertEquals("", it.promptInput)
            assertTrue(it.pendingMedia == null)
            assertTrue(it.recordingState is com.loresuelvo.serviceprovider.ui.screens.conversation.RecordingState.Idle)
        }
    }

    fun whenRepeatedSend(prompt: String) {
        whenTyping(prompt)
        repeat(3) { viewModel.onSendClick() }
        scheduler.advanceUntilIdle()
    }

    fun whenRepeatedRetryWithDraft(prompt: String) {
        whenTyping(prompt)
        sendCallsAtRetryBaseline = repository.sendCalls
        val key = readReadyStateOrNull()!!.items.filterIsInstance<ChatListItem.LocalFailed>().single().key
        repeat(3) { viewModel.onRetrySendFailedBubble(key) }
        scheduler.advanceUntilIdle()
    }

    fun thenDraftSurvives(prompt: String) {
        releaseSendGate()
        assertEquals(prompt, readReadyStateOrNull()!!.promptInput)
        assertTrue(readReadyStateOrNull()!!.items.single() is ChatListItem.ServerConfirmed)
    }

    fun givenAccessFailure(operation: String, failure: String) {
        this.operation = operation
        givenEmptyDetail()
        if (operation == "envio") {
            whenOpeningConversation()
            repository.sendOutcome = when (failure) {
                "Unauthorized" -> SendMessageOutcome.Failure.Unauthorized
                "Forbidden" -> SendMessageOutcome.Failure.Server(403, "private diagnostic")
                else -> SendMessageOutcome.Failure.ConversationNotFound("private diagnostic")
            }
        } else {
            repository.detailOutcome = when (failure) {
                "Unauthorized" -> ConversationDetailOutcome.Failure.Unauthorized
                "Forbidden" -> ConversationDetailOutcome.Failure.Server(403, "private diagnostic")
                "WrongId" -> ConversationDetailOutcome.Success(detail(emptyList()).copy(id = 99))
                else -> ConversationDetailOutcome.Failure.NotFound("private diagnostic")
            }
        }
    }

    fun whenExecutingAccessOperation() {
        if (operation == "detalle") whenOpeningConversation() else {
            whenTyping("Message")
            whenTappingSend()
        }
    }

    fun thenConversationIsInaccessible() {
        assertTrue(readState() is ProviderConversationUiState.Error)
        assertTrue(!viewModel.canStartComposerOperation())
    }

    fun whenRestoringProcess() {
        sendCallsAtRetryBaseline = repository.sendCalls
        whenOpeningConversation()
    }

    fun thenNoLocalStateWasRestored() {
        assertEquals(sendCallsAtRetryBaseline, repository.sendCalls)
        val ready = readReadyStateOrNull()!!
        assertTrue(ready.items.isEmpty())
        assertEquals("", ready.promptInput)
        assertTrue(!ready.sending)
    }

    // --- Then -----------------------------------------------------------

    fun thenReadyStateRendersAllMessagesInOrder(
        expectedConsumerCount: Int,
        expectedProviderCount: Int,
    ) {
        val state = viewModel.uiState.value
        assertTrue("expected Ready, got $state", state is ProviderConversationUiState.Ready)
        val ready = state as ProviderConversationUiState.Ready
        assertEquals(expectedConsumerCount + expectedProviderCount, ready.items.size)

        val messages = ready.detail.messages
        for (i in messages.indices) {
            assertEquals(messages[i].id, ready.items[i].key.toInt())
        }
        assertEquals(ConversationSender.Consumer, ready.detail.messages.first().sender)
    }

    fun thenHeaderShowsCounterpartFullName() {
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        assertEquals("Ana", ready.detail.counterpart.name)
        assertEquals("Pérez", ready.detail.counterpart.surname)
        assertNotNull(ready.detail)
    }

    fun thenInputBarIsEmptyAndEnabled() {
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        assertEquals("", ready.promptInput)
        assertEquals(false, ready.sending)
    }

    fun thenNoBubblesAndNoError() {
        val state = viewModel.uiState.value
        assertTrue("expected Ready, got $state", state is ProviderConversationUiState.Ready)
        val ready = state as ProviderConversationUiState.Ready
        assertTrue("expected no bubbles, got ${ready.items}", ready.items.isEmpty())
        assertTrue(ready.detail.messages.isEmpty())
    }

    fun readReadyStateOrNull(): ProviderConversationUiState.Ready? =
        viewModel.uiState.value as? ProviderConversationUiState.Ready

    fun readState(): ProviderConversationUiState = viewModel.uiState.value

    fun thenStagedMediaBytes(expected: ByteArray) {
        val ready = readReadyStateOrNull()
            ?: error("expected Ready, got ${readState()}")
        val staged = ready.pendingMedia
            ?: error("expected pendingMedia, got null")
        assertTrue(
            "expected ${expected.size}B, got ${staged.bytes.size}B",
            expected.contentEquals(staged.bytes),
        )
    }

    fun thenNoStagedMedia() {
        val ready = readReadyStateOrNull()
            ?: error("expected Ready, got ${readState()}")
        org.junit.Assert.assertNull(ready.pendingMedia)
    }

    fun thenImageBubbleConfirmed(mediaId: String) {
        val ready = readReadyStateOrNull()
            ?: error("expected Ready, got ${readState()}")
        val confirmed = ready.items
            .filterIsInstance<ChatListItem.ServerConfirmed>()
            .lastOrNull { item ->
                item.message.images.any { it.id == mediaId }
            }
            ?: error(
                "expected a ServerConfirmed image bubble with id $mediaId, got ${ready.items}",
            )
    }

    fun thenOnePendingBubbleExists(expectedContent: String) {
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        val pending = ready.items.filterIsInstance<ChatListItem.LocalPending>()
        assertEquals(
            "expected exactly one pending bubble, got ${ready.items}",
            1,
            pending.size,
        )
        assertEquals(expectedContent, pending.single().content)
        assertEquals(true, ready.sending)
        assertEquals("", ready.promptInput)
    }

    fun thenBubbleReplacedByServerConfirmed(serverMessageId: Int) {
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        val confirmed = ready.items.filterIsInstance<ChatListItem.ServerConfirmed>()
        assertTrue(
            "expected at least one ServerConfirmed bubble for id $serverMessageId, got ${ready.items}",
            confirmed.any { it.message.id == serverMessageId },
        )
        assertEquals(10_000L, confirmed.single { it.message.id == serverMessageId }.message.createdOnEpochMillis)
        assertEquals(false, ready.sending)
        assertEquals("", ready.promptInput)
    }

    fun thenBubbleReplacedByLocalFailed(expectedContent: String) {
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        val failed = ready.items.filterIsInstance<ChatListItem.LocalFailed>()
        assertEquals(
            "expected exactly one failed bubble, got ${ready.items}",
            1,
            failed.size,
        )
        assertEquals(expectedContent, failed.single().content)
        assertEquals(false, ready.sending)
    }

    fun thenOnlyOneSendWasFired() {
        // For 05-PCC the assertion is scoped to the retry action:
        // tapping Retry must fire exactly one new send (no double-
        // submit, no spurious retries from the optimistic-then-
        // failed path).
        assertEquals(
            "expected exactly one send fired by the retry action",
            sendCallsAtRetryBaseline + 1,
            repository.sendCalls,
        )
    }

    fun thenSendButtonIsDisabled() {
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        val canSend = ready.promptInput.isNotBlank() && !ready.sending
        assertTrue("expected send to be disabled, was enabled", !canSend)
    }

    fun thenNoSendWasFired() {
        assertEquals(0, repository.sendCalls)
    }

    override fun close() {
        ownedModels.forEach { androidx.lifecycle.ViewModelStore().apply { put("conversation", it) }.clear() }
        repository.pendingDetailCompletion?.cancel()
        repository.sendGate?.cancel()
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    fun givenReceivedImage() {
        repository.detailOutcome = ConversationDetailOutcome.Success(detail(listOf(ConversationMessage(
            99, ConversationSender.Provider, "", 10_000L,
            media = MediaReference.Image("file-uuid-99", "https://example.test/image.jpg", "image/jpeg", "image.jpg"),
        ))))
    }

    fun selectImages(count: Int, mime: String) {
        givenMediaPickerReturns(byteArrayOf(1, 2, 3, 4), mime, "image")
        viewModel.onImagesPicked(List(count) { "content://bdd/$it" })
        scheduler.advanceUntilIdle()
    }

    fun assertImageCount(count: Int) {
        val ready = readReadyStateOrNull()!!
        assertEquals(count, ready.pendingImages.size)
        assertTrue(ready.canStartComposerOperation)
    }

    fun replaceAndDiscard() {
        givenMediaPickerReturns(byteArrayOf(9), "image/png", "replacement.png")
        viewModel.onImagesPicked(listOf("content://bdd/replacement"), 1)
        scheduler.advanceUntilIdle()
        viewModel.onDiscardImage(0)
    }

    fun assertReplacement() {
        val images = readReadyStateOrNull()!!.pendingImages
        assertEquals(2, images.size)
        assertEquals("replacement.png", images[0].originalName)
        assertTrue(images[1].bytes.contentEquals(byteArrayOf(1, 2, 3, 4)))
    }

    fun selectInvalid(kind: String) {
        if (kind == "ilegible") {
            readFailure = java.io.IOException("Unreadable")
        } else givenMediaPickerReturns(
            when (kind) { "vacía" -> byteArrayOf(); "mayor a 5MiB" -> ByteArray(5 * 1024 * 1024 + 1); else -> byteArrayOf(1) },
            if (kind == "no soportada") "image/gif" else "image/jpeg", "invalid",
        )
        whenProviderPicksMedia()
    }

    fun assertLocalFailure() {
        assertNotNull(readReadyStateOrNull()!!.transientMediaError)
        assertTrue(readReadyStateOrNull()!!.pendingImages.isEmpty())
        thenNoSendWasFired()
    }

    fun assertFourthRejected() {
        assertImageCount(3)
        assertNotNull(readReadyStateOrNull()!!.transientMediaError)
    }

    fun selectExactLimit() {
        givenMediaPickerReturns(ByteArray(5 * 1024 * 1024), "image/jpeg", "boundary.jpg")
        whenProviderPicksMedia()
    }

    fun cancelSelection(prompt: String) {
        whenTyping(prompt)
        viewModel.onImagesPicked(emptyList())
        scheduler.advanceUntilIdle()
    }

    fun assertDraft(prompt: String) = assertEquals(prompt, readReadyStateOrNull()!!.promptInput)
    fun repeatedImageSend() { repeat(3) { viewModel.onSendClick() }; scheduler.advanceUntilIdle() }

    fun recordAudio(seconds: Int = 3, kind: String = "WebM válido") {
        stagedMedia = MediaUpload.Audio(
            ByteArray(if (kind == "cinco MiB") 5 * 1024 * 1024 else if (kind == "mayor a cinco MiB") 5 * 1024 * 1024 + 1 else 4),
            if (kind == "AAC") "audio/aac" else "audio/webm", "clip.webm",
            when (kind) { "300 segundos" -> 300_000L; "301 segundos" -> 301_000L; "duración cero" -> 0L; "muy corto" -> 999L; else -> seconds * 1000L },
        )
        check(microphoneGranted)
        viewModel.onStartRecording()
        scheduler.runCurrent()
        scheduler.advanceTimeBy(seconds * 1000L)
        viewModel.onStopRecording()
        scheduler.runCurrent()
    }

    fun startAudio() { check(microphoneGranted); viewModel.onStartRecording(); scheduler.runCurrent() }
    fun cancelAudio() { viewModel.onCancelRecording(); scheduler.runCurrent() }
    fun denyMicrophone() = viewModel.onMicrophonePermissionDenied()
    fun backgroundAudio() { viewModel.onConversationBackgrounded(); scheduler.runCurrent() }
    fun assertPermissionDenied() {
        assertEquals(0, recorder.starts)
        assertEquals(SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.MicrophonePermission), readReadyStateOrNull()!!.transientMediaError)
    }
    fun assertAudioCancelled() {
        assertTrue(!recorder.active)
        assertTrue(readReadyStateOrNull()!!.recordingState is com.loresuelvo.serviceprovider.ui.screens.conversation.RecordingState.Idle)
        thenNoSendWasFired()
    }
    fun assertAudioResult(result: String) {
        assertEquals(result == "acepta", readReadyStateOrNull()!!.pendingMedia is MediaUpload.Audio)
        assertEquals(result == "rechaza", readReadyStateOrNull()!!.transientMediaError != null)
        if (result == "rechaza") assertEquals(1, recorder.discards)
        thenNoSendWasFired()
    }
    fun reachAudioCeiling() {
        stagedMedia = MediaUpload.Audio(byteArrayOf(1), "audio/webm", "clip.webm", 300_000L)
        scheduler.advanceTimeBy(300_000L)
        scheduler.runCurrent()
    }
    fun previewAudio() { viewModel.onPlayPreview(); player.currentPositionMillis.value = 1500; scheduler.runCurrent(); viewModel.onPauseAudio(); scheduler.runCurrent() }
    fun assertPreviewPaused() { assertEquals(1500L, readReadyStateOrNull()!!.playingPositionMillis); assertTrue(!readReadyStateOrNull()!!.isPlaying); thenNoSendWasFired() }
    fun assertPreviewDiscarded() { thenNoStagedMedia(); assertEquals(1, recorder.discards); assertTrue(!player.isPlaying.value) }
    fun givenAudioSend() { givenSendWillSucceedWithMedia(99); pauseSendOnGate() }
    fun assertPendingAudio() {
        val pending = readReadyStateOrNull()!!.items.filterIsInstance<ChatListItem.LocalPending>().single()
        assertEquals(3000L, (pending.pendingMedia as MediaUpload.Audio).durationMillis)
        assertEquals("", pending.content)
    }
    fun assertConfirmedAudio() {
        releaseSendGate()
        val message = readReadyStateOrNull()!!.items.filterIsInstance<ChatListItem.ServerConfirmed>().single().message
        assertEquals(99, message.id)
        assertTrue((message.media as MediaReference.Audio).url.startsWith("https://"))
    }
    fun givenReceivedAudio(count: Int = 1) {
        repository.detailOutcome = ConversationDetailOutcome.Success(detail(List(count) { index -> ConversationMessage(
            id = 99 + index, sender = ConversationSender.Consumer, content = "", createdOnEpochMillis = 10_000L,
            media = MediaReference.Audio("audio-$index", "https://example.test/audio-$index.webm", "audio/webm", "audio.webm", 5000L),
        ) }))
    }
    fun playReceivedAudio() { viewModel.onPlayAudio("99", "https://example.test/audio-0.webm"); scheduler.runCurrent() }
    fun assertReceivedProgress() {
        val message = readReadyStateOrNull()!!.detail.messages.single()
        val duration = (message.media as MediaReference.Audio).durationMillis
        assertEquals(5000L, duration)
        for (position in listOf(1000L, 2500L, duration)) {
            player.currentPositionMillis.value = position
            scheduler.runCurrent()
            assertEquals(position, readReadyStateOrNull()!!.playingPositionMillis)
            assertTrue(readReadyStateOrNull()!!.isPlaying)
        }
        player.stop()
        scheduler.runCurrent()
        assertTrue(!readReadyStateOrNull()!!.isPlaying)
    }
    fun seekAndSwitchAudio() {
        playReceivedAudio()
        viewModel.onPauseAudio()
        viewModel.onSeekAudio("99", "https://example.test/audio-0.webm", 2000L); scheduler.runCurrent()
        assertEquals(2000L, player.currentPositionMillis.value)
        assertTrue(!player.isPlaying.value)
        viewModel.onPlayAudio("100", "https://example.test/audio-1.webm"); scheduler.runCurrent()
    }
    fun assertSecondAudio() { assertEquals("100", readReadyStateOrNull()!!.playingMediaKey); assertEquals(0L, player.currentPositionMillis.value); assertTrue(player.isPlaying.value) }
    fun givenFailedAudio() {
        grantMicrophone(); givenEmptyDetail(); whenOpeningConversation(); recordAudio(); givenSendWillFailWithNetwork(); whenTappingSend()
    }
    fun retryAudio(prompt: String) { givenSendWillSucceedWithMedia(99); pauseSendOnGate(); whenRepeatedRetryWithDraft(prompt) }
    fun assertAudioRetry() { thenOnlyOneSendWasFired(); thenDraftSurvives("Otro mensaje") }

    private class BddAudioRecorder : com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder {
        var starts = 0
        var discards = 0
        var active = false
        override fun start(): Result<Unit> { starts++; active = true; return Result.success(Unit) }
        override fun stop(): Result<String> { active = false; return Result.success("file:///bdd/audio.webm") }
        override fun cancel() { active = false }
        override fun discard(uri: String) { discards++ }
    }
    private class BddAudioPlayer : com.loresuelvo.serviceprovider.domain.conversation.AudioPlayer {
        override val isPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val currentPositionMillis = kotlinx.coroutines.flow.MutableStateFlow(0L)
        override fun play(url: String, startPositionMillis: Long) { currentPositionMillis.value = startPositionMillis; isPlaying.value = true }
        override fun seekTo(positionMillis: Long) { currentPositionMillis.value = positionMillis }
        override fun pause() { isPlaying.value = false }
        override fun stop() { isPlaying.value = false; currentPositionMillis.value = 0L }
    }

    // --- helpers --------------------------------------------------------

    private fun newViewModel(): ProviderConversationViewModel = ProviderConversationViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to conversationId)),
        getConversationById = GetConversationByIdUseCase(repository),
        sendMessage = SendMessageUseCase(repository),
        sendMediaMessage = SendMediaMessageUseCase(repository),
        mediaReader = BddMediaReader { readFailure?.let { throw it }; stagedMedia },
        audioRecorder = recorder,
        audioPlayer = player,
        recordingTimeSource = object : com.loresuelvo.serviceprovider.ui.screens.conversation.RecordingTimeSource() {
            override fun nowMillis() = scheduler.currentTime
        },
    ).also(ownedModels::add)

    private fun detail(messages: List<ConversationMessage>): ConversationDetail = ConversationDetail(
        id = conversationId,
        status = ConversationStatus.Active,
        counterpart = ConversationCounterpart(
            id = 7,
            name = "Ana",
            surname = "Pérez",
            profilePhotoUrl = null,
        ),
        messages = messages,
        updatedOnEpochMillis = 1L,
    )

    private class BddMediaReader(
        private val media: () -> MediaUpload?,
    ) : com.loresuelvo.serviceprovider.domain.conversation.MediaReader {
        override suspend fun read(uri: String): MediaUpload =
            media() ?: error("BDD media reader has no staged media for $uri")
    }

    private class FakeConversationRepository : ConversationRepository {
        var detailOutcome: ConversationDetailOutcome = ConversationDetailOutcome.Success(
            detail = ConversationDetail(
                id = 42,
                status = ConversationStatus.Active,
                counterpart = ConversationCounterpart(
                    id = 7,
                    name = "Ana",
                    surname = "Pérez",
                    profilePhotoUrl = null,
                ),
                messages = emptyList(),
                updatedOnEpochMillis = 1L,
            ),
        )
        var pendingDetailCompletion: CompletableDeferred<Unit>? = null
        var detailCalls: Int = 0
        var sendOutcome: SendMessageOutcome =
            SendMessageOutcome.Failure.Network(cause = RuntimeException("not set"))
        var sendCalls: Int = 0
        var sendGate: CompletableDeferred<Unit>? = null

        override suspend fun getConversations(): ConversationsOutcome =
            error("not exercised by ProviderConversationWorld")

        override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome {
            detailCalls += 1
            pendingDetailCompletion?.await()
            return detailOutcome
        }

        override suspend fun sendMessage(
            conversationId: Int,
            content: String,
        ): SendMessageOutcome {
            sendCalls += 1
            sendGate?.await()
            return sendOutcome
        }

        override suspend fun sendMediaMessage(
            conversationId: Int,
            media: List<com.loresuelvo.serviceprovider.domain.conversation.MediaUpload>,
        ): SendMessageOutcome {
            sendCalls += 1
            sendGate?.await()
            // Synthesize a server-confirmed media response so the
            // BDD scenarios can assert on the persisted bubble.
            // The id is derived from the configured sendOutcome to
            // keep the success path distinguishable per scenario.
            val baseId = (sendOutcome as? SendMessageOutcome.Success)
                ?.message?.id ?: 99
            return when (val outcome = sendOutcome) {
                is SendMessageOutcome.Success -> SendMessageOutcome.Success(
                    message = ConversationMessage(
                        id = outcome.message.id.takeIf { it != 0 } ?: baseId,
                        sender = ConversationSender.Provider,
                        content = outcome.message.content,
                        createdOnEpochMillis = outcome.message.createdOnEpochMillis,
                        media = media.filterIsInstance<MediaUpload.Audio>().singleOrNull()?.let { upload ->
                            MediaReference.Audio("audio-99", "https://example.test/audio.webm", upload.mimeType, upload.originalName, upload.durationMillis)
                        },
                        images = media.filterIsInstance<MediaUpload.Image>().map { upload ->
                            MediaReference.Image(
                                id = "file-uuid-${outcome.message.id.takeIf { it != 0 } ?: baseId}",
                                url = "https://example.test/${upload.originalName}",
                                mimeType = upload.mimeType,
                                originalName = upload.originalName,
                            )
                        },
                    ),
                )
                else -> outcome
            }
        }
    }
}
