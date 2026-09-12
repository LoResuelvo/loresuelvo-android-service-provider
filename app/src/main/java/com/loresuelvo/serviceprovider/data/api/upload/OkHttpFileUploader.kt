package com.loresuelvo.serviceprovider.data.api.upload

import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * OkHttp implementation of [FileUploader].
 * Executes storage PUT requests using an isolated nonlogging OkHttpClient
 * without authorization interceptors.
 */
@Singleton
class OkHttpFileUploader @Inject constructor(
    @Named("uploadOkHttp") private val client: OkHttpClient,
) : FileUploader {

    override suspend fun upload(
        uploadUrl: String,
        headers: Map<String, String>,
        bytes: ByteArray,
    ): UploadBytesOutcome = withContext(Dispatchers.IO) {
        try {
            val contentType = headers[HEADER_CONTENT_TYPE] ?: headers[HEADER_CONTENT_TYPE.lowercase()]
            val mediaType = contentType?.toMediaTypeOrNull()
            val requestBody = bytes.toRequestBody(
                contentType = mediaType,
                offset = 0,
                byteCount = bytes.size,
            )

            val requestBuilder = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)

            headers.forEach { (name, value) ->
                if (name.isNotBlank()) {
                    requestBuilder.header(name, value)
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            response.use {
                if (response.isSuccessful) {
                    UploadBytesOutcome.Success
                } else {
                    UploadBytesOutcome.Failure.Server(
                        code = response.code,
                        message = response.message.ifBlank { "Upload failed" },
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            UploadBytesOutcome.Failure.Network(e)
        } catch (e: Exception) {
            UploadBytesOutcome.Failure.Server(0, e.message ?: "Upload failed")
        }
    }

    private companion object {
        const val HEADER_CONTENT_TYPE = "Content-Type"
    }
}
