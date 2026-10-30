package com.iqsocial.iq_message.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10

/**
 * Records a voice message to a temporary file.
 *
 * AAC in an MP4 container: `audio/mp4` is on the server's accepted list, plays
 * everywhere, and is what `MediaRecorder` produces most reliably across the
 * versions this app supports.
 *
 * Amplitude is sampled while recording so the waveform sent with the message is the
 * real shape of what was said, rather than a decorative one invented afterwards.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L
    private val amplitudes = mutableListOf<Int>()

    /**
     * Sampling runs here, not in the screen.
     *
     * It used to be a `LaunchedEffect` in the chat screen ticking `sample()` beside
     * the visible timer. That made the waveform depend on a loop that belongs to the
     * UI: if it did not run, the recording still had the right length - the duration
     * is measured from the clock, not from the ticks - and still sent, but with
     * nothing in it. A voice message would arrive with a flat line and no sign
     * anything had gone wrong.
     *
     * The recorder owns its own waveform now, so there is nothing left to go out of
     * step with.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sampling: Job? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        if (recorder != null) return false
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            output = file
            startedAt = System.currentTimeMillis()
            amplitudes.clear()
            sampling = scope.launch {
                // The first reading is the peak since the recorder was created
                // rather than since the last call, so it is not a sample of
                // anything and is thrown away.
                runCatching { r.maxAmplitude }
                while (isActive) {
                    delay(SAMPLE_MS)
                    val level = runCatching { r.maxAmplitude }.getOrDefault(0)
                    synchronized(amplitudes) {
                        if (amplitudes.size < MAX_SAMPLES) {
                            amplitudes += level.coerceIn(0, MAX_AMPLITUDE)
                        }
                    }
                }
            }
            true
        }.getOrElse {
            runCatching { r.release() }
            file.delete()
            false
        }
    }

    /**
     * Turns a raw amplitude into a bar height.
     *
     * **Decibels, not a straight scale.** `maxAmplitude` runs 0..32767 and ordinary
     * speech into a phone sits somewhere around 2000-12000 of that, so mapping it
     * linearly - which is what this did - squashed a whole sentence into the bottom
     * third of the bar and drew every syllable at nearly the same height. Loudness
     * is heard logarithmically, so it has to be drawn that way to look like what was
     * said.
     *
     * [FLOOR_DB] is where a quiet room sits; anything below it is silence rather
     * than a very short bar.
     */
    private fun loudness(amplitude: Int): Int {
        if (amplitude <= 0) return 0
        val db = 20.0 * log10(amplitude.toDouble() / MAX_AMPLITUDE)   // -90..0
        if (db <= FLOOR_DB) return 0
        return ((db - FLOOR_DB) / -FLOOR_DB * 100).toInt().coerceIn(0, 100)
    }

    data class Recording(val file: File, val durationMs: Long, val waveform: List<Int>)

    /** Stops and returns the file, or null if it was too short to be a message. */
    fun stop(): Recording? {
        val r = recorder ?: return null
        val file = output
        recorder = null
        output = null
        sampling?.cancel()
        sampling = null

        val stopped = runCatching {
            r.stop()
            true
        }.getOrDefault(false)
        runCatching { r.release() }

        val duration = System.currentTimeMillis() - startedAt
        if (!stopped || file == null || !file.exists() || duration < MIN_MS) {
            file?.delete()
            return null
        }
        val captured = synchronized(amplitudes) { amplitudes.toList() }
        return Recording(file, duration, waveformOf(captured))
    }

    /** Throws the recording away — used when the gesture is cancelled. */
    fun cancel() {
        val r = recorder ?: return
        recorder = null
        sampling?.cancel()
        sampling = null
        runCatching { r.stop() }
        runCatching { r.release() }
        output?.delete()
        output = null
    }

    /**
     * The bars the message is sent with.
     *
     * Three steps, and each one matters to whether the shape reads as speech:
     *
     * 1. **Peak, not mean, within a bar.** Averaging raw samples flattens the
     *    consonants that give a waveform its texture; a bar should say how loud it
     *    got, not how loud it was on average.
     * 2. **Decibels** - see [loudness].
     * 3. **Normalised to the recording's own peak**, so a message spoken quietly
     *    still fills the bubble. The peak has to clear [QUIET_PEAK] first, or a
     *    recording of a silent room would be amplified into a wall of noise.
     */
    internal fun waveformOf(raw: List<Int>): List<Int> {
        // Empty, not a row of zeros. Zeros are a claim - that the recording was
        // silent - and the sender cannot tell that from having captured nothing.
        // The bubble draws its own placeholder for an absent waveform.
        if (raw.isEmpty()) return emptyList()

        val peaks = if (raw.size <= BARS) raw else {
            val step = raw.size.toFloat() / BARS
            (0 until BARS).map { i ->
                val from = (i * step).toInt()
                val to = ((i + 1) * step).toInt().coerceAtMost(raw.size)
                if (from >= to) raw[from.coerceAtMost(raw.lastIndex)]
                else raw.subList(from, to).max()
            }
        }

        val levels = peaks.map { loudness(it) }
        val loudest = levels.maxOrNull() ?: 0
        // Nothing audible was captured, which is not the same as silence having
        // been recorded - either way there is no shape worth sending.
        if (loudest <= 0) return emptyList()
        if (loudest < QUIET_PEAK) return levels
        return levels.map { (it * 100 / loudest).coerceIn(0, 100) }
    }

    companion object {
        /** Below this a press reads as a mis-tap, not a message. */
        const val MIN_MS = 700L
        private const val BARS = 48

        /** Often enough that a two-second clip still has a shape. */
        private const val SAMPLE_MS = 60L

        /** A ceiling so a long recording cannot grow without bound. */
        private const val MAX_SAMPLES = 2_000

        /** The range `MediaRecorder.maxAmplitude` reports. */
        private const val MAX_AMPLITUDE = 32767

        /** Quieter than this is silence, not a very short bar. */
        private const val FLOOR_DB = -50.0

        /** A recording whose loudest bar is under this is not worth stretching. */
        private const val QUIET_PEAK = 12
    }
}
