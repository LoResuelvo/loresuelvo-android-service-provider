package com.loresuelvo.serviceprovider.data.media

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
class AndroidAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

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
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            mediaRecorder.setOutputFile(file.absolutePath)

            mediaRecorder.prepare()
            mediaRecorder.start()

            recorder = mediaRecorder
            outputFile = file
        }.onFailure {
            recorder?.runCatching { release() }
            recorder = null
            outputFile = null
            file.delete()
        }
    }

    override fun stop(): Result<Uri> {
        val mediaRecorder = recorder
            ?: return Result.failure(
                IllegalStateException("Audio recording is not in progress"),
            )

        val file = outputFile
            ?: return Result.failure(
                IllegalStateException("Audio recording file is missing"),
            )

        return runCatching {
            mediaRecorder.stop()
            Uri.fromFile(file)
        }.also {
            mediaRecorder.release()
            recorder = null
            outputFile = null
        }
    }

    override fun cancel() {
        recorder?.runCatching {
            stop()
        }

        recorder?.release()

        recorder = null

        outputFile?.delete()
        outputFile = null
    }
}
