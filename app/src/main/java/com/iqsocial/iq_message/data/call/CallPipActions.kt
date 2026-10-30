package com.iqsocial.iq_message.data.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The three buttons in the floating call window.
 *
 * They are broadcasts rather than taps, and that is not a style choice: the content
 * of a picture-in-picture window **does not receive touch**. Tapping anywhere in it
 * tells the system to expand the window, so a mute button drawn inside the
 * composition would be a picture of a button - it would open the call instead of
 * muting it. The row of actions Android draws on the window is the only thing in
 * there that can be pressed, and each one carries a `PendingIntent`.
 *
 * Which is also why they land here and not in the activity: the window belongs to
 * the system, and the press arrives whether or not there is an activity in a state
 * to take it.
 */
class CallPipActions : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            ACTION_MIC -> CallEngine.toggleMic()
            ACTION_SPEAKER -> CallEngine.cycleRoute()
            ACTION_END -> CallCenter.end(app)
        }
    }

    companion object {
        const val ACTION_MIC = "com.iqsocial.iq_message.PIP_MIC"
        const val ACTION_SPEAKER = "com.iqsocial.iq_message.PIP_SPEAKER"
        const val ACTION_END = "com.iqsocial.iq_message.PIP_END"
    }
}
