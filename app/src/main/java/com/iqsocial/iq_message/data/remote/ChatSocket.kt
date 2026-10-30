package com.iqsocial.iq_message.data.remote

import android.util.Log
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.profile.PresenceRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The messenger's realtime channel: Socket.IO v4 on the same host.
 *
 * Verified against the server directly:
 *
 * - it is mounted at `/socket.io/` and speaks **WebSocket only** — the handshake
 *   reports `upgrades: []` and a polling request is refused with
 *   `{"code":0,"message":"Transport unknown"}`, so the transport is pinned here
 *   rather than left to negotiate;
 * - authentication is the bearer token in the Socket.IO **auth payload**. Without
 *   it the server answers `You are not authorized` with a 401 and closes.
 *
 * Sending still goes over HTTP. `message:send` exists and does the same thing, but
 * the HTTP route returns the stored message and is idempotent on `client_id`,
 * which is what makes a retry safe; the socket is used for what only it can do,
 * which is hearing about everything else.
 */
object ChatSocket {

    sealed interface Event {
        /** A message arrived in some conversation. */
        data class New(val message: ChatMessage) : Event

        /** An existing message changed: edited, deleted, reacted to, preview added. */
        data class Updated(val message: ChatMessage) : Event

        /** Someone's delivered/read position moved. */
        data class ReceiptChanged(val receipt: Receipt) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events

    /** Observable so the UI can show that realtime is down. */
    var connected by mutableStateOf(false)
        private set

    private var socket: Socket? = null
    private var currentToken: String? = null

    /** Idempotent: reconnecting with the same token leaves the live socket alone. */
    fun connect(token: String) {
        if (token.isBlank()) return
        if (socket != null && currentToken == token) {
            if (socket?.connected() != true) socket?.connect()
            return
        }
        disconnect()
        currentToken = token

        val options = IO.Options.builder()
            // The server offers no polling fallback, so asking for one only
            // produces a failed request before the socket comes up.
            .setTransports(arrayOf(WebSocket.NAME))
            .setAuth(mapOf("token" to token))
            .setReconnection(true)
            .build()

        socket = IO.socket(MessengerConfig.BASE_URL, options).apply {
            on(Socket.EVENT_CONNECT) {
                connected = true
                // A subscription lives on the connection and dies with it. The
                // client reconnects by itself after a tunnel or a handover, and
                // without this it would come back attached to nothing - presence
                // frozen at whatever it was when the line dropped, with no sign.
                resendPresenceWatch()
            }
            on(Socket.EVENT_DISCONNECT) { connected = false }
            on(Socket.EVENT_CONNECT_ERROR) { connected = false }

            on("message:new") { args -> args.firstObject()?.let { emitMessage(it, ::New) } }
            on("message:updated") { args -> args.firstObject()?.let { emitMessage(it, ::Updated) } }
            on("message:receipt") { args ->
                args.firstObject()?.let { _events.tryEmit(Event.ReceiptChanged(Receipt.from(it))) }
            }

            // Calls go straight to the centre rather than out as events. They are not
            // something a screen subscribes to while it happens to be open - a call
            // can arrive with nothing open at all, and the same events come by push
            // when the app is away. One handler for both keeps them from diverging.
            on("call:incoming") { args ->
                args.firstObject()?.optJSONObject("call")
                    ?.let { CallCenter.ringing(ChatCall.from(it)) }
            }
            // Presence, under whatever name it arrives.
            //
            // `presence:subscribe` is named in the API description and nothing else
            // about the socket is written down anywhere the server publishes - no
            // AsyncAPI document, no socket page, only that one sentence. So the
            // name the server answers on is an inference from this API's own habit
            // (`message:updated`, `call:updated`, `call:accepted`), and the cheap
            // insurance is to take all of the plausible ones rather than pick one
            // and be silently wrong.
            PRESENCE_EVENTS.forEach { name ->
                on(name) { args -> presenceUpdate(name, args) }
            }

            on("call:accepted") { args -> callState(args) }
            on("call:updated") { args -> callState(args) }
            on("call:ended") { args -> callState(args) }

            connect()
        }
    }

