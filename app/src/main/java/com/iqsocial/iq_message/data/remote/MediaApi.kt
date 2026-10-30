package com.iqsocial.iq_message.data.remote

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Where the bytes go: a presigned R2 link that takes no Authorization header. */
@Immutable
data class UploadTarget(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val expiresAt: Long
) {
    companion object {
        fun from(json: JSONObject): UploadTarget {
            val headers = json.optJSONObject("headers")
            return UploadTarget(
                url = json.optString("url"),
                method = json.optString("method").ifBlank { "PUT" },
                headers = headers?.keys()?.asSequence()
                    ?.associateWith { headers.optString(it) }
                    .orEmpty(),
                expiresAt = json.optLong("expires_at")
            )
        }
    }
}

/** What `POST /v1/media` hands back: the upload's id and where to put the bytes. */
@Immutable
data class CreatedUpload(
    val mediaId: String,
    val status: String,
    val upload: UploadTarget,
    val thumbnailUpload: UploadTarget?
) {
    companion object {
        fun from(json: JSONObject): CreatedUpload = CreatedUpload(
            mediaId = json.optJSONObject("media")?.optString("id").orEmpty(),
            status = json.optJSONObject("media")?.optString("status").orEmpty(),
            upload = UploadTarget.from(json.optJSONObject("upload") ?: JSONObject()),
            thumbnailUpload = json.optJSONObject("thumbnail_upload")?.let(UploadTarget::from)
        )
    }
}

/**
 * The three-step upload.
 *
 * The server presigns for an exact object, so the declared `size` and `mime_type`
 * have to match the bytes that follow to the letter — a mismatch is refused by R2,
 * not by this API, which is why the size is taken from the file itself rather than
 * from anything the caller passes around.
 */
object MediaApi {

    /**
     * Declares the file. [body] is one of the spec's five shapes, built by
     * [com.iqsocial.iq_message.data.media.MediaPreparer].
     */
    suspend fun createUpload(token: String, body: JSONObject): ApiResult<CreatedUpload> =
        ApiClient.post("/v1/media", body, token, MessengerConfig.BASE_URL)
            .mapTo(CreatedUpload::from)

    /**
     * Confirms the bytes landed. 409 while storage has not seen them yet, and
     * calling it again after success returns the same thing.
     */
    suspend fun complete(token: String, mediaId: String): ApiResult<Unit> =
        ApiClient.post(
            "/v1/media/${Uri.encode(mediaId)}/complete",
            JSONObject(),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }

    /**
     * Streams [bytes] to a presigned target.
     *
     * Deliberately not routed through [ApiClient]: this one must carry **no**
     * Authorization header — R2 rejects a signed request that also bears one — and
     * the length is fixed up front so the stream is never buffered in memory.
     */
    suspend fun putBytes(
        target: UploadTarget,
        size: Long,
        onProgress: (Long) -> Unit = {},
        bytes: () -> InputStream?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val input = bytes() ?: return@withContext ApiResult.Failure("Could not read the file")
            conn = (URL(target.url).openConnection() as HttpURLConnection).apply {
                requestMethod = target.method
                doOutput = true
                connectTimeout = ApiConfig.TIMEOUT_MS
                readTimeout = UPLOAD_TIMEOUT_MS
                setFixedLengthStreamingMode(size)
                // Exactly the headers the server signed for, and nothing else.
                target.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            input.use { source ->
                conn.outputStream.use { sink -> source.pumpTo(sink, size, onProgress) }
            }
            val code = conn.responseCode
            if (code in 200..299) ApiResult.Success(Unit)
            else ApiResult.Failure("Upload failed ($code)")
        } catch (e: java.io.IOException) {
            ApiResult.Failure(e.message ?: "Upload failed")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Copies, reporting how far it has got.
     *
     * `copyTo` would do the copying but says nothing while it runs, and an upload
     * with no progress is the one place a spinner is not enough - the whole point is
     * that it takes a while.
     *
     * Reports only when the whole percent changes. A 20 MB photo is some three
     * hundred buffers, and every one of them redrawing the thread would cost more
     * than the upload.
     */
    private fun InputStream.pumpTo(sink: OutputStream, size: Long, onProgress: (Long) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var done = 0L
        var lastPercent = -1
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            sink.write(buffer, 0, read)
            done += read
            val percent = if (size > 0L) (done * 100 / size).toInt() else 0
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(done)
            }
        }
        sink.flush()
        onProgress(done)
    }

    /** Uploads can be large and slow; the API timeout is far too tight for them. */
    private const val UPLOAD_TIMEOUT_MS = 120_000
}
