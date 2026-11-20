package com.iqsocial.iq_message.data.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.iqsocial.iq_message.MainActivity
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.remote.CallStatus
import com.iqsocial.iq_message.data.remote.CallType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
 * returned to. So it is built to be used rather than merely posted - a face, who it
 * is with, how long it has been going, and the two controls a call needs from
 * outside the app.
 */
class CallService : Service() {

    /**
     * Lives as long as this service instance.
     *
     * Two things need it: fetching the other person's picture, which is network work
     * and cannot hold up [onStartCommand], and watching what the notification says
     * for changes - a microphone muted from the floating window has to reach the
     * shade too, or the two disagree about the same call.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** What the last posted notification said, so an unchanged one is not reposted. */
    private var posted: Shown? = null

    /** The face, once it has arrived, and the address it was fetched from. */
    private var face: Bitmap? = null
    private var faceFrom: String? = null
    private var watching = false


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // A call can be answered in a process with no activity in it - a push woke
        // this one - and the picture cache is set up by the composition. Without
        // this the notification's face would be a fresh download every call, and
        // none at all with no connection.
        if (RemoteImage.cacheDir == null) RemoteImage.cacheDir = applicationContext.cacheDir
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        // First, and synchronously: the system gives a service started this way a
        // few seconds to show its notification, and anything waited on here - a
        // picture most of all - is a window in which the app is killed for silence.
        post(first = true)
        watch()
        // Not sticky: a call the system had to kill is over, and bringing the service
        // back without a room to attach it to would show a notification for nothing.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // The system takes down a service's own notification with the service, so
        // this is the belt to that brace: whatever route the last post went by, a
        // call notification must not outlive the call. One that does is worse than
        // none - it offers a Hang up that ends nothing.
        runCatching { NotificationManagerCompat.from(this).cancel(ID) }
        super.onDestroy()
    }

    /**
     * Everything the notification draws, in one value.
     *
     * The point of it being one value is the comparison: the shade is repainted when
     * this differs and left alone when it does not, so a call object that updates
     * every few seconds with a field nobody shows does not flicker the notification.
     */
    private data class Shown(
        val name: String,
        val photo: String,
        val video: Boolean,
        val muted: Boolean,
        val calling: Boolean,
        val answeredAt: Long
    )

    private fun shown(): Shown {
        val call = CallCenter.call
        return Shown(
            // Whatever was last said, until something better than nothing arrives.
            // A call object is updated by the server as it goes, and an update that
            // carries no peer - a bare state change - would otherwise rename the
            // person mid-call to the word "Call".
            name = call?.peer?.fullname?.takeIf { it.isNotBlank() }
                ?: posted?.name
                ?: "Call",
            photo = call?.peer?.profilePicture?.takeIf { it.isNotBlank() }
                ?: posted?.photo.orEmpty(),
            video = call?.type == CallType.VIDEO,
            muted = !CallEngine.micOn,
            // An outgoing call that is still ringing is not a call yet, and saying
            // "ongoing" over a phone nobody has picked up is the notification
            // claiming something that has not happened.
            calling = call?.status == CallStatus.RINGING && call?.isIncoming == false,
            answeredAt = call?.answeredAt ?: 0L
        )
    }

    /**
     * Repaints the shade when, and only when, what it says has changed.
     *
     * `snapshotFlow` because what it watches is the Compose state the screens read -
     * the same values, not a copy kept in step by hand. A mute pressed in the
     * floating window, the call being answered, a name that arrived late: each lands
     * here by itself, and none of them has to remember to tell the shade.
     *
     * The mute in particular is why this watches the microphone rather than the
     * button press: [CallEngine.toggleMic] only takes effect once the room agrees,
     * so a notification redrawn on the press would say muted a moment before it was
     * true - and would go on saying it if the change never landed.
     */
    private fun watch() {
        if (watching) return
        watching = true
        scope.launch {
            snapshotFlow { shown() }.collect {
                // Nothing is redrawn for a call that is over. The call going is the
                // first half of ending - [CallService.stop] is the second - and in
                // the moment between them this would otherwise repaint the shade
                // from an empty call: "Ongoing voice call with Call", over a call
                // nobody was in.
                if (CallCenter.call != null && it != posted) post(first = false)
            }
        }
    }

    private fun post(first: Boolean) {
        val now = shown()
        posted = now
        // Before it is drawn, not after: this clears a face that belonged to
        // somebody else, and clearing it afterwards would show the last caller's
        // picture over this one's name until the new one had been fetched.
        fetchFace(now.photo)
        // Every post goes the same way, updates included, and that is the whole of a
        // bug this had on the phone: a notification put up with `notify` is no longer
        // the *service's* notification, so stopping the service does not take it
        // away. The first update - the call being answered - quietly handed it over,
        // and when the call ended "Ongoing voice call" stayed in the shade with
        // nobody left to hang up. It also carries the flags the system puts on a
        // service's own notice, without which a live call can be swiped out of view.
        if (startForegroundCompat(build(now), now.video, loud = first) || first) return

        // Only when the system would not take it that way: a notification that can
        // be cleared still beats one that goes on claiming an unmuted microphone.
        //
        // The permission is asked for here rather than in a helper, because lint can
        // only see a check it finds beside the call - and being refused costs
        // nothing either way: without it the shade shows none of this app's
        // notifications, the service's own included.
        val mayPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (mayPost) {
            runCatching { NotificationManagerCompat.from(this).notify(ID, build(now)) }
        }
    }

    private fun build(now: Shown): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(
                when {
                    now.calling -> if (now.video) "Video call" else "Voice call"
                    now.video -> "Ongoing video call"
                    else -> "Ongoing voice call"
                }
            )
            // The name only when the line is the only one carrying it. The style puts
            // the name in the title itself, where "Mithun sarker / Calling Mithun
            // sarker" is the notification saying it twice and nothing else.
            .setContentText(
                // The style puts the name in the title itself, so a second line
                // naming them again is the notification saying it twice.
                when {
                    now.calling -> "Calling"
                    now.video -> "Ongoing video call"
                    else -> "Ongoing voice call"
                }
            )
            // The face belongs to the call, so it is the picture on it, cut round
            // by [CallFace] rather than left to the template - Android 12 and above
            // clips a large icon to a circle and everything before it does not.
            .setLargeIcon(face ?: CallFace.initials(this, now.name))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            // Not dismissable, and no sound: it is a handle back into the call, not
            // news. The call itself was announced when it started ringing.
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(reopen(this))

        val mute = NotificationCompat.Action.Builder(
            if (now.muted) R.drawable.ic_pip_mic_off else R.drawable.ic_pip_mic,
            // What pressing it will do, not what the microphone currently is. The
            // same wording as the floating window's button, for the same reason.
            if (now.muted) "Unmute" else "Mute",
            broadcast(CallPipActions.ACTION_MIC)
        ).build()

        builder.setStyle(
                NotificationCompat.CallStyle
                    .forOngoingCall(caller(now), broadcast(CallPipActions.ACTION_END))
                .setIsVideo(now.video)
        )
        // The hang up is the style's own, drawn red because the style draws it so.
        // Only the mute is added, or the call would have two ways to end it sitting
        // side by side.
        builder.addAction(mute)
        // The credential the style is allowed on, and the whole reason this is
        // allowed to be a CallStyle at all. Never *launched*: a full-screen intent
        // is only acted on above default importance, and this channel is low - the
        // same arrangement the ringing notification has always had.
        builder.setFullScreenIntent(reopen(this), false)

        // How long it has been going, counted by the system from when it was
        // answered - the same reckoning the call screen and the floating window use,
        // so the three never show three different times. Nothing before that: a
        // timer on a call still ringing counts how long nobody has answered.
        if (now.answeredAt > 0L) {
            builder.setWhen(now.answeredAt).setUsesChronometer(true).setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    /**
     * Promotes this to a foreground service, and says whether it worked.
     *
     * A refused promotion leaves the call running as an ordinary background service,
     * which means the only thing giving the process its standing is whatever
     * activity happens to be on screen - the call then works for exactly as long as
     * that screen does, and the caller below stops the service rather than letting
     * the system kill the app for it.
     *
     * **The notification is drawn as a call, and that took three attempts.** Android
     * refuses to post a `CallStyle` notification unless it belongs to a foreground
     * service, belongs to a user-initiated job, or carries a full-screen intent -
     * and a refused `startForeground` notification is not simply dropped, it takes
     * the whole app down from the system's own thread:
     *
     *     IllegalArgumentException: 0|com.iqsocial.iq_message|3|null Not posted.
     *     CallStyle notifications must be for a foreground service or user
     *     initated job or use a fullScreenIntent.
     *     RemoteServiceException$CannotPostForegroundServiceNotificationException:
     *     Bad notification for startForeground
     *
     * The second attempt was careful about it: a plain notification went up first
     * and the system was *asked* - by reading our own posted notification back -
     * whether it had accepted it as a service's own, and only then was the style
     * used. On Android 14 that worked, red hang-up button and all. On Android 15 the
     * answer was yes and the styled post was refused anyway, twice within a minute,
     * killing the app both times. Whatever the first notification was accepted as
     * does not travel to the next one.
     *
     * So the third way is the one taken, and it is the same way the *ringing*
     * notification has always been allowed: it carries a **full-screen intent**.
     * That satisfies the check on the notification's own terms, with nothing
     * inherited from a previous post and nothing to be wrong about.
     *
     * What makes it safe to carry one on a call that is already running - where a
     * screen takeover would be the app seizing the phone mid-sentence - is the
     * channel. `IMPORTANCE_LOW`, and Android only *launches* a full-screen intent
     * for a notification above default importance; below that the intent is a
     * credential and never an action.
     *
     * It was briefly gated on `canUseFullScreenIntent` as well, on the reasoning
     * that a permission the person had turned down would have the intent stripped
     * on the way in and take the style down with it. That was wrong, and the
     * ringing notification had been proving it wrong on the same phone all along:
     * it is CallStyle, it carries a full-screen intent, that permission is off, and
     * it posts and draws perfectly - what the permission withholds is the takeover,
     * not the intent. The gate did nothing but quietly return the ongoing call to
     * the plain template on exactly the phones that needed it most.
     */
    private fun startForegroundCompat(
        notification: Notification,
        video: Boolean,
        loud: Boolean
    ): Boolean {
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
        }.exceptionOrNull() ?: return true

        // Only the first one is worth shouting about. A refused *update* leaves the
        // call exactly as protected as it was a moment ago; it is the promotion that
        // was never granted which decides whether the call survives being left.
        if (!loud) return false

        Log.e(
            TAG,
            "Foreground promotion refused - this call is only protected for as " +
                "long as a screen of this app is in front",
            refused
        )
        // **And then stop, rather than linger.** Catching this was never the whole
        // job. A service started with `startForegroundService` that does not become
        // foreground is one Android kills the *app* for, from its own thread, about
        // ten seconds later:
        //
        //     RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
        //     Context.startForegroundService() did not then call
        //     Service.startForeground()
        //
        // Which is the same crash, by the same mechanism, as the one that took the
        // app down while a call was ringing on a locked screen: a microphone service
        // started from the background is exactly what Android 14 refuses, and the
        // refusal was caught, logged, and then sat on until the system lost patience.
        // Stopping here is what cancels that: an unprotected call is a call that may
        // drop when the app is left, which is what the log says - a killed process
        // is a call that is already over, and a ringing notification nobody is left
        // alive to take down.
        runCatching { stopSelf() }
        return false
    }

    /**
     * Fetches the other person's picture, once, and repaints when it lands.
     *
     * Never on the way in. The notification has to be up within seconds of the
     * service starting or the system kills the app, and a face is worth none of
     * that risk - so one is posted immediately with the initial [CallFace] draws,
     * and the photograph replaces it whenever it arrives.
     */
    private fun fetchFace(url: String) {
        if (url == faceFrom) return
        faceFrom = url
        face = null
        if (url.isBlank()) return
        scope.launch {
            val bitmap = RemoteImage.load(url)
                ?.let { runCatching { it.asAndroidBitmap() }.getOrNull() }
                ?: return@launch
            // Still the person this is a call with. A slow fetch outliving the call
            // it was for would otherwise put the last caller's face on this one.
            if (url != faceFrom) return@launch
            face = CallFace.circle(bitmap) ?: return@launch
            post(first = false)
        }
    }

    /**
     * The buttons on the notification.
     *
     * Broadcasts, not activities: neither hanging up nor muting should need the call
     * screen to come to the front first, and from Android 10 a receiver may not start
     * one anyway. The same receiver the floating window's buttons use, so there is
     * one path for each of these however it was asked for - and pressing mute in the
     * shade and in the window cannot end up meaning two different things.
     */
    /**
     * Whether a full-screen intent on this notification would survive being posted.
     *
     * From Android 14 the permission behind one is not granted at install: an app
     * that is not a dialer or an alarm clock asks for it, and until it is given the
     * system **takes the intent off the notification** on the way in. That matters
     * here for a reason that has nothing to do with full screens - the intent is
     * what makes a CallStyle notification legal, and one stripped of it is refused,
     * which kills the app. So the style is asked for only where the credential will
     * still be there when the check is made.
     */

    /**
     * Who the call is with, as the style wants it.
     *
     * The face goes here rather than in `setLargeIcon` while the style is on, and
     * that is what moves it to the left of the notification with this app's own icon
     * badged on to it: a person the call is with, rather than a picture an app has
     * attached to a notice. Cut round by [CallFace], which is also where the ringing
     * notification gets its face, so the same call is the same person either side of
     * being answered.
     */
    private fun caller(now: Shown) = Person.Builder()
        .setName(now.name)
        .setImportant(true)
        .apply { CallFace.icon(this@CallService, face, now.name)?.let(::setIcon) }
        .build()

    private fun broadcast(action: String): PendingIntent {
        val intent = Intent(this, CallPipActions::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
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
            // What the notification is about. Without it this opened the app on
            // whatever screen it was last left on - usually the chat list, with the
            // call it had just been tapped for reachable only through the strip at
            // the top of that list.
            putExtra(MainActivity.EXTRA_OPEN_CALL, true)
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
