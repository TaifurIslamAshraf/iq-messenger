package com.iqsocial.iq_message.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Turns a picked file into the exact declaration `POST /v1/media` wants.
 *
 * The server presigns for the size and type declared here, so both are read from
 * the file rather than assumed: the size comes from the descriptor, not from a
 * column that may be stale or absent, because a byte's difference makes R2 refuse
 * the upload that follows.
 */
object MediaPreparer {

    /** The spec's five kinds, with the limits it enforces. */
    enum class Kind(val value: String, val maxBytes: Long) {
        IMAGE("image", 20L * 1024 * 1024),
        VIDEO("video", 100L * 1024 * 1024),
        VOICE("voice", 16L * 1024 * 1024),
        AUDIO("audio", 100L * 1024 * 1024),
        DOCUMENT("document", 100L * 1024 * 1024)
    }

    data class Prepared(
        val uri: Uri,
        val kind: Kind,
        val mimeType: String,
        val size: Long,
        val body: JSONObject,
        /**
         * The preview to upload beside the file, when one was declared.
         *
         * Held as bytes rather than re-made at upload time: the declaration names an
         * exact size and the server presigns for it, so the very bytes that were
         * measured have to be the ones that go.
         */
        val thumbnail: ByteArray? = null,
        /**
         * What to upload, when it is not the file itself.
         *
         * Set only for a photo that was shrunk: the original stays on the phone and
         * these are the bytes that go. Null means stream [uri], which is what every
         * other kind does and what a full-quality photo does too - re-encoding a
         * file that is already going up whole would cost quality for nothing.
         */
        val payload: ByteArray? = null
    )

