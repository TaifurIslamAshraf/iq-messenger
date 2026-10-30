package com.iqsocial.iq_message.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBitmapTest {
    @Test
    fun `camera photos and 4K video frames fit the preview budget`() {
        for ((width, height) in listOf(4032 to 3024, 3024 to 4032, 3840 to 2160)) {
            val sample = PreviewBitmap.sampleSize(width, height)
            // The intermediate decode is bounded too, before the final resize.
            assertTrue(maxOf(width, height) / sample < 1440)
            val (w, h) = PreviewBitmap.size(width / sample, height / sample)
            assertEquals(720, maxOf(w, h))
            assertTrue(w.toLong() * h * 4 <= 720L * 720 * 4)
            assertEquals(width.toDouble() / height, w.toDouble() / h, 0.01)
        }
    }

    @Test
    fun `panoramas and tall screenshots do not decode at full size`() {
        for ((width, height) in listOf(16000 to 400, 400 to 16000)) {
            val sample = PreviewBitmap.sampleSize(width, height)
            assertTrue(sample > 1)
            assertTrue(maxOf(width, height) / sample < 1440)
            val (w, h) = PreviewBitmap.size(width, height)
            assertEquals(720, maxOf(w, h))
            assertEquals(18, minOf(w, h))
        }
    }

    @Test
    fun `small previews are never enlarged`() {
        assertEquals(1, PreviewBitmap.sampleSize(320, 180))
        assertEquals(320 to 180, PreviewBitmap.size(320, 180))
        assertEquals(720 to 720, PreviewBitmap.size(720, 720))
    }

    @Test
    fun `grid thumbnails use their own smaller budget`() {
        assertEquals(256 to 144, PreviewBitmap.size(3840, 2160, 256))
        assertEquals(8, PreviewBitmap.sampleSize(3840, 2160, 256))
    }

    @Test
    fun `invalid bounds do not loop and very thin images stay nonzero`() {
        assertEquals(1, PreviewBitmap.sampleSize(0, 0))
        assertEquals(1, PreviewBitmap.sampleSize(-1, 4000))
        assertEquals(720 to 1, PreviewBitmap.size(Int.MAX_VALUE, 1))
        assertTrue(PreviewBitmap.sampleSize(Int.MAX_VALUE, 1) > 0)
    }
}
