package com.iqsocial.iq_message.data.overlay

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

/**
 * Keeps the app alive for as long as a head is floating.
 *
 * **An overlay window does not keep a process running.** That is the whole reason
 * this exists, and it is not a guess - it is what the device said when a head was
 * finally pressed:
 *
 *     Input focus has changed to Window{50c3848 com.iqsocial.iq_message}
 *     Focus not requested ... because it has no surface
 *     ActivityManager: Killing 27080:com.iqsocial.iq_message (adj 995): empty #9
 *     WIN DEATH: Window{50c3848}
 *
 * The panel was built and added. Fifty-eight milliseconds later Android killed the
 * process for being empty - no activity, no foreground service, therefore nothing
 * worth keeping - and the window died before it had drawn a single frame. From the
 * outside that looks exactly like a head that does nothing when tapped, which is
 * what it looked like.
 *
 * So the heads declare themselves. The notification is the price, and it is the same
 * price every app with chat heads pays: a process that intends to be reachable while
 * the person is using something else has to say so.
 *
 * `specialUse` because that is what this is. None of the other types describe it -
 * there is no microphone, no camera, no sync and no playback, only a window the
 * person asked for and a process that has to outlive the screen it was opened from.
 */
class HeadsService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Chat heads")
            .setContentText("Tap a head to open a chat")
            // Quiet and not dismissable: it is a statement about the process, not
            // news about a message. The messages announce themselves.
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open(this))
            .build()
        startForegroundCompat(notification)
        // Not sticky: brought back without the heads it was holding up, this would
        // be a notification about nothing.
        return START_NOT_STICKY
    }

    /**
     * Says so when the promotion is refused, rather than running on as an ordinary
     * service that the system is free to kill - which is the state this whole class
     * exists to get out of.
     */
    private fun startForegroundCompat(notification: Notification) {
        val refused = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification)
            }
        }.exceptionOrNull() ?: return

        Log.e(TAG, "Foreground promotion refused - heads will not survive the app closing", refused)
    }

    private fun open(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_REQUEST,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {

        private const val TAG = "HeadsService"
        private const val CHANNEL = "chat_heads"
        private const val ID = 7
        private const val OPEN_REQUEST = 71

        private var context: Context? = null

        /**
         * Started when the first head appears.
         *
         * Through the compat starter for the same reason the call service uses it:
         * `startForegroundService` does not exist before Android 8, and calling it
         * there is a `NoSuchMethodError` rather than a refusal.
         */
        fun start(from: Context) {
            val app = from.applicationContext
            context = app
            runCatching {
                ContextCompat.startForegroundService(app, Intent(app, HeadsService::class.java))
            }.onFailure {
                // Android 12 and above refuse a service started from the background
                // in most states. Said out loud, because the consequence is a head
                // that works until the app is closed and then quietly stops.
                Log.w(TAG, "Could not start the heads service", it)
            }
        }

        /** Stopped with the last head: nothing left to hold the process up for. */
        fun stop() {
            val app = context ?: return
            runCatching { app.stopService(Intent(app, HeadsService::class.java)) }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Chat heads", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while a chat head is on screen" }
            )
        }
    }
}
