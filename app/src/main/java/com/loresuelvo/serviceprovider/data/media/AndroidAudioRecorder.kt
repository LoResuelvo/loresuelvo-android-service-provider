package com.loresuelvo.serviceprovider.data.media

import com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [AudioRecorder].
 *
 * Recordings are stored temporarily in the application's cache
 * directory as `audio-<uuid>.webm` (audio-only Opus inside a
 * WebM container — the only audio format the backend's
 * `conversation_message_audio` purpose accepts).
 *
 * This class deliberately does not request `RECORD_AUDIO`
 * permission — runtime permission handling belongs to the UI
 * layer (the route acquires the permission via
 * `ActivityResultContracts.RequestPermission` before invoking
 * [start]).
 */
@Singleton
open class AndroidAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private val completedFiles = mutableMapOf<String, File>()

    protected open fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }

    override fun start(): Result<Unit> {
        if (recorder != null) {
            return Result.failure(
                IllegalStateException("Audio recording is already in progress"),
            )
        }

        val file = File(
            context.cacheDir,
            "audio-${UUID.randomUUID()}.webm",
        )

        return runCatching {
            val mediaRecorder = createRecorder()

            recorder = mediaRecorder
            outputFile = file
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            mediaRecorder.setOutputFile(file.absolutePath)

            mediaRecorder.prepare()
            mediaRecorder.start()
        }.onFailure {
            recorder?.runCatching { release() }
            recorder = null
            outputFile = null
            file.delete()
        }
    }

    override fun stop(): Result<String> {
        val mediaRecorder = recorder
            ?: return Result.failure(
                IllegalStateException("Audio recording is not in progress"),
            )

        val file = outputFile ?: run {
            cancel()
            return Result.failure(IllegalStateException("Audio recording file is missing"))
        }

        return runCatching {
            mediaRecorder.stop()
            Uri.fromFile(file).toString()
        }.also {
            mediaRecorder.runCatching { release() }
            if (it.isFailure) file.delete() else completedFiles[it.getOrThrow()] = file
            recorder = null
            outputFile = null
        }
    }

    override fun discard(uri: String) {
        completedFiles.remove(uri)?.delete()
    }

    override fun cancel() {
        recorder?.runCatching {
            stop()
        }

        recorder?.runCatching { release() }

        recorder = null

        outputFile?.delete()
        outputFile = null
    }
}
