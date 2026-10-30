package com.iqsocial.iq_message.data.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.iqsocial.iq_message.MainActivity
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.push.MessageNotifications
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.ui.screens.ChatScreen
import com.iqsocial.iq_message.ui.theme.IQMessageTheme

/**
 * One chat, in a floating panel over whatever app is in front.
 *
 * What a head is for. Tapping one used to try to bring the whole app forward, which
 * is both the wrong shape - the point of a head is not leaving what you were doing -
 * and, from the background, something Android often refuses outright. A panel of our
 * own asks nobody's permission beyond the overlay the head already has.
 *
 * The real [ChatScreen], not a cut-down copy. It is composed into an overlay window
 * through [OverlayHost], so the thread, the composer, the attachments and the
 * recorder are the same code running in a smaller frame - there is no second chat
 * screen to keep in step with the first.
 *
 * **Focusable, unlike the heads.** A head only has to be pressed, so it stays out of
 * the way of everything else; this has to be typed into, and a window the input
 * method cannot reach is a chat you can read and not answer.
 */
object ChatWindow {

    private const val TAG = "ChatWindow"

    /** Room above for the docked row of heads, and below for the app underneath. */
    private const val PANEL_TOP_DP = 96
    private const val PANEL_BOTTOM_DP = 24

    /**
     * How much shorter than the display the window has to be before that counts as
     * the keyboard. Generous, so a gesture bar or a cutout is never mistaken for one.
     */
    private const val KEYBOARD_MIN_PX = 300

    private var windows: WindowManager? = null
    private var panel: View? = null
    private var host: OverlayHost? = null

    /** Which conversation is open, or null when the panel is down. */
    var showing: String? = null
        private set

    /**
     * Opens this chat, or closes it if it is the one already open.
     *
     * The toggle is the whole gesture: a head is pressed to look and pressed again
     * to put it away, and pressing a different head while one is open swaps rather
     * than stacking - there is one panel, because there is one screen.
     */
    fun toggle(context: Context, conversationId: String) {
        Log.i(TAG, "toggle: showing=" + showing + " wanted=" + conversationId)
        if (showing == conversationId) {
            close()
            return
        }
        open(context, conversationId)
    }

