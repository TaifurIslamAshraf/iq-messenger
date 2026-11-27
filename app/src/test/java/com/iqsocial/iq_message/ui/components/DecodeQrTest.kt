package com.iqsocial.iq_message.ui.components

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the scanner reads out of a camera frame's brightness plane. */
class DecodeQrTest {

    private val size = 480

    /** A frame showing [text] as a QR code, the way the QR screen draws one: dark on white. */
    private fun frameOf(text: String): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return ByteArray(size * size) { i -> if (matrix[i % size, i / size]) 0 else 0xFF.toByte() }
    }

    private fun turned(frame: ByteArray) = ByteArray(size * size) { i ->
        val x = i % size
        val y = i / size
        frame[(size - 1 - x) * size + y]
    }

    @Test
    fun `reads the code in a frame`() {
        assertEquals("K7M2Q9XA4T", decodeQr(frameOf("K7M2Q9XA4T"), size, size))
    }

    @Test
    fun `reads it held sideways too, as a phone in portrait sees it`() {
        assertEquals("K7M2Q9XA4T", decodeQr(turned(frameOf("K7M2Q9XA4T")), size, size))
    }

    @Test
    fun `finds nothing in a frame without a code`() {
        assertNull(decodeQr(ByteArray(size * size) { 0x80.toByte() }, size, size))
    }
}
