package com.iqsocial.iq_message.data.call

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.iqsocial.iq_message.MainActivity
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.push.NotificationActions
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.data.remote.ChatCall
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * The ringing, when there is no app on screen to do it.
 *
 * This is what makes a call arrive at all while the app is closed. A push wakes the
 * process and [CallCenter] learns about the call, but in a process with no activity
 * that is a fact nobody can see: there is no composition, no window, nothing drawn.
 * The notification is the only thing that can put a ringing call in front of someone.
 *
 * `CallStyle` with a **full-screen intent**, which is the pair Android reserves for
 * this: on a locked phone it takes the whole screen, and on an unlocked one it is a
 * heads-up with Answer and Decline on it. Built by hand it would be neither - just
 * another banner among the messages.
 */
object RingingNotification {

    /**
     * [silent] posts it without a sound of its own.
     *
     * For when the app is in front and [Ringtone] is doing the ringing. The
     * notification is still wanted - it is how the call is reached from the shade,
     * and on a locked screen it is what takes over - but a channel ringtone on top
     * of an in-app one is two ringtones a moment apart.
     *
     * A second channel rather than a flag on the notification, because a channel's
     * sound is fixed once it exists: `setSilent` on the builder is ignored for
     * anything the channel has already decided to play.
     */
    fun show(context: Context, call: ChatCall, silent: Boolean = false) {
        if (!allowed(context)) return
        ensureChannel(context)

        val app = context.applicationContext
        val name = call.peer?.fullname?.takeIf { it.isNotBlank() } ?: "Unknown"
        val caller = Person.Builder()
            .setName(name)
            .setKey(call.peer?.uid.orEmpty())
            .setImportant(true)
            .apply { face(call)?.let { setIcon(IconCompat.createWithAdaptiveBitmap(it)) } }
            .build()

        val notification = NotificationCompat.Builder(app, if (silent) CHANNEL_QUIET else CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(app, R.color.notification_accent))
            .setContentTitle(name)
            .setContentText(
                if (call.type == CallType.VIDEO) "Incoming video call" else "Incoming voice call"
            )
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    action(app, NotificationActions.ACTION_DECLINE, call.id),
                    action(app, NotificationActions.ACTION_ANSWER, call.id)
                )
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Not swipeable: a ringing call is answered or declined, and letting it
            // be brushed away would leave the caller listening to nothing.
            .setOngoing(true)
            .setAutoCancel(false)
            // The whole screen when the phone is locked; a heads-up when it is not.
            .setFullScreenIntent(answerScreen(app), true)
            .build()

        runCatching { NotificationManagerCompat.from(app).notify(ID, notification) }
    }

    fun hide(context: Context) {
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(ID) }
    }

    /**
     * The caller's face, if it has already been fetched.
     *
     * Only from the cache, never over the network: this runs while the phone is
     * ringing and a round trip would hold the notification back. A call is late by
     * however long it takes to show, and a missing picture costs nothing by
     * comparison.
     */
    private fun face(call: ChatCall) =
        call.peer?.profilePicture?.takeIf { it.isNotBlank() }
            ?.let { RemoteImage.peek(it) }
            ?.let { runCatching { it.asAndroidBitmap() }.getOrNull() }

    private fun action(
        context: Context,
        what: String,
        callId: String
    ): PendingIntent {
        val intent = Intent(context, NotificationActions::class.java).apply {
            action = what
            putExtra(NotificationActions.EXTRA_CALL, callId)
        }
        return PendingIntent.getBroadcast(
            context,
            (what + callId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** What the full-screen intent opens: the app, which then shows the call. */
    private fun answerScreen(context: Context): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            // Its own code, so this and the ongoing call's "back to the call" are
            // two PendingIntents rather than one that the other overwrites.
            ANSWER_REQUEST,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Its own channel, and it rings.
     *
     * The ringtone and the vibration are the channel's, not the notification's, so
     * the phone's own rules apply: silent means silent, and anyone who wants a
     * different tone for calls can set one without touching messages.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // The same notification without the sound, for when the app is ringing
        // itself. Still IMPORTANCE_HIGH: it has to arrive as a heads-up and take a
        // locked screen; it is only the tone that is not wanted twice.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUIET,
                "Incoming calls (app open)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shown while the app is already on screen"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )

        val channel = NotificationChannel(
            CHANNEL,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rings when somebody calls"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 800)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private const val ANSWER_REQUEST = 7302
    private const val CHANNEL = "calls_incoming"
    private const val CHANNEL_QUIET = "calls_incoming_quiet"
    private const val ID = 4
}
