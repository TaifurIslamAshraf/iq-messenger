package com.iqsocial.iq_message.data.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.text.TextUtils
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.view.WindowManager
import com.iqsocial.iq_message.data.profile.SettingsRepository
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.iqsocial.iq_message.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Floating heads over other apps, the way a messenger has always done it.
 *
 * Android's own bubbles are the sanctioned version of this and are already built -
 * but they start on Android 11, and even there a conversation only bubbles after the
 * reader has promoted it from its notification. There is no API to ask for that.
 * This is the older mechanism and the one that works everywhere from Android 6: an
 * overlay window the app draws itself, once `SYSTEM_ALERT_WINDOW` is granted.
 *
 * The two do not fight. A bubble is the system's window and this is the app's; the
 * reader will have one or the other depending on what they allowed.
 *
 * **Views, not Compose.** A window added through `WindowManager` has no lifecycle,
 * no saved-state registry and no view-tree owners, and a composition needs all
 * three. Wiring them up by hand for a circle with a picture in it would be more
 * machinery than the thing it draws.
 */
object ChatHeads {

    /** One person, floating. */
    private class Head(
        val conversationId: String,
        val name: String,
        val view: HeadView
    )

    private val heads = mutableListOf<Head>()

    /**
     * Where the row sat before the panel pushed it to the top.
     *
     * Kept so that closing the panel puts it back where it was left, rather than
     * leaving it wherever opening a chat happened to move it.
     */
    private var parked: Pair<Int, Int>? = null

    /** The bin at the bottom, up only while something is being dragged. */
    private var binView: RemoveTarget? = null
    private var binParams: WindowManager.LayoutParams? = null

    /** The line of text that sits beside the heads for a moment when one arrives. */
    private var bubble: PreviewBubble? = null
    private val takeBubbleAway = Runnable { hidePreview() }
    private var windows: WindowManager? = null
    private var row: DragRow? = null
    private var params: WindowManager.LayoutParams? = null

    /** Whether the reader has allowed drawing over other apps. */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * Everything that touches the window, on the thread that is allowed to.
     *
     * A `WindowManager` window and the views in it belong to a looper thread, and
     * the only caller of [show] is the socket listener, which runs on
     * `Dispatchers.IO`. So every head ever asked for was built on the wrong thread
     * and thrown out - inside the listener's own `runCatching`, which meant no
     * crash, no log, and no heads. The feature was written, shipped and silent.
     *
     * Run straight through when already on the main thread, so a tap on a head -
     * which is on it - still dismisses in the same frame rather than the next.
     */
    private val main = Handler(Looper.getMainLooper())

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    /**
     * Adds a head, or brings an existing one to the front of the row.
     *
     * Silently does nothing without the permission: a head is an extra, and an app
     * that threw here would be failing a message over a decoration.
     */
    fun show(
        context: Context,
        conversationId: String,
        name: String,
        face: Bitmap?,
        unread: Int = 0
    ) {
        onMain { showNow(context, conversationId, name, face, unread) }
    }

    /**
     * A conversation is being read: clear its head's count, and - if asked - give
     * it one.
     *
     * [create] is the whole question, and the answer depends on where the chat was
     * opened. **In the panel**, yes: the reader is already floating over another
     * app, the heads are that panel's tabs, and a chat opened from its list without
     * a head of its own would be a tab with nothing to press to get back to it.
     * **In the app**, no. A head is a way back into a conversation from outside the
     * app, and the reader who is inside the app has not asked for one - opening a
     * chat there was leaving a head on their screen for every person they looked
     * at, which turns a shortcut into litter.
     *
     * The count clears either way, because that part is about the chat being read
     * and not about where it is being read: a head with 3 on it for a thread open
     * on the screen is wrong wherever the thread was opened from.
     *
     * No head is ever moved. It used to be pulled to the front as the newest thing
     * being read, which meant every head after it slid along to make room:
     * reopening one chat rearranged all of them. A head is a place as much as it is
     * a person, and the hand learns where each one is. Only a chat without a head
     * changes the row, and then only by adding one at the front.
     */
    fun opened(
        context: Context,
        conversationId: String,
        name: String,
        face: Bitmap?,
        create: Boolean
    ) = onMain {
        if (!allowed(context) || conversationId.isBlank()) return@onMain
        val app = context.applicationContext
        val already = heads.firstOrNull { it.conversationId == conversationId }
        if (already != null) {
            already.view.unread = 0
            return@onMain
        }
        if (!create) return@onMain
        showNow(app, conversationId, name, face, unread = 0)
    }

