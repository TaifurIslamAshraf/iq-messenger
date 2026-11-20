package com.iqsocial.iq_message.data.media

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

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
        val bars = waveformOf(captured)
        // Said out loud, because a waveform that comes out empty is invisible in
        // every other way: the message still sends, still plays, still shows the
        // right length, and draws the flat line meant for messages that never had a
        // shape. The three numbers tell the three apart - no samples at all means
        // the sampling loop never ran, samples with a peak of zero means this phone
        // reports no amplitude, and bars of zero from a real peak means the floor
        // in [loudness] is set above what was actually said.
        Log.i(
            TAG,
            "voice ${duration}ms: ${captured.size} samples, " +
                "peak=${captured.maxOrNull() ?: 0}, ${bars.size} bars"
        )
        return Recording(file, duration, bars)
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
     * The bars, built by [VoiceShape] so that both ways of measuring a clip draw
     * the same shape for the same words.
     */
    internal fun waveformOf(raw: List<Int>): List<Int> = VoiceShape.of(raw)

    companion object {
        private const val TAG = "VoiceRecorder"

        /** Below this a press reads as a mis-tap, not a message. */
        const val MIN_MS = 700L

        /** Often enough that a two-second clip still has a shape. */
        private const val SAMPLE_MS = 60L

        /** A ceiling so a long recording cannot grow without bound. */
        private const val MAX_SAMPLES = 2_000

        /** The range `MediaRecorder.maxAmplitude` reports. */
        private const val MAX_AMPLITUDE = 32767

    }
}
