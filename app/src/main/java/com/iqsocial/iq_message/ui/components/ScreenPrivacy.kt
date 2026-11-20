package com.iqsocial.iq_message.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.overlay.ChatWindow
import com.iqsocial.iq_message.data.privacy.ScreenshotWitness
import com.iqsocial.iq_message.data.profile.PeerPrivacy

/**
 * Keeps a screen out of screenshots and screen recordings while it is up.
 *
 * `FLAG_SECURE` is the whole of it, and it is the only thing an app can do: the
 * screenshot is taken by the system, so the system is who has to be asked. With the
 * flag set, a screenshot of this window comes out blank, a recording shows nothing,
 * and the window is left out of the recents preview.
 *
 * **Whose setting decides is not whose phone it is.** `allow_screenshot` belongs to
 * the person being looked at, and the button is pressed on the phone doing the
 * looking - so the flag goes up on *this* phone because of what the *other* person
 * asked for. That is why this takes a value rather than reading the settings itself:
 * the caller knows which person the screen is about. [peerAllowsScreenshot] is how
 * the caller finds out what they asked for.
 *
 * The flag is cleared again on the way out, and only ever by the screen that set it.
 * A nested count rather than a plain clear, because two screens can want it at once
 * - a profile sheet over a chat - and the inner one closing must not unlock the
 * outer one.
 *
 * Works in the floating panel too, where there is no activity to flag: the panel is
 * a window handed straight to the window manager, so it is asked to lock itself.
 */
@Composable
fun SecureScreen(secure: Boolean) {
    val activity = LocalContext.current.activity()
    DisposableEffect(activity, secure) {
        if (!secure) return@DisposableEffect onDispose { }
        if (activity == null) {
            // Inside [ChatWindow]: the composition has no activity behind it, and
            // the panel is the one place that shows a whole conversation with
            // nothing of its own protecting it.
            ChatWindow.secureForPeer(true)
            onDispose { ChatWindow.secureForPeer(false) }
        } else {
            ScreenPrivacy.hold(activity)
            onDispose { ScreenPrivacy.release(activity) }
        }
    }
}

/**
 * Whether [uid] lets this phone screenshot them, asked of the server as needed.
 *
 * One line at each screen that shows a person, and it does three things: it asks
 * (once a minute at most, and once for several screens showing the same person),
 * it redraws the screen when the answer lands, and it tells [ScreenshotWitness]
 * whose conversation is on display - which is the only way a screenshot that *is*
 * allowed can be attributed to anybody.
 *
 * Locked until the answer arrives; see [PeerPrivacy] for why that way round.
 */
@Composable
fun peerAllowsScreenshot(uid: String?, conversationId: String? = null): Boolean {
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    LaunchedEffect(uid, token) { PeerPrivacy.refresh(token, uid) }
    DisposableEffect(uid, conversationId) {
        ScreenshotWitness.showing(uid, conversationId)
        onDispose { ScreenshotWitness.left(uid) }
    }
    return PeerPrivacy.allows(uid)
}

/**
 * Watches for the gesture that takes a screenshot on phones that have one.
 *
 * Only for the screens the system is **already blocking**. When a screenshot can
 * be taken, it is reported when it is taken, from the file or the platform's own
 * callback - evidence, not a guess. When it cannot, there is no evidence to have:
 * the system refuses the capture, writes nothing and tells no one, so the only
 * thing left to notice is the hand.
 *
 * Three fingers down at once is Xiaomi's screenshot gesture and several other
 * makers' too. It is a guess and it is admitted as one: a hardware-button
 * screenshot is invisible here, because the system takes those keys before any app
 * sees them, and three fingers that meant something else will be reported as an
 * attempt. It is enabled only while the window is locked, which is the only time
 * the question is live.
 *
 * `PointerEventPass.Initial` so the count is taken before the list, the bubbles or
 * anything else consumes the touch.
 */
fun Modifier.watchForScreenshotGesture(active: Boolean): Modifier = composed {
    val context = LocalContext.current
    if (!active) return@composed this
    pointerInput(Unit) {
        awaitPointerEventScope {
            var reported = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val fingers = event.changes.count { it.pressed }
                if (fingers >= FINGERS && !reported) {
                    reported = true
                    ScreenshotWitness.attempted(context, how = "$fingers fingers")
                }
                // Armed again once the hand is off the glass, so one gesture is one
                // report however many events it arrives in.
                if (fingers == 0) reported = false
            }
        }
    }
}

/** Xiaomi, Honor, Oppo and others all use three. */
private const val FINGERS = 3

private object ScreenPrivacy {

    /** How many screens currently want the window locked. */
    private var holds = 0

    fun hold(activity: Activity) {
        holds++
        if (holds == 1) {
            runCatching {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
            Log.i(TAG, "window locked against screenshots")
        }
    }

    fun release(activity: Activity) {
        holds = (holds - 1).coerceAtLeast(0)
        if (holds == 0) {
            runCatching { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            Log.i(TAG, "window unlocked")
        }
    }

    private const val TAG = "ScreenPrivacy"
}

/**
 * The activity behind a composable's context.
 *
 * `LocalContext.current` is almost never the activity itself - it is a wrapper
 * around it, sometimes more than one - so casting straight to Activity returns null
 * and whatever depended on it quietly does nothing.
 */
private fun Context.activity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