    private fun showNow(
        context: Context,
        conversationId: String,
        name: String,
        face: Bitmap?,
        unread: Int
    ) {
        if (!allowed(context) || conversationId.isBlank()) return

        // Already floating, so this is the second message and not the first: the
        // head stays where it is and the number on it goes up. What the line here
        // used to do was return, which is why every message after the first left no
        // mark anywhere - the head was there, and it looked exactly as it had when
        // a single message had arrived.
        heads.firstOrNull { it.conversationId == conversationId }?.let { already ->
            // The server's count when there is one, and one more than before when
            // there is not. The push carries what the account actually has waiting,
            // which is worth more than counting the messages this phone happened to
            // be awake for - a head built after four were missed should not say 1.
            already.view.unread =
                if (unread > 0) unread else already.view.unread + 1
            return
        }

        val app = context.applicationContext
        ensureRow(app)
        val manager = windows ?: return
        val strip = row ?: return

        val view = HeadView(app, name, face)
        // Set either way. The view starts at one because a head usually exists
        // because something arrived; a head for a chat being *read* exists for the
        // opposite reason, and would otherwise wear a count of one for a message
        // the reader is looking at.
        view.unread = unread.coerceAtLeast(0)
        // Ringed from the start when this is the chat the panel is already showing.
        // The panel marks the open head when it changes level, and for a chat opened
        // from the list *inside* the panel that happens a frame before the head for
        // it exists - so the head that arrives afterwards was the one head without
        // the mark saying it is the one being read.
        view.active = conversationId == ChatWindow.showing
        view.setOnClickListener {
            // The panel, not the app.
            //
            // This used to hand the conversation to whoever was listening, which
            // meant launching the activity - and a head exists precisely so that
            // reading a message does not mean leaving what you were doing. It also
            // did not work: an app in the background is not allowed to start an
            // activity, so the head vanished and nothing opened.
            //
            // The head stays put, because it is the way back out: pressed again it
            // closes the panel it opened.
            ChatWindow.toggle(app, conversationId)
            // Read now, so the badge does not go on counting a chat being looked at.
            view.unread = 0
        }
        // In front of the ones already there, so the row grows away from the edge
        // it is pinned to.
        //
        // Appending looked right and read wrong. The row is anchored by its right
        // side, so a head added at that end takes the place the previous one was
        // sitting in and shoves it along - every arrival landing in the same spot
        // while the older heads shuffle sideways. Added at the front, the ones
        // already up keep their places and the new one appears beside them.
        strip.addView(view, 0)
        heads += Head(conversationId, name, view)
        restack(app)

        // More than a handful stops being a row and becomes a wall across the
        // screen, so the oldest drops off - the same order a shade drops them in.
        //
        // **The oldest is the first**, now that the list and the row are built in
        // the same direction. Taking it from the end was taking the newest: the
        // fifth head arrived, pushed the count over, and was itself the one removed
        // - so a fifth conversation could never show a head at all, and the one it
        // was meant to replace stayed.
        // The oldest is the first in this list and the *last* in the row, which is
        // the one against the edge. Removed by reference rather than by index, so
        // the two orders never have to be reconciled.
        while (heads.size > MAX_HEADS) {
            val oldest = heads.removeAt(0)
            runCatching { strip.removeView(oldest.view) }
            restack(app)
        }
        runCatching { manager.updateViewLayout(strip, params) }
        // Pinned to its edge again, now that there is one more head in it.
        //
        // The window's x is its **left** edge and the row is wrap-content, so a row
        // resting against the right of the screen grows rightwards as heads are
        // added - straight off the display. The second head was created, added and
        // drawn, entirely past the edge, which from the outside is a head that
        // never came up.
        reanchor(strip)
    }

    /**
     * Lays the heads out: a row while a chat is open, a pile while none is.
     *
     * The two states want opposite things. With the panel up the heads are its
     * tabs - each one has to be reachable, so they spread into a row along the top
     * and every face can be pressed. With nothing open they are resting on
     * somebody else's screen, and four circles down the edge is four times as much
     * of that screen taken for something nobody is using. So they collapse onto
     * each other, [STACK_STEP_PX] apart: one head to look at, and the rest a stack
     * of edges behind it saying how many there are.
     *
     * The newest is the one on top - see [DragRow.getChildDrawingOrder] - which is
     * both what a pile means and what makes the pile tappable: the press lands on
     * the head that arrived last, and the others come back out of it the moment
     * the panel opens.
     *
     * The preview bubble is a child of this row too, and it keeps a real gap
     * either way: it is a line of text beside the heads, not one of them.
     *
     * Capped at [MAX_HEADS], so a row stays a row rather than becoming a bar across
     * the screen.
     */
    private fun restack(context: Context) {
        val strip = row ?: return
        val gap = context.dp(HEAD_GAP_DP)
        // Negative, by design: a margin is how far this head starts after the last
        // one ended, so a head's own width taken back off it, less a pixel, leaves
        // each face exactly a pixel to the right of the one in front of it.
        val between =
            if (strip.locked) gap else STACK_STEP_PX - context.dp(HEAD_SIZE_DP)
        var seenHead = false
        for (i in 0 until strip.childCount) {
            val child = strip.getChildAt(i)
            val lp = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            val head = child is HeadView
            lp.marginStart = when {
                i == 0 -> 0
                head && seenHead -> between
                // Either the bubble, or the first head after it.
                else -> gap
            }
            if (head) seenHead = true
            child.layoutParams = lp
        }
    }

