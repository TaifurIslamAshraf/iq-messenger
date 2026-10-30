package com.iqsocial.iq_message.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.iqsocial.iq_message.data.remote.MessengerConfig
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

/**
 * Fetches and caches the image on a message.
 *
 * `GET /v1/messages/{id}/media` answers **302** to a short-lived R2 link, and the
 * spec is explicit that the redirect must be followed *without* the Authorization
 * header. `HttpURLConnection` would carry it across automatically, so redirects are
 * turned off and the second hop is made by hand.
 */
object MediaCache {

    /** Wide enough to read as the picture, small enough to keep hundreds. */
    private const val TINY_PX = 48
    private const val TINY_BYTES = 4 * 1024 * 1024

    /** Photos on disk are cheap; this is roughly a few hundred of them. */
    private const val DISK_BYTES = 128L * 1024 * 1024
    private const val TRIM_EVERY = 20

    /**
     * A share of the heap rather than a fixed figure, because the right size depends
     * on the device and a number picked here would be wrong on most of them.
     */
    private val CACHE_BYTES = (Runtime.getRuntime().maxMemory() / 8)
        .coerceIn(16L * 1024 * 1024, 64L * 1024 * 1024)
        .toInt()

    /** Decoded images, capped by bytes rather than count so one huge photo cannot evict everything. */
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    /**
     * Messages whose file could not be fetched.
     *
     * Without this a failure is retried every time the row comes back on screen,
     * which while scrolling means a fresh request per pass - the work that makes a
     * thread of photos stutter, and none of it ever succeeding.
     */
    private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Where downloaded bytes are kept between launches.
     *
     * Set once at start-up. Without it a photo evicted from memory has to come back
     * over the network, which is what you feel when one scrolls off and then back
     * on: the bubble empties and refills. The decode still has to happen again, but
     * a decode is milliseconds and a round trip is not.
     */
    @Volatile
    var cacheDir: File? = null

    private fun fileFor(messageId: String): File? {
        val dir = cacheDir ?: return null
        val safe = messageId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        if (safe.isEmpty()) return null
        val media = File(dir, "media")
        if (!media.exists() && !media.mkdirs()) return null
        return File(media, safe)
    }

    /**
     * A thumbnail of every photo ever decoded, kept far longer than the full one.
     *
     * At this size a picture is about twelve kilobytes, so a few hundred of them fit
     * where two full-size ones would. The point is the moment a photo scrolls back
     * into view: the full bitmap may be long evicted, and without this the bubble is
     * an empty rectangle until it is fetched and decoded again. Blurred is a far
     * better answer than blank, and it costs nothing to keep.
     */
    private val tiny = object : LruCache<String, ImageBitmap>(TINY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    fun peek(messageId: String): ImageBitmap? = cache.get(messageId)

    /** The blurred stand-in, if this photo has ever been decoded. */
    fun peekTiny(messageId: String): ImageBitmap? = tiny.get(messageId)

    /** For a retry the user asked for; a failure is otherwise remembered. */
    fun forget(messageId: String) {
        failed -= messageId
    }

    /**
     * At most this many photos are fetched and decoded at once.
     *
     * Every row that scrolls into view asks for its picture, so flinging through a
     * thread of them used to start a download *and a decode* for each one. Decoding
     * is the expensive half and it is not free of the main thread - enough of them
     * at once and the frame budget goes, which is the stutter you feel as a photo
     * scrolls back in. Three keeps the pipe busy without taking the device over.
     */
    private val gate = Semaphore(3)

    /**
     * Loads already running, keyed by message.
     *
     * The same photo can be asked for twice over - scrolled away and back before the
     * first fetch returned - and without this both would download and decode it. The
     * second caller waits on the first instead.
     */
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * @param thumbnailOnly for a video, where the file behind `/media` is the video
     *   itself. Falling back to it would download the whole clip and then fail to
     *   decode it as an image - the slowest possible way to arrive at nothing.
     */
    suspend fun load(
        token: String?,
        messageId: String,
        thumbnailOnly: Boolean = false
    ): ImageBitmap? {
        if (token.isNullOrBlank() || messageId.isBlank()) return null
        cache.get(messageId)?.let { return it }
        if (messageId in failed) return null

        // Started on a scope of its own, so a row scrolling away mid-fetch cancels
        // its own wait without throwing away work the next row will want.
        val job = synchronized(inFlight) {
            inFlight[messageId] ?: scope.async {
                try {
                    gate.withPermit { fetchAndDecode(token, messageId, thumbnailOnly) }
                } finally {
                    synchronized(inFlight) { inFlight -= messageId }
                }
            }.also { inFlight[messageId] = it }
        }
        return job.await()
    }

    private fun fetchAndDecode(
        token: String,
        messageId: String,
        thumbnailOnly: Boolean
    ): ImageBitmap? {
        cache.get(messageId)?.let { return it }

        val file = fileFor(messageId)
        // Disk before network. A file that is there was whole when it was written -
        // it is only written after the body has been read in full.
        val bytes = file?.takeIf { it.exists() && it.length() > 0 }
            ?.let { runCatching { it.readBytes() }.getOrNull() }
            ?: runCatching { download(token, messageId, thumbnailOnly) }.getOrNull()
                ?.also { downloaded ->
                    runCatching {
                        file?.writeBytes(downloaded)
                        trimDisk()
                    }
                }
        if (bytes == null) {
            failed += messageId
            return null
        }

        // Bounds first, so a large photo is downsampled instead of decoded whole.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (bitmap == null) {
            failed += messageId
            return null
        }

        rememberTiny(messageId, bitmap)
        return PreviewBitmap.prepare(bitmap).also { cache.put(messageId, it) }
    }