    /** Everything the image variant will accept; anything else is a document. */
    private val IMAGE_TYPES = setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif"
    )
    private val VIDEO_TYPES = setOf(
        "video/mp4", "video/3gpp", "video/quicktime", "video/webm", "video/x-matroska"
    )

    /**
     * Which kind a MIME type declares.
     *
     * Pulled out of [prepare] so it can be checked without a file: the server
     * presigns for the kind named here, so getting it wrong means an upload that is
     * refused after the bytes are already moving.
     *
     * An unrecognised image or video type still has bytes worth sending, so it
     * degrades to a document rather than being refused outright.
     */
    fun kindFor(mimeType: String): Kind = when (mimeType.lowercase().substringBefore(';').trim()) {
        in IMAGE_TYPES -> Kind.IMAGE
        in VIDEO_TYPES -> Kind.VIDEO
        else -> Kind.DOCUMENT
    }

    /** The refusal for a file over its kind's limit, or null when it fits. */
    fun overLimit(kind: Kind, size: Long): String? =
        if (size <= kind.maxBytes) null
        else "That ${kind.value} is over the ${kind.maxBytes / (1024 * 1024)} MB limit"

    /**
     * Reads [uri] and builds its declaration, or returns a message explaining why
     * it cannot be sent.
     */
    /**
     * [hd] sends the picture as it was taken. Off, a photo bigger than
     * [PhotoQuality.MAX_EDGE] on its longest side is shrunk to fit inside it first.
     *
     * Only photos. A video shrunk the same way would need re-encoding, which is
     * minutes of work and a different problem; a document that is "compressed" is a
     * corrupted document.
     */
    suspend fun prepare(
        context: Context,
        uri: Uri,
        hd: Boolean = true
    ): Result<Prepared> =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }

            val size = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull()?.takeIf { it > 0 } ?: queryColumnSize(context, uri)

            if (size == null || size <= 0L) {
                return@withContext Result.failure(IllegalStateException("Could not read that file"))
            }

            val kind = kindFor(mime)
            overLimit(kind, size)?.let {
                return@withContext Result.failure(IllegalStateException(it))
            }

            val body = JSONObject()
                .put("kind", kind.value)
                .put("mime_type", mime)
                .put("size", size)

            var shrunk: ByteArray? = null

            when (kind) {
                Kind.IMAGE -> {
                    val (w, h) = imageSize(resolver.openInputStream(uri))
                    if (w <= 0 || h <= 0) {
                        return@withContext Result.failure(
                            IllegalStateException("Could not read that image")
                        )
                    }
                    val smaller = if (hd) null else shrink(context, uri, size)
                    if (smaller != null) {
                        shrunk = smaller.bytes
                        // The declaration has to describe what is actually sent: the
                        // server presigns for this exact size, and the bubble is
                        // drawn from these dimensions.
                        body.put("mime_type", SHRUNK_MIME)
                            .put("size", smaller.bytes.size.toLong())
                            .put("width", smaller.width)
                            .put("height", smaller.height)
                    } else {
                        body.put("width", w).put("height", h)
                    }
                }

                Kind.VIDEO -> {
                    val meta = videoMeta(context, uri)
                    if (meta == null) {
                        return@withContext Result.failure(
                            IllegalStateException("Could not read that video")
                        )
                    }
                    body.put("width", meta.width)
                        .put("height", meta.height)
                        .put("duration_ms", meta.durationMs)
                }

                Kind.DOCUMENT -> body.put("file_name", displayName(context, uri))
                else -> Unit
            }

            // A preview, so a thread of photos is a thread of previews rather than
            // of originals. A phone photo is several megabytes; what a bubble shows
            // is a few score kilobytes of it, and fetching the rest to throw most of
            // it away is the weight behind every stutter while scrolling.
            val thumbnail = thumbnailFor(context, uri, kind)
            if (thumbnail != null) {
                body.put(
                    "thumbnail",
                    JSONObject()
                        .put("mime_type", THUMB_MIME)
                        .put("size", thumbnail.size)
                )
            }

            Result.success(
                Prepared(
                    uri = uri,
                    kind = kind,
                    mimeType = if (shrunk != null) SHRUNK_MIME else mime,
                    size = shrunk?.size?.toLong() ?: size,
                    body = body,
                    thumbnail = thumbnail,
                    payload = shrunk
                )
            )
        }

    /** Just enough to draw a placeholder bubble: what it is and what shape. */
    data class Shape(val kind: String, val aspect: Float)

    /**
     * Reads only the dimensions, before anything is uploaded.
     *
     * Cheap on purpose - the image decoder is asked for bounds only, and the video
     * retriever for two numbers - because this runs to size a bubble that has to
     * appear the moment a photo is picked. Reading the whole file first would be a
     * visible pause before anything showed.
     *
     * Falls back to a square, which is wrong but stable: a bubble that resizes once
     * the real shape is known looks worse than one that was never quite right.
     */
    suspend fun shapeOf(context: Context, uri: Uri): Shape = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        when (val kind = kindFor(mime)) {
            Kind.IMAGE -> {
                val (w, h) = imageSize(context.contentResolver.openInputStream(uri))
                Shape("image", if (w > 0 && h > 0) w.toFloat() / h else 1f)
            }

            Kind.VIDEO -> {
                val meta = videoMeta(context, uri)
                Shape(
                    "video",
                    if (meta != null && meta.width > 0 && meta.height > 0) {
                        meta.width.toFloat() / meta.height
                    } else {
                        1f
                    }
                )
            }

            else -> Shape(kind.value, 1f)
        }
    }

    /**
     * The declaration for a finished recording.
     *
     * Built from the file itself rather than from the picker path: the size is the
     * recorder's own output, and the waveform is what was actually sampled while
     * it ran.
     */
    fun voice(file: java.io.File, durationMs: Long, waveform: List<Int>): JSONObject =
        JSONObject()
            .put("kind", Kind.VOICE.value)
            .put("mime_type", "audio/mp4")
            .put("size", file.length())
            .put("duration_ms", durationMs)
            .apply {
                // Left out entirely when nothing was captured. The field is optional,
                // and sending an empty array would record "this clip has no shape" as
                // though that were measured.
                if (waveform.isNotEmpty()) put("waveform", org.json.JSONArray(waveform))
            }

    /**
     * A finished recording, in the same shape a picked file comes out in.
     *
     * So the upload queue has one kind of thing to carry. Everything after the
     * declaration - presign, PUT, complete, send - is identical for a voice note and
     * a photo; only the measuring differs, and that has already happened by the time
     * the recorder hands this over.
     */
    fun voiceUpload(file: java.io.File, durationMs: Long, waveform: List<Int>): Prepared =
        Prepared(
            uri = Uri.fromFile(file),
            kind = Kind.VOICE,
            mimeType = "audio/mp4",
            size = file.length(),
            body = voice(file, durationMs, waveform)
        )

    fun open(context: Context, uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    private fun queryColumnSize(context: Context, uri: Uri): Long? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (i >= 0 && c.moveToFirst() && !c.isNull(i)) c.getLong(i) else null
            }
        }.getOrNull()

    private fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "file")

    /** Bounds only: the pixels are never decoded, so a large photo costs nothing. */
    /**
     * A JPEG preview of a photo or a video's first frame, or null for anything else.
     *
     * Decoded straight to preview size rather than loaded and then shrunk - the
     * original can be twelve megapixels and there is no reason for all of it to be
     * in memory to produce a thumbnail of it.
     *
     * Null on any failure, which is not an error: the upload simply goes without
     * one, and the reader falls back to the file itself.
     */
    private fun thumbnailFor(context: Context, uri: Uri, kind: Kind): ByteArray? =
        runCatching {
            val source = when (kind) {
                Kind.IMAGE -> decodeAt(context, uri, THUMB_PX)
                Kind.VIDEO -> withRetriever { r ->
                    r.setDataSource(context, uri)
                    r.getFrameAtTime(0)
                }?.let { frame -> scaleTo(frame, THUMB_PX) }

                else -> null
            } ?: return null

            ByteArrayOutputStream().use { out ->
                source.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
                out.toByteArray()
            }.takeIf { it.isNotEmpty() && it.size <= THUMB_MAX_BYTES }
        }.getOrNull()

    private class Shrunk(val bytes: ByteArray, val width: Int, val height: Int)

    /**
     * A photo brought inside [PhotoQuality.MAX_EDGE], or null to send the original.
     *
     * Null in three cases, and each is a case where shrinking would be the worse
     * answer: the picture is already small enough, the decode failed, or the result
     * came out no smaller than what it replaced. The last one is not far-fetched -
     * a flat graphic stored as a small PNG re-encodes to a larger JPEG - and sending
     * it would mean spending more bytes to deliver less picture.
     */
    private fun shrink(context: Context, uri: Uri, original: Long): Shrunk? =
        runCatching {
            val bitmap = decodeAt(context, uri, PhotoQuality.MAX_EDGE) ?: return null
            val scaled = scaleTo(bitmap, PhotoQuality.MAX_EDGE)
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, SHRUNK_QUALITY, out)
                out.toByteArray()
            }
            if (bytes.isEmpty() || bytes.size >= original) return null
            Shrunk(bytes, scaled.width, scaled.height)
        }.getOrNull()