    /**
     * Puts the row back on top of whatever has just been added over it.
     *
     * Overlay windows of one type stack in the order they were added, so a panel
     * opened after the heads covers them - including the head that has to be pressed
     * to close it again. Removing and re-adding is the only way to say "last", and
     * it is cheap: the views are kept, only the window is remade.
     */
    fun raise() = onMain {
        val manager = windows ?: return@onMain
        val strip = row ?: return@onMain
        val attrs = params ?: return@onMain
        // `removeView` is asynchronous - it posts the removal and returns - so
        // adding the same view back in the same breath is a race, and the race was
        // being lost: the row came down and never went up again, which looked from
        // the outside like a head that deleted itself when pressed.
        //
        // `removeViewImmediate` is the one that is finished when it returns.
        runCatching { manager.removeViewImmediate(strip) }
            .onFailure { Log.w(TAG, "Could not take the heads row down to raise it", it) }
        runCatching { manager.addView(strip, attrs) }
            .onFailure { Log.e(TAG, "The heads row did not come back up", it) }
    }

    /**
     * Sends the row to the top, the way Messenger does when a chat opens.
     *
     * A head where it was left is right while it is only a head; with a panel under
     * it, it is in the way of the conversation. Messenger answers this by lining the
     * heads up along the top edge and putting the panel beneath them, so the two
     * read as one thing rather than a window with a sticker on it.
     *
     * Where it was is remembered, because the position is the reader's and taking it
     * away permanently to open one chat would be the app tidying up after them.
     */
    fun dock(context: Context) = onMain {
        val manager = windows ?: return@onMain
        val strip = row ?: return@onMain
        val attrs = params ?: return@onMain
        if (parked == null) parked = attrs.x to attrs.y
        // Anything already floating beside the heads goes now, for the reason in
        // [preview]: the panel about to cover the screen says it better.
        hidePreview()
        strip.locked = true
        // Out of the pile and into a row: with a chat under them the heads are the
        // panel's tabs, and a tab hidden behind the one in front is not one.
        restack(context)
        attrs.y = context.dp(DOCK_TOP_DP)
        // Against the right edge, growing leftwards as heads are added, so the one
        // most recently added stays where the thumb last was rather than shuffling
        // the whole row sideways each time.
        attrs.x = rightEdge(manager, strip, context)
        runCatching { manager.updateViewLayout(strip, attrs) }
        // Measured again once it has been laid out: the first dock of a new head
        // happens before the row knows how wide it is, and a width of zero would
        // put it off the right edge entirely.
        strip.post {
            val now = params ?: return@post
            if (!strip.locked) return@post
            now.x = rightEdge(manager, strip, context)
            runCatching { manager.updateViewLayout(strip, now) }
        }
        raise()
        refreshActive()
    }

    /**
     * Puts the row back against whichever edge it is resting on.
     *
     * After the row has been laid out, because its width is the whole question and
     * a row that has just gained or lost a head does not have one yet.
     *
     * While the panel is open the edge is not in question - it is the right one,
     * because that is where [dock] puts it - but the re-anchoring still has to
     * happen, and for exactly the same reason.
     */
    private fun reanchor(strip: DragRow) {
        // Nothing posted here. `post` runs on the next message, which is usually
        // before the row has been measured again - so the width read is the width
        // it had *before* the new head, the edge is worked out from that, and the
        // row ends up one head too far over: the newest sitting where the oldest
        // was, and the oldest pushed off the screen. That is the whole of "the new
        // one lands on top of the old one".
        //
        // The row says when it knows its own size instead; see [DragRow.onLayout].
        strip.requestLayout()
    }

    /** Called by the row once it has measured itself, with the width settled. */
    private fun anchorToNearestEdge(strip: DragRow) {
        val manager = windows ?: return
        val attrs = params ?: return
        // Docked, the row has an edge already and it is not the nearest one: it is
        // held against the right with the panel beneath it, and a head arriving
        // while a chat is open must not send the strip looking for a side.
        //
        // It used to return here, and that was the whole of "a head added while the
        // window is open does not join the row". The window is wrap-content and
        // positioned by its **left** side, so the row grows rightwards as heads are
        // added; with the position frozen, the new head took the place the previous
        // one was sitting in and pushed the older ones off the screen. Re-anchored,
        // the left edge moves out by the width of a head and the row stays whole.
        if (strip.locked) {
            attrs.x = rightEdge(manager, strip, strip.context)
            runCatching { manager.updateViewLayout(strip, attrs) }
            return
        }
        val screen = Rect().also {
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRectSize(it)
        }
        anchor(onLeft = attrs.x + strip.width / 2 < screen.width() / 2)
    }

    /** Where the row sits when it is pinned to the right of the screen. */
    private fun rightEdge(manager: WindowManager, strip: View, context: Context): Int {
        val screen = Rect().also {
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRectSize(it)
        }
        val margin = context.dp(EDGE_MARGIN_DP)
        return (screen.width() - strip.width - margin).coerceAtLeast(margin)
    }

    /** Puts it back where the reader had it. */
    fun undock() = onMain {
        val manager = windows ?: return@onMain
        val strip = row ?: return@onMain
        val attrs = params ?: return@onMain
        strip.locked = false
        // Back into a pile, now that there is no chat for them to be the tabs of.
        restack(strip.context)
        // Back exactly where it was before the panel took it to the top. The
        // position was kept at [dock] precisely so that closing is a return rather
        // than a new guess at where the heads belong.
        parked?.let { (x, y) ->
            attrs.x = x
            attrs.y = y
            runCatching { manager.updateViewLayout(strip, attrs) }
        }
        parked = null
        refreshActive()
    }

