package com.iqsocial.iq_message.data.privacy

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.push.IqMessagingService
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.IqNotification
import com.iqsocial.iq_message.data.remote.NotificationsApi
import androidx.compose.ui.graphics.asAndroidBitmap
import org.json.JSONObject

/**
 * Being told that somebody screenshotted you.
 *
 * The other side of [ScreenshotWitness]: that one reports a screenshot this phone
 * took, this one shows the one somebody else took of this account. The server
 * decides whether to send anything at all - it checks the owner's
 * `notify_on_screenshot` before it saves a row or sends a push - so nothing here
 * has to ask whether the reader wanted to know. They did, or this never arrives.
 *
 * **Three ways in, one notification out.** A push while the app is away, a socket
 * event while it is open, and a catch-up read of `/v1/notifications` when it comes
 * back - all three can carry the same alert, and a phone that was asleep during the
 * screenshot and awake for the catch-up would otherwise be told twice. They are
 * reconciled by using the notification's own id as the tag: the second posting
 * replaces the first rather than stacking under it.
 */
object ScreenshotAlerts {

    private const val TAG = "ScreenshotAlerts"

    /** One slot; the tag is what keeps separate alerts apart. */
    private const val ID = 7301

    /** How far back a catch-up looks. More than this and it is history, not news. */
    private const val CATCH_UP = 10

    /**
     * How long the same alert is remembered, so three routes show one notification.
     *
     * The tag alone was supposed to do this - post under the notification's own id
     * and the second posting replaces the first - and it does, when every route
     * carries that id. The socket event's shape is not written down anywhere the
     * server publishes, so one of them can arrive without it, fall back to a
     * different tag, and sit beside the first as a second notification. This is
     * the belt to that pair of braces: same person, same minute, same alert.
     */
    private const val SAME_ALERT_MS = 60_000L

    /** Alerts already announced, by whatever identified them, and when. */
    private val recent = mutableMapOf<String, Long>()

    private const val PREFS = "iq_alerts"
    private const val KEY_SEEN_AT = "screenshot_seen_at"

    /**
     * Shows an alert that arrived live, as a push or over the socket.
     *
     * The payload's shape is not written down anywhere the server publishes - the
     * API description says only that a `profile:screenshot` event is sent - so this
     * reads the fields it can find rather than insisting on one layout. What it
     * needs is a name and a line of text, and it has a stand-in for both.
     */
    suspend fun live(context: Context, payload: JSONObject) {
        val row = payload.optJSONObject("notification")
            ?: payload.optJSONObject("item")
            ?: payload
        val actor = row.optJSONObject("actor")
            ?: payload.optJSONObject("actor")
            ?: payload.optJSONObject("by")
            ?: payload.optJSONObject("person")
        val who = actor?.optString("fullname").orEmpty().ifBlank { "Someone" }
        val what = row.optString("text").orEmpty()
            .ifBlank { "took a screenshot of your profile" }
        val face = actor?.optString("profile_picture").orEmpty()
        // The id when there is one, and the person when there is not: two routes
        // carrying the same event have to land on the same key or they are two
        // events as far as this is concerned.
        val tag = row.optString("id").orEmpty()
            .ifBlank { actor?.optString("uid").orEmpty() }
            .ifBlank { who }
        show(context, tag, who, what, face)
    }