/**
 * Reads a video with a retriever and always gives it back.
 *
 * `MediaMetadataRetriever` only became `AutoCloseable` in API 29, so Kotlin's `use`
 * on it compiles happily and throws `NoSuchMethodError` on anything older - which
 * is every video thumbnail on Android 7 through 9. `release()` has been there all
 * along and is what those versions want.
 */
private inline fun <T> withRetriever(block: (MediaMetadataRetriever) -> T): T? {
    val r = MediaMetadataRetriever()
    return try {
        block(r)
    } finally {
        runCatching { r.release() }
    }
}

    /** Reads the bounds first so the decode itself is already near [target]. */
    private fun decodeAt(context: Context, uri: Uri, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / 2 >= target && h / 2 >= target) {
            w /= 2
            h /= 2
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun scaleTo(source: Bitmap, target: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= target) return source
        val scale = target.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Quality for a shrunk photo.
     *
     * Higher than the thumbnail's, because this is the picture rather than a stand-in
     * for it - at 600px the artefacts of a low setting are the whole image.
     */
    private const val SHRUNK_QUALITY = 88
    private const val SHRUNK_MIME = "image/jpeg"

    /** Big enough to fill a bubble without being the original. */
    private const val THUMB_PX = 720
    private const val THUMB_QUALITY = 80
    private const val THUMB_MIME = "image/jpeg"

    /** The spec's ceiling for a declared thumbnail. */
    private const val THUMB_MAX_BYTES = 512 * 1024

    private fun imageSize(stream: InputStream?): Pair<Int, Int> {
        stream ?: return 0 to 0
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream.use { BitmapFactory.decodeStream(it, null, options) }
        return options.outWidth to options.outHeight
    }

    private data class VideoMeta(val width: Int, val height: Int, val durationMs: Long)

    private fun videoMeta(context: Context, uri: Uri): VideoMeta? = runCatching {
        withRetriever { r ->
            r.setDataSource(context, uri)
            fun key(k: Int) = r.extractMetadata(k)?.toLongOrNull() ?: 0L
            val w = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            val h = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            val d = key(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (w <= 0 || h <= 0) null else VideoMeta(w, h, d)
        }
    }.getOrNull()
}
