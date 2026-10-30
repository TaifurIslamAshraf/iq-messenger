package com.iqsocial.iq_message.data.push

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.OpenChat
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.overlay.ChatHeads
import com.iqsocial.iq_message.data.profile.SettingsRepository
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatMessage
import com.iqsocial.iq_message.data.remote.ChatPerson
import com.iqsocial.iq_message.data.remote.ChatSocket
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.data.remote.NotificationPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
/**
 * Message notifications from the socket, for as long as the app is running - in
 * front or not, and for the chat on screen as much as any other.
 *
 * The gap this fills: `message:new` arrives on the socket the whole time the process
 * is alive, including with the app in the background, and until now the only thing
 * listening was the open chat screen. Put the app in the background and the socket
 * went on delivering messages to nobody.
 *
 * So this listens for the whole life of the process and builds the conversation
 * notification from what the socket says.
 *
 * It is not the only route. A dead process has no socket, and that case belongs to
 * the push: `IqMessagingService` takes message pushes back before Firebase can draw
 * its own plain version of them (see its `handleIntent`) and builds the same
 * notification this does. With the app alive both arrive, and whichever is first
 * announces the message - the other finds it already seen.
 */
object SocketAlerts {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listening: Job? = null

    /**
     * Starts listening, once.
     *
     * Called where the socket is connected, because the two have the same lifetime:
     * from signing in until signing out.
     */
    fun start(context: Context) {
        if (listening?.isActive == true) return
        val app = context.applicationContext
        listening = scope.launch {
            ChatSocket.events.collect { event ->
                // One message going wrong must not take the listener with it. An
                // exception here would end this collect for good, and every message
                // after it would go unannounced until the app was restarted.
                if (event is ChatSocket.Event.New) {
                    runCatching { announce(app, event.message) }
                }
            }
        }
    }

    fun stop() {
        listening?.cancel()
        listening = null
        // Names and faces of people the signed-out account was talking to.
        peers.clear()
    }

    /**
     * Who is on the other end of a conversation, remembered.
     *
     * Not a cache for speed. [ChatRepository.conversations] is emptied by
     * `clear()`, which runs on every push the app handles - so the list is reliably
     * there only until the first message arrives, which is precisely when this needs
     * it. Once a peer is known it is kept here, where nothing else empties it.
     */
    private val peers = java.util.Collections.synchronizedMap(mutableMapOf<String, ChatPerson>())

