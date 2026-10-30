package com.iqsocial.iq_message.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The phone's own photos and videos, for an in-app picker.
 *
 * The system picker needs no permission and is the right default for a one-off
 * choice; a tray that lives in the composer is a different thing — it has to show
 * the roll itself, which means reading MediaStore and asking for access.
 */
object DeviceGallery {

    // Bound decoder work when several local attachments enter the viewport together.
    private val decodeGate = Semaphore(2)

    @Immutable
    data class Item(
        val uri: Uri,
        val isVideo: Boolean,
        val durationMs: Long
    )

    /**
     * What to ask for, which changed in Android 13 and again in 14.
     *
     * Before 13 one storage permission covered everything; after it, images and
     * video are asked for separately. From 14 there is a third answer - "select
     * photos" - and it has to be asked for by name or the system treats the
     * selection as a one-off and asks again on every open.
     */
    val permissions: Array<String> =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )

            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun isGranted(context: Context): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The most recent [limit] items, newest first.
     *
     * Images and video come from one query over `MediaStore.Files` rather than two
     * merged ones, so the ordering is the store's own and paging it later stays
     * honest.
     */
    suspend fun recent(context: Context, limit: Int = 120): List<Item> =
        withContext(Dispatchers.IO) {
            if (!isGranted(context)) return@withContext emptyList()

            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DURATION
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
            val args = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
            val order = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            val items = mutableListOf<Item>()
            runCatching {
                context.contentResolver.query(collection, projection, selection, args, order)
                    ?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val typeColumn =
                            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                        val durationColumn =
                            cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)

                        while (cursor.moveToNext() && items.size < limit) {
                            val id = cursor.getLong(idColumn)
                            val isVideo = cursor.getInt(typeColumn) ==
                                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                            val duration =
                                if (durationColumn >= 0 && !cursor.isNull(durationColumn)) {
                                    cursor.getLong(durationColumn)
                                } else {
                                    0L
                                }
                            items += Item(
                                uri = ContentUris.withAppendedId(collection, id),
                                isVideo = isVideo,
                                durationMs = duration
                            )
                        }
                    }
            }
            items
        }

    /* -- thumbnails ---------------------------------------------------------- */

    private val thumbs = object : LruCache<String, ImageBitmap>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    fun peek(uri: Uri): ImageBitmap? = thumbs.get(uri.toString())

    /**
     * A grid-sized thumbnail.
     *
     * `loadThumbnail` asks the store for one it may already have, which is far
     * cheaper than decoding the original; below API 29 there is no such call, so
     * the bounds are read and the decode is downsampled instead.
     */
    suspend fun thumbnail(context: Context, uri: Uri): ImageBitmap? {
        thumbs.get(uri.toString())?.let { return it }
        return withContext(Dispatchers.IO) {
            decodeGate.withPermit {
                thumbs.get(uri.toString())?.let { return@withPermit it }
                val bitmap = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, Size(THUMB_PX, THUMB_PX), null)
                    } else {
                        decodeDownsampled(context, uri)
                    }
                }.getOrNull() ?: return@withPermit null

                PreviewBitmap.prepare(bitmap, THUMB_PX).also { thumbs.put(uri.toString(), it) }
            }
        }
    }

    private fun decodeDownsampled(
        context: Context,
        uri: Uri,
        targetPx: Int = THUMB_PX
    ): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = PreviewBitmap.sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /* -- bubble-sized previews ----------------------------------------------- */

    private val PREVIEW_CACHE_BYTES = (Runtime.getRuntime().maxMemory() / 16)
        .coerceIn(8L * 1024 * 1024, 32L * 1024 * 1024)
        .toInt()

    private val previews = object : LruCache<String, ImageBitmap>(PREVIEW_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    fun peekPreview(uri: Uri): ImageBitmap? = previews.get(uri.toString())

    /**
     * The picture at bubble size, for one being uploaded.
     *
     * Not [thumbnail]: that is 256px for a grid tile, and a bubble is three times
     * that across, so a tile stretched into one is visibly soft. The file is on the
     * device either way, so there is no reason to show a worse copy of it than the
     * one that is about to be sent.
     */
    suspend fun preview(context: Context, uri: Uri): ImageBitmap? {
        previews.get(uri.toString())?.let { return it }
        return withContext(Dispatchers.IO) {
            decodeGate.withPermit {
                previews.get(uri.toString())?.let { return@withPermit it }
                val bitmap = runCatching {
                    val video = context.contentResolver.getType(uri)
                        .orEmpty().startsWith("video/")
                    if (video) {
                        withRetriever { r ->
                            r.setDataSource(context, uri)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                r.getScaledFrameAtTime(
                                    0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                    PREVIEW_PX, PREVIEW_PX
                                )
                            } else {
                                r.getFrameAtTime(0)
                            }
                        }
                    } else {
                        decodeDownsampled(context, uri, PREVIEW_PX)
                    }
                }.getOrNull() ?: return@withPermit null
                PreviewBitmap.prepare(bitmap).also { previews.put(uri.toString(), it) }
            }
        }
    }

    private const val THUMB_PX = 256
    private const val THUMB_CACHE_BYTES = 16 * 1024 * 1024
    /** The same ceiling a message photo gets; a bubble is a bubble either way. */
    private const val PREVIEW_PX = PreviewBitmap.MAX_EDGE
}

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