    /**
     * Rings the head whose chat is open.
     *
     * With several heads in a row and a panel under them, which one the panel
     * belongs to is otherwise a guess.
     */
    fun refreshActive() = onMain {
        heads.forEach { it.view.active = it.conversationId == ChatWindow.showing }
    }

    /**
     * Says what just arrived, beside the heads, for a few seconds.
     *
     * What a head is missing on its own: it says *who*, and a badge says *how many*,
     * and neither says anything about the message - so a head is a prompt to go and
     * look rather than something you can read. Messenger puts the line of text next
     * to the head and takes it away again, and that is the difference between
     * glancing and interrupting.
     *
     * Which side it opens on follows the heads. A bubble to the right of a head
     * parked on the right edge would be drawn off the screen, so it goes on
     * whichever side there is room for - and the row is re-anchored afterwards, so
     * growing it never pushes the heads themselves away from the edge they were
     * left on.
     *
     * Not shown while that chat's panel is open: the message is already on screen,
     * in the thread, where it was going anyway.
     */
    fun preview(context: Context, conversationId: String, text: String) = onMain {
        if (text.isBlank()) return@onMain
        // Nothing beside the heads while the panel is open - not just for the chat
        // being read, for any of them. The panel is already a window onto the
        // conversation, and a line of a message floating over it is the same thing
        // said twice, in the smaller and worse of the two places.
        if (ChatWindow.isOpen) return@onMain
        val manager = windows ?: return@onMain
        val strip = row ?: return@onMain
        val attrs = params ?: return@onMain

        bubble?.let { runCatching { strip.removeView(it) } }
        val screen = Rect().also {
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRectSize(it)
        }
        val onLeft = attrs.x + strip.width / 2 <= screen.width() / 2
        val fresh = PreviewBubble(context).apply { setText(text) }
        // Beside the heads, on the side with room: after them when they sit on the
        // left, before them when they sit on the right.
        if (onLeft) strip.addView(fresh) else strip.addView(fresh, 0)
        bubble = fresh
        // The bubble is a child of the row, so it shifts every head's index along:
        // without this the head it was put in front of keeps the margin of a first
        // child, and the pile opens up behind it.
        restack(context)

        // The row is wider now; held to the edge it was on so the heads do not walk.
        strip.post { anchor(onLeft) }

        main.removeCallbacks(takeBubbleAway)
        main.postDelayed(takeBubbleAway, PREVIEW_MS)
    }

    private fun hidePreview() = onMain {
        val strip = row ?: return@onMain
        val attrs = params ?: return@onMain
        val gone = bubble ?: return@onMain
        runCatching { strip.removeView(gone) }
        bubble = null
        restack(strip.context)
        val manager = windows ?: return@onMain
        val screen = Rect().also {
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRectSize(it)
        }
        strip.post { anchor(attrs.x + strip.width / 2 <= screen.width() / 2) }
    }

    /** Holds the row against the edge it belongs to, whatever its width is now. */
    private fun anchor(onLeft: Boolean) {
        val manager = windows ?: return
        val strip = row ?: return
        val attrs = params ?: return
        val screen = Rect().also {
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRectSize(it)
        }
        // Tucked part way off the edge at rest, which is what Messenger does and
        // what keeps a head out of the way of the app underneath: a sliver of the
        // face is enough to say who it is, and the whole of it comes back the
        // moment it is picked up. The window already has LAYOUT_NO_LIMITS, which is
        // what lets it sit past the edge at all.
        val margin = strip.context.dp(EDGE_MARGIN_DP)
        val tuck = strip.context.dp(HEAD_SIZE_DP * TUCK_PERCENT / 100)
        attrs.x = if (onLeft) margin - tuck
        else (screen.width() - strip.width - margin + tuck).coerceAtLeast(margin - tuck)
        runCatching { manager.updateViewLayout(strip, attrs) }
    }

