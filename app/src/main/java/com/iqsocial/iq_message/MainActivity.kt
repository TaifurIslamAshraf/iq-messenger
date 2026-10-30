package com.iqsocial.iq_message

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.iqsocial.iq_message.data.call.CallAudio
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.data.call.CallPipActions
import com.iqsocial.iq_message.data.call.CallWindow
import com.iqsocial.iq_message.data.push.IqMessagingService
import com.iqsocial.iq_message.data.push.NotificationActions
import com.iqsocial.iq_message.ui.theme.IQMessageTheme
class MainActivity : ComponentActivity() {

    /**
     * The conversation a tapped notification asked for, if any.
     *
     * State rather than a field read once, because the activity is `singleTop`: a
     * second notification tapped while the app is already open arrives through
     * [onNewIntent] rather than a fresh [onCreate], and reading the intent only at
     * start-up would open the first conversation and ignore every one after it.
     */
    private val opened = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        opened.value = conversationOf(intent)
        answerIfAsked(intent)
        probeIfAsked(intent) // TEMPORARY PROBE
        // Transparent bars; each screen sets its own icon contrast via SystemBarIcons.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent)
        )
        setContent {
            IQMessageTheme {
                IqApp(
                    openConversation = opened.value,
                    onConversationOpened = { opened.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        opened.value = conversationOf(intent)
        answerIfAsked(intent)
    }

    /**
     * Leaving the app during a call shrinks it instead of hiding it.
     *
     * This is the one Android calls when the home button is pressed or another app
     * is opened - a moment that for every other screen means "go away" and for a
     * call means "keep this where I can see it". The back gesture is handled in the
     * composition instead, which is where the navigation stack is.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (CallWindow.callOnScreen) enterCallPip()
    }

    /**
     * Android has put this window in a corner, or taken it back out.
     *
     * The composition cannot be told any other way: entering picture-in-picture is
     * not a configuration the screens can see, and without this they would go on
     * drawing a full call screen inside a thumbnail.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        CallWindow.inPip = isInPictureInPictureMode
    }

    /**
     * Picture-in-picture is also how a call ends up closed.
     *
     * A floating window that is dismissed stops the activity without destroying it,
     * and a call left running behind nothing visible is a call nobody can hang up.
     * The service keeps it alive - that is its job - so this does not end it, it
     * only stops pretending the window is still there.
     */
    override fun onStop() {
        super.onStop()
        if (CallWindow.inPip) CallWindow.inPip = false
    }

    // TEMPORARY PROBE - remove after diagnosing the refused conversation notification.
    private fun probeIfAsked(intent: Intent?) {
        intent ?: return
        // TEMPORARY: floats a head with no message behind it, so the window the head
        // opens can be tried without waiting for somebody to write.
        if (intent.getBooleanExtra("probe_head", false)) {
            com.iqsocial.iq_message.data.overlay.ChatHeads.show(
                applicationContext,
                conversationId = "probe-head",
                name = "Probe",
                face = null
            )
            com.iqsocial.iq_message.data.overlay.ChatHeads.preview(
                applicationContext,
                "probe-head",
                "Tomorrow at 5 works for me - shall I book the table?"
            )
        }
        val app = applicationContext
        if (intent.getBooleanExtra("probe_post", false)) {
            Thread {
                kotlinx.coroutines.runBlocking {
                    com.iqsocial.iq_message.data.push.MessageNotifications.show(
                        app, "probe-notification-test", "Probe Person", "Probe message"
                    )
                }
            }.start()
        }
        if (intent.getBooleanExtra("probe_clean", false)) {
            com.iqsocial.iq_message.data.push.MessageNotifications.clear(app, "probe-notification-test")
            androidx.core.content.pm.ShortcutManagerCompat.removeDynamicShortcuts(
                app, listOf("probe-notification-test")
            )
        }
    }

    /**
     * Answers the call the notification's Answer button was for.
     *
     * Done here rather than in the receiver because from Android 10 a broadcast
     * receiver may not start an activity, and answering has to bring the call on
     * screen - so the accept travels on the launch instead.
     */
    private fun answerIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationActions.EXTRA_ANSWER, false) != true) return
        intent.removeExtra(NotificationActions.EXTRA_ANSWER)
        CallCenter.accept(applicationContext)
    }

    /**
     * The conversation a tapped notification is about.
     *
     * Android puts the push's `data` on the intent as extras, so what arrives is the
     * same two fields every push carries: `event`, and `payload` as a JSON string.
     * There is no `conversation_id` extra to read - it is inside the payload, which
     * is what this unpacks.
     */
    private fun conversationOf(intent: Intent?): String? {
        intent ?: return null
        if (intent.getStringExtra(IqMessagingService.EXTRA_EVENT) != "message:notification") {
            return null
        }
        val payload = intent.getStringExtra(IqMessagingService.EXTRA_PAYLOAD) ?: return null
        return runCatching {
            org.json.JSONObject(payload)
                .optJSONObject("message")
                ?.optString("conversation_id")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 915)
@Composable
private fun AppPreview() {
    IQMessageTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            IqApp()
        }
    }
}

