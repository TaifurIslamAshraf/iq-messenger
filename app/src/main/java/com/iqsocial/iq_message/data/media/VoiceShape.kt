package com.iqsocial.iq_message.data.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.log10

/**
 * The shape of a recording, measured from the recording itself.
 *
 * [VoiceRecorder] asks the microphone how loud it is while it records, which is
 * free and instant and works on most phones. On this one it does not:
 *
 *     VoiceRecorder: voice 4906ms: 76 samples, peak=0, 0 bars
 *
 * Seventy-six readings taken, every one of them zero. `MediaRecorder.maxAmplitude`
 * is allowed to return nothing and several makers' builds do, so a voice note
 * arrived with no waveform at all and the bubble drew the flat line meant for
 * messages that never had one. Nothing was broken in a way anything could see.
 *
 * So the loudness is taken from the file instead: decode the finished clip back to
 * PCM and read the peaks out of it. It costs a few tens of milliseconds for a clip
 * of a few seconds, off the main thread, once - and it cannot be refused, because
 * by then the audio is simply bytes this app owns.
 *
 * The bars are built here for both routes, so a phone that reports amplitude and one
 * that does not draw the same shape for the same words.
 */
object VoiceShape {

    private const val TAG = "VoiceShape"

    /** How many bars a bubble draws. */
    const val BARS = 48

    /** The range both `maxAmplitude` and 16-bit PCM report. */
    private const val MAX_AMPLITUDE = 32767

    /** Quieter than this is silence, not a very short bar. */
    private const val FLOOR_DB = -50.0

    /** A recording whose loudest bar is under this is not worth stretching. */
    private const val QUIET_PEAK = 12

    /** One peak per this many frames: fine enough to keep the consonants. */
    private const val WINDOW = 512

    /**
     * Reads the clip and returns its bars, or an empty list if it cannot be read.
     *
     * Empty rather than flat, deliberately and for the same reason the recorder is:
     * zeros are a claim that the recording was silent, and failing to measure is not
     * the same as having measured silence.
     */
    suspend fun measure(file: File, bars: Int = BARS): List<Int> =
        withContext(Dispatchers.Default) {
            val peaks = runCatching { peaksOf(file) }.getOrElse {
                Log.w(TAG, "Could not read the recording to measure it", it)
                emptyList()
            }
            if (peaks.isEmpty()) return@withContext emptyList()
            of(peaks, bars)
        }

    /**
     * The loudest sample in each small window of the decoded audio.
     *
     * Peak rather than average, here as in the live path: averaging flattens the
     * consonants that give a waveform its texture.
     */
    private fun peaksOf(file: File): List<Int> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return emptyList()

            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return drain(codec, extractor)
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * The decode loop: feed it the file, take back PCM, keep the peaks.
     *
     * Deliberately not keeping the audio. A clip of a few seconds is a few hundred
     * kilobytes decoded, and all that is wanted from it is forty-eight numbers - so
     * each buffer is reduced as it arrives and dropped.
     */
    private fun drain(codec: MediaCodec, extractor: MediaExtractor): List<Int> {
        val peaks = mutableListOf<Int>()
        val info = MediaCodec.BufferInfo()
        var fed = false
        var done = false
        // A decode that never ends is the one failure this loop can have, and it
        // would not look like a failure: a thread spinning on a background
        // dispatcher for the life of the process, with a voice note that simply
        // never gets its shape. A clip is seconds long and decodes in tens of
        // milliseconds, so anything past this is a file or a codec that is not
        // going to finish - better to give up with no waveform than not to give up.
        val deadline = System.currentTimeMillis() + DECODE_LIMIT_MS

        while (!done) {
            if (System.currentTimeMillis() > deadline) {
                Log.w(TAG, "Gave up measuring the recording after ${DECODE_LIMIT_MS}ms")
                return peaks
            }
            if (!fed) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val input = codec.getInputBuffer(index)
                    val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        fed = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> if (out >= 0) {
                    codec.getOutputBuffer(out)?.let { buffer ->
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        peaks += windowPeaks(buffer.asShortBuffer())
                    }
                    codec.releaseOutputBuffer(out, false)
                    // Only now, because `info` is only written when a buffer comes
                    // back. Read after a "try again" it still holds the flags of
                    // whatever came out last, which is somebody else's answer.
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                }
            }
        }
        return peaks
    }

    private fun windowPeaks(samples: java.nio.ShortBuffer): List<Int> {
        val found = mutableListOf<Int>()
        var loudest = 0
        var counted = 0
        while (samples.hasRemaining()) {
            val value = abs(samples.get().toInt())
            if (value > loudest) loudest = value
            if (++counted == WINDOW) {
                found += loudest
                loudest = 0
                counted = 0
            }
        }
        if (counted > 0) found += loudest
        return found
    }

    /**
     * Raw peaks to drawable bars.
     *
     * The three steps that decide whether the shape reads as speech:
     *
     * 1. **Peak within a bar**, not mean - see [peaksOf].
     * 2. **Decibels**, because loudness is heard logarithmically. `maxAmplitude`
     *    runs 0..32767 and ordinary speech into a phone sits around 2000-12000 of
     *    that, so a linear scale squashes a whole sentence into the bottom third and
     *    draws every syllable at nearly the same height.
     * 3. **Normalised to the clip's own peak**, so a message spoken quietly still
     *    fills the bubble - but only once the peak clears [QUIET_PEAK], or a
     *    recording of a silent room would be amplified into a wall of noise.
     */
    fun of(raw: List<Int>, bars: Int = BARS): List<Int> {
        // Empty, not a row of zeros. Zeros are a claim - that the recording was
        // silent - and having captured nothing is not that claim.
        if (raw.isEmpty()) return emptyList()

        val grouped = if (raw.size <= bars) raw else {
            val step = raw.size.toFloat() / bars
            (0 until bars).map { i ->
                val from = (i * step).toInt()
                val to = ((i + 1) * step).toInt().coerceAtMost(raw.size)
                if (from >= to) raw[from.coerceAtMost(raw.lastIndex)]
                else raw.subList(from, to).max()
            }
        }

        val levels = grouped.map(::loudness)
        val loudest = levels.maxOrNull() ?: 0
        // Nothing audible, which is not the same as silence having been recorded.
        if (loudest <= 0) return emptyList()
        if (loudest < QUIET_PEAK) return levels
        return levels.map { (it * 100 / loudest).coerceIn(0, 100) }
    }

    private fun loudness(amplitude: Int): Int {
        if (amplitude <= 0) return 0
        val db = 20.0 * log10(amplitude.toDouble() / MAX_AMPLITUDE)   // -90..0
        if (db <= FLOOR_DB) return 0
        return ((db - FLOOR_DB) / -FLOOR_DB * 100).toInt().coerceIn(0, 100)
    }

    private const val TIMEOUT_US = 10_000L

    /** Past this, the file is not going to decode and the thread is better used. */
    private const val DECODE_LIMIT_MS = 5_000L
}
