package com.iqsocial.iq_message.data.call

import android.content.Context
import android.os.PowerManager

/**
 * Keeps the screen out of the way during a voice call, and awake during a video one.
 *
 * Two opposite problems with one cause - a call is the rare screen nobody is looking
 * at while using it:
 *
 * - **A voice call is held against the face.** With the screen on, a cheek presses
 *   mute, or hangs up, and the display burns through the battery for a call nobody
 *   is watching. Every phone app turns the screen off when the proximity sensor is
 *   covered, and that is what `PROXIMITY_SCREEN_OFF_WAKE_LOCK` is: the system does
 *   the sensing, so nothing here has to read a sensor or guess a threshold.
 * - **A video call is looked at without being touched.** The normal idle timeout has
 *   no idea anyone is there, so it dims and locks mid-conversation.
 *
 * Both locks are released the moment the call ends. A proximity lock left held makes
 * a phone look broken - the screen stays dark and nothing brings it back.
 */
object CallScreenLock {

    private var proximity: PowerManager.WakeLock? = null

    /**
     * Starts whichever lock suits the call.
     *
     * Only the voice case is handled here. Keeping the screen awake for video is the
     * window's business, not the power manager's - `FLAG_KEEP_SCREEN_ON` goes away
     * by itself when the window does, where a wake lock held by a process that is
     * killed has to be cleaned up by the system.
     */
    fun begin(context: Context, video: Boolean) {
        release()
        if (video) return

        val power = context.applicationContext
            .getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        // Older phones and a few newer ones simply do not have the sensor, and
        // taking the lock anyway would be asking for a screen that never comes back.
        if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            return
        }
        runCatching {
            @Suppress("DEPRECATION")
            val lock = power.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "IQMessage:call"
            )
            lock.setReferenceCounted(false)
            lock.acquire(MAX_CALL_MS)
            proximity = lock
        }
    }

    fun release() {
        runCatching { proximity?.takeIf { it.isHeld }?.release() }
        proximity = null
    }

    /**
     * A ceiling, not an expectation.
     *
     * `acquire()` with no timeout is a lock that outlives every bug that forgets to
     * release it. Three hours is longer than any call this app will carry and short
     * enough that a leak fixes itself.
     */
    private const val MAX_CALL_MS = 3 * 60 * 60 * 1000L
}
