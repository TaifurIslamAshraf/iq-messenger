package com.iqsocial.iq_message.data.remote

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * The messenger's own models, named and shaped as the API defines them.
 *
 * Deliberately separate from [com.iqsocial.iq_message.data.Person] and the sample
 * `Conversation`: those exist to drive the mockup, and bending the server's model
 * into them would lose the parts the UI has to honour — `seq`, the delivered/read
 * positions, pinned/muted/archived.
 */

/** A person as the chat API returns them. */
@Immutable
data class ChatPerson(
    val uid: String,
    val fullname: String,
    val profilePicture: String,
    val verified: Int,
    val badgeKey: String
) {
    val isVerified: Boolean get() = verified != 0

    companion object {
        fun from(json: JSONObject): ChatPerson = ChatPerson(
            uid = json.optString("uid"),
            fullname = json.optString("fullname").trim(),
            profilePicture = json.optString("profile_picture"),
            verified = json.optInt("verified"),
            badgeKey = json.optString("badge_key")
        )
    }
}

/** The preview of a conversation's most recent message. */
@Immutable
data class LastMessage(
    val id: String,
    /** Position in the conversation: 1, 2, 3… */
    val seq: Int,
    val senderUid: String,
    val type: String,
    /** Empty for messages without text. */
    val text: String,
    /** Set when the message was deleted for everyone. */
    val deletedAt: Long,
    val createdAt: Long
) {
    val isDeleted: Boolean get() = deletedAt > 0

    companion object {
        fun from(json: JSONObject): LastMessage = LastMessage(
            id = json.optString("id"),
            seq = json.optInt("seq"),
            senderUid = json.optString("sender_uid"),
            type = json.optString("type"),
            text = json.optString("text"),
            deletedAt = json.optLong("deleted_at"),
            createdAt = json.optLong("created_at")
        )
    }
}

@Immutable
data class ChatConversation(
    val id: String,
    /** `private` or `group`. */
    val type: String,
    val peer: ChatPerson?,
    val lastMessage: LastMessage?,
    val lastMessageAt: Long,
    val unreadCount: Int,
    /**
     * How far the other person has got with **your** messages. These are what the
     * tick states are drawn from: sent, delivered (✓✓), read (blue ✓✓).
     */
    val peerDeliveredSeq: Int,
    val peerReadSeq: Int,
    val markedUnread: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val muted: Boolean,
    /** When a timed mute ends; 0 when not muted, or muted for good. */
    val mutedUntil: Long,
    val createdAt: Long
) {
    /** A conversation reads as unread either by count or because it was marked so. */
    val showsUnread: Boolean get() = unreadCount > 0 || markedUnread

    companion object {
        fun from(json: JSONObject): ChatConversation = ChatConversation(
            id = json.optString("id"),
            type = json.optString("type"),
            peer = json.optJSONObject("peer")?.let(ChatPerson::from),
            lastMessage = json.optJSONObject("last_message")?.let(LastMessage::from),
            lastMessageAt = json.optLong("last_message_at"),
            unreadCount = json.optInt("unread_count"),
            peerDeliveredSeq = json.optInt("peer_delivered_seq"),
            peerReadSeq = json.optInt("peer_read_seq"),
            markedUnread = json.optBoolean("marked_unread"),
            pinned = json.optBoolean("pinned"),
            archived = json.optBoolean("archived"),
            muted = json.optBoolean("muted"),
            mutedUntil = json.optLong("muted_until"),
            createdAt = json.optLong("created_at")
        )
    }
}

/** One page of the chat list. [nextCursor] is null on the last page. */
@Immutable
data class ConversationPage(
    val items: List<ChatConversation>,
    val nextCursor: String?
) {
    companion object {
        fun from(json: JSONObject): ConversationPage = ConversationPage(
            items = json.optJSONArray("items").mapObjects(ChatConversation::from),
            nextCursor = json.optString("next_cursor").takeIf {
                it.isNotBlank() && it != "null"
            }
        )
    }
}

/** `{"conversations": n, "messages": n}` from `/v1/conversations/unread-count`. */
@Immutable
data class UnreadCount(val conversations: Int, val messages: Int)


/** The start of the message being replied to. */
@Immutable
data class Quote(
    val id: String,
    val seq: Int,
    val senderUid: String,
    val type: String,
    val text: String,
    val deletedAt: Long
) {
    companion object {
        fun from(json: JSONObject): Quote = Quote(
            id = json.optString("id"),
            seq = json.optInt("seq"),
            senderUid = json.optString("sender_uid"),
            type = json.optString("type"),
            text = json.optString("text"),
            deletedAt = json.optLong("deleted_at")
        )
    }
}

