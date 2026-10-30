package com.iqsocial.iq_message.data.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.iqsocial.iq_message.MainActivity
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.data.remote.CallType

/**
 * Keeps a call alive while the app is not in front.
 *
 * Android stops a process that has gone to the background, and a microphone held
 * open is not a reason to spare it - the system wants to be *told*, in the form of a
 * foreground service declaring what it is for. Without this a call ends a few seconds
 * after the home button, with nothing to explain why.
 *
 * The notification is the price of that, and it is not a loss: a call running behind
 * other apps is exactly the thing that should be visible and one tap from being
 * returned to.
 */
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val call = CallCenter.call
        val who = call?.peer?.fullname?.takeIf { it.isNotBlank() } ?: "Call"
        val video = call?.type == CallType.VIDEO

        ensureChannel(this)

        // Deliberately **not** `CallStyle`, which is what this used to be.
        //
        // Android refuses to post a CallStyle notification unless it belongs to a
        // foreground service, belongs to a user-initiated job, or carries a
        // full-screen intent. This one claimed the first, and on Android 15 the
        // claim was not accepted - the system threw the notification out, and
        // because a rejected `startForeground` notification means the service does
        // not get to be foreground, it killed the app for it:
        //
        //     IllegalArgumentException: Not posted. CallStyle notifications must be
        //     for a foreground service or user initated job or use a
        //     fullScreenIntent.
        //         at com.android.server.am.ServiceRecord$2.run
        //     RemoteServiceException$CannotPostForegroundServiceNotificationException:
        //     Bad notification for startForeground
        //
        // That is thrown by the system, from the system's own thread, up to half a
        // minute after the service started - so there is nothing here to catch and
        // nothing to retry. It is why a call answered on the lock screen died the
        // moment the screen was left: the process was never foreground, so leaving
        // the activity left nothing holding it up. The call went on running at the
        // other end, because this phone was killed before it could say otherwise.
        //
        // The ringing notification is CallStyle and posts without trouble, and the
        // difference between the two is the whole answer: it has a full-screen
        // intent, which satisfies the rule the other way. An ongoing call cannot
        // borrow that - a full-screen intent is a screen takeover, and one of those
        // for a call already in progress is the app seizing the phone mid-sentence.
        //
        // So the style goes and the parts worth having stay: the name, what kind of
        // call it is, a way back in, and the red button. It is a plainer notice than
        // the one Android draws for a call, and that is the cost of one that is
        // actually posted.
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(who)
            .setContentText(if (video) "Video call" else "Voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(
                R.drawable.ic_pip_end,
                "Hang up",
                hangUp(this)
            )
            // Not dismissable, and no sound: it is a handle back into the call, not
            // news. The call itself was announced when it started ringing.
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(reopen(this))
            .build()

        startForegroundCompat(notification, video)
        // Not sticky: a call the system had to kill is over, and bringing the service
        // back without a room to attach it to would show a notification for nothing.
        return START_NOT_STICKY
    }

    /**
     * Promotes this to a foreground service, and says so when it cannot.
     *
     * The failure used to be swallowed whole, and of all the places to do that this
     * was the worst: a refused promotion leaves the call running as an ordinary
     * background service, which means the only thing giving the process its
     * foreground standing is whatever activity happens to be on screen. The call
     * then works for exactly as long as that screen does - answer on the lock
     * screen and it is fine, unlock and the activity goes, and Android takes the
     * microphone off a process it now considers background. The call dies with
     * nothing anywhere to say why.
     *
     * Still not fatal, because a call that might drop is worth more than one
     * refused outright, and the promotion is refused for reasons this cannot fix -
     * Android 14 wants the app to be eligible for microphone use at this moment,
     * and a service started with nothing of the app on screen is not. But it is
     * logged loudly, because it is the difference between a call that drops and a
     * call that drops *for a reason*.
     */
    private fun startForegroundCompat(notification: Notification, video: Boolean) {
        val refused = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Declared type has to match what is actually used, or Android 14 and
                // above refuses the service outright.
                val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    if (video) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
                startForeground(ID, notification, type)
            } else {
                startForeground(ID, notification)
            }
        }.exceptionOrNull() ?: return

        Log.e(
            TAG,
            "Foreground promotion refused - this call is only protected for as long " +
                "as a screen of this app is in front",
            refused
        )
    }

    /**
     * The red button on the notification.
     *
     * A broadcast, not an activity: hanging up should not need the call screen to
     * come to the front first, and from Android 10 a receiver may not start one
     * anyway. The same receiver the floating window's buttons use, so there is one
     * path for "end this call" however it was asked for.
     */
    private fun hangUp(context: Context): PendingIntent {
        val intent = Intent(context, CallPipActions::class.java)
            .setAction(CallPipActions.ACTION_END)
        return PendingIntent.getBroadcast(
            context,
            CallPipActions.ACTION_END.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * What tapping the notification goes back to.
     *
     * **`NEW_TASK` is what makes this work at all.** The intent is fired with this
     * service's own context, and starting an activity from something that is not an
     * activity without that flag is refused outright - so tapping the ongoing-call
     * notification did nothing, silently, and the only way back into a call was to
     * find the app by hand. The ringing notification had the flag and worked, which
     * is what made the difference visible.
     *
     * A request code of its own for the same reason the flag was missed: two
     * `PendingIntent`s with the same code and an intent that compares equal - and
     * `Intent` comparison ignores flags - are *one* PendingIntent, with whichever
     * was built last overwriting the other. The ringing screen's intent is also
     * MainActivity at code 0.
     */
    private fun reopen(context: Context): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REOPEN_REQUEST,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL = "calls"

        /** Distinct from every other PendingIntent that opens MainActivity. */
        private const val REOPEN_REQUEST = 7301
        private const val ID = 3

        @Volatile
        private var context: Context? = null

        fun start(from: Context) {
            val app = from.applicationContext
            context = app
            // `Context.startForegroundService` does not exist before Android 8, so
            // calling it there is not a refusal - it is a NoSuchMethodError, and a
            // call that crashed the app the moment it connected. The compat version
            // picks the right one; below 8 a plain `startService` is correct,
            // because that is where the five-second rule came from.
            runCatching {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, CallService::class.java)
                )
            }
        }

        private const val TAG = "CallService"

        fun stop() {
            val app = context ?: return
            runCatching { app.stopService(Intent(app, CallService::class.java)) }
        }

        /**
         * Its own channel, at low importance.
         *
         * A call in progress should sit quietly in the shade. The ringing is a
         * different notification on a different channel, and conflating them would
         * mean silencing one silences the other.
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ongoing calls", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while a call is running" }
            )
        }
    }
}