    private fun rememberTiny(messageId: String, source: Bitmap) {
        if (tiny.get(messageId) != null) return
        runCatching {
            val scale = TINY_PX.toFloat() / maxOf(source.width, source.height)
            if (scale >= 1f) return
            val small = Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
            small.prepareToDraw()
            tiny.put(messageId, small.asImageBitmap())
        }
    }

    /**
     * The preview if there is one, the file itself if there is not.
     *
     * Messages sent before thumbnails were declared have none, and the endpoint says
     * so rather than inventing one - so a miss here is ordinary and falls through to
     * the original rather than being treated as a failure.
     */
    private fun download(token: String, messageId: String, thumbnailOnly: Boolean): ByteArray? {
        val thumb = fetch(token, "/v1/messages/$messageId/media/thumbnail")
        if (thumb != null || thumbnailOnly) return thumb
        return fetch(token, "/v1/messages/$messageId/media")
    }

    private fun fetch(token: String, path: String): ByteArray? {
        val first = (URL("${MessengerConfig.BASE_URL}$path")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false          // the token must not travel on
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

        val target = try {
            when (first.responseCode) {
                in 300..399 -> first.getHeaderField("Location")
                in 200..299 -> return first.inputStream.use { it.readAllBytesCompat() }
                else -> null
            }
        } finally {
            first.disconnect()
        }
        if (target.isNullOrBlank()) return null

        // Second hop: signed link, no headers of our own.
        val second = (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        return try {
            if (second.responseCode in 200..299) {
                second.inputStream.use { it.readAllBytesCompat() }
            } else {
                null
            }
        } finally {
            second.disconnect()
        }
    }

    private fun java.io.InputStream.readAllBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        copyTo(out, DEFAULT_BUFFER_SIZE)
        return out.toByteArray()
    }

    /**
     * Keeps the folder under [DISK_BYTES], oldest first.
     *
     * Checked only every so often rather than on every write: listing a directory to
     * save a few megabytes is not worth doing in the middle of a scroll.
     */
    private var writesSinceTrim = 0

    private fun trimDisk() {
        if (++writesSinceTrim < TRIM_EVERY) return
        writesSinceTrim = 0
        val dir = cacheDir?.let { File(it, "media") } ?: return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= DISK_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    /** The long edge governs decoding so a narrow image cannot bypass downsampling. */
    internal fun sampleFor(width: Int, height: Int): Int =
        PreviewBitmap.sampleSize(width, height)

}
