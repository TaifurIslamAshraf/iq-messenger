package com.iqsocial.iq_message.data.media

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Images addressed by a plain URL — profile pictures, as the account API stores them.
 *
 * Separate from [MediaCache], which exists for the dance a *message* attachment
 * needs: an authorized call that answers 302 to a signed link the token must not
 * follow. A profile picture has none of that. `profile_picture` holds the public R2
 * URL the upload returned, so it is one unauthenticated GET, and sending a bearer
 * token to a public bucket would be a leak for no gain.
 *
 * Avatars are small and repeat down a list, so they are decoded once at thumbnail
 * size and kept. A failure is remembered too: without that, every scroll past a
 * broken or deleted picture would retry it.
 */
object RemoteImage {

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Standalone file messages repeat the same avatar on many rows. Share its
    // request, including when a row leaves the viewport before decoding finishes.
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(3)

    /**
     * The size is part of the key.
     *
     * Two callers can want the same address at different sizes - an avatar wants it
     * small, a link card wants it sharp - and keying on the address alone would hand
     * whichever asked second the other one's copy.
     */
    private fun keyOf(url: String, targetPx: Int) = targetPx.toString() + "|" + url

    fun peek(url: String, targetPx: Int = TARGET_PX): ImageBitmap? =
        if (url.isBlank()) null else cache.get(keyOf(url, targetPx))

    /**
     * @param token sent as a bearer when the URL is one of our own server's - the
     *   link-preview proxy signs its links and will not serve them otherwise. A
     *   public R2 avatar takes none, and sending one there would leak it to a bucket
     *   that never asked.
     */
    /**
     * @param targetPx how small it may be decoded. The default suits an avatar; a
     *   link preview is drawn several times that wide and would be visibly soft at
     *   it.
     */
    suspend fun load(
        url: String,
        token: String? = null,
        targetPx: Int = TARGET_PX
    ): ImageBitmap? {
        val key = keyOf(url, targetPx)
        if (url.isBlank() || key in failed) return null
        cache.get(key)?.let { return it }

        val job = synchronized(inFlight) {
            inFlight[key] ?: scope.async {
                try {
                    gate.withPermit { fetchAndDecode(url, token, targetPx, key) }
                } finally {
                    synchronized(inFlight) { inFlight.remove(key) }
                }
            }.also { inFlight[key] = it }
        }
        return job.await()
    }

    private fun fetchAndDecode(
        url: String,
        token: String?,
        targetPx: Int,
        key: String
    ): ImageBitmap? {
        cache.get(key)?.let { return it }
        if (key in failed) return null
        // 403 means this server did not sign the link and 502 that the site
        // served no image. Both are answers, not hiccups, so both are remembered
        // the same way a failed fetch is - there is nothing to gain by asking
        // again on every scroll.
        val bytes = runCatching { download(url, token) }.getOrNull()
        if (bytes == null) {
            failed += key
            return null
        }

        // Bounds first: an avatar is drawn at 40dp and the original may be a
        // full-size photo, so decoding it whole would cost megabytes per row.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, targetPx)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (bitmap == null) {
            failed += key
            return null
        }

        bitmap.prepareToDraw()
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    private fun download(url: String, token: String?): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            if (connection.responseCode in 200..299) {
                connection.inputStream.use { stream ->
                    val out = ByteArrayOutputStream()
                    stream.copyTo(out, DEFAULT_BUFFER_SIZE)
                    out.toByteArray()
                }
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Halves until the image is near the largest an avatar is ever drawn. */
    internal fun sampleFor(width: Int, height: Int, targetPx: Int = TARGET_PX): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= targetPx && h / 2 >= targetPx) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    // Large enough for the hero avatar on the profile screen, small enough that a
    // list of them stays inside the cache.
    internal const val TARGET_PX = 192

    /** Wide enough for the card a preview is drawn in. */
    const val PREVIEW_PX = 512
    private const val CACHE_BYTES = 16 * 1024 * 1024
}