/** At most one per member, so a member's second reaction replaces the first. */
@Immutable
data class Reaction(val uid: String, val emoji: String, val createdAt: Long) {
    companion object {
        fun from(json: JSONObject): Reaction = Reaction(
            uid = json.optString("uid"),
            emoji = json.optString("emoji"),
            createdAt = json.optLong("created_at")
        )
    }
}

@Immutable
data class MessageMedia(
    val id: String,
    /** `image`, `video`, `voice`, `audio` or `document`. */
    val kind: String,
    val mimeType: String,
    val size: Long,
    val fileName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    /** BlurHash to show while the file loads. */
    val placeholder: String,
    /** Voice only: 0-100 per bar. */
    val waveform: List<Int>,
    val url: String,
    val thumbnailUrl: String
) {
    companion object {
        fun from(json: JSONObject): MessageMedia {
            val bars = json.optJSONArray("waveform")
            return MessageMedia(
                id = json.optString("id"),
                kind = json.optString("kind"),
                mimeType = json.optString("mime_type"),
                size = json.optLong("size"),
                fileName = json.optString("file_name"),
                width = json.optInt("width"),
                height = json.optInt("height"),
                durationMs = json.optLong("duration_ms"),
                placeholder = json.optString("placeholder"),
                waveform = if (bars == null) emptyList()
                else (0 until bars.length()).map { bars.optInt(it) },
                url = json.optString("url"),
                thumbnailUrl = json.optString("thumbnail_url")
            )
        }
    }
}

/**
 * The call a `voice_call` or `video_call` message is about.
 *
 * Written by the server when the call **ended**, so every field here is final: this
 * is the record of a call, not a call in progress. One of these is the whole content
 * of its message - a call message carries no text, which is why a thread that did
 * not read this drew an empty bubble for every call ever placed.
 *
 * [missed] is wider than it sounds. The server sets it when the person called never
 * picked up, which covers no answer, a cancelled ring and a busy line alike;
 * [endReason] is what tells those apart for anyone who needs to.
 */
@Immutable
data class MessageCall(
    val id: String,
    val video: Boolean,
    val endReason: CallEnd?,
    val missed: Boolean,
    val answeredAt: Long,
    val endedAt: Long,
    /** Seconds talked, and zero when it was never answered. */
    val duration: Int
) {
    /**
     * The one line a bubble, a chat row or a notification shows for it.
     *
     * Said the same way in all four places on purpose: they are describing the same
     * event, and a call that reads "Missed voice call" in the shade and something
     * else in the thread is two facts where there is one.
     */
    val label: String
        get() {
            val kind = if (video) "video call" else "audio call"
            val titled = kind.replaceFirstChar { it.uppercase() }
            return when {
                missed -> "Missed $kind"
                duration > 0 -> titled + " \u00b7 " + spanOf(duration)
                else -> titled
            }
        }

    companion object {
        fun from(json: JSONObject): MessageCall = MessageCall(
            id = json.optString("id"),
            video = json.optString("type") == "video",
            endReason = CallEnd.of(json.optString("end_reason").takeIf { it.isNotBlank() }),
            missed = json.optBoolean("missed"),
            answeredAt = json.optLong("answered_at"),
            endedAt = json.optLong("ended_at"),
            duration = json.optInt("duration")
        )
    }
}

/** How long it lasted: minutes and seconds, and hours when there were any. */
private fun spanOf(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
    else "%d:%02d".format(minutes, secs)
}

/**
 * A message as the API defines it.
 *
 * [clientId] is the id this app gave it, and the reason sending is safe to retry:
 * the server returns the already-stored message when it sees a `client_id` twice.
 * A locally-created message that has not been acknowledged yet has a blank [id].
 */
