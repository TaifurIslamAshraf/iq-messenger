package com.iqsocial.iq_message.data.media

import com.iqsocial.iq_message.ui.screens.clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a picked file is declared as, and what it costs to show. */
class MediaPreparerTest {

    @Test
    fun `the types the image variant accepts are declared as images`() {
        listOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif")
            .forEach {
                assertEquals(it, MediaPreparer.Kind.IMAGE, MediaPreparer.kindFor(it))
            }
    }

    @Test
    fun `video types are declared as video`() {
        listOf("video/mp4", "video/3gpp", "video/quicktime", "video/webm", "video/x-matroska")
            .forEach {
                assertEquals(it, MediaPreparer.Kind.VIDEO, MediaPreparer.kindFor(it))
            }
    }

    @Test
    fun `an unrecognised image still goes as a document rather than being refused`() {
        // TIFF has bytes worth sending even though the image variant will not take it.
        assertEquals(MediaPreparer.Kind.DOCUMENT, MediaPreparer.kindFor("image/tiff"))
        assertEquals(MediaPreparer.Kind.DOCUMENT, MediaPreparer.kindFor("application/pdf"))
    }

    @Test
    fun `a missing type is still sendable`() {
        assertEquals(MediaPreparer.Kind.DOCUMENT, MediaPreparer.kindFor(""))
        assertEquals(
            MediaPreparer.Kind.DOCUMENT,
            MediaPreparer.kindFor("application/octet-stream")
        )
    }

    @Test
    fun `a resolver type with charset or odd case is still recognised`() {
        // Providers hand back things like "image/jpeg; charset=utf-8" and "IMAGE/PNG".
        assertEquals(MediaPreparer.Kind.IMAGE, MediaPreparer.kindFor("image/jpeg; charset=utf-8"))
        assertEquals(MediaPreparer.Kind.IMAGE, MediaPreparer.kindFor("IMAGE/PNG"))
        assertEquals(MediaPreparer.Kind.VIDEO, MediaPreparer.kindFor(" video/mp4 "))
    }

    @Test
    fun `a file inside its limit is accepted`() {
        assertNull(MediaPreparer.overLimit(MediaPreparer.Kind.IMAGE, 20L * 1024 * 1024))
        assertNull(MediaPreparer.overLimit(MediaPreparer.Kind.VIDEO, 1L))
    }

    @Test
    fun `a file over its limit is refused with the limit named`() {
        val message = MediaPreparer.overLimit(MediaPreparer.Kind.IMAGE, 20L * 1024 * 1024 + 1)
        assertNotNull(message)
        assertTrue("should name the limit, was: $message", message!!.contains("20 MB"))
        assertTrue("should name the kind, was: $message", message.contains("image"))
    }

    @Test
    fun `the limits are the ones the spec sets`() {
        assertEquals(20L * 1024 * 1024, MediaPreparer.Kind.IMAGE.maxBytes)
        assertEquals(100L * 1024 * 1024, MediaPreparer.Kind.VIDEO.maxBytes)
        assertEquals(16L * 1024 * 1024, MediaPreparer.Kind.VOICE.maxBytes)
        assertEquals(100L * 1024 * 1024, MediaPreparer.Kind.AUDIO.maxBytes)
        assertEquals(100L * 1024 * 1024, MediaPreparer.Kind.DOCUMENT.maxBytes)
    }
}

/**
 * Downsampling, which is silent when wrong: too small and avatars are mush, too
 * large and a list of them evicts itself out of the cache on every scroll.
 */
class DownsampleTest {

    @Test
    fun `an image already small enough is decoded whole`() {
        assertEquals(1, RemoteImage.sampleFor(180, 180))
        assertEquals(1, RemoteImage.sampleFor(300, 300))
    }

    @Test
    fun `a large photo is halved until it is near the target`() {
        // 1536 -> 768 -> 384 -> 192. The last halving is taken because it lands *on*
        // the target, not under it; the next would reach 96 and is not.
        assertEquals(8, RemoteImage.sampleFor(1536, 1536))
        assertEquals(192, 1536 / RemoteImage.sampleFor(1536, 1536))
    }

    @Test
    fun `the result never falls below the target in either dimension`() {
        for (w in listOf(200, 400, 900, 1080, 2000, 4032)) {
            for (h in listOf(200, 400, 900, 1080, 2000, 3024)) {
                val sample = RemoteImage.sampleFor(w, h)
                assertTrue(
                    "sample $sample for ${w}x$h is not a power of two",
                    sample > 0 && (sample and (sample - 1)) == 0
                )
                assertTrue(
                    "${w}x$h sampled by $sample drops under 192",
                    w / sample >= 192 || h / sample >= 192
                )
            }
        }
    }

    @Test
    fun `a panorama is governed by its shorter side`() {
        // 4000x200: halving would take the height under the target at once, so the
        // wide edge stays large rather than the image being reduced to a strip.
        assertEquals(1, RemoteImage.sampleFor(4000, 200))
    }

    @Test
    fun `message photos keep far more detail than avatars`() {
        // A chat preview keeps more detail than an avatar, within a bounded texture.
        val forBubble = MediaCache.sampleFor(4032, 3024)
        val forAvatar = RemoteImage.sampleFor(4032, 3024)
        assertTrue(
            "bubble sample $forBubble should be smaller than avatar sample $forAvatar",
            forBubble < forAvatar
        )
        val (width, height) = PreviewBitmap.size(4032 / forBubble, 3024 / forBubble)
        assertEquals(720, width)
        assertEquals(540, height)
    }

    @Test
    fun `zero dimensions do not loop or crash`() {
        assertEquals(1, RemoteImage.sampleFor(0, 0))
        assertEquals(1, RemoteImage.sampleFor(-1, -1))
    }
}

/** The duration badge on a video tile. */
class ClockTest {

    @Test
    fun `seconds are padded so the colon does not jump`() {
        assertEquals("0:05", clock(5_000))
        assertEquals("0:00", clock(0))
    }

    @Test
    fun `minutes roll over`() {
        assertEquals("1:00", clock(60_000))
        assertEquals("2:03", clock(123_000))
    }

    @Test
    fun `a long video is still minutes, not hours`() {
        assertEquals("90:00", clock(90L * 60 * 1000))
    }

    @Test
    fun `part seconds round down rather than up`() {
        // 9.9s is still 0:09; showing 0:10 would overstate a clip's length.
        assertEquals("0:09", clock(9_900))
    }
}
