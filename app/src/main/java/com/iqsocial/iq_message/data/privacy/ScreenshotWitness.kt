package com.iqsocial.iq_message.data.privacy

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.SettingsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Noticing that a screenshot was taken, and of whom.
 *
 * The other half of `allow_screenshot`. When somebody has that setting **off** the
 * system refuses the screenshot outright and there is nothing to report - the
 * window carries `FLAG_SECURE` and the shot comes out as "Can't take screenshot".
 * When it is **on**, the shot succeeds, and `notify_on_screenshot` says that the
 * person in the chat should be told it happened.
 *
 * Android gives an app exactly one honest way to know: `ScreenCaptureCallback`,
 * added in Android 14 and gated behind `DETECT_SCREEN_CAPTURE`. It fires for a
 * screenshot of this app's own window and says nothing about what was captured,
 * which is the whole of what is needed here. Below Android 14 there is no such
 * signal; the alternatives are watching the screenshots folder, which means asking
 * for the photo library, and that is a far larger thing to take from somebody than
 * this feature is worth.
 *
 * [captured] is called on the main thread by the activity; [showing] and [left] are
 * called by whichever screen is displaying a person, so the two together answer
 * "whose chat was on screen when the button was pressed".
 *
 * **Telling the other person is the server's job.** This reports the event to
 * `POST /v1/settings/users/{uid}/screenshot` and stops there: the server checks
 * that person's `notify_on_screenshot`, and only then saves them a notification
 * and sends the push. Which is the right place for the decision - their setting is
 * theirs, and an app that had to read it first would be holding a copy of somebody
 * else's preference in order to decide whether to respect it.
 */
object ScreenshotWitness {

    private const val TAG = "ScreenshotWitness"

    /**
     * Who is on screen, innermost last.
     *
     * A stack rather than one value, because the screens nest: a profile sheet
     * opens over a chat, a call screen over both. One value meant the sheet
     * closing cleared the subject while the chat it opened from was still there,
     * and a screenshot taken a second later belonged to nobody.
     */
    private val shown = mutableListOf<Pair<String, String?>>()

    /** Says who is on screen now, over whatever was already there. */
    fun showing(uid: String?, conversationId: String? = null) {
        val who = uid?.takeIf { it.isNotBlank() } ?: return
        shown += who to conversationId?.takeIf { it.isNotBlank() }
    }

    /**
     * Says that screen has gone.
     *
     * The last entry for that person, not every one of them: the same person can
     * be on screen twice over - their chat with their profile sheet above it - and
     * closing the sheet leaves the chat.
     */
    fun left(uid: String?) {
        val who = uid?.takeIf { it.isNotBlank() } ?: return
        val at = shown.indexOfLast { it.first == who }
        if (at >= 0) shown.removeAt(at)
    }

    /** Who is on screen now, for a detector that wants to know before it looks. */
    fun showing(): String? = shown.lastOrNull()?.first

    /**
     * Somebody **tried** to screenshot, and the system refused.
     *
     * The one case the platform will not report. A screenshot that succeeds is
     * announced - by the callback on Android 14, by the file landing in the
     * gallery everywhere else - but a screenshot that `FLAG_SECURE` blocks writes
     * nothing and tells nobody, which is exactly the case the person who turned
     * screenshots off would most want to hear about.
     *
     * So the attempt is caught at the only place it is still visible: the gesture
     * itself. See [com.iqsocial.iq_message.ui.components.watchForScreenshotGesture]
     * for what that can and cannot see - it is three fingers on the glass, not the
     * hardware buttons, which the system takes before any app sees them.
     *
     * The server is told through the same route as a successful one. Its own
     * description asks for exactly this: "call this when the user takes (or tries
     * to take) a screenshot".
     */
    fun attempted(context: Context, how: String) =
        captured(context, from = "tried: $how", attempted = true)

    /**
     * A screenshot was taken, says [from] - the platform callback, or the gallery.
     *
     * Both can notice the same one: Android 14 tells the app directly, and the
     * file it wrote turns up in the gallery a moment later. Whichever is first
     * reports it and the other is dropped, because [SAME_SHOT_MS] apart and about
     * the same person is one screenshot seen twice, not two screenshots - and the
     * person on the other end should be told once.
     */
    fun captured(context: Context, from: String = "system", attempted: Boolean = false) {
        val top = shown.lastOrNull()
        if (top == null) {
            // A screenshot of the chat list, the settings screen, anything that is
            // not about one person. Nobody to tell.
            Log.i(TAG, "Screenshot taken ($from), not of anybody's conversation")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (top.first == lastUid && now - lastAt < SAME_SHOT_MS) {
            Log.i(TAG, "Screenshot seen again ($from); already reported")
            return
        }
        lastUid = top.first
        lastAt = now
        Log.i(TAG, "Screenshot noticed by: $from")
        report(context.applicationContext, top.first, top.second, attempted)
    }

    /** The last one reported, so the second sighting of it can be recognised. */
    private var lastUid: String? = null
    private var lastAt = 0L
    private const val SAME_SHOT_MS = 4_000L

    /**
     * Its own scope, and not the activity's.
     *
     * The screenshot is taken at the moment the reader is leaving - that is what a
     * screenshot usually precedes - so the request that reports it would be
     * cancelled by exactly the thing it is reporting on.
     */
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Where the other person would be told, once there is anywhere to tell them.
     *
     * Deliberately not a message in the thread. A line of text sent into the
     * conversation would be a real message from this account, written by the app,
     * which is a bigger lie than the feature is worth and would sync, reply and
     * notify like anything else somebody said. A screenshot notice is the server's
     * to send, and it belongs to the owner's `notify_on_screenshot`, which only
     * the server can read.
     */
    private fun report(
        context: Context,
        uid: String,
        conversationId: String?,
        attempted: Boolean
    ) {
        val token = SessionStore(context).token
        if (token.isNullOrBlank()) return
        Log.i(
            TAG,
            "Screenshot taken while showing $uid" +
                (conversationId?.let { " in conversation $it" } ?: "")
        )
        work.launch {
            when (val said = SettingsApi.reportScreenshot(token, uid, attempted)) {
                is ApiResult.Success ->
                    Log.i(TAG, "reported; they were told: ${said.data}")

                is ApiResult.Failure ->
                    Log.w(TAG, "could not report the screenshot: ${said.message}")
            }
        }
    }
}