@Immutable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    /** Position in the conversation: 1, 2, 3… 0 while still pending. */
    val seq: Int,
    val senderUid: String,
    val clientId: String,
    /**
     * text, image, video, voice, audio, document, location, contact, voice_call,
     * video_call.
     */
    val type: String,
    val text: String,
    val replyTo: Quote?,
    val forwarded: Boolean,
    val reactions: List<Reaction>,
    val media: MessageMedia?,
    /** Set on `voice_call` and `video_call`, and the only content those carry. */
    val call: MessageCall?,
    /**
     * Filled by the server *after* the message is sent, not by the sender.
     *
     * It fetches the page itself and tells everyone over the socket as
     * `message:updated`, which is why a link looks bare for a moment and then grows
     * a card. The compose box asks for its own through `GET /v1/link-preview`; this
     * is the one that belongs to the sent message.
     */
    val linkPreview: LinkPreview?,
    val editedAt: Long,
    val deletedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    /** Local only: set when this app created the message and is waiting on the server. */
    val pending: Boolean = false,
    /** Local only: the send failed and can be retried. */
    val failed: Boolean = false
) {
    val isDeleted: Boolean get() = deletedAt > 0
    val isEdited: Boolean get() = editedAt > 0

    companion object {
        fun from(json: JSONObject): ChatMessage = ChatMessage(
            id = json.optString("id"),
            conversationId = json.optString("conversation_id"),
            seq = json.optInt("seq"),
            senderUid = json.optString("sender_uid"),
            clientId = json.optString("client_id"),
            type = json.optString("type"),
            text = json.optString("text"),
            replyTo = json.optJSONObject("reply_to")?.let(Quote::from),
            forwarded = json.optBoolean("forwarded"),
            reactions = json.optJSONArray("reactions").mapObjects(Reaction::from),
            media = json.optJSONObject("media")?.let(MessageMedia::from),
            call = json.optJSONObject("call")?.let(MessageCall::from),
            linkPreview = json.optJSONObject("link_preview")?.let(LinkPreview::from),
            editedAt = json.optLong("edited_at"),
            deletedAt = json.optLong("deleted_at"),
            createdAt = json.optLong("created_at"),
            updatedAt = json.optLong("updated_at")
        )

        /** The optimistic copy shown the instant Send is tapped. */
        fun pending(
            conversationId: String,
            clientId: String,
            senderUid: String,
            text: String
        ): ChatMessage = ChatMessage(
            id = "",
            conversationId = conversationId,
            seq = 0,
            senderUid = senderUid,
            clientId = clientId,
            type = "text",
            text = text,
            replyTo = null,
            forwarded = false,
            reactions = emptyList(),
            media = null,
            linkPreview = null,
            call = null,
            editedAt = 0,
            deletedAt = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            pending = true
        )
    }
}

/** One page of a conversation. [nextSeq] feeds `before_seq` or `after_seq`. */
@Immutable
data class MessagePage(val items: List<ChatMessage>, val nextSeq: Int?) {
    companion object {
        fun from(json: JSONObject): MessagePage = MessagePage(
            items = json.optJSONArray("items").mapObjects(ChatMessage::from),
            nextSeq = json.optInt("next_seq").takeIf { json.has("next_seq") && it > 0 }
        )
    }
}

/**
 * The reply from `/messages/changes` - deliberately not a [MessagePage].
 *
 * Changes are cursor-paged rather than seq-paged, because an edit or a reaction
 * does not move a message's position. [syncedAt] is the point to resume from: save
 * it off the last page and send it back as `since`.
 */
@Immutable
data class MessageChanges(
    /** Changed messages, oldest change first. */
    val items: List<ChatMessage>,
    val nextCursor: String?,
    val syncedAt: Long
) {
    companion object {
        fun from(json: JSONObject): MessageChanges = MessageChanges(
            items = json.optJSONArray("items").mapObjects(ChatMessage::from),
            nextCursor = json.optString("next_cursor")
                .takeIf { it.isNotBlank() && it != "null" },
            syncedAt = json.optLong("synced_at")
        )
    }
}

/** When one member received a message, and when they read it. */
@Immutable
data class MessageRecipient(val uid: String, val deliveredAt: Long, val readAt: Long) {
    companion object {
        fun from(json: JSONObject): MessageRecipient = MessageRecipient(
            uid = json.optString("uid"),
            deliveredAt = json.optLong("delivered_at"),
            readAt = json.optLong("read_at")
        )
    }
}

/** `/messages/{id}/info`: the message, and where it has got to with each member. */
@Immutable
data class MessageInfo(val message: ChatMessage?, val recipients: List<MessageRecipient>) {
    companion object {
        fun from(json: JSONObject): MessageInfo = MessageInfo(
            message = json.optJSONObject("message")?.let(ChatMessage::from),
            recipients = json.optJSONArray("recipients").mapObjects(MessageRecipient::from)
        )
    }
}

/** A global search hit carries the conversation it was found in. */
@Immutable
data class MessageHit(val conversation: ChatConversation?, val message: ChatMessage?) {
    companion object {
        fun from(json: JSONObject): MessageHit = MessageHit(
            conversation = json.optJSONObject("conversation")?.let(ChatConversation::from),
            message = json.optJSONObject("message")?.let(ChatMessage::from)
        )
    }
}

@Immutable
data class MessageSearchResult(val items: List<MessageHit>, val nextCursor: String?) {
    companion object {
        fun from(json: JSONObject): MessageSearchResult = MessageSearchResult(
            items = json.optJSONArray("items").mapObjects(MessageHit::from),
            nextCursor = json.optString("next_cursor")
                .takeIf { it.isNotBlank() && it != "null" }
        )
    }
}

/** What `delete-for-me` reports back. */
@Immutable
data class DeletedForMe(val messageIds: List<String>) {
    companion object {
        fun from(json: JSONObject): DeletedForMe {
            val ids = json.optJSONArray("message_ids")
            return DeletedForMe(
                if (ids == null) emptyList()
                else (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotBlank) }
            )
        }
    }
}

internal fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }
}