/**
 * Shrinks the activity into a floating window, if this phone can.
 *
 * An extension rather than a method, because the composition is what decides when -
 * it knows a call screen is up and that back was pressed - and it reaches the
 * activity through the context it already has.
 *
 * The shape is set rather than left to the system: a video call is a picture and a
 * voice call is a face, and a 16:9 window around a round avatar is mostly empty
 * black. Android refuses a ratio outside roughly 1:2.39 to 2.39:1, so both of these
 * are well inside.
 *
 * Returns false when nothing happened - below Android 8, on a device without the
 * feature, or when the system simply refuses - so the caller can fall back to what
 * it would have done.
 */
fun Activity.enterCallPip(video: Boolean = CallWindow.video): Boolean {
    if (!canPip()) return false
    @Suppress("NewApi")   // canPip() is the check; lint cannot see through it
    return runCatching { enterPictureInPictureMode(callPipParams(video)) }
        .getOrDefault(false)
}

/**
 * Comes back out of the floating window.
 *
 * There is no "exit picture-in-picture" call; the documented way out is to launch
 * the activity again, which the system reads as a request for the whole screen.
 * `SINGLE_TOP` so it is this instance that comes forward rather than a second one -
 * which would start the app over with the call screen gone.
 *
 * Needed because the window outlives the call. When a call ends the composition pops
 * back to whatever was underneath, and without this the chat list would be left
 * sitting in a thumbnail in the corner.
 */
fun Activity.leaveCallPip() {
    if (!canPip()) return
    runCatching {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        )
    }
}

/**
 * Redraws the window's buttons after the thing they show has changed.
 *
 * The actions are a fixed picture taken when they were built, so a microphone muted
 * from the window leaves a "mute" button sitting over a muted microphone until this
 * is called. Cheap, and ignored by the system when the window is not up.
 */
fun Activity.updateCallPipParams(video: Boolean = CallWindow.video) {
    if (!canPip()) return
    @Suppress("NewApi")
    runCatching { setPictureInPictureParams(callPipParams(video)) }
}

private fun Activity.canPip(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

/**
 * The window's shape, and the three buttons on it.
 *
 * Three is not an arbitrary choice: the system says how many it will draw, and on a
 * phone that is three. Asking for more is not an error - it simply drops the extra
 * ones, and the one dropped would be whichever was listed last. So the list is
 * exactly the three that matter, in the order they are reached: mute, hang up,
 * speaker, with hanging up in the middle where it cannot be hit by accident
 * reaching for either edge.
 *
 * Each one's title doubles as its description, because the system shows it as a
 * tooltip and reads it aloud - so it says what pressing it will do, not what the
 * state currently is.
 */
@RequiresApi(Build.VERSION_CODES.O)
private fun Activity.callPipParams(video: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
        // A video call is a picture, so it gets the shape of one. A voice call is a
        // face over a name over a clock, which is a column - and the square this
        // used to be was chosen when there was only the face to fit. Three stacked
        // things in a square window crowd each other and leave the sides empty, so
        // it is portrait too, just not as tall: 9:16 around an avatar is mostly
        // black.
        //
        // Android refuses a ratio outside roughly 1:2.39 to 2.39:1, so both of
        // these are well inside.
        .setAspectRatio(if (video) Rational(9, 16) else Rational(3, 4))

    val muted = !CallEngine.micOn
    // The route, not a loudspeaker flag: a call on a headset is neither "speaker
    // on" nor "earpiece", and drawing it as either was wrong half the time.
    val route = CallAudio.route
    builder.setActions(
        listOf(
            pipAction(
                if (muted) R.drawable.ic_pip_mic_off else R.drawable.ic_pip_mic,
                if (muted) "Unmute" else "Mute",
                CallPipActions.ACTION_MIC
            ),
            pipAction(R.drawable.ic_pip_end, "End call", CallPipActions.ACTION_END),
            pipAction(
                // Three routes, three pictures - the same three the call screens
                // show, so the floating window never names a different device from
                // the screen it shrank out of.
                when (route) {
                    CallAudio.Route.EARPIECE -> R.drawable.ic_pip_speaker_off
                    CallAudio.Route.SPEAKER -> R.drawable.ic_pip_speaker
                    CallAudio.Route.BLUETOOTH -> R.drawable.ic_pip_headset
                },
                // Says where it will go next, because pressing it is what the label
                // is for - the same cycle the call screens offer.
                when (route) {
                    CallAudio.Route.EARPIECE -> "Speaker"
                    CallAudio.Route.SPEAKER ->
                        if (CallAudio.headsetAvailable) "Headset" else "Earpiece"
                    CallAudio.Route.BLUETOOTH -> "Earpiece"
                },
                CallPipActions.ACTION_SPEAKER
            )
        )
    )
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.O)
private fun Activity.pipAction(icon: Int, title: String, action: String): RemoteAction {
    val intent = Intent(this, CallPipActions::class.java).setAction(action)
    return RemoteAction(
        Icon.createWithResource(this, icon),
        title,
        title,
        PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            // Immutable: nothing is filled in by the system on the way, unlike a
            // reply box. Mutable here would only widen what another app could do
            // with it.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )
}
