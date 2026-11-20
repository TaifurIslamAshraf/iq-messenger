package com.iqsocial.iq_message.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.iqsocial.iq_message.data.media.Tones

/**
 * The sound a phone makes when somebody is calling *you*.
 *
 * Deliberately not the same thing as [Ringback], and not the same job either. The
 * caller hears a tone meaning "it is on its way"; the person being called hears
 * their own ringtone meaning "answer this". Two different events, two different
 * sounds - and until now only one of them was this app's to make.
 *
 * The incoming side had no sound of its own at all. It leaned on the notification's
 * channel, which is right while the app is away and useless while it is in front:
 * the ringing screen appears and the phone stays silent, because Android had no
 * reason to alert for a notification whose app is already looking at the user.
 *
 * So this rings while that screen is up, and the notification rings when it is not.
 * One of them, never both - see [CallCenter.ringing].
 *
 * The sound is the app's own, from `res/raw` - see [Tones] for why that is, and
 * where somebody who wants a different one can set it.
 *
 * Played through `MediaPlayer` rather than `Ringtone`: a ring has to repeat until it
 * is answered, and `Ringtone.isLooping` only exists from Android 9. `MediaPlayer`
 * loops on every version this app ships to. It still plays with the ring stream's
 * attributes, so the phone's ring volume governs it and silent mode silences it.
 */
object Ringtone {

    private var tone: MediaPlayer? = null
    private var buzzer: Vibrator? = null

    /** Starts ringing, if it is not already. Safe to call twice. */
    @Synchronized
    fun start(context: Context) {
        if (tone != null) return
        val app = context.applicationContext

        runCatching {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(app, Tones.ringtone(app))
            player.isLooping = true
            // Prepared on this thread rather than asynchronously: the file is in
            // the apk, so there is nothing to wait for, and a ring that starts a
            // moment after the screen does is a ring that sounds broken.
            player.prepare()
            player.start()
            tone = player
        }.onFailure {
            // A player that failed half-built still holds a codec. Nothing is
            // assigned to [tone] until start() returns, so there is nothing to
            // stop - only something to let go of.
            tone = null
        }

        // Ringing without buzzing is half the signal, and a phone face down or in a
        // pocket is where the other half does the work.
        runCatching {
            val v = vibrator(app) ?: return@runCatching
            if (!v.hasVibrator()) return@runCatching
            val asRingtone = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()
            // `VibrationEffect` arrived in API 26 and this app ships to 24, so the
            // two oldest versions get the pattern the old way. Same buzz, same
            // repeat-from-the-start; only the way of asking differs.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(PATTERN, 0), asRingtone)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(PATTERN, 0, asRingtone)
            }
            buzzer = v
        }
    }

    /**
     * Stops, and lets go.
     *
     * Called from every way a ringing call can end - answered, declined, cancelled,
     * rung out - because a ringtone that outlives its call is the loudest bug an app
     * can have.
     */
    @Synchronized
    fun stop() {
        // Claimed before it is used, like [Ringback]: the same two paths end a call
        // at the same moment, and a field read twice is a thing stopped twice -
        // which for a `MediaPlayer` is a crash rather than a harmless repeat.
        val playing = tone
        tone = null
        val buzzing = buzzer
        buzzer = null
        runCatching { playing?.stop() }
        // Released as well as stopped: a `MediaPlayer` holds a codec and an audio
        // track, and one left behind per call is one the next call cannot have.
        runCatching { playing?.release() }
        runCatching { buzzing?.cancel() }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Buzz, pause, buzz - the shape every phone uses for a call rather than a message. */
    private val PATTERN = longArrayOf(0, 700, 900)
}