    private suspend fun announce(context: Context, message: ChatMessage) {
        val session = SessionStore(context)
        val myUid = session.load()?.uid.orEmpty()

        // Your own message, arriving back from the server. Announcing it would tell
        // you what you just did.
        if (message.senderUid.isBlank() || message.senderUid == myUid) return

        // Who this is, learned while the list still has it - the invalidation two
        // lines down empties it. Cheap and synchronous, so it costs nothing to do
        // for a message that turns out not to need announcing.
        remember(message.conversationId)

        // Delivered, said from here as well as from the push. Whichever reaches the
        // phone first gives the sender their second tick sooner, and the server
        // takes the same report twice without complaint.
        if (message.seq > 0) {
            ChatSocket.reportDelivered(message.conversationId, message.seq)
        }

        // The chat list and the badge are the server's; this only says they are out
        // of date, so whatever opens next asks again. Emptying it instead is what
        // made the list disappear on every incoming message.
        ChatRepository.invalidate()

        // The same gate the push path uses, and deliberately the same one: whichever
        // of the two arrives first announces the message, and the other finds it
        // already seen. Without this a foreground app would show it twice.
        if (!SeenMessages.firstSight(message.id)) return

        // Who sent it, if it can be found at all.
        //
        // This used to give up when it could not, on the reasoning that a
        // notification from nobody is worse than the plain one Android draws. That
        // reasoning only holds while Android has drawn one - and with the app in the
        // foreground it has not, because it handed the push to this app instead. So
        // giving up there was not falling back to something worse, it was silence.
        //
        // For the same reason a lookup that fails outright costs the name and
        // nothing more. The message is claimed by now, and the push will not
        // announce it a second time.
        val peer = runCatching { peerFor(session.token, message.conversationId) }.getOrNull()

        val preview = SettingsRepository.notificationPreview
        val hide = preview == NotificationPreview.NOTHING
        // The app's own name when there is no name to give - either because the
        // setting hides it or because the sender could not be found. The same
        // stand-in the push path uses, and it has to be something: an empty sender
        // would be titled "New message" over a body reading "New message".
        val anonymous = context.getString(R.string.app_name)
        // A head **instead of** a notification, where there can be one.
        //
        // The two were shown side by side, which is two announcements of one
        // message: the head says who, carries the count and opens the chat on the
        // spot, and the notification underneath it then says the same thing again.
        // Where the reader has allowed the floating window, the head is the better
        // of the two and the only one shown.
        //
        // Not for the conversation already on screen, where a head would point at
        // the screen it is drawn on - and that case falls back to the notification,
        // which the chat clears as it opens.
        val headInstead =
            ChatHeads.allowed(context) && !OpenChat.isOpen(message.conversationId)

        if (headInstead) {
            ChatHeads.show(
                context,
                conversationId = message.conversationId,
                name = if (hide) anonymous else peer?.fullname.orEmpty().ifBlank { anonymous },
                face = if (hide) null else peer?.profilePicture
                    ?.takeIf { it.isNotBlank() }
                    ?.let { RemoteImage.load(it) }
                    ?.let { runCatching { it.asAndroidBitmap() }.getOrNull() }
            )
            // And what it says, beside the head, for a moment.
            ChatHeads.preview(context, message.conversationId, bodyFor(message, preview))
        } else {
            MessageNotifications.show(
                context = context,
                conversationId = message.conversationId,
                // `nothing` hides the sender too. The setting is about what a glance
                // at the phone gives away, and a name is most of that.
                sender = if (hide) anonymous
                else peer?.fullname?.takeIf { it.isNotBlank() } ?: anonymous,
                body = bodyFor(message, preview),
                avatarUrl = if (hide) "" else peer?.profilePicture.orEmpty()
            )
        }
    }

    /** Takes the peer out of the chat list, if it is there, before it is emptied. */
    private fun remember(conversationId: String) {
        if (peers.containsKey(conversationId)) return
        ChatRepository.conversations
            .firstOrNull { it.id == conversationId }
            ?.peer
            ?.let { peers[conversationId] = it }
    }

    /**
     * The other end of a conversation, from wherever it can be had.
     *
     * Three places, cheapest first: what this object already learned, the chat list
     * if it happens to be loaded, and failing both the server. The last one is what
     * makes this dependable - the chat list is empty more often than it looks, and
     * the first message after a `clear()` would otherwise go unannounced.
     *
     * Also asked by the push, for a message push that arrived without its sender.
     */
    internal suspend fun peerFor(token: String?, conversationId: String): ChatPerson? {
        remember(conversationId)
        peers[conversationId]?.let { return it }

        val auth = token?.takeIf { it.isNotBlank() } ?: return null
        val fetched = when (val r = ConversationsApi.get(auth, conversationId)) {
            is ApiResult.Success -> r.data.peer
            is ApiResult.Failure -> null
        }
        return fetched?.also { peers[conversationId] = it }
    }

    /**
     * What the notification says.
     *
     * The socket carries the real text whatever the preview setting is - the setting
     * strips pushes, and this is not one. Honoured here all the same: someone who
     * asked for their messages not to appear on the lock screen did not mean "unless
     * the app happens to be running".
     */
    private fun bodyFor(message: ChatMessage, preview: NotificationPreview): String =
        if (preview != NotificationPreview.NAME_AND_TEXT) {
            "New message"
        } else {
            // The call's own words when it brought them, so the shade reads
            // "Missed voice call" rather than the bare kind of call.
            message.text.takeIf { it.isNotBlank() }
                ?: message.call?.let { "📞 " + it.label }
                ?: when (message.type) {
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
}
