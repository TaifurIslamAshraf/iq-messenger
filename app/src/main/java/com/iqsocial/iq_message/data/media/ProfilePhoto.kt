package com.iqsocial.iq_message.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import com.iqsocial.iq_message.data.remote.ProfileImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * A picked photo, made into something the profile endpoints will take.
 *
 * The server signs an upload link for an **exact** number of bytes of an exact
 * type, and refuses a picture over two megabytes (five for a cover). A photo off a
 * modern phone is four to twelve megabytes of HEIC or JPEG, so handing the file
 * over as picked would fail for most people most of the time - and fail at the
 * last step, after the link had been spent.
 *
 * So the picture is re-encoded here first: decoded at roughly the size it will be
 * shown at, re-compressed as JPEG, and squeezed further while it is still too big.
 * What goes up is a byte array whose length is known before the link is asked for,
 * which is the only way the declared size can be the truth.
 *
 * Rotation is applied rather than carried. A JPEG says which way up it is in its
 * EXIF, and a bitmap re-encoded from it loses that tag - so a portrait photo taken
 * sideways would arrive on its side, on every screen in both apps.
 */
object ProfilePhoto {

    private const val TAG = "ProfilePhoto"

    /** Where the squeezing starts, and how far down it is allowed to go. */
    private const val FIRST_QUALITY = 90
    private const val LAST_QUALITY = 60
    private const val QUALITY_STEP = 10

    /** JPEG for everything: it is one of the three the server takes, and the small one. */
    const val MIME = "image/jpeg"

    /** The picture, ready to be declared and uploaded. */
    class Ready(val bytes: ByteArray, val mimeType: String = MIME) {
        val size: Long get() = bytes.size.toLong()
    }

    /**
     * Reads [uri] and gives back something within [kind]'s limit, or null.
     *
     * Null means the picture could not be read or could not be brought under the
     * limit - a caller that gets null has nothing to upload and should say so,
     * rather than asking for a link it cannot fill.
     */
    suspend fun prepare(context: Context, uri: Uri, kind: ProfileImage): Ready? =
        withContext(Dispatchers.IO) {
            val source = decode(context, uri, kind.maxEdge) ?: return@withContext null
            squeeze(source, kind)
        }

    /**
     * The same, for a picture already in hand - the one the crop screen cut.
     *
     * No decoding and no rotation: a bitmap that came out of [ImageCrop] has been
     * through both already, and turning it again by the original file's EXIF would
     * turn it twice.
     */
    suspend fun prepare(bitmap: Bitmap, kind: ProfileImage): Ready? =
        withContext(Dispatchers.IO) { squeeze(bitmap, kind) }

    /**
     * A picture off the device, at about the size asked for and the right way up.
     *
     * Public because cropping needs the same thing the upload does: the file read
     * once, cheaply, with its rotation applied - and a crop screen that worked on
     * the unrotated bitmap would let somebody carefully frame a photo that then
     * arrives on its side.
     */
    suspend fun decode(context: Context, uri: Uri, maxEdge: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeAt(context, uri, maxEdge) ?: return@runCatching null
                turnUpright(context, uri, bitmap)
            }.getOrElse {
                Log.w(TAG, "Could not read the picture", it)
                null
            }
        }

    /**
     * Down to something the server will take.
     *
     * Three passes at most: full size, half, quarter. Past that the picture is not
     * a picture any more, and a file that still will not fit at a quarter of
     * 1080px is not one this is going to fix.
     */
    private fun squeeze(source: Bitmap, kind: ProfileImage): Ready? {
        var edge = kind.maxEdge
        repeat(3) {
            val scaled = runCatching { scaleTo(source, edge) }.getOrNull() ?: return null
            var quality = FIRST_QUALITY
            while (quality >= LAST_QUALITY) {
                val bytes = runCatching { encode(scaled, quality) }.getOrNull() ?: return null
                if (bytes.size <= kind.maxBytes) {
                    Log.i(
                        TAG,
                        "${kind.wire}: ${scaled.width}x${scaled.height} " +
                            "q$quality -> ${bytes.size} bytes"
                    )
                    return Ready(bytes)
                }
                quality -= QUALITY_STEP
            }
            edge /= 2
        }
        Log.w(TAG, "${kind.wire}: could not bring the picture under ${kind.maxBytes} bytes")
        return null
    }

    /** Reads the bounds first, so the decode itself lands near [target]. */
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

    /**
     * Turns the bitmap the way the photo says it should be.
     *
     * Only JPEG carries this, and only sometimes; anything without the tag is
     * already upright and comes back untouched.
     */
    private fun turnUpright(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val turn = Matrix().apply { postRotate(degrees) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, turn, true)
        }.getOrDefault(bitmap)
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

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}
