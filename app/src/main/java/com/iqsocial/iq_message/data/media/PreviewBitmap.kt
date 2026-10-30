package com.iqsocial.iq_message.data.media

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Bounds the memory and texture upload cost of a chat preview, including panoramas. */
internal object PreviewBitmap {
    const val MAX_EDGE = 720

    // Decode close to the final size first, so resizing never allocates a full camera photo.
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        require(maxEdge > 0)
        if (width <= 0 || height <= 0) return 1
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / sample / 2 >= maxEdge) sample *= 2
        return sample
    }

    fun size(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0)
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        return (width.toLong() * maxEdge / longest).toInt().coerceAtLeast(1) to
            (height.toLong() * maxEdge / longest).toInt().coerceAtLeast(1)
    }

    /** Takes ownership of a newly decoded bitmap. Call on the decoder's worker thread. */
    fun prepare(source: Bitmap, maxEdge: Int = MAX_EDGE): ImageBitmap {
        val (width, height) = size(source.width, source.height, maxEdge)
        val preview = if (width == source.width && height == source.height) source
        else Bitmap.createScaledBitmap(source, width, height, true).also { source.recycle() }
        // Schedule the texture upload before the first visible frame needs the image.
        preview.prepareToDraw()
        return preview.asImageBitmap()
    }
}
