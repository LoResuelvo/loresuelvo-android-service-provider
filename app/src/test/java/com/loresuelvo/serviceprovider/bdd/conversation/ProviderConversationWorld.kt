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
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProviderConversationWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val repository = FakeConversationRepository()

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
    private lateinit var mediaPickerUri: android.net.Uri
    private lateinit var stagedMediaUri: android.net.Uri
    private var stagedMedia: MediaUpload.Image? = null

    fun seedMediaUri(uri: android.net.Uri) {
        mediaPickerUri = uri
        stagedMediaUri = uri
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
        viewModel.onMediaPicked(stagedMediaUri)
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
                val m = item.message.media
                m is MediaReference.Image && m.id == mediaId
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
        if (::viewModel.isInitialized) scheduler.advanceUntilIdle()
        repository.pendingDetailCompletion?.complete(Unit)
        repository.sendGate?.complete(Unit)
        Dispatchers.resetMain()
    }

    // --- helpers --------------------------------------------------------

    private fun newViewModel(): ProviderConversationViewModel = ProviderConversationViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to conversationId)),
        getConversationById = GetConversationByIdUseCase(repository),
        sendMessage = SendMessageUseCase(repository),
        sendMediaMessage = SendMediaMessageUseCase(repository),
        mediaReader = BddMediaReader(stagedMedia),
        audioRecorder = NotExercisedAudioRecorder,
        audioPlayer = NotExercisedAudioPlayer,
    )

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

    private object NotExercisedMediaReader :
        com.loresuelvo.serviceprovider.data.media.MediaReader {
        override suspend fun read(uri: android.net.Uri):
            com.loresuelvo.serviceprovider.domain.conversation.MediaUpload =
            error("MediaReader is not exercised by US-A BDD scenarios")
    }

    private class BddMediaReader(
        private val media: MediaUpload.Image?,
    ) : com.loresuelvo.serviceprovider.data.media.MediaReader {
        override suspend fun read(uri: android.net.Uri): MediaUpload =
            media ?: error("BDD media reader has no staged media for $uri")
    }

    private object NotExercisedAudioRecorder :
        com.loresuelvo.serviceprovider.data.media.AudioRecorder {
        override fun start(): Result<Unit> =
            error("AudioRecorder is not exercised by US-A BDD scenarios")
        override fun stop(): Result<android.net.Uri> =
            error("AudioRecorder is not exercised by US-A BDD scenarios")
        override fun cancel() =
            error("AudioRecorder is not exercised by US-A BDD scenarios")
    }

    private object NotExercisedAudioPlayer :
        com.loresuelvo.serviceprovider.data.media.AudioPlayer {
        override val isPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val currentPositionMillis = kotlinx.coroutines.flow.MutableStateFlow(0L)
        override fun play(url: String, startPositionMillis: Long) = Unit
        override fun pause() = Unit
        override fun stop() = Unit
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
                        media = media.firstOrNull()?.let { upload ->
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