    /**
     * Raises the bin, and says where it is.
     *
     * Its own window rather than part of the row: it belongs to the bottom of the
     * screen while the row belongs to the finger, and one window cannot be in two
     * places. Untouchable, so it never takes the drag it is there to receive - the
     * row keeps the gesture the whole way down and simply asks, on release, whether
     * it ended over this.
     */
    private fun showBin(context: Context) {
        if (binView != null) return
        val manager = windows ?: return
        val target = RemoveTarget(context)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val attrs = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = context.dp(BIN_BOTTOM_DP)
        }
        runCatching { manager.addView(target, attrs) }
            .onFailure { Log.w(TAG, "The bin would not open", it) }
        binView = target
        binParams = attrs
    }

    private fun hideBin() {
        val manager = windows
        val target = binView
        if (manager != null && target != null) runCatching { manager.removeView(target) }
        binView = null
        binParams = null
    }

    /**
     * Whether the row, where it is now, is over the bin.
     *
     * Measured centre to centre with a generous reach, because a bin that has to be
     * hit exactly is a bin nobody uses: the pull towards it is most of what makes it
     * feel like it catches things.
     */
    private fun overBin(rowX: Int, rowY: Int, rowW: Int, rowH: Int): Boolean {
        val manager = windows ?: return false
        val target = binView ?: return false
        val screen = Rect().also {
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRectSize(it)
        }
        val binCx = screen.width() / 2f
        val binCy = screen.height() - target.context.dp(BIN_BOTTOM_DP) - target.height / 2f
        val cx = rowX + rowW / 2f
        val cy = rowY + rowH / 2f
        val reach = target.context.dp(BIN_REACH_DP).toFloat()
        return hypot(cx - binCx, cy - binCy) <= reach
    }

    /** Takes one away, and the row with it once the last one goes. */
    fun dismiss(conversationId: String) = onMain {
        val at = heads.indexOfFirst { it.conversationId == conversationId }
        if (at < 0) return@onMain
        val head = heads.removeAt(at)
        runCatching { row?.removeView(head.view) }
        if (heads.isEmpty()) {
            tearDown()
            return@onMain
        }
        // The one that went may have been the first, and the first carries no
        // margin: left alone, whichever head inherits its place keeps a margin
        // meant for a head with something in front of it - which in a pile is a
        // negative one, and the whole stack slides sideways by a head.
        row?.let {
            restack(it.context)
            reanchor(it)
        }
    }

    /** Everything goes: signing out, or the last chat being read. */
    fun clear() = onMain {
        heads.clear()
        tearDown()
    }

    private fun tearDown() {
        // The panel belongs to a head; with no heads left there is nothing holding
        // it open and nothing to press to put it away.
        ChatWindow.close()
        // And nothing left to keep the process up for.
        HeadsService.stop()
        val manager = windows
        val strip = row
        if (manager != null && strip != null) runCatching { manager.removeView(strip) }
        row = null
        params = null
        windows = null
        // Otherwise the next row, built for whoever signs in after this, is told it
        // was docked and never anchors itself.
        parked = null
    }

    /**
     * The window the heads live in, created on first use.
     *
     * One window holding a row rather than one window per head, and that is what
     * makes them draggable together: a single `LayoutParams` to move, and the heads
     * keep their spacing without any of them being told about the others.
     */
    private fun ensureRow(context: Context) {
        if (row != null) return
        // Before the window, not after: the process has to be worth keeping by the
        // time there is something in it to keep.
        HeadsService.start(context)
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val strip = DragRow(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // The bubble is shorter than a head; centred, the two read as one row
            // rather than a card with a circle hanging off it.
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // Not focusable, so the app underneath keeps its keyboard and its taps.
            // Without this the overlay swallows everything behind it.
            // Not locked from this account's own setting, which is what it used to
            // do. That setting says whether other people may screenshot this
            // account, and the faces in this row are other people - so the setting
            // that would cover them is theirs, asked for the person each head
            // belongs to. The row holds several at once and is given names and
            // faces rather than ids, so it has nobody to ask about; the panel
            // underneath, which shows the conversation itself, does and locks
            // itself accordingly.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Off to the right to begin with, corrected below once the row has a
            // width. A head arrives on the side it is reached from, and that is the
            // side the whole row is pinned to when a chat is open.
            x = context.resources.displayMetrics.widthPixels
            y = context.dp(120)
        }

        strip.moves(manager, layout)

        // Loud when the window will not go up.
        //
        // It matters more than it used to: the head is now shown *instead of* the
        // message notification, so a window that silently fails to appear is a
        // message that was never announced at all.
        runCatching { manager.addView(strip, layout) }
            .onFailure { Log.e(TAG, "The heads window would not open", it) }
        // Measured rather than guessed: the row has no width until it has been laid
        // out, and the right edge is the screen minus that width.
        strip.post { if (parked == null) anchor(onLeft = false) }
        windows = manager
        row = strip
        params = layout
    }

    /**
     * Dragging the row, and snapping it to whichever edge it was let go nearest.
     *
     * Free positioning would leave the heads in the middle of whatever is being
     * read. An edge is the only place they are out of the way and still reachable,
     * which is why every app that has ever done this snaps.
     */
    /**
     * The row, which moves when it is dragged.
     *
     * The drag used to be an `OnTouchListener` on this layout, and it never fired:
     * the heads inside it are clickable, so they take the gesture at ACTION_DOWN and
     * a parent's touch listener is never asked again. The row wraps its children
     * exactly, so there was no part of it left for the listener to hear about - the
     * head could be pressed and never moved.
     *
     * Intercepting is the way round it. A press goes to the head as before; once the
     * finger has travelled past the slop the row takes the gesture over, the head is
     * sent a cancel and stops thinking it is being tapped, and the window follows the
     * finger from there.
     */
    private class DragRow(context: Context) : LinearLayout(context) {

        /**
         * True while the panel is open, and then the row does not move.
         *
         * Docked, the heads are a strip belonging to the panel under them - a row
         * that could be dragged away from the thing it labels would leave the two
         * in different places with nothing joining them. They go back to being
         * draggable the moment the panel closes, which is also when they return to
         * where they were left.
         */
        var locked = false

        /**
         * The width the window was last positioned for.
         *
         * A row anchored to an edge has to be moved every time it grows or shrinks,
         * because the window's x is its left side and the growing happens on the
         * right. Kept here because this is the only place that knows when the width
         * has actually changed - a layout pass that did not change it must not move
         * the window, or every move would ask for another one.
         */
        private var anchoredFor = -1

        private var manager: WindowManager? = null
        private var layout: WindowManager.LayoutParams? = null

        init {
            // The newest head is the front of the pile.
            //
            // Children are drawn in the order they were added and the newest is
            // added first, so left alone the *oldest* head would be the one lying
            // on top - a pile showing the face of whichever conversation arrived
            // longest ago. Reversed, the newest is drawn last and so sits on top,
            // and because touches are dispatched in the drawing order reversed, it
            // is also the one a press on the pile lands on.
            //
            // Harmless in a row, where nothing overlaps anything.
            isChildrenDrawingOrderEnabled = true
        }

        override fun getChildDrawingOrder(count: Int, position: Int): Int =
            count - 1 - position

        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        /**
         * How fast it was going when it was let go.
         *
         * A head is thrown as often as it is placed, and a throw says which edge was
         * meant whatever side of the middle the finger happened to lift on. Without
         * it, flicking a head to the right from just left of centre sends it back
         * where it came from, which feels like the app arguing.
         */
        private var speeds: VelocityTracker? = null

        /** The glide home, kept so a second drag can cut it short. */
        private var settling: ValueAnimator? = null

        fun moves(manager: WindowManager, layout: WindowManager.LayoutParams) {
            this.manager = manager
            this.layout = layout
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val now = r - l
            if (now == anchoredFor || now == 0) return
            anchoredFor = now
            // After the pass, not during it: moving the window from inside layout is
            // a layout inside a layout.
            post { anchorToNearestEdge(this) }
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            if (locked) return false
            val attrs = layout ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // A touch during the glide home stops it where it is, so the
                    // head is caught rather than wrestled.
                    settling?.cancel()
                    settling = null
                    downX = event.rawX
                    downY = event.rawY
                    startX = attrs.x
                    startY = attrs.y
                    dragging = false
                    speeds = VelocityTracker.obtain()
                    speeds?.addMovement(event)
                }

                MotionEvent.ACTION_MOVE -> {
                    speeds?.addMovement(event)
                    if (!dragging &&
                        abs(event.rawX - downX) + abs(event.rawY - downY) > slop
                    ) {
                        dragging = true
                        showBin(context)
                    }
                    // Taking it over here is what sends the head its cancel.
                    return dragging
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val windows = manager ?: return false
            val attrs = layout ?: return false
            when (event.actionMasked) {
                // Only reached when no head took the press - the padding around
                // them - but the start has to be recorded either way.
                MotionEvent.ACTION_DOWN -> {
                    settling?.cancel()
                    settling = null
                    downX = event.rawX
                    downY = event.rawY
                    startX = attrs.x
                    startY = attrs.y
                    dragging = false
                    speeds = VelocityTracker.obtain()
                    speeds?.addMovement(event)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    speeds?.addMovement(event)
                    if (!dragging &&
                        abs(event.rawX - downX) + abs(event.rawY - downY) > slop
                    ) {
                        dragging = true
                    }
                    if (!dragging) {
                        showBin(context)
                        return true
                    }
                    attrs.x = startX + (event.rawX - downX).roundToInt()
                    attrs.y = startY + (event.rawY - downY).roundToInt()
                    runCatching { windows.updateViewLayout(this, attrs) }
                    binView?.hungry = overBin(attrs.x, attrs.y, width, height)
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val thrown = speeds?.let {
                        it.addMovement(event)
                        it.computeCurrentVelocity(1000)
                        it.xVelocity
                    } ?: 0f
                    speeds?.recycle()
                    speeds = null
                    // Dropped in the bin: the heads go, and the panel with them.
                    if (dragging && overBin(attrs.x, attrs.y, width, height)) {
                        hideBin()
                        dragging = false
                        clear()
                        return true
                    }
                    hideBin()
                    if (dragging) settle(windows, attrs, thrown)
                    dragging = false
                    return true
                }
            }
            return false
        }

        /**
         * The glide to the edge when the finger lifts.
         *
         * Animated, not assigned. Jumping the window to the edge is the one thing
         * that gives a dragged head away as a window being moved rather than an
         * object being thrown, and it is the difference everyone notices without
         * being able to name.
         *
         * A throw wins over the midpoint. Flick a head rightwards from just left of
         * centre and it should go right - deciding by where the finger happened to
         * lift would send it back the way it came, which reads as the app arguing
         * with the gesture.
         *
         * Up against the edge but not welded to it: a circle flush to x=0 has its
         * own edge cut off by a curved display's, which is why Messenger leaves a
         * gap there too.
         */
        private fun settle(
            windows: WindowManager,
            attrs: WindowManager.LayoutParams,
            thrown: Float
        ) {
            val screen = Rect().also {
                @Suppress("DEPRECATION")
                windows.defaultDisplay.getRectSize(it)
            }
            val margin = context.dp(EDGE_MARGIN_DP)
            val left = margin
            val right = (screen.width() - width - margin).coerceAtLeast(margin)
            val fling = context.dp(FLING_DP_PER_SECOND).toFloat()

            val target = when {
                thrown > fling -> right
                thrown < -fling -> left
                attrs.x + width / 2 < screen.width() / 2 -> left
                else -> right
            }
            val from = attrs.x
            val restY = attrs.y.coerceIn(0, (screen.height() - height).coerceAtLeast(0))

            settling?.cancel()
            settling = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SETTLE_MS
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { step ->
                    val t = step.animatedFraction
                    attrs.x = (from + (target - from) * t).roundToInt()
                    attrs.y = restY
                    runCatching { windows.updateViewLayout(this@DragRow, attrs) }
                }
                start()
            }
            // Where the reader put it is where it goes back to after a panel.
            parked = null
        }
    }

    /** One circle: the person's face, or their initial on a coloured disc. */
    /**
     * The message itself, in a card beside the head.
     *
     * A `TextView` rather than something drawn by hand, unlike everything else in
     * this file: a circle with a letter in it is a few lines of canvas work, and
     * wrapping, ellipsising and measuring a sentence is not.
     */
    private class PreviewBubble(context: Context) : TextView(context) {
        init {
            maxLines = PREVIEW_LINES
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(0xFF121214.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxWidth = context.dp(PREVIEW_WIDTH_DP)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = context.dp(16).toFloat()
            }
            elevation = context.dp(4).toFloat()
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_VERTICAL }
        }
    }

    /**
     * The bin: a dark disc with a cross in it, at the foot of the screen.
     *
     * Grows and brightens when the row is over it, because the only way to know a
     * drop will land is to be told before letting go.
     */
    private class RemoveTarget(context: Context) : View(context) {

        /** True while the row is close enough to be swallowed. */
        var hungry: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        private val size = context.dp(BIN_SIZE_DP)

        private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3).toFloat()
            strokeCap = Paint.Cap.ROUND
        }

        init {
            layoutParams = FrameLayout.LayoutParams(size, size)
        }

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            setMeasuredDimension(size, size)
        }

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            // Bigger and more solid once it is going to catch something.
            val radius = if (hungry) r else r * 0.82f
            disc.color = if (hungry) 0xF2E8114C.toInt() else 0xB3202024.toInt()
            canvas.drawCircle(r, r, radius, disc)

            val arm = radius * 0.34f
            canvas.drawLine(r - arm, r - arm, r + arm, r + arm, cross)
            canvas.drawLine(r + arm, r - arm, r - arm, r + arm, cross)
        }
    }

    /**
     * One floating circle: a face, or an initial on a colour, and the number of
     * messages waiting behind it.
     *
     * A class rather than the anonymous view this was, for one reason: the count
     * has to change after the head is on screen, and there is no way to reach a
     * property on an anonymous object from outside it.
     */
    private class HeadView(
        context: Context,
        private val name: String,
        private val face: Bitmap?
    ) : View(context) {

        /**
         * How many messages are waiting in this conversation.
         *
         * One to begin with, because a head only exists because something arrived.
         * Setting it redraws, which is why the count lives on the view rather than
         * beside it in [Head].
         */
        var unread: Int = 1
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        /** True for the head whose chat the panel is showing. */
        var active: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        private val size = context.dp(HEAD_SIZE_DP)

        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(context.dp(4).toFloat(), 0f, context.dp(1).toFloat(), 0x55000000)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val letters = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * 0.38f
        }
        private val activeRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.notification_accent)
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3).toFloat()
        }
        private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // The app accent, from the one place that defines it, so the heads and
            // the notifications are not two different reds.
            color = ContextCompat.getColor(context, R.color.notification_accent)
            style = Paint.Style.FILL
        }
        private val badgeEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val counter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * 0.22f
        }

        private val caretHeight = context.dp(CARET_HEIGHT_DP)
        private val caretWidth = context.dp(CARET_WIDTH_DP)

        /**
         * The pointer under the open one, in the panel's own colour.
         *
         * White, and tall enough to reach the panel's top edge, so the two are one
         * shape: a notch rising out of the window to the face it belongs to. In the
         * accent colour it was a separate red arrow floating in the gap, which says
         * the same thing while looking like something else.
         */
        private val caret = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        /**
         * This app's own icon, small, in the corner of the face.
         *
         * A head is a person, and a floating circle with a face in it says nothing
         * about which app it belongs to - which matters when two messengers are
         * both floating heads on the same screen. Bottom right, because the unread
         * count already owns the top right.
         */
        private val mark = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)

        init {
            // Software layer, because the shadow under the circle is not drawn by
            // the hardware pipeline on an overlay window.
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            // Taller than it is wide, by exactly the pointer: the circle keeps its
            // size and the arrow lives in the space below it, so turning the
            // pointer on and off never moves the face.
            layoutParams = FrameLayout.LayoutParams(size, size + caretHeight)
        }

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            canvas.drawCircle(r, r, r - context.dp(3), ring)
            // The open one wears the app's colour instead of the plain white ring.
            if (active) canvas.drawCircle(r, r, r - context.dp(3), activeRing)
            val inner = r - context.dp(5)
            if (face != null) {
                val square = Bitmap.createScaledBitmap(
                    face,
                    (inner * 2).toInt().coerceAtLeast(1),
                    (inner * 2).toInt().coerceAtLeast(1),
                    true
                )
                fill.shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                canvas.save()
                canvas.translate(r - inner, r - inner)
                canvas.drawCircle(inner, inner, inner, fill)
                canvas.restore()
            } else {
                fill.shader = null
                fill.color = TINTS[abs(name.hashCode()) % TINTS.size]
                canvas.drawCircle(r, r, inner, fill)
                val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
                val mid = (letters.descent() + letters.ascent()) / 2
                canvas.drawText(initial, r, r - mid, letters)
            }
            drawCount(canvas)
            drawMark(canvas)
            if (active) drawCaret(canvas)
        }

        /**
         * The arrow under the open head.
         *
         * Pointing up at the face rather than down at the panel: it is a mark
         * saying *this* is the one being read, and the panel below it is already
         * the size of the screen and in no need of being pointed at.
         */
        private fun drawCaret(canvas: Canvas) {
            val middle = width / 2f
            val top = size.toFloat()
            val path = android.graphics.Path().apply {
                moveTo(middle, top)
                lineTo(middle - caretWidth / 2f, top + caretHeight)
                lineTo(middle + caretWidth / 2f, top + caretHeight)
                close()
            }
            canvas.drawPath(path, caret)
        }

        private fun drawMark(canvas: Canvas) {
            val icon = mark ?: return
            val side = (size * MARK_PERCENT / 100).coerceAtLeast(1)
            val inset = context.dp(2)
            val left = size - side - inset
            val top = size - side - inset
            // No disc behind it. The icon is round and carries its own background,
            // and a white ring around it only made the head look like it had a
            // sticker on it.
            icon.setBounds(left, top, left + side, top + side)
            icon.draw(canvas)
        }

        /**
         * The count, over the top right of the face.
         *
         * On a white disc of its own, so it separates from whatever the photo
         * underneath happens to be - the same job the ring does for the head.
         *
         * Capped, and widened for the cap: a three-character count in a circle
         * sized for one digit would be drawn over its own edge.
         */
        private fun drawCount(canvas: Canvas) {
            if (unread < 1) return
            val text = if (unread > 99) "99+" else unread.toString()
            val radius = context.dp(if (text.length > 2) 11 else 9).toFloat()
            val cx = width - radius - context.dp(3)
            val cy = radius + context.dp(3)
            canvas.drawCircle(cx, cy, radius + context.dp(2), badgeEdge)
            canvas.drawCircle(cx, cy, radius, badge)
            val mid = (counter.descent() + counter.ascent()) / 2
            canvas.drawText(text, cx, cy - mid, counter)
        }
    }

    private const val TAG = "ChatHeads"

    /** How far off the screen edge a resting head sits. */
    private const val EDGE_MARGIN_DP = 8

    /** Where the row goes while a panel is open: under the status bar. */
    private const val DOCK_TOP_DP = 36

    /** How long the head takes to glide to the edge after a drag. */
    private const val SETTLE_MS = 240L

    /** Past this much sideways speed, the throw decides the edge, not the midpoint. */
    private const val FLING_DP_PER_SECOND = 700

    /** How big a head is. */
    private const val HEAD_SIZE_DP = 72

    /**
     * The pointer under the head whose chat is open.
     *
     * Its height is the gap it has to cross, not a shape anybody chose: the row
     * docks [DOCK_TOP_DP] down, a head is [HEAD_SIZE_DP] tall, and the panel starts
     * at `ChatWindow.PANEL_TOP_DP`. Fourteen is what is left between them, so the
     * tip touches the face and the base meets the window with nothing in between.
     * Change either of those and this has to change with them.
     */
    private const val CARET_HEIGHT_DP = 14
    private const val CARET_WIDTH_DP = 18

    /** How much of the head the app's own mark takes, in the bottom corner. */
    private const val MARK_PERCENT = 30

    /** The preview beside the heads: how wide, how many lines, how long it stays. */
    private const val PREVIEW_WIDTH_DP = 210
    private const val PREVIEW_LINES = 2
    private const val PREVIEW_MS = 5000L

    /** The bin: how big, how far up from the bottom, and how near counts as over it. */
    private const val BIN_SIZE_DP = 64
    private const val BIN_BOTTOM_DP = 72
    private const val BIN_REACH_DP = 110

    /** Enough to keep a conversation going; more is a wall down the screen. */
    /** The space between one head and the next, now that they sit in a row. */
    private const val HEAD_GAP_DP = 6

    /** How much of a resting head hangs off the edge of the screen. */
    private const val TUCK_PERCENT = 28

    /**
     * How far apart the heads sit once they have collapsed into a pile.
     *
     * A pixel, counted in pixels rather than dp because it is not a measurement of
     * anything - it is the smallest offset there is. All it has to say is that
     * there is another head back there; the face on top says everything else.
     */
    private const val STACK_STEP_PX = 1

    private const val MAX_HEADS = 4
}

// At file level rather than inside the object, so the head view can reach them: a
// nested class does not get its container's receiver.

private fun Context.dp(value: Int): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value.toFloat(),
    resources.displayMetrics
).roundToInt()

private val TINTS = intArrayOf(
    0xFFE8114C.toInt(), 0xFF7B2FF7.toInt(), 0xFF1F8A70.toInt(),
    0xFFE2683C.toInt(), 0xFF2D6CDF.toInt()
)