    fun open(context: Context, conversationId: String) {
        Log.i(TAG, "open: enter")
        if (conversationId.isBlank() || !ChatHeads.allowed(context)) {
            Log.w(TAG, "open: refused - blank id or no overlay permission")
            return
        }
        close()

        val app = context.applicationContext
        val manager = app.getSystemService(WindowManager::class.java) ?: return
        val owner = OverlayHost().also { it.start() }

        // Worked out once, because the window and the theme must agree about it.
        //
        // Messenger leaves the heads above and a strip of whatever was underneath
        // below, and the gap is the point: a panel that covers everything is an app,
        // and the reader has not left the one they were in.
        val screen = app.resources.displayMetrics
        // What the chat actually gets, once the room above and below is taken out.
        // Told to the theme so its spacing and type are sized for the box the chat
        // has, not for the display behind it - and kept still while the keyboard
        // comes and goes, because re-scaling every line on a keypress would be
        // worse than the thing it corrected.
        val panelHeight = (
            screen.heightPixels - ((PANEL_TOP_DP + PANEL_BOTTOM_DP) * screen.density).toInt()
            ).coerceAtLeast(1)

        // A frame around the composition rather than the composition itself.
        //
        // `ComposeView` is final, and back has to be caught somewhere: the window is
        // focusable, so the press arrives here and there is no activity behind it to
        // take it instead. The owners go on this root and the view tree carries them
        // down, which is all the ComposeView inside asks for.
        val view = object : FrameLayout(app) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK &&
                    event.action == KeyEvent.ACTION_UP
                ) {
                    close()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        /**
         * Whether the keyboard is up, measured rather than asked.
         *
         * `WindowInsets.ime` reads zero here: ADJUST_RESIZE has already taken the
         * keyboard out of the window by the time the composition sees it, so from
         * the inside there is nothing left to inset against. What there is, is a
         * window that has become shorter than the display - which is the same fact
         * from the other side.
         *
         * Measured in the view layer on purpose. The obvious Compose answer is
         * `BoxWithConstraints`, and that is the one thing this window cannot do:
         * it subcomposes, and a subcomposition here tears the slot table apart.
         */
        val keyboardUp = mutableStateOf(false)
        view.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            keyboardUp.value = (bottom - top) < screen.heightPixels - KEYBOARD_MIN_PX
        }
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        // The fourth owner, and the one that cannot be faked into working.
        //
        // The chat screen asks for three launchers - a camera, a media picker and
        // the microphone permission - and every one of them is a question put to the
        // system that is answered **to an activity**. A window has no activity to
        // answer to, which is why composing this screen here threw outright:
        //
        //     IllegalStateException: No ActivityResultRegistryOwner was provided
        //
        // So the launch is honoured by handing the whole thing over: the panel
        // closes and the app opens on this chat, where the picker works. The
        // alternative - a registry that accepts the launch and drops the answer -
        // would leave a camera button that opens a camera whose photo goes nowhere,
        // which is the kind of quiet half-failure this app has had enough of.
        val results = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                Log.i(TAG, "Panel cannot launch " + contract.javaClass.simpleName +
                    "; opening the app on the chat instead")
                openInApp(app, conversationId)
            }
        }
        val owners = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = results
        }

        view.addView(
            ComposeView(app).apply {
                // Tie the composition to the owner above, not to the window.
                //
                // The default strategy disposes when the view leaves its window and
                // leans on a recomposer the window factory makes - which is built
                // for an activity's decor view, not for something handed straight to
                // the window manager. Left alone it produced a slot table that did
                // not survive its first measure:
                //
                //     NullPointerException: RememberObserverHolder.getWrapped()
                //       at RememberEventDispatcher.dispatchRememberList
                //
                // Pointed at the lifecycle this window actually has, there is one
                // owner of the composition and one thing that ends it.
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent {
                    CompositionLocalProvider(
                        LocalActivityResultRegistryOwner provides owners
                    ) {
                        // The panel's own size, not the screen's.
                        //
                        // It is shorter than the display by the room left for the
                        // heads above and the strip below, and handing the theme
                        // the screen instead sized every space and every line for a
                        // box this one does not have. Told rather than measured, for
                        // the reason written over the overload.
                        IQMessageTheme(
                            widthDp = screen.widthPixels / screen.density,
                            heightDp = panelHeight / screen.density
                        ) {
                            // The gap the window used to leave, left here instead.
                            //
                            // Above: the docked row of heads sits in it, so the two
                            // no longer fight over the same strip of screen.
                            // Below: a sliver of whatever was underneath, which is
                            // what keeps this a panel rather than an app - and it
                            // gives way to the keyboard, because the window shrinks
                            // and this padding shrinks with it.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = PANEL_TOP_DP.dp,
                                        // Gone while the keyboard is up: the strip
                                        // is there to show a little of what is
                                        // underneath, and what is underneath then
                                        // is the keyboard. Left in, it is a band of
                                        // wallpaper wedged between the composer and
                                        // the keys.
                                        bottom = if (keyboardUp.value) 0.dp
                                        else PANEL_BOTTOM_DP.dp
                                    )
                            ) {
                                ChatScreen(
                                    conversationId = conversationId,
                                    onBack = { close() },
                                    onAudioCall = { handOver(app, it, CallType.AUDIO) },
                                    onVideoCall = { handOver(app, it, CallType.VIDEO) }
                                )
                            }
                        }
                    }
                }
            },
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        // Full screen, and the room above and below is left by the content inside.
        //
        // Fixing the window's height was what broke the keyboard: ADJUST_RESIZE
        // works by shrinking the window, and a window pinned to a number has
        // nothing to shrink - so the composer went behind the keys instead of
        // climbing above them, and a panel you cannot type in is no panel at all.
        // It is also why the panel and the heads ran into each other: two windows
        // each laying claim to the top of the screen.
        //
        // Messenger does the same thing, and the window list says so: its panel is
        // `(0,0) fillxfill`, with the wallpaper showing through its own transparent
        // edges rather than through a gap left in the window.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // No NOT_FOCUSABLE here, which is the difference from the heads: this
            // window takes key events, so the keyboard opens for it and back
            // reaches the view above.
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Resize rather than pan, so the composer rides the keyboard instead of
            // the whole chat sliding up and taking its own header off the screen.
            //
            // Deprecated for activities, which are told to handle insets themselves
            // instead - but this is not an activity, and for a window handed to the
            // window manager it is still how the keyboard is accounted for.
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val attempt = runCatching { manager.addView(view, params) }
        attempt.exceptionOrNull()?.let {
            Log.e(TAG, "open: addView failed", it)
            owner.stop()
            return
        }
        Log.i(TAG, "open: panel added")

        windows = manager
        panel = view
        host = owner
        showing = conversationId
        // Above the panel, so the head that opened it can still be pressed to put it
        // away. Both are overlay windows and the later one wins, so the row is put
        // back after this rather than left underneath.
        // The row goes to the top and the panel sits under it, which is the shape
        // that makes the two read as one thing.
        ChatHeads.dock(app)
        Log.i(TAG, "open: docked the heads row")
    }

    fun close() {
        Log.i(TAG, "close: showing=" + showing, Throwable("who called close"))
        val manager = windows
        val view = panel
        if (manager != null && view != null) runCatching { manager.removeView(view) }
        // After the window is gone: the composition's own teardown runs here, and
        // it should not be drawing while it happens.
        host?.stop()
        windows = null
        panel = null
        host = null
        showing = null
        // The row goes back where the reader had put it.
        ChatHeads.undock()
    }

    /**
     * Closes the panel and opens the app on the same chat.
     *
     * Logged rather than left to chance: an app in the background is not always
     * allowed to start an activity, and when it is refused the refusal is the
     * system's and silent. The line before it is what tells the two apart.
     */
    private fun openInApp(app: Context, conversationId: String) {
        close()
        runCatching {
            app.startActivity(MessageNotifications.openIntentFor(app, conversationId))
        }.onFailure { Log.w(TAG, "Could not open the app on the chat", it) }
    }

    /**
     * A call wants the whole screen, so it leaves the panel behind.
     *
     * The same hand-over the bubble activity makes, and for the same reason: a call
     * is a picture, a speaker and a row of controls, and this is a panel the size of
     * a message thread.
     */
    private fun handOver(app: Context, peerId: String, type: CallType) {
        close()
        CallCenter.start(app, peerId, type)
        runCatching {
            app.startActivity(
                Intent(app, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }
    }
}
