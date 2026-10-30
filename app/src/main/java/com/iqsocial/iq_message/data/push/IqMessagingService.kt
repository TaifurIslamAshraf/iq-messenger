package com.iqsocial.iq_message.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.OpenChat
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.overlay.ChatHeads
import com.iqsocial.iq_message.data.remote.ChatCall
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.data.remote.NotificationPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * What arrives from Firebase.
 *
 * **Every push has the same two data fields**: `event`, saying what happened, and
 * `payload`, the event's body as a JSON string. FCM carries four kilobytes, which is
 * too little for a message, so a push is never the message - it is a pointer to one,
 * with its text cut and no file, quote or reactions. Nothing here is ever stored as
 * a message; the thread fetches the real one.
 *
 * The two kinds behave differently and that difference decides what this class gets:
 *
 * - **Notification pushes** (`message:notification`, `call:missed`) are drawn by the
 *   Firebase SDK itself when the app is away, and [onMessageReceived] sees them only
 *   while the app is in the foreground. Messages are the exception: [handleIntent]
 *   takes them back before the SDK can draw them, so every one arrives here.
 * - **Data-only pushes** (the call events) wake the app either way.
 */
class IqMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Firebase has issued a new token for this install.
     *
     * Not an error and not rare: it happens on restore to a new device, on a data
     * clear, and at Firebase's discretion. The server has to be told, or this phone
     * quietly stops ringing.
     */
    override fun onNewToken(token: String) {
        PushTokens.register(applicationContext, token)
    }

    /**
     * Takes a message push back from Firebase before Firebase draws it.
     *
     * A message push carries a `notification` block, and with the app away the SDK
     * draws that block by itself - a title, a line of text and the app's icon - and
     * [onMessageReceived] is never called. That plain notice was what people saw
     * whenever the app was not in front, instead of the conversation
     * [MessageNotifications] builds: no face, no name, no Like or Reply.
     *
     * The drawing happens in this process, inside `super`, and one flag in the push
     * decides it: `gcm.n.e`, "show this as a notification". With it taken out the
     * push carries on as a data message, which reaches [onMessageReceived] whether
     * the app is open, in the background or not running at all.
     *
     * Messages only, and only those this app is sure to show itself - see
     * [showsItself]. Every other push passes through untouched.
     *
     * Not documented API, so worth a look after a Firebase upgrade. It fails safe:
     * were the flag ever renamed, the SDK would draw its plain notice again, never
     * nothing. The clean fix is the server sending message pushes data-only, after
     * which there is nothing here to remove.
     */
    override fun handleIntent(intent: Intent) {
        if (showsItself(intent)) {
            intent.removeExtra(KEY_DRAW)
            intent.removeExtra(KEY_DRAW_OLD)
        }
        super.handleIntent(intent)
    }

    /**
     * Whether this app will show the push itself, so Firebase need not.
     *
     * Asked before the flag is taken, because taking it is a promise: once it is gone
     * nothing else will draw the push. One the app would turn away - nobody signed
     * in, or no conversation to put it in - stays with Firebase, which draws its
     * plain notice as it always did. A plain notification is worse than a
     * conversation; none at all is worse than either.
     */
    private fun showsItself(intent: Intent): Boolean {
        if (intent.getStringExtra(EXTRA_EVENT) != "message:notification") return false
        if (SessionStore(applicationContext).token.isNullOrBlank()) return false
        val conversationId = intent.getStringExtra(EXTRA_PAYLOAD)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optJSONObject("message")
            ?.optString("conversation_id")
        return !conversationId.isNullOrBlank()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Nothing to say to someone who is not signed in - and nothing we could say
        // it about, since the thread behind it needs the token to load.
        val session = SessionStore(applicationContext)
        if (session.token.isNullOrBlank()) return

        val event = message.data["event"].orEmpty()
        val payload = message.data["payload"]
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return

        when (event) {
            "message:notification" -> onMessage(session, payload)

            // Android shows this one itself while the app is away. In the
            // foreground it shows nothing, so it is said here - on its own channel,
            // which is the point of having two: a missed call should not arrive with
            // a message's sound and heads-up.
            "call:missed" -> {
                ChatRepository.invalidate()
                scope.launch { onMissedCall(payload) }
            }

            // Data-only, and the reason they are: a notification push in the
            // background is drawn by Firebase rather than handed to this method, and
            // a call has to be answered from a phone that was asleep.
            "call:incoming" -> payload.optJSONObject("call")
                ?.let { CallCenter.ringing(ChatCall.from(it), applicationContext) }

            // Somebody else got there first, or it is over. Either way this phone
            // stops - which is the whole purpose of sending these.
            "call:accepted", "call:updated", "call:ended" ->
                payload.optJSONObject("call")
                    ?.let { CallCenter.updated(ChatCall.stateFrom(it)) }

            else -> Unit
        }
    }

    private fun onMessage(session: SessionStore, payload: JSONObject) {
        val body = payload.optJSONObject("message") ?: return
        val messageId = body.optString("id")
        val conversationId = body.optString("conversation_id")

        // Delivery is reported however it arrives. Every message push reaches this
        // method, the app running or not (see [handleIntent]), so this is also where
        // a phone that was asleep gives the sender their second tick.
        val seq = body.optInt("seq")
        val token = session.token
        if (!token.isNullOrBlank() && conversationId.isNotBlank() && seq > 0) {
            scope.launch { ConversationsApi.markDelivered(token, conversationId, seq) }
        }

        // The badge and the chat list both come from the server; this only says they
        // are out of date - said without emptying them, so a list on screen is not
        // blanked while the new one is on its way.
        ChatRepository.invalidate()

        // Last, and that matters. An app that is up is told twice - once on the
        // socket and once here - and this gate is what stops it being announced
        // twice. It used to be the first thing in this method, which was fine while
        // nothing else claimed it; now that the socket does, claiming it up here
        // meant the socket won the race and everything above was skipped, so the
        // sender's second tick and the chat list quietly stopped updating. The gate
        // belongs next to the thing it guards, and nothing else.
        if (!SeenMessages.firstSight(messageId)) return

        val preview = payload.optString("preview")
        val text = body.optString("text")
        val sender = payload.optJSONObject("sender")
        val named = sender?.optString("fullname")?.takeIf { it.isNotBlank() }

        // Waited for, not launched. With the app not running this push is the only
        // reason the process exists, and once this method returns Firebase stops the
        // service - a notification still being built could go down with it. This is
        // Firebase's own worker thread, never the main one, so nothing on screen
        // waits on it, and every wait inside is bounded.
        runBlocking {
            // A push that may name its sender but did not is looked up, so the
            // notification has a face and a name rather than the app's. In a scope
            // of its own because the request underneath cannot be interrupted: the
            // wait gives up on time, and the lookup finishes in the background and
            // is remembered for the next message. A lookup that fails costs the name,
            // never the notification.
            val peer = if (named == null && nameAllowed(preview, text)) {
                runCatching {
                    withTimeoutOrNull(LOOKUP_WAIT_MS) {
                        scope.async { SocketAlerts.peerFor(session.token, conversationId) }
                            .await()
                    }
                }.getOrNull()
            } else {
                null
            }
            // Worked out once, because the head below wants the same three.
            val who = named
                ?: peer?.fullname?.takeIf { it.isNotBlank() }
                ?: getString(R.string.app_name)
            val faceUrl = sender?.optString("profile_picture")?.takeIf { it.isNotBlank() }
                ?: peer?.profilePicture.orEmpty()
            val waiting = payload.optJSONObject("unread")?.optInt("messages") ?: 0

            // The same choice the socket path makes: where a head can be drawn it
            // is the announcement, and the notification would be a second one.
            val headInstead = ChatHeads.allowed(applicationContext) &&
                !OpenChat.isOpen(conversationId)

            if (!headInstead) MessageNotifications.show(
                context = applicationContext,
                conversationId = conversationId,
                sender = who,
                // The server leaves out whatever the preview setting hides, so an
                // empty text is not a missing one - it is the setting doing its job.
                body = text.takeIf { it.isNotBlank() }
                    ?: labelFor(body.optString("type"), preview),
                avatarUrl = faceUrl,
                unread = waiting
            )

            // And a floating head for it.
            //
            // This is the path that runs with the app dead - the socket listener
            // needs a living process to listen in - so without this the heads only
            // appeared while the app was already open, which is when they are least
            // use. A service may add an overlay window: what that needs is the
            // permission, not an activity, and it is how heads have always worked.
            //
            // The face comes from the cache alone. The notification above has just
            // asked for the same picture, so it is usually there; and this is a
            // bounded window before Firebase stops the service, which a download has
            // no business spending. Missing, the head draws the initial on a colour,
            // the same fallback a ringing call makes for the same reason.
            //
            // Skipped for the chat on screen, where a head would point at the screen
            // it is drawn on. Only reachable with the app alive and this push
            // beating the socket to the message, but that race is real.
            if (headInstead) {
                ChatHeads.show(
                    applicationContext,
                    conversationId = conversationId,
                    name = who,
                    face = RemoteImage.peek(faceUrl)
                        ?.let { runCatching { it.asAndroidBitmap() }.getOrNull() },
                    unread = waiting
                )
                ChatHeads.preview(
                    applicationContext,
                    conversationId,
                    text.takeIf { it.isNotBlank() }
                        ?: labelFor(body.optString("type"), preview)
                )
            }
        }
    }

    /**
     * Whether a push that carried no sender may be given one.
     *
     * Only when it says so. `nothing` leaves the sender out on purpose, and looking
     * them up anyway would put on the lock screen exactly what was asked to be kept
     * off it. A push carrying the message's text is one whose setting shows
     * everything, so a name is no secret there either.
     */
    private fun nameAllowed(preview: String, text: String): Boolean =
        preview == NotificationPreview.NAME_AND_TEXT.wire ||
            preview == NotificationPreview.NAME_ONLY.wire ||
            text.isNotBlank()

    private suspend fun onMissedCall(payload: JSONObject) {
        val call = payload.optJSONObject("call") ?: return
        val peer = call.optJSONObject("peer")
        val video = call.optString("type") == "video"
        MessageNotifications.showMissedCall(
            context = applicationContext,
            callId = call.optString("id"),
            who = peer?.optString("fullname")?.takeIf { it.isNotBlank() } ?: "Someone",
            what = if (video) "Missed video call" else "Missed voice call",
            avatarUrl = peer?.optString("profile_picture").orEmpty()
        )
    }

    /** What a message without text is called, when the setting allows naming it. */
    private fun labelFor(type: String, preview: String): String {
        // Compared against the enum's own wire value rather than a second copy of
        // the string, so the settings screen and this cannot drift apart.
        //
        // Deliberately not `NotificationPreview.from(preview)`: that falls back to
        // name_and_text, which is the right default for a settings screen and the
        // wrong one here. A push with no `preview` at all is a push whose setting
        // this side does not know, and the safe reading of not knowing is to say
        // less rather than more.
        if (preview != NotificationPreview.NAME_AND_TEXT.wire) return "New message"
        return when (type) {
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            "voice" -> "🎤 Voice message"
            "audio" -> "🎵 Audio"
            "voice_call" -> "📞 Voice call"
            "video_call" -> "📹 Video call"
            "document" -> "📄 Document"
            "location" -> "📍 Location"
            "contact" -> "👤 Contact"
            else -> "New message"
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_MISSED_CALLS = "missed_calls"

        /** The two fields every push carries, and so the two extras a tap carries. */
        const val EXTRA_EVENT = "event"
        const val EXTRA_PAYLOAD = "payload"

        private const val NOTIFICATION_ID = 1

        /**
         * The flag that has Firebase draw a push itself, under its current name and
         * the one older payloads use. See [handleIntent].
         */
        private const val KEY_DRAW = "gcm.n.e"
        private const val KEY_DRAW_OLD = "gcm.notification.e"

        /** How long a push waits to learn who sent it, when it did not say. */
        private const val LOOKUP_WAIT_MS = 3_000L

        /**
         * Creates both channels if they are not there.
         *
         * Both, at start-up, because a notification naming a channel the app has not
         * created falls back to the manifest's default - so a missed call would
         * arrive on the messages channel, with its sound and its heads-up, and no
         * way for anyone to quieten one without the other.
         *
         * Cheap to repeat: once a channel exists the system keeps whatever settings
         * the person has changed and ignores everything here but the name.
         */
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New messages"
                    enableVibration(true)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MISSED_CALLS,
                    "Missed calls",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Calls you did not pick up" }
            )
        }
    }
}
