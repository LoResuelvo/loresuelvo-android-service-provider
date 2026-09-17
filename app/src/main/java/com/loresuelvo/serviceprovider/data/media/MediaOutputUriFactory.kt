package com.loresuelvo.serviceprovider.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for the `content://` URI the system camera writes the
 * captured photo to. The provider chat uses
 * `ActivityResultContracts.TakePicture` which only accepts a
 * pre-allocated output URI (the launcher doesn't take a request
 * payload, only a URI), so this factory:
 *
 *  1. Resolves (and lazily creates) the camera output directory
 *     under `cacheDir/camera/`.
 *  2. Returns a fresh `File` for the next capture.
 *  3. Wraps the file in a `FileProvider`-backed `Uri` using the
 *     `${applicationId}.fileprovider` authority declared in
 *     `AndroidManifest.xml` so the camera app can write to the
 *     private cache directory (read / write permission is
 *     granted transiently to the receiving app via the
 *     `Intent.FLAG_GRANT_WRITE_URI_PERMISSION` flag the launcher
 *     sets when calling `takePicture`).
 *
 * The `FileProvider` is the only place in the manifest that
 * grants the camera write access; the matching `file_paths.xml`
 * declares `cacheDir/camera/` as a writable root so the OS
 * grants the `FLAG_GRANT_WRITE_URI_PERMISSION` without a runtime
 * `CAMERA` permission (the consumer's `TakePicture` flow does the
 * same).
 */
@Singleton
class MediaOutputUriFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createCameraOutputUri(): Uri {
        val cameraDir = File(context.cacheDir, CAMERA_SUBDIR).apply { mkdirs() }
        val file = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private companion object {
        const val CAMERA_SUBDIR: String = "camera"
    }
}
