package com.iqsocial.iq_message.data.remote

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/** `audio` or `video`; video starts with the camera on. */
enum class CallType(val value: String) {
    AUDIO("audio"),
    VIDEO("video");

    companion object {
        fun of(raw: String) = if (raw == VIDEO.value) VIDEO else AUDIO
    }
}

/**
 * Where a call has got to.
 *
 * `ringing` and `ongoing` are live; `ended` is final and says why in
 * [ChatCall.endReason].
 */
enum class CallStatus(val value: String) {
    RINGING("ringing"),
    ONGOING("ongoing"),
    ENDED("ended");

    companion object {
        fun of(raw: String) = entries.firstOrNull { it.value == raw } ?: ENDED
    }
}

/** Why a call stopped. Null while it is still going. */
enum class CallEnd(val value: String) {
    DECLINED("declined"),
    CANCELLED("cancelled"),
    MISSED("missed"),
    HANGUP("hangup"),
    BUSY("busy");

    companion object {
        fun of(raw: String?) = entries.firstOrNull { it.value == raw }
    }
}

/** Where one person has got to within a call. */
@Immutable
data class CallParticipant(
    val uid: String,
    /** `ringing`, `joined`, `left`, `declined`, `missed` or `busy`. */
    val state: String,
    val person: ChatPerson?,
    val invitedBy: String,
    val ringExpiresAt: Long,
    val joinedAt: Long,
    val leftAt: Long
) {
    val ringing: Boolean get() = state == "ringing"
    val joined: Boolean get() = state == "joined"

    companion object {
        fun from(json: JSONObject): CallParticipant = CallParticipant(
            uid = json.optString("uid"),
            state = json.optString("state"),
            person = json.optJSONObject("person")?.let(ChatPerson::from),
            invitedBy = json.optString("invited_by"),
            ringExpiresAt = json.optLong("ring_expires_at"),
            joinedAt = json.optLong("joined_at"),
            leftAt = json.optLong("left_at")
        )
    }
}

/**
 * A call, as the API describes it.
 *
 * Named `ChatCall` rather than `Call` so it cannot be confused with the mockup's
 * `CallRecord`, which the calls tab still draws from.
 */
@Immutable
data class ChatCall(
    val id: String,
    val status: CallStatus,
    val endReason: CallEnd?,
    val answeredAt: Long,
    val endedAt: Long,
    /** `direct` or `group`. */
    val mode: String,
    val participants: List<CallParticipant>,
    val type: CallType,
    /** `outgoing` or `incoming`, from the reader's side. */
    val direction: String,
    /** Who is on the other end. In a group call, whoever started it. */
    val peer: ChatPerson?,
    /**
     * When the ringing gives up, in epoch millis.
     *
     * Honoured by the app rather than waited on: a push can be late or lost, so a
     * ringing screen that waits for `call:ended` can ring for a call that is long
     * over. This is the backstop.
     */
    val ringExpiresAt: Long,
    val createdAt: Long,
    val e2ee: Boolean,
    val missed: Boolean
) {
    val isGroup: Boolean get() = mode == "group"
    val isIncoming: Boolean get() = direction == "incoming"
    val isLive: Boolean get() = status == CallStatus.RINGING || status == CallStatus.ONGOING

    companion object {
        fun from(json: JSONObject): ChatCall = ChatCall(
            id = json.optString("id"),
            status = CallStatus.of(json.optString("status")),
            endReason = CallEnd.of(json.optString("end_reason").takeIf { it.isNotBlank() }),
            answeredAt = json.optLong("answered_at"),
            endedAt = json.optLong("ended_at"),
            mode = json.optString("mode"),
            participants = json.optJSONArray("participants").mapObjects(CallParticipant::from),
            type = CallType.of(json.optString("type")),
            direction = json.optString("direction"),
            peer = json.optJSONObject("peer")?.let(ChatPerson::from),
            ringExpiresAt = json.optLong("ring_expires_at"),
            createdAt = json.optLong("created_at"),
            e2ee = json.optBoolean("e2ee"),
            missed = json.optBoolean("missed")
        )

        /**
         * A `CallState`, which is the same thing with the fixed half left out.
         *
         * It arrives on `call:accepted`, `call:updated` and `call:ended`, where only
         * what changed is sent. [merge] puts it back over the call already in hand.
         */
        fun stateFrom(json: JSONObject): ChatCall = ChatCall(
            id = json.optString("id"),
            status = CallStatus.of(json.optString("status")),
            endReason = CallEnd.of(json.optString("end_reason").takeIf { it.isNotBlank() }),
            answeredAt = json.optLong("answered_at"),
            endedAt = json.optLong("ended_at"),
            mode = json.optString("mode"),
            participants = json.optJSONArray("participants").mapObjects(CallParticipant::from),
            type = CallType.AUDIO,
            direction = "",
            peer = null,
            ringExpiresAt = 0L,
            createdAt = 0L,
            e2ee = false,
            missed = false
        )
    }

    /** Takes the changed half from [state] and keeps the rest of this one. */
    fun merge(state: ChatCall): ChatCall = copy(
        status = state.status,
        endReason = state.endReason,
        answeredAt = state.answeredAt,
        endedAt = state.endedAt,
        mode = state.mode.ifBlank { mode },
        participants = state.participants.ifEmpty { participants }
    )
}

/**
 * Where the audio and video actually go.
 *
 * The call API carries none of it: this is a LiveKit room, and the token is for one
 * person in one room. The spec says it plainly and so does this - it is never to be
 * passed on to anybody.
 */
@Immutable
data class CallConnection(
    val url: String,
    val room: String,
    val token: String,
    val expiresAt: Long
) {
    companion object {
        fun from(json: JSONObject): CallConnection = CallConnection(
            url = json.optString("url"),
            room = json.optString("room"),
            token = json.optString("token"),
            expiresAt = json.optLong("expires_at")
        )
    }
}

/** What starting or answering a call hands back. */
@Immutable
data class CallSession(
    val call: ChatCall,
    val connection: CallConnection,
    /** The call key wrapped for this device, for an encrypted call. */
    val e2eeKey: String = ""
) {
    companion object {
        fun started(json: JSONObject): CallSession = CallSession(
            call = ChatCall.from(json.optJSONObject("call") ?: JSONObject()),
            connection = CallConnection.from(json.optJSONObject("livekit") ?: JSONObject())
        )

        /** Accepting answers with a `CallState`, not a whole `Call`. */
        fun accepted(json: JSONObject, existing: ChatCall?): CallSession {
            val state = ChatCall.stateFrom(json.optJSONObject("call") ?: JSONObject())
            return CallSession(
                call = existing?.merge(state) ?: state,
                connection = CallConnection.from(json.optJSONObject("livekit") ?: JSONObject()),
                e2eeKey = json.optString("e2ee_key")
            )
        }
    }
}

/** One page of the call history. */
@Immutable
data class CallPage(val items: List<ChatCall>, val nextCursor: String?) {
    companion object {
        fun from(json: JSONObject): CallPage = CallPage(
            items = json.optJSONArray("items").mapObjects(ChatCall::from),
            nextCursor = json.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}