    /**
     * Asks the server to keep this device told about these accounts.
     *
     * Remembered as well as sent, for the reconnect above. Only new ids are sent:
     * the set is small - the people whose chats are open - and re-sending the whole
     * of it on every chat opened would be chatter for nothing.
     *
     * Quiet when the socket is down. The screen that wanted this has already asked
     * over HTTP and has an answer; this only keeps it fresh, and it will be sent
     * for real the moment the connection comes back.
     */
    fun watchPresence(uids: List<String>) {
        val fresh = synchronized(watching) {
            uids.filter { it.isNotBlank() && watching.add(it) }
        }
        if (fresh.isNotEmpty()) sendPresenceWatch(fresh)
    }

    private fun resendPresenceWatch() {
        val all = synchronized(watching) { watching.toList() }
        if (all.isNotEmpty()) sendPresenceWatch(all)
    }

    private fun sendPresenceWatch(uids: List<String>) {
        val live = socket ?: return
        if (!live.connected()) return
        live.emit("presence:subscribe", JSONObject().put("uids", JSONArray(uids)))
    }

    /**
     * One or many, bare or wrapped - whatever shape it comes in.
     *
     * The same tolerance `emitMessage` applies to messages, and for a better reason
     * here: the shape of this event is not documented at all, so the parse is built
     * to accept the three it could reasonably be rather than to assume one.
     *
     * Logged, and that is deliberate. If the guessed event name is wrong this does
     * nothing, and a feature that does nothing in silence is the hardest kind of
     * bug to find - so when it does fire, it says so, and the absence of that line
     * is the answer to "did the name work".
     */
    private fun presenceUpdate(event: String, args: Array<Any>) {
        val json = args.firstObject() ?: return
        val array = json.optJSONArray("items") ?: json.optJSONArray("presence")
        val updates = if (array != null) {
            array.mapObjects(Presence::from)
        } else {
            listOf(Presence.from(json.optJSONObject("presence") ?: json))
        }
        val real = updates.filter { it.uid.isNotBlank() }
        if (real.isEmpty()) return
        Log.i(TAG, "presence on '" + event + "' for " + real.size + " account(s)")
        PresenceRepository.remember(real)
    }

    private fun callState(args: Array<Any>) {
        args.firstObject()?.optJSONObject("call")
            ?.let { CallCenter.updated(ChatCall.stateFrom(it)) }
    }

    fun disconnect() {
        socket?.apply {
            off()
            disconnect()
            close()
        }
        socket = null
        currentToken = null
        connected = false
        synchronized(watching) { watching.clear() }
    }

    /**
     * Tells the server this device has received messages up to [seq] — the sender's
     * second tick. The spec prefers this over the HTTP route while the socket is up.
     */
    /** Returns false when the socket could not carry it, so the caller can fall back. */
    fun reportDelivered(conversationId: String, seq: Int): Boolean {
        val live = socket ?: return false
        if (!live.connected()) return false
        live.emit(
            "message:delivered",
            JSONObject().put("conversation_id", conversationId).put("seq", seq)
        )
        return true
    }

    /* -- helpers ------------------------------------------------------------- */

    private fun New(message: ChatMessage): Event = Event.New(message)
    private fun Updated(message: ChatMessage): Event = Event.Updated(message)

    private fun emitMessage(json: JSONObject, wrap: (ChatMessage) -> Event) {
        // The payload is either the message itself or an envelope around it; both
        // shapes have been seen on this kind of server, so accept either.
        val body = json.optJSONObject("message") ?: json
        _events.tryEmit(wrap(ChatMessage.from(body)))
    }

    private fun Array<out Any?>.firstObject(): JSONObject? =
        firstOrNull() as? JSONObject

    /** Who this device has asked to be told about, kept for the next reconnect. */
    private val watching = mutableSetOf<String>()

    private const val TAG = "ChatSocket"

    /**
     * The names presence could arrive under.
     *
     * In the order they are likely: this API says `updated` for a thing that
     * changed (`message:updated`, `call:updated`), and the others are here because
     * listening costs nothing and being wrong costs the feature.
     */
    private val PRESENCE_EVENTS = listOf(
        "presence:updated",
        "presence:changed",
        "presence:update",
        "presence"
    )
}

/** How far one member has received and read a conversation. */
data class Receipt(
    val conversationId: String,
    val uid: String,
    val deliveredSeq: Int,
    val readSeq: Int
) {
    companion object {
        fun from(json: JSONObject): Receipt = Receipt(
            conversationId = json.optString("conversation_id"),
            uid = json.optString("uid"),
            deliveredSeq = json.optInt("delivered_seq"),
            readSeq = json.optInt("read_seq")
        )
    }
}
