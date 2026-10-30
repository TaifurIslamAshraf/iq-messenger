package com.iqsocial.iq_message.data.call

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * The tone the caller hears while the other phone rings.
 *
 * It was silence. The person being called got a ringtone, and the person who placed
 * the call got nothing at all - no way to tell a call that is ringing from one that
 * never left the phone, which is the moment people hang up and try again.
 *
 * `ToneGenerator` rather than an audio file, because this particular sound is one
 * the platform already has and already knows how to play down a call stream:
 * `TONE_SUP_RINGTONE` is the network ringback every phone uses, and playing it on
 * `STREAM_VOICE_CALL` means it follows the call's own routing - out of the earpiece,
 * the speaker or a headset, wherever the call is going, and at the call's volume
 * rather than the media one.
 */
object Ringback {

    private var tone: ToneGenerator? = null

    /** Starts it, if it is not already going. Safe to call twice. */
    fun start() {
        if (tone != null) return
        runCatching {
            // Not full volume: this plays against the ear while waiting, and the
            // platform tone is louder than speech.
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, VOLUME).also {
                // No duration, deliberately. `TONE_SUP_RINGTONE` is defined as a
                // repeating tone - two seconds on, four off - so this form plays it
                // until `stopTone`, which is exactly as long as it should last.
                //
                // It was `startTone(tone, Int.MAX_VALUE)`, meaning "forever". The
                // native side turns milliseconds into a sample count by multiplying,
                // and that many milliseconds overflows it: the phone aborted the
                // whole process with `ubsan: mul-overflow` the moment a call was
                // placed. Not catchable from here either - a native abort takes the
                // process down without an exception for `runCatching` to see.
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
                tone = it
            }
        }
    }

    /**
     * Stops and lets go.
     *
     * Released rather than merely stopped: a `ToneGenerator` holds an audio track
     * open, and one left behind keeps a slice of the call stream for a call that has
     * already been answered.
     */
    fun stop() {
        runCatching {
            tone?.stopTone()
            tone?.release()
        }
        tone = null
    }

    private const val VOLUME = 60
}
