package com.iqsocial.iq_message.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.iqsocial.iq_message.data.media.Tones

/**
 * What the caller hears while they wait, which is two different sounds.
 *
 * It was silence. The person being called got a ringtone, and the person who placed
 * the call got nothing at all - no way to tell a call that is ringing from one that
 * never left the phone, which is the moment people hang up and try again. Then it
 * was the platform's own `TONE_SUP_RINGTONE`, which answered that but said only one
 * thing for the whole wait.
 *
 * There are two waits, and they feel nothing alike:
 *
 * - **[connecting]** - the call has been asked for and the other phone is not
 *   ringing yet. A short beep, repeated. It means "going", not "ringing", and it
 *   covers the gap that used to be dead air: the request flying out, the server
 *   finding the other person, the push reaching a phone that may be asleep.
 * - **[ringing]** - their phone is ringing. The app's own ringback, which is what
 *   every caller is listening for and the thing that says the call has landed.
 *
 * The change between them is information, not decoration: it is the caller's only
 * way of knowing whether anything reached the other end.
 *
 * Both play with the signalling usage, which puts them on the call stream - so they
 * follow the call's own routing (earpiece, speaker or headset) and the call's
 * volume, rather than the media one, exactly as the platform tone did.
 */
object Ringback {

    /** How far an outgoing call has got, as far as the caller's ear knows. */
    enum class Stage { SILENT, CONNECTING, RINGING }

    private var stage = Stage.SILENT
    private var player: MediaPlayer? = null

    private val hand = Handler(Looper.getMainLooper())
    private var again: Runnable? = null

    /** The beep, for a call that has left but not arrived. Safe to repeat. */
    @Synchronized
    fun connecting(context: Context) = play(context, Stage.CONNECTING)

    /** The ringback, for a call that is now ringing on the other phone. */
    @Synchronized
    fun ringing(context: Context) = play(context, Stage.RINGING)

    /**
     * Stops and lets go.
     *
     * Released rather than merely stopped: a `MediaPlayer` holds a codec and an
     * audio track, and one left behind keeps a slice of the call stream for a call
     * that has already been answered.
     */
    @Synchronized
    fun stop() {
        release()
        stage = Stage.SILENT
    }

    /**
     * Swaps one sound for the other, or starts the first.
     *
     * Asked for the stage it is already playing, it does nothing - which is what
     * lets the callers say what the call is doing on every update without having to
     * know what was said last. A restart on every socket message would be a ringback
     * that stutters back to its beginning every second.
     */
    private fun play(context: Context, want: Stage) {
        if (stage == want) return
        release()
        stage = want

        val app = context.applicationContext
        runCatching {
            val sound = MediaPlayer()
            sound.setAudioAttributes(
                AudioAttributes.Builder()
                    // The usage for a ringback or a dialling tone: the call stream,
                    // so it goes wherever the call goes.
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            sound.setDataSource(
                app,
                if (want == Stage.CONNECTING) Tones.connecting(app) else Tones.ringback(app)
            )
            // Not full volume: this plays against the ear while waiting, and a tone
            // at the level of speech is louder than speech.
            sound.setVolume(VOLUME, VOLUME)

            if (want == Stage.RINGING) {
                // The ringback is written as a loop - a ring, then the gap after it -
                // so looping it is simply playing it.
                sound.isLooping = true
            } else {
                // The beep is not: it is nine tenths of a second of tone with no
                // silence in it, and looped straight it becomes a drone. The pause
                // is what makes it read as a signal repeating rather than a fault.
                sound.setOnCompletionListener { beepAgain(sound) }
            }
            sound.prepare()
            sound.start()
            player = sound
        }.onFailure {
            // A player that failed half-built still holds a codec; nothing is
            // assigned until start() returns, so this is the one that let go.
            release()
            stage = Stage.SILENT
        }
    }

    private fun beepAgain(sound: MediaPlayer) {
        val task = Runnable { beep(sound) }
        again = task
        hand.postDelayed(task, BEEP_GAP_MS)
    }

    @Synchronized
    private fun beep(sound: MediaPlayer) {
        // The call may have been answered, cancelled or moved on to ringing while
        // this was waiting its turn. Anything but the player this was posted for,
        // still on the stage it was posted for, is a beep nobody wants.
        if (player !== sound || stage != Stage.CONNECTING) return
        runCatching {
            sound.seekTo(0)
            sound.start()
        }
    }

    /**
     * Takes the player away before touching it.
     *
     * Read-then-release was a race with one outcome, and with the tone generator
     * this replaced it was not an exception but the runtime aborting: two paths end
     * a call at almost the same instant - the ring timing out and the server saying
     * it is over - and both released the same object. A `MediaPlayer` released twice
     * throws rather than aborts, but the shape that caused it is the one worth
     * keeping out.
     */
    private fun release() {
        again?.let { hand.removeCallbacks(it) }
        again = null
        val playing = player ?: return
        player = null
        runCatching { playing.stop() }
        runCatching { playing.release() }
    }

    /** Long enough to be a gap between beeps, short enough not to sound dropped. */
    private const val BEEP_GAP_MS = 700L

    private const val VOLUME = 0.6f
}