    /**
     * Anything missed while this phone was not listening.
     *
     * A push is not a delivery guarantee: the phone can be off, out of signal, or
     * have the app's notifications turned off at the system level, and the alert is
     * then gone for good even though the server kept the row. So the list is read
     * when the app comes forward, and anything newer than the last one shown is
     * shown now.
     *
     * The marker is a timestamp rather than a set of ids: ids would grow forever,
     * and the question being asked - "is this newer than the last one I showed" -
     * is exactly what a timestamp answers.
     */
    suspend fun catchUp(context: Context) {
        // Coming to the front fires more than once - the effect that registers the
        // lifecycle observer also runs it immediately - and two reads a moment
        // apart is one read with the work done twice.
        synchronized(recent) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastCatchUp < CATCH_UP_GAP_MS) return
            lastCatchUp = now
        }
        val token = SessionStore(context).token ?: return
        val result = NotificationsApi.list(
            token = token,
            type = NotificationsApi.TYPE_SCREENSHOT,
            limit = CATCH_UP
        )
        if (result is ApiResult.Failure) {
            Log.w(TAG, "Could not read the notification list: ${result.message}")
            return
        }
        val items = (result as ApiResult.Success).data.items
        Log.i(TAG, "catch-up: " + items.size + " screenshot alert(s) on the server")
        if (items.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = prefs.getLong(KEY_SEEN_AT, 0L)
        val newest = items.maxOf { it.createdAt }
        // First run on a phone that already has history: the marker is set without
        // showing anything. Announcing a month of old alerts the first time this
        // code ships would be the app shouting about things already lived through.
        if (since == 0L) {
            prefs.edit().putLong(KEY_SEEN_AT, newest).apply()
            return
        }
        val fresh = items.filter { it.createdAt > since }.sortedBy { it.createdAt }
        Log.i(TAG, "catch-up: " + fresh.size + " newer than the last shown")
        fresh.forEach { show(context, it) }
        if (newest > since) prefs.edit().putLong(KEY_SEEN_AT, newest).apply()
    }

    private var lastCatchUp = 0L
    private const val CATCH_UP_GAP_MS = 5_000L

    /** Marks everything up to now as already announced. */
    fun markSeen(context: Context, at: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (at > prefs.getLong(KEY_SEEN_AT, 0L)) {
            prefs.edit().putLong(KEY_SEEN_AT, at).apply()
        }
    }

    /**
     * Takes away the copy Firebase put up by itself.
     *
     * When a push carries a `notification` block and the app is in the background,
     * the system draws it without the app ever being asked - so the app's own
     * announcement of the same event, when it comes forward and reads the list,
     * lands beside it as a second notification. Nothing inside the app can stop
     * the first one being drawn; what it can do is take it down when it posts its
     * own, which is this.
     *
     * Recognised by what it is not: every notification this app draws is on one of
     * its own three channels, so anything of ours on another channel came from the
     * system, and matching the text makes sure it is this same event rather than a
     * message that happened to arrive the same way.
     *
     * The real fix is on the server - send this event data-only, as `call:incoming`
     * already is, and the system draws nothing at all.
     */
    private fun clearTheOneFirebaseDrew(context: Context, text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val ours = setOf(
            IqMessagingService.CHANNEL_ALERTS,
            IqMessagingService.CHANNEL_MESSAGES,
            IqMessagingService.CHANNEL_MISSED_CALLS
        )
        runCatching {
            manager.activeNotifications.forEach { live ->
                if (live.notification.channelId in ours) return@forEach
                val body = live.notification.extras
                    ?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                if (body.isNotBlank() && body.equals(text, ignoreCase = true)) {
                    Log.i(TAG, "taking down the copy the system drew")
                    manager.cancel(live.tag, live.id)
                }
            }
        }.onFailure { Log.w(TAG, "Could not look at what is already showing", it) }
    }

    /**
     * Whether this is the first time this alert has been seen, recently.
     *
     * Synchronized because the three routes are three threads: a push arrives on
     * Firebase's, the socket on its own, and the catch-up on whatever the app was
     * using when it came forward.
     */
    private fun firstSight(key: String): Boolean = synchronized(recent) {
        val now = android.os.SystemClock.elapsedRealtime()
        recent.entries.removeAll { now - it.value > SAME_ALERT_MS }
        if (recent.containsKey(key)) return false
        recent[key] = now
        true
    }

    private suspend fun show(context: Context, row: IqNotification) {
        show(
            context = context,
            tag = row.id.ifBlank { row.headline },
            who = row.headline,
            what = row.text.ifBlank { "took a screenshot of your profile" },
            faceUrl = row.actor?.profilePicture.orEmpty()
        )
    }

    private suspend fun show(
        context: Context,
        tag: String,
        who: String,
        what: String,
        faceUrl: String
    ) {
        if (!firstSight(tag)) {
            Log.i(TAG, "already shown: $tag")
            return
        }
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) {
            Log.i(TAG, "Not shown: notifications are off for this app")
            return
        }
        IqMessagingService.ensureChannels(context)
        clearTheOneFirebaseDrew(context, what)
        val face = faceUrl.takeIf { it.isNotBlank() }
            ?.let { RemoteImage.load(it) }
            ?.let { image -> runCatching { image.asAndroidBitmap() }.getOrNull() }

        val alert = NotificationCompat.Builder(context, IqMessagingService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(who)
            .setContentText(what)
            .setStyle(NotificationCompat.BigTextStyle().bigText(what))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .apply { face?.let { setLargeIcon(it) } }
            .build()

        Log.i(TAG, "showing alert from " + who)
        runCatching { NotificationManagerCompat.from(context).notify(tag, ID, alert) }
            .onFailure { Log.w(TAG, "Could not post the screenshot alert", it) }
    }
}
