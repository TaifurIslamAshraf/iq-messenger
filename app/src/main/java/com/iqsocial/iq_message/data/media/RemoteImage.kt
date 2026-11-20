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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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

    /**
     * Where downloaded bytes are kept between launches.
     *
     * Set once at start-up, like [MediaCache.cacheDir]. Null until then, and
     * everything below treats that as "no disk" rather than failing - the memory
     * cache alone is what this was before.
     */
    var cacheDir: File? = null

    /**
     * The file for an address, named by its digest.
     *
     * Keyed on the address alone, not on the size asked for: these are the bytes as
     * downloaded, and the same picture wanted small for an avatar and sharp for a
     * link card is one file decoded two ways rather than two copies.
     */
    private fun fileFor(url: String): File? {
        val dir = cacheDir ?: return null
        val images = File(dir, "remote")
        if (!images.exists() && !images.mkdirs()) return null
        val digest = runCatching {
            MessageDigest.getInstance("SHA-1")
                .digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.getOrNull() ?: return null
        return File(images, digest)
    }

    /**
     * Keeps the folder from growing for ever.
     *
     * Avatars are small and there are not many of them, but link previews and
     * profile pictures accumulate, and a cache nobody ever empties is a disk leak
     * with a friendly name. Oldest first, down to half, so this runs rarely rather
     * than on every write.
     */
    private fun prune(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES / 2)
            .forEach { runCatching { it.delete() } }
    }

    fun peek(url: String, targetPx: Int = TARGET_PX): ImageBitmap? =
        if (url.isBlank()) null else cache.get(keyOf(url, targetPx))

    /**
     * Signing out: these were the faces of an account's contacts.
     *
     * The memory cache would go with the process, but the files would not - and a
     * folder of people's profile pictures outliving the account they belong to is
     * the kind of leftover nobody goes looking for.
     */
    fun clear() {
        cache.evictAll()
        failed.clear()
        cacheDir?.let { dir -> runCatching { File(dir, "remote").deleteRecursively() } }
    }

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
        // What was downloaded last time, if it is still here. This is what makes a
        // face appear with no connection: before, the picture lived only in memory,
        // so every cold start began with grey circles and initials and stayed that
        // way until the network came back.
        val kept = fileFor(url)
        val cached = kept?.takeIf { it.exists() }?.let { file ->
            runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
        }

        val attempt = cached?.let { Result.success<ByteArray?>(it) }
            ?: runCatching { download(url, token) }

        val bytes = attempt.getOrNull()
        if (bytes == null) {
            // A server that answered "no" is an answer, and remembering it saves
            // asking again on every scroll. A request that never got out is not:
            // marking those would have one pass through a thread offline blacklist
            // every face on the screen, and they would stay blank for the rest of
            // the process even after the network came back.
            if (attempt.isSuccess) failed += key
            return null
        }
        if (cached == null && kept != null) {
            runCatching {
                kept.writeBytes(bytes)
                kept.parentFile?.let(::prune)
            }
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

    private const val MAX_FILES = 240
}
