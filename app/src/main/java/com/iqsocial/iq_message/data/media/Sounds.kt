package com.iqsocial.iq_message.data.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The two small sounds the app makes: a message leaving, and a reaction landing.
 *
 * Synthesised rather than played from files. Not to avoid the work - a recorded
 * sound would be better - but because `ToneGenerator`, the usual no-asset answer,
 * only has the phone's dialling tones in it. Those are *signals*: flat, square and
 * instantly recognisable as a keypad. A few lines of arithmetic give a sine with an
 * envelope on it, which is the difference between a beep and a note.
 *
 * Dropping in real audio later changes only [play]: put the files in `res/raw` and
 * sound them there, and both callers stay as they are.
 */
object Sounds {

    /** A message went. Low, short and almost under the threshold of noticing. */
    fun sent(context: Context) = play(context, SENT, 0.22f)

    /**
     * A reaction landed. Two notes rising, because it is a reply of sorts and should
     * read as going somewhere rather than stopping.
     */
    fun reaction(context: Context) = play(context, REACTION, 0.30f)

    /**
     * The swipe has gone far enough to fire.
     *
     * A tick rather than a tone, and the quietest of the four. It sounds while a
     * finger is still moving, so it has to read as the gesture catching on something
     * - not as an event in its own right. It goes with the haptic at the same
     * instant; the two are one piece of feedback in two senses.
     */
    fun swipeArmed(context: Context) = play(context, SWIPE_ARMED, 0.13f)

    /** The reply took. A shade brighter, and only on release. */
    fun swipeDone(context: Context) = play(context, SWIPE_DONE, 0.18f)

    private data class Note(val hz: Double, val ms: Int)

    private val SENT = listOf(Note(740.0, 70))
    private val REACTION = listOf(Note(659.25, 60), Note(987.77, 110))
    private val SWIPE_ARMED = listOf(Note(523.25, 35))
    private val SWIPE_DONE = listOf(Note(587.33, 40), Note(880.0, 70))

    /**
     * Builds the waveform and plays it once.
     *
     * Silent and vibrate are answers, not states to work around: somebody on vibrate
     * has said they do not want sounds, and these are the least important ones this
     * app could make.
     */
    private fun play(context: Context, notes: List<Note>, volume: Float) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        runCatching {
            val samples = render(notes, volume)
            val bytes = samples.size * 2

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Says what it is: a short confirmation, not media. The system
                        // ducks it against music and keeps it off the call stream.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()

            // Released on a timer rather than left open: an AudioTrack holds a mixer
            // slot, and these fire often enough to run the device out of them.
            val ms = notes.sumOf { it.ms }.toLong() + 150L
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ runCatching { track.release() } }, ms)
        }
    }

    /**
     * A sine per note, with a raised-cosine attack and an exponential tail.
     *
     * The envelope is the whole point. A tone that starts and stops at full
     * amplitude clicks at both ends, because the waveform jumps; easing it in over a
     * few milliseconds and letting it decay is what turns the same frequency from a
     * beep into something that sounds struck.
     */
    private fun render(notes: List<Note>, volume: Float): ShortArray {
        val total = notes.sumOf { it.ms * RATE / 1000 }
        val out = ShortArray(total)
        var at = 0

        notes.forEach { note ->
            val length = note.ms * RATE / 1000
            val attack = (RATE * ATTACK_MS / 1000).coerceAtMost(length / 2)
            for (i in 0 until length) {
                val t = i.toDouble() / RATE
                // A touch of the octave above keeps it from sounding hollow.
                val wave = sin(2 * PI * note.hz * t) + 0.25 * sin(4 * PI * note.hz * t)
                val rise = if (i < attack) 0.5 * (1 - kotlin.math.cos(PI * i / attack)) else 1.0
                val fall = exp(-DECAY * i.toDouble() / length)
                val value = wave / 1.25 * rise * fall * volume * Short.MAX_VALUE
                out[at + i] = value.toInt().coerceIn(-32768, 32767).toShort()
            }
            at += length
        }
        return out
    }

    private const val RATE = 44_100
    private const val ATTACK_MS = 6
    private const val DECAY = 4.0
}
