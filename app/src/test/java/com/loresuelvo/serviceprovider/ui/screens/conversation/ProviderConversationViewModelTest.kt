package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationByIdUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMediaMessageUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMessageUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderConversationViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val ownedModels = mutableListOf<ProviderConversationViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        ownedModels.forEach { androidx.lifecycle.ViewModelStore().apply { put("conversation", it) }.clear() }
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun delayed_plural_media_send_and_retry_run_once_and_retain_newer_draft() = runTest(scheduler) {
        val repo = RecordingRepository(detailOutcome = detailOutcome(emptyList()),
            sendOutcome = SendMessageOutcome.Failure.Server(503, "Post failed"), sendGate = CompletableDeferred())
        val image = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(byteArrayOf(1), "image/jpeg", "image")
        val vm = viewModelWithMediaReader(repo, FakeMediaReader(mapOf("content://image" to image)))
        advanceUntilIdle()
        vm.onImagesPicked(List(3) { "content://image" })
        advanceUntilIdle()
        repeat(3) { vm.onSendClick() }
        advanceUntilIdle()
        assertEquals(1, repo.sendCalls)
        assertEquals(3, (readyState(vm).items.single() as ChatListItem.LocalPending).pendingImages.size)
        repo.sendGate!!.complete(Unit)
        advanceUntilIdle()
        val failed = readyState(vm).items.single() as ChatListItem.LocalFailed
        assertEquals(3, failed.pendingImages.size)
        repo.sendGate = CompletableDeferred()
        repo.sendOutcome = SendMessageOutcome.Success(message(99, ConversationSender.Provider, ""))
        vm.onPromptChange("Newer draft")
        repeat(3) { vm.onRetrySendFailedBubble(failed.key) }
        advanceUntilIdle()
        assertEquals(2, repo.sendCalls)
        repo.sendGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("Newer draft", readyState(vm).promptInput)
        assertTrue(readyState(vm).items.single() is ChatListItem.ServerConfirmed)
    }

    @Test
    fun image_selection_preserves_order_rejects_fourth_replaces_and_discards_individually() = runTest(scheduler) {
        val repo = RecordingRepository(detailOutcome = detailOutcome(emptyList()))
        val images = (1..4).associate { "content://image/$it" to com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(byteArrayOf(it.toByte()), "image/jpeg", "$it.jpg") }
        val vm = viewModelWithMediaReader(repo, FakeMediaReader(images))
        advanceUntilIdle()
        vm.onPromptChange("Keep draft")
        vm.onImagesPicked(images.keys.take(3))
        advanceUntilIdle()
        val original = readyState(vm).pendingImages
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg"), original.map { it.originalName })
        assertEquals("Keep draft", readyState(vm).promptInput)
        vm.onImagesPicked(listOf("content://image/4"))
        assertEquals(original, readyState(vm).pendingImages)
        assertNotNull(readyState(vm).transientMediaError)
        vm.onImagesPicked(listOf("content://image/4"), replaceIndex = 1)
        advanceUntilIdle()
        vm.onDiscardImage(0)
        assertEquals(listOf("4.jpg", "3.jpg"), readyState(vm).pendingImages.map { it.originalName })
    }

    @Test
    fun pending_image_read_reserves_composer_and_cancellation_keeps_draft() = runTest(scheduler) {
        val repo = RecordingRepository(detailOutcome = detailOutcome(emptyList()))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var reads = 0
        val reader = object : com.loresuelvo.serviceprovider.domain.conversation.MediaReader {
            override suspend fun read(uri: String): com.loresuelvo.serviceprovider.domain.conversation.MediaUpload {
                reads++; gate.await()
                return com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(byteArrayOf(1), "image/jpeg", "one")
            }
        }
        val vm = viewModelWithMediaReader(repo, reader)
        advanceUntilIdle()
        vm.onPromptChange("Draft")
        vm.onImagesPicked(emptyList())
        assertEquals("Draft", readyState(vm).promptInput)
        vm.onImagesPicked(listOf("content://one"))
        vm.onImagesPicked(listOf("content://two"))
        vm.onSendClick()
        scheduler.runCurrent()
        assertEquals(1, reads)
        assertTrue(readyState(vm).readingMedia)
        vm.onConversationBackgrounded()
        assertTrue(readyState(vm).readingMedia)
        vm.onImagesPicked(listOf("content://three"))
        scheduler.runCurrent()
        assertEquals(1, reads)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, readyState(vm).pendingImages.size)
        assertEquals("Draft", readyState(vm).promptInput)
        assertFalse(readyState(vm).sending)
    }

    @Test
    fun load_emits_Ready_with_server_confirmed_messages() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(
            messages = listOf(
                message(id = 1, sender = ConversationSender.Consumer, content = "Hola"),
                message(id = 2, sender = ConversationSender.Provider, content = "Listo"),
            ),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        val ready = readyState(vm)
        assertEquals(2, ready.items.size)
        assertTrue(ready.items[0] is ChatListItem.ServerConfirmed)
        assertTrue(ready.items[1] is ChatListItem.ServerConfirmed)
        assertEquals("Hola", ready.items[0].content)
        assertEquals("", ready.promptInput)
        assertEquals(false, ready.sending)
    }

    @Test
    fun load_emits_Error_on_network_failure() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = ConversationDetailOutcome.Failure.Network(
            cause = RuntimeException("offline"),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        val error = vm.uiState.value as ProviderConversationUiState.Error
        assertTrue(
            "expected Network failure, got ${error.failure}",
            error.failure is ConversationDetailOutcome.Failure.Network,
        )
    }

    @Test
    fun load_emits_Ready_with_empty_messages_for_brand_new_conversation() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val vm = viewModel(repo)
        advanceUntilIdle()

        val ready = readyState(vm)
        assertTrue(ready.items.isEmpty())
    }

    @Test
    fun onSendClick_appends_pending_bubble_and_replaces_with_server_confirmed_on_success() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            repo.sendOutcome = SendMessageOutcome.Success(
                message = message(
                    id = 99,
                    sender = ConversationSender.Provider,
                    content = "Listo para empezar",
                ),
            )
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.onPromptChange("Listo para empezar")
            vm.onSendClick()
            advanceUntilIdle()

            val ready = readyState(vm)
            assertEquals(1, ready.items.size)
            val resolved = ready.items[0]
            assertTrue(
                "expected ServerConfirmed, got $resolved",
                resolved is ChatListItem.ServerConfirmed,
            )
            assertEquals(99, (resolved as ChatListItem.ServerConfirmed).message.id)
            assertEquals("", ready.promptInput)
            assertEquals(false, ready.sending)
        }

    @Test
    fun onSendClick_replaces_pending_with_failed_bubble_on_network_failure() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            repo.sendOutcome = SendMessageOutcome.Failure.Network(
                cause = RuntimeException("offline"),
            )
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.onPromptChange("Mañana a las 10")
            vm.onSendClick()
            advanceUntilIdle()

            val ready = readyState(vm)
            assertEquals(1, ready.items.size)
            val resolved = ready.items[0]
            assertTrue(
                "expected LocalFailed, got $resolved",
                resolved is ChatListItem.LocalFailed,
            )
            assertEquals(
                "Mañana a las 10",
                (resolved as ChatListItem.LocalFailed).pendingPrompt,
            )
            assertEquals(false, ready.sending)
        }

    @Test
    fun onSendClick_is_a_noop_when_prompt_is_blank() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onPromptChange("   ")
        vm.onSendClick()
        advanceUntilIdle()

        val ready = readyState(vm)
        assertTrue(ready.items.isEmpty())
        assertEquals(false, ready.sending)
        assertEquals(0, repo.sendCalls)
    }

    @Test
    fun onSendClick_is_a_noop_when_already_sending() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val gate = CompletableDeferred<Unit>()
        repo.sendOutcome = SendMessageOutcome.Success(
            message = message(id = 1, sender = ConversationSender.Provider, content = "x"),
        )
        repo.sendGate = gate
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onPromptChange("hola")
        vm.onSendClick()
        advanceUntilIdle()
        // First send is now in flight (waiting on the gate).
        assertEquals(true, readyState(vm).sending)

        vm.onPromptChange("chau")
        vm.onSendClick()
        advanceUntilIdle()

        // Second send did not fire because sending=true.
        assertEquals(1, repo.sendCalls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun onRetrySendFailedBubble_resubmits_and_replaces_with_server_confirmed() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            repo.sendOutcome = SendMessageOutcome.Failure.Network(
                cause = RuntimeException("offline"),
            )
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.onPromptChange("Mañana a las 10")
            vm.onSendClick()
            advanceUntilIdle()

            val failedKey = readyState(vm).items
                .filterIsInstance<ChatListItem.LocalFailed>()
                .single()
                .key

            repo.sendOutcome = SendMessageOutcome.Success(
                message = message(
                    id = 7,
                    sender = ConversationSender.Provider,
                    content = "Mañana a las 10",
                ),
            )
            vm.onRetrySendFailedBubble(failedKey)
            advanceUntilIdle()

            val ready = readyState(vm)
            assertEquals(1, ready.items.size)
            val resolved = ready.items[0]
            assertTrue(resolved is ChatListItem.ServerConfirmed)
            assertEquals(7, (resolved as ChatListItem.ServerConfirmed).message.id)
            assertEquals(false, ready.sending)
        }

    @Test
    fun onRetryLoad_replays_load_after_a_failed_initial_fetch() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = ConversationDetailOutcome.Failure.Network(
            cause = RuntimeException("offline"),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ProviderConversationUiState.Error)

        repo.detailOutcome = detailOutcome(messages = emptyList())
        vm.onRetryLoad()
        advanceUntilIdle()

        val ready = readyState(vm)
        assertTrue(ready.items.isEmpty())
    }

    @Test
    fun onPromptChange_is_a_noop_outside_Ready() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = ConversationDetailOutcome.Failure.Network(
            cause = RuntimeException("offline"),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onPromptChange("hola")
        advanceUntilIdle()

        // Still in Error state — the prompt change was ignored.
        assertTrue(vm.uiState.value is ProviderConversationUiState.Error)
    }

    @Test
    fun onMediaPicked_stages_the_image_as_pendingMedia() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val image = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(
            bytes = byteArrayOf(1, 2, 3, 4),
            mimeType = "image/jpeg",
            originalName = "kitchen.jpg",
        )
        val reader = FakeMediaReader(mapOf("content://images/42" to image))
        val vm = viewModelWithMediaReader(repo, reader)
        advanceUntilIdle()

        vm.onMediaPicked(android.net.Uri.parse("content://images/42"))
        advanceUntilIdle()

        val ready = readyState(vm)
        assertEquals(image, ready.pendingMedia)
        assertEquals("", ready.promptInput)
    }

    @Test
    fun onMediaPicked_with_a_reader_failure_sets_transientMediaError() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val reader = ThrowingMediaReader
        val vm = viewModelWithMediaReader(repo, reader)
        advanceUntilIdle()

        vm.onMediaPicked(android.net.Uri.parse("content://missing/1"))
        advanceUntilIdle()

        val ready = readyState(vm)
        assertNull(ready.pendingMedia)
        assertTrue(
            "expected Network failure, got ${ready.transientMediaError}",
            ready.transientMediaError
                is com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.Network,
        )
    }

    @Test
    fun onClearStagedMedia_discards_the_pending_image() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val image = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(
            bytes = byteArrayOf(1),
            mimeType = "image/jpeg",
            originalName = "x.jpg",
        )
        val reader = FakeMediaReader(mapOf("content://x" to image))
        val vm = viewModelWithMediaReader(repo, reader)
        advanceUntilIdle()
        vm.onMediaPicked(android.net.Uri.parse("content://x"))
        advanceUntilIdle()
        assertNotNull(readyState(vm).pendingMedia)

        vm.onClearStagedMedia()
        advanceUntilIdle()
        assertNull(readyState(vm).pendingMedia)
    }

    @Test
    fun onSendClick_with_pending_media_fires_sendMediaMessage_with_an_optimistic_pending_bubble() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            val image = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(
                bytes = byteArrayOf(1, 2, 3),
                mimeType = "image/jpeg",
                originalName = "kitchen.jpg",
            )
            repo.sendOutcome = SendMessageOutcome.Success(
                message = com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage(
                    id = 99,
                    sender = com.loresuelvo.serviceprovider.domain.conversation.ConversationSender.Provider,
                    content = "",
                    createdOnEpochMillis = 10_000L,
                    media = com.loresuelvo.serviceprovider.domain.conversation.MediaReference.Image(
                        id = "file-uuid-1",
                        url = "https://example.test/kitchen.jpg",
                        mimeType = "image/jpeg",
                        originalName = "kitchen.jpg",
                    ),
                ),
            )
            val reader = FakeMediaReader(mapOf("content://x" to image))
            val vm = viewModelWithMediaReader(repo, reader)
            advanceUntilIdle()
            vm.onMediaPicked(android.net.Uri.parse("content://x"))
            advanceUntilIdle()

            vm.onSendClick()
            advanceUntilIdle()

            assertEquals(1, repo.sendCalls)
            val ready = readyState(vm)
            assertEquals(false, ready.sending)
            assertEquals(1, ready.items.size)
            val confirmed = ready.items.single()
            assertTrue(
                "expected ServerConfirmed, got $confirmed",
                confirmed is ChatListItem.ServerConfirmed,
            )
            assertEquals(
                "https://example.test/kitchen.jpg",
                ((confirmed as ChatListItem.ServerConfirmed).message.media
                    as com.loresuelvo.serviceprovider.domain.conversation.MediaReference.Image).url,
            )
        }

    @Test
    fun onSendClick_with_pending_media_replaces_pending_with_failed_bubble_on_network_failure() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            val image = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(
                bytes = byteArrayOf(1),
                mimeType = "image/jpeg",
                originalName = "x.jpg",
            )
            repo.sendOutcome = SendMessageOutcome.Failure.Network(
                cause = RuntimeException("offline"),
            )
            val reader = FakeMediaReader(mapOf("content://x" to image))
            val vm = viewModelWithMediaReader(repo, reader)
            advanceUntilIdle()
            vm.onMediaPicked(android.net.Uri.parse("content://x"))
            advanceUntilIdle()

            vm.onSendClick()
            advanceUntilIdle()

            val ready = readyState(vm)
            assertEquals(1, ready.items.size)
            val failed = ready.items.single()
            assertTrue(
                "expected LocalFailed, got $failed",
                failed is ChatListItem.LocalFailed,
            )
            assertEquals(
                image,
                (failed as ChatListItem.LocalFailed).pendingMedia,
            )
            assertEquals(false, ready.sending)
        }

    @Test
    fun onRetrySendFailedBubble_with_media_resubmits_and_replaces_with_confirmed() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            val image = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(
                bytes = byteArrayOf(1),
                mimeType = "image/jpeg",
                originalName = "x.jpg",
            )
            repo.sendOutcome = SendMessageOutcome.Failure.Network(
                cause = RuntimeException("offline"),
            )
            val reader = FakeMediaReader(mapOf("content://x" to image))
            val vm = viewModelWithMediaReader(repo, reader)
            advanceUntilIdle()
            vm.onMediaPicked(android.net.Uri.parse("content://x"))
            advanceUntilIdle()
            vm.onSendClick()
            advanceUntilIdle()

            val failedKey = readyState(vm).items
                .filterIsInstance<ChatListItem.LocalFailed>()
                .single()
                .key

            repo.sendCalls = 0
            repo.sendOutcome = SendMessageOutcome.Success(
                message = com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage(
                    id = 7,
                    sender = com.loresuelvo.serviceprovider.domain.conversation.ConversationSender.Provider,
                    content = "",
                    createdOnEpochMillis = 1L,
                    media = com.loresuelvo.serviceprovider.domain.conversation.MediaReference.Image(
                        id = "file-uuid-1",
                        url = "https://example.test/x.jpg",
                        mimeType = "image/jpeg",
                        originalName = "x.jpg",
                    ),
                ),
            )
            vm.onRetrySendFailedBubble(failedKey)
            advanceUntilIdle()

            assertEquals(1, repo.sendCalls)
            val ready = readyState(vm)
            assertEquals(1, ready.items.size)
            val resolved = ready.items.single()
            assertTrue(
                "expected ServerConfirmed, got $resolved",
                resolved is ChatListItem.ServerConfirmed,
            )
            assertEquals(7, (resolved as ChatListItem.ServerConfirmed).message.id)
        }

    @Test
    fun restricted_states_do_not_read_media_record_or_send() = runTest(scheduler) {
        for (status in listOf(ConversationStatus.Pending, ConversationStatus.Rejected, ConversationStatus.Unsupported("future"))) {
            val detail = detailOutcome(emptyList()).detail.copy(status = status)
            val repo = RecordingRepository(detailOutcome = ConversationDetailOutcome.Success(detail))
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onPromptChange("blocked")
            vm.onMediaPicked(android.net.Uri.parse("content://blocked"))
            vm.onStartRecording()
            vm.onSendClick()
            vm.onRetrySendFailedBubble("missing")
            advanceUntilIdle()
            assertEquals(0, repo.sendCalls)
            assertFalse(vm.canStartComposerOperation())
            assertEquals("", readyState(vm).promptInput)
        }
    }

    @Test
    fun unloaded_states_reject_side_effects() = runTest(scheduler) {
        val repo = RecordingRepository(detailOutcome = ConversationDetailOutcome.Failure.Unauthorized)
        val vm = viewModel(repo)
        vm.onMediaPicked(android.net.Uri.parse("content://blocked"))
        vm.onStartRecording()
        vm.onSendClick()
        advanceUntilIdle()
        vm.onMediaPicked(android.net.Uri.parse("content://blocked"))
        vm.onStartRecording()
        vm.onSendClick()
        advanceUntilIdle()
        assertEquals(0, repo.sendCalls)
        assertTrue(vm.uiState.value is ProviderConversationUiState.Error)
    }

    @Test
    fun wrong_detail_id_never_exposes_counterpart_or_messages() = runTest(scheduler) {
        val detail = detailOutcome(listOf(message(1, ConversationSender.Consumer, "private"))).detail.copy(id = 99)
        val vm = viewModel(RecordingRepository(detailOutcome = ConversationDetailOutcome.Success(detail)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is ProviderConversationUiState.Error)
        assertFalse(vm.canStartComposerOperation())
    }

    @Test
    fun access_loss_on_send_removes_messages_and_composer() = runTest(scheduler) {
        for (failure in listOf(
            SendMessageOutcome.Failure.Unauthorized,
            SendMessageOutcome.Failure.Server(403, "private diagnostic"),
            SendMessageOutcome.Failure.ConversationNotFound("private diagnostic"),
        )) {
            val repo = RecordingRepository(sendOutcome = failure)
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onPromptChange("message")
            vm.onSendClick()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is ProviderConversationUiState.Error)
            vm.onStartRecording()
            vm.onSendClick()
            assertEquals(1, repo.sendCalls)
        }
    }

    @Test
    fun delayed_retry_is_single_and_preserves_newer_draft() = runTest(scheduler) {
        val repo = RecordingRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.onPromptChange("failed")
        vm.onSendClick()
        advanceUntilIdle()
        val key = readyState(vm).items.single().key
        repo.sendGate = CompletableDeferred()
        repo.sendOutcome = SendMessageOutcome.Success(message(99, ConversationSender.Provider, "failed"))
        vm.onPromptChange("new draft")
        repeat(3) { vm.onRetrySendFailedBubble(key) }
        advanceUntilIdle()
        assertEquals(2, repo.sendCalls)
        assertEquals("new draft", readyState(vm).promptInput)
        vm.onRetryLoad()
        assertTrue(readyState(vm).sending)
        repo.sendGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("new draft", readyState(vm).promptInput)
        assertEquals(99, (readyState(vm).items.single() as ChatListItem.ServerConfirmed).message.id)
    }

    @Test
    fun restoring_new_model_loads_server_snapshot_without_replaying_local_failure() = runTest(scheduler) {
        val repo = RecordingRepository()
        val first = viewModel(repo)
        advanceUntilIdle()
        first.onPromptChange("not persisted")
        first.onSendClick()
        advanceUntilIdle()
        assertTrue(readyState(first).items.single() is ChatListItem.LocalFailed)
        val restored = viewModel(repo)
        advanceUntilIdle()
        assertEquals(1, repo.sendCalls)
        assertTrue(readyState(restored).items.isEmpty())
        assertFalse(readyState(restored).sending)
        assertEquals("", readyState(restored).promptInput)
    }

    // --- helpers --------------------------------------------------------

    private fun viewModel(repo: RecordingRepository): ProviderConversationViewModel =
        ProviderConversationViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
            getConversationById = GetConversationByIdUseCase(repo),
            sendMessage = SendMessageUseCase(repo),
            sendMediaMessage = SendMediaMessageUseCase(repo),
            mediaReader = NotExercisedMediaReader,
            audioRecorder = NotExercisedAudioRecorder,
            audioPlayer = NotExercisedAudioPlayer,
        ).also(ownedModels::add)

    private fun viewModelWithMediaReader(
        repo: RecordingRepository,
        reader: com.loresuelvo.serviceprovider.domain.conversation.MediaReader,
    ): ProviderConversationViewModel = ProviderConversationViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
        getConversationById = GetConversationByIdUseCase(repo),
        sendMessage = SendMessageUseCase(repo),
        sendMediaMessage = SendMediaMessageUseCase(repo),
        mediaReader = reader,
        audioRecorder = NotExercisedAudioRecorder,
        audioPlayer = NotExercisedAudioPlayer,
    ).also(ownedModels::add)

    private fun readyState(vm: ProviderConversationViewModel): ProviderConversationUiState.Ready {
        val state = vm.uiState.value
        assertTrue(
            "expected Ready, got $state",
            state is ProviderConversationUiState.Ready,
        )
        return state as ProviderConversationUiState.Ready
    }

    private fun detailOutcome(
        messages: List<ConversationMessage>,
    ): ConversationDetailOutcome.Success = ConversationDetailOutcome.Success(
        detail = ConversationDetail(
            id = 42,
            status = ConversationStatus.Active,
            counterpart = ConversationCounterpart(
                id = 7,
                name = "Ana",
                surname = "Pérez",
                profilePhotoUrl = null,
            ),
            messages = messages,
            updatedOnEpochMillis = 1L,
        ),
    )

    private fun message(id: Int, sender: ConversationSender, content: String) = ConversationMessage(
        id = id,
        sender = sender,
        content = content,
        createdOnEpochMillis = id.toLong(),
    )

    private object NotExercisedMediaReader : com.loresuelvo.serviceprovider.domain.conversation.MediaReader {
        override suspend fun read(uri: String): com.loresuelvo.serviceprovider.domain.conversation.MediaUpload =
            error("MediaReader is not exercised by US-A VM tests")
    }

    private object NotExercisedAudioRecorder : com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder {
        override fun start(): Result<Unit> = error("AudioRecorder is not exercised by US-A VM tests")
        override fun stop(): Result<String> = error("AudioRecorder is not exercised by US-A VM tests")
        override fun discard(uri: String) = Unit
        override fun cancel() = Unit
    }

    private object NotExercisedAudioPlayer : com.loresuelvo.serviceprovider.domain.conversation.AudioPlayer {
        override val isPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val currentPositionMillis = kotlinx.coroutines.flow.MutableStateFlow(0L)
        override fun play(url: String, startPositionMillis: Long) = Unit
        override fun seekTo(positionMillis: Long) { currentPositionMillis.value = positionMillis }
        override fun pause() = Unit
        override fun stop() = Unit
    }

    /**
     * Controllable [AudioPlayer] for the play / pause / collector
     * tests. The VM mirrors [isPlaying] and [currentPositionMillis]
     * via a long-lived collector, so the test flips the values
     * imperatively to simulate `MediaPlayer` callbacks without
     * pulling Robolectric into the picture.
     */
    private class FakeAudioPlayer : com.loresuelvo.serviceprovider.domain.conversation.AudioPlayer {
        override val isPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val currentPositionMillis = kotlinx.coroutines.flow.MutableStateFlow(0L)
        var playCalls = mutableListOf<String>()
        var pauseCalls = 0
        var stopCalls = 0
        override fun play(url: String, startPositionMillis: Long) {
            playCalls += url
        }
        override fun seekTo(positionMillis: Long) { currentPositionMillis.value = positionMillis }
        override fun pause() {
            pauseCalls += 1
        }
        override fun stop() {
            stopCalls += 1
        }
    }

    // ------------------------------------------------------------------------
    // US-C — Audio recording flow tests
    // ------------------------------------------------------------------------

    @Test
    fun onStartRecording_sets_recordingState_with_zero_elapsed_millis() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            val recorder = FakeAudioRecorder()
            val player = NotExercisedAudioPlayer
            val vm = viewModelWithAudio(repo, recorder, player)
            advanceUntilIdle()

            vm.onStartRecording()
            // The recording ticker loops with delay(250L); advance
            // only the immediate queue to observe the synchronous
            // Recording(0) state without looping forever.
            scheduler.runCurrent()
            stopTicker(vm)

            val ready = readyState(vm)
            assertTrue(
                "expected Recording state, got ${ready.recordingState}",
                ready.recordingState is RecordingState.Recording,
            )
            assertEquals(0L, (ready.recordingState as RecordingState.Recording).elapsedMillis)
        }

    @Test
    fun onStartRecording_with_recorder_failure_sets_transientMediaError() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val recorder = ThrowingAudioRecorder
        val player = NotExercisedAudioPlayer
        val vm = viewModelWithAudio(repo, recorder, player)
        advanceUntilIdle()

        vm.onStartRecording()
            scheduler.runCurrent()

            val ready = readyState(vm)
            assertTrue(
                "expected Idle after recorder failure, got ${ready.recordingState}",
                ready.recordingState is RecordingState.Idle,
            )
            assertTrue(
                "expected Server failure, got ${ready.transientMediaError}",
                ready.transientMediaError
                    is com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.Server,
            )
        }

    @Test
    fun retrying_an_older_failed_media_bubble_preserves_the_new_audio_preview_and_its_owned_uri() = runTest(scheduler) {
        for (oldIsAudio in listOf(false, true)) {
            val repo = RecordingRepository(detailOutcome = detailOutcome(emptyList()))
            val discarded = mutableListOf<String>()
            var clip = 0
            val recorder = object : com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder {
                override fun start() = Result.success(Unit)
                override fun stop() = Result.success("file:///audio-${++clip}.webm")
                override fun cancel() = Unit
                override fun discard(uri: String) { discarded += uri }
            }
            val oldAudio = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Audio(byteArrayOf(1), "audio/webm", "old.webm", 3000)
            val newAudio = oldAudio.copy(bytes = byteArrayOf(9), originalName = "new.webm")
            var nextAudio = oldAudio
            val reader = object : com.loresuelvo.serviceprovider.domain.conversation.MediaReader {
                override suspend fun read(uri: String): com.loresuelvo.serviceprovider.domain.conversation.MediaUpload =
                    if (uri.startsWith("content:")) com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image(byteArrayOf(1), "image/jpeg", "old.jpg") else nextAudio
            }
            val player = FakeAudioPlayer()
            val vm = viewModelWithAudioAndReader(repo, recorder, player, reader)
            advanceUntilIdle()
            fun record() { vm.onStartRecording(); scheduler.runCurrent(); vm.onStopRecording(); scheduler.runCurrent() }
            if (oldIsAudio) record() else { vm.onImagesPicked(listOf("content://old")); scheduler.runCurrent() }
            vm.onSendClick()
            advanceUntilIdle()
            val failedKey = readyState(vm).items.filterIsInstance<ChatListItem.LocalFailed>().single().key
            nextAudio = newAudio
            record()
            val newUri = "file:///audio-$clip.webm"
            assertEquals(newAudio, readyState(vm).pendingMedia)
            val gate = CompletableDeferred<Unit>()
            repo.sendGate = gate
            repo.sendOutcome = SendMessageOutcome.Success(ConversationMessage(99, ConversationSender.Provider, "", 10_000))
            repeat(3) { vm.onRetrySendFailedBubble(failedKey) }
            scheduler.runCurrent()
            assertEquals(2, repo.sendCalls)
            assertEquals(newAudio, readyState(vm).pendingMedia)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(newAudio, readyState(vm).pendingMedia)
            assertTrue(readyState(vm).items.single() is ChatListItem.ServerConfirmed)
            assertTrue(newUri !in discarded)
            vm.onPlayPreview()
            scheduler.runCurrent()
            assertEquals(newUri, player.playCalls.single())
            assertEquals(2, repo.sendCalls)
        }
    }

    @Test
    fun permission_revocation_and_stop_failure_cancel_capture_and_restore_idle_without_send() = runTest(scheduler) {
        for (revoked in listOf(true, false)) {
            val repo = RecordingRepository().apply { detailOutcome = detailOutcome(messages = emptyList()) }
            var cancellations = 0
            val recorder = object : com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder {
                override fun start(): Result<Unit> = if (revoked) Result.failure(SecurityException("revoked")) else Result.success(Unit)
                override fun stop(): Result<String> = Result.failure(IllegalStateException("too short"))
                override fun discard(uri: String) = Unit
                override fun cancel() { cancellations++ }
            }
            val vm = viewModelWithAudio(repo, recorder, FakeAudioPlayer())
            advanceUntilIdle()
            vm.onStartRecording(); scheduler.runCurrent()
            if (!revoked) vm.onStopRecording()
            scheduler.runCurrent()
            assertEquals(1, cancellations)
            assertTrue(readyState(vm).recordingState is RecordingState.Idle)
            assertNotNull(readyState(vm).transientMediaError)
            if (revoked) assertEquals(com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.InvalidMedia(
                com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.MediaReason.MicrophonePermission), readyState(vm).transientMediaError)
            assertEquals(0, repo.sendCalls)
            assertNull(readyState(vm).pendingMedia)
        }
    }

    @Test
    fun recording_uses_monotonic_virtual_time_stops_at_ceiling_and_never_sends_automatically() = runTest(scheduler) {
        val repo = RecordingRepository().apply { detailOutcome = detailOutcome(messages = emptyList()) }
        val recorder = FakeAudioRecorder()
        val audio = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Audio(byteArrayOf(1), "audio/webm", "clip.webm", 300_000)
        val vm = viewModelWithAudioAndReader(repo, recorder, FakeAudioPlayer(), AudioMediaReader(mapOf(FakeAudioRecorder.OUTPUT_URI to audio)))
        advanceUntilIdle()
        vm.onStartRecording()
        scheduler.runCurrent()
        scheduler.advanceTimeBy(1000)
        scheduler.runCurrent()
        assertEquals(1000L, (readyState(vm).recordingState as RecordingState.Recording).elapsedMillis)
        scheduler.advanceTimeBy(299_000)
        scheduler.runCurrent()
        assertEquals(audio, readyState(vm).pendingMedia)
        assertTrue(readyState(vm).recordingState is RecordingState.Idle)
        assertTrue(readyState(vm).items.isEmpty())
        vm.onPromptChange("mixed text")
        vm.onImagesPicked(listOf("content://mixed"))
        assertEquals("", readyState(vm).promptInput)
        assertEquals(audio, readyState(vm).pendingMedia)
    }

    @Test
    fun background_cancels_delayed_audio_read_discards_file_and_prevents_late_preview() = runTest(scheduler) {
        val repo = RecordingRepository().apply { detailOutcome = detailOutcome(messages = emptyList()) }
        var discarded = 0
        val recorder = object : com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder {
            override fun start() = Result.success(Unit)
            override fun stop() = Result.success("file:///owned.webm")
            override fun cancel() = Unit
            override fun discard(uri: String) { discarded++ }
        }
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val reader = object : com.loresuelvo.serviceprovider.domain.conversation.MediaReader {
            override suspend fun read(uri: String): com.loresuelvo.serviceprovider.domain.conversation.MediaUpload {
                gate.await()
                return com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Audio(byteArrayOf(1), "audio/webm", "clip.webm", 3000)
            }
        }
        val vm = viewModelWithAudioAndReader(repo, recorder, FakeAudioPlayer(), reader)
        advanceUntilIdle()
        vm.onStartRecording(); scheduler.runCurrent(); vm.onStopRecording(); scheduler.runCurrent()
        assertTrue(readyState(vm).readingMedia)
        vm.onConversationBackgrounded()
        gate.complete(Unit)
        scheduler.runCurrent()
        assertNull(readyState(vm).pendingMedia)
        assertFalse(readyState(vm).readingMedia)
        assertEquals(1, discarded)
    }

    @Test
    fun onStopRecording_stages_audio_as_pendingMedia() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val recorder = FakeAudioRecorder()
        val audio = com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Audio(
            bytes = byteArrayOf(1, 2, 3, 4, 5),
            mimeType = "audio/webm",
            originalName = "recording.webm",
            durationMillis = 2_500L,
        )
        val reader = AudioMediaReader(
            audioForUri = mapOf(FakeAudioRecorder.OUTPUT_URI to audio),
        )
        val player = NotExercisedAudioPlayer
        val vm = viewModelWithAudioAndReader(repo, recorder, player, reader)
        advanceUntilIdle()

        vm.onStartRecording()
        scheduler.runCurrent()
        vm.onStopRecording()
        advanceUntilIdle()

        val ready = readyState(vm)
        assertTrue(
            "expected Idle after stop, got ${ready.recordingState}",
            ready.recordingState is RecordingState.Idle,
        )
        assertEquals(audio, ready.pendingMedia)
    }

    @Test
    fun onCancelRecording_clears_recording_state() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val recorder = FakeAudioRecorder()
        val player = NotExercisedAudioPlayer
        val vm = viewModelWithAudio(repo, recorder, player)
        advanceUntilIdle()

        vm.onStartRecording()
        scheduler.runCurrent()
        vm.onCancelRecording()
        scheduler.runCurrent()

        val ready = readyState(vm)
        assertTrue(
            "expected Idle after cancel, got ${ready.recordingState}",
            ready.recordingState is RecordingState.Idle,
        )
    }

    @Test
    fun onPlayAudio_sets_playingMediaKey() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val player = FakeAudioPlayer()
        val vm = viewModelWithAudio(repo, NotExercisedAudioRecorder, player)
        advanceUntilIdle()

        vm.onPlayAudio(
            bubbleKey = "bubble-1",
            url = "https://example.test/audio.webm",
        )
        player.isPlaying.value = true
        player.currentPositionMillis.value = 1_000L
        advanceUntilIdle()

        val ready = readyState(vm)
        assertEquals("bubble-1", ready.playingMediaKey)
        assertTrue(
            "VM should mirror AudioPlayer.isPlaying into state, got ${ready.isPlaying}",
            ready.isPlaying,
        )
        assertEquals(1_000L, ready.playingPositionMillis)
    }

    @Test
    fun onPauseAudio_preserves_playingMediaKey_and_mirrors_player_state() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val player = FakeAudioPlayer()
        val vm = viewModelWithAudio(repo, NotExercisedAudioRecorder, player)
        advanceUntilIdle()
        vm.onPlayAudio("bubble-1", "https://example.test/audio.webm")
        player.isPlaying.value = true
        player.currentPositionMillis.value = 2_500L
        advanceUntilIdle()

        vm.onPauseAudio()
        // The player emits isPlaying=false + a fresh position
        // snapshot the moment it pauses.
        player.isPlaying.value = false
        player.currentPositionMillis.value = 2_500L
        advanceUntilIdle()

        val ready = readyState(vm)
        assertEquals("bubble-1", ready.playingMediaKey)
        assertFalse(
            "VM should mirror paused state, got isPlaying=${ready.isPlaying}",
            ready.isPlaying,
        )
        // Position is preserved so the bubble can show the mm:ss
        // counter at the paused frame.
        assertEquals(2_500L, ready.playingPositionMillis)
    }

    @Test
    fun player_completion_resets_isPlaying_and_position_in_state() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val player = FakeAudioPlayer()
        val vm = viewModelWithAudio(repo, NotExercisedAudioRecorder, player)
        advanceUntilIdle()
        vm.onPlayAudio("bubble-1", "https://example.test/audio.webm")
        player.isPlaying.value = true
        player.currentPositionMillis.value = 5_000L
        advanceUntilIdle()

        // Simulate AndroidAudioPlayer's setOnCompletionListener
        // semantics: isPlaying flips false, position resets to 0.
        player.isPlaying.value = false
        player.currentPositionMillis.value = 0L
        advanceUntilIdle()

        val ready = readyState(vm)
        assertFalse(ready.isPlaying)
        assertEquals(0L, ready.playingPositionMillis)
    }

    /** Cancel the recording ticker via reflection so the test
     *  scheduler can exit `advanceUntilIdle` without looping. */
    private fun stopTicker(vm: ProviderConversationViewModel) {
        val field = vm::class.java.getDeclaredField("recordingTickerJob")
        field.isAccessible = true
        val job = field.get(vm) as? kotlinx.coroutines.Job ?: return
        job.cancel()
        scheduler.runCurrent()
    }

    private fun viewModelWithAudio(
        repo: RecordingRepository,
        recorder: com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder,
        player: com.loresuelvo.serviceprovider.domain.conversation.AudioPlayer,
    ): ProviderConversationViewModel = ProviderConversationViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
        getConversationById = GetConversationByIdUseCase(repo),
        sendMessage = SendMessageUseCase(repo),
        sendMediaMessage = SendMediaMessageUseCase(repo),
        mediaReader = NotExercisedMediaReader,
        audioRecorder = recorder,
        audioPlayer = player,
        recordingTimeSource = object : RecordingTimeSource() { override fun nowMillis() = scheduler.currentTime },
    ).also(ownedModels::add)

    private fun viewModelWithAudioAndReader(
        repo: RecordingRepository,
        recorder: com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder,
        player: com.loresuelvo.serviceprovider.domain.conversation.AudioPlayer,
        reader: com.loresuelvo.serviceprovider.domain.conversation.MediaReader,
    ): ProviderConversationViewModel = ProviderConversationViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
        getConversationById = GetConversationByIdUseCase(repo),
        sendMessage = SendMessageUseCase(repo),
        sendMediaMessage = SendMediaMessageUseCase(repo),
        mediaReader = reader,
        audioRecorder = recorder,
        audioPlayer = player,
        recordingTimeSource = object : RecordingTimeSource() { override fun nowMillis() = scheduler.currentTime },
    ).also(ownedModels::add)

    private class FakeAudioRecorder : com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder {
        private var started = false

        override fun start(): Result<Unit> {
            if (started) return Result.failure(
                IllegalStateException("Audio recording is already in progress"),
            )
            started = true
            return Result.success(Unit)
        }

        override fun stop(): Result<String> {
            if (!started) return Result.failure(
                IllegalStateException("Audio recording is not in progress"),
            )
            started = false
            return Result.success(OUTPUT_URI.toString())
        }

        override fun discard(uri: String) = Unit
        override fun cancel() {
            started = false
        }

        companion object {
            val OUTPUT_URI: android.net.Uri =
                android.net.Uri.parse("file:///fake/audio-recording.webm")
        }
    }

    private object ThrowingAudioRecorder : com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder {
        override fun start(): Result<Unit> =
            Result.failure(IllegalStateException("Mic is busy"))
        override fun stop(): Result<String> =
            Result.failure(IllegalStateException("Recording not started"))
        override fun discard(uri: String) = Unit
        override fun cancel() = Unit
    }

    private class AudioMediaReader(
        private val audioForUri: Map<android.net.Uri, com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Audio>,
    ) : com.loresuelvo.serviceprovider.domain.conversation.MediaReader {
        override suspend fun read(uri: String) =
            audioForUri[android.net.Uri.parse(uri)]
                ?: error("AudioMediaReader has no entry for $uri")
    }

    private class FakeMediaReader(
        private val images: Map<String, com.loresuelvo.serviceprovider.domain.conversation.MediaUpload.Image>,
    ) : com.loresuelvo.serviceprovider.domain.conversation.MediaReader {
        override suspend fun read(uri: String) =
            images[uri.toString()]
                ?: error("MediaReader has no entry for $uri")
    }

    private object ThrowingMediaReader : com.loresuelvo.serviceprovider.domain.conversation.MediaReader {
        override suspend fun read(uri: String): com.loresuelvo.serviceprovider.domain.conversation.MediaUpload =
            throw java.io.IOException("Could not open input stream for $uri")
    }

    private class RecordingRepository(
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
        ),
        var sendOutcome: SendMessageOutcome =
            SendMessageOutcome.Failure.Network(cause = RuntimeException("not set")),
        var sendGate: CompletableDeferred<Unit>? = null,
    ) : ConversationRepository {
        var sendCalls: Int = 0

        override suspend fun getConversations(): ConversationsOutcome =
            error("not exercised by ProviderConversationViewModelTest")

        override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
            detailOutcome

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
            return sendOutcome
        }
    }
}
