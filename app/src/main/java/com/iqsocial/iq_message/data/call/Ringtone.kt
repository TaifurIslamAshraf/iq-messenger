package com.iqsocial.iq_message.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

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
 * The person's own ringtone rather than a sound shipped here, because a ringtone is
 * something people choose, and a messaging app substituting its own is a messaging
 * app deciding it knows better.
 */
object Ringtone {

    private var tone: Ringtone? = null
    private var buzzer: Vibrator? = null

    /** Starts ringing, if it is not already. Safe to call twice. */
    fun start(context: Context) {
        if (tone != null) return
        val app = context.applicationContext

        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return@runCatching
            RingtoneManager.getRingtone(app, uri)?.also { r ->
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                // Looping from Android 9; below that it plays once, which is still
                // better than the silence this replaced.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.isLooping = true
                r.play()
                tone = r
            }
        }

        // Ringing without buzzing is half the signal, and a phone face down or in a
        // pocket is where the other half does the work.
        runCatching {
            val v = vibrator(app) ?: return@runCatching
            if (!v.hasVibrator()) return@runCatching
            v.vibrate(
                VibrationEffect.createWaveform(PATTERN, 0),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
            )
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
    fun stop() {
        runCatching { tone?.stop() }
        tone = null
        runCatching { buzzer?.cancel() }
        buzzer = null
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
