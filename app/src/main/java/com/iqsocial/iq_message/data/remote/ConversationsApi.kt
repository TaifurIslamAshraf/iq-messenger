package com.iqsocial.iq_message.data.remote

import android.net.Uri
import org.json.JSONObject

/**
 * The conversation half of the messenger API.
 *
 * Paths, parameter names and defaults follow the published spec exactly — `folder`
 * is `inbox`/`archived`, paging is a `cursor` carried from `next_cursor`, and the
 * read/delivered calls take a `seq`, not a message id.
 */
object ConversationsApi {

    /** `inbox` or `archived`, as the spec spells them. */
    enum class Folder(val value: String) { INBOX("inbox"), ARCHIVED("archived") }

    /** The caller's chat list, newest message first. */
    suspend fun list(
        token: String,
        folder: Folder = Folder.INBOX,
        cursor: String? = null,
        limit: Int? = null
    ): ApiResult<ConversationPage> {
        val path = buildString {
            append("/v1/conversations?folder=").append(folder.value)
            cursor?.let { append("&cursor=").append(Uri.encode(it)) }
            limit?.let { append("&limit=").append(it) }
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL)
            .mapTo(ConversationPage::from)
    }

    /** One conversation as the caller sees it. */
    suspend fun get(token: String, id: String): ApiResult<ChatConversation> =
        ApiClient.get("/v1/conversations/${Uri.encode(id)}", token, MessengerConfig.BASE_URL)
            .mapTo(ChatConversation::from)

    /**
     * Conversations with someone whose name has a word starting with [query].
     *
     * The spec is specific that this matches on a **word prefix** of the peer's
     * name, so it is the server's search, not a filter over the loaded page.
     */
    suspend fun search(
        token: String,
        query: String,
        limit: Int? = null
    ): ApiResult<List<ChatConversation>> {
        val path = buildString {
            append("/v1/conversations/search?q=").append(Uri.encode(query))
            limit?.let { append("&limit=").append(it) }
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL)
            .mapTo { json -> json.optJSONArray("items").mapObjects(ChatConversation::from) }
    }

    /**
     * Delete for me: hides the history so far and drops the conversation off the
     * list until a new message arrives. The other side keeps theirs.
     */
    suspend fun deleteForMe(token: String, id: String): ApiResult<Unit> =
        ApiClient.delete(
            "/v1/conversations/" + Uri.encode(id),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }

    /** Opens the private conversation with a person, creating it the first time. */
    suspend fun open(token: String, uid: String): ApiResult<ChatConversation> =
        ApiClient.post(
            "/v1/conversations",
            JSONObject().put("uid", uid),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatConversation::from)

    /** Unread totals for the app badge; muted and archived conversations don't count. */
    /**
     * Lets the other person in this chat call you, or stops them.
     *
     * Off by default for everybody new, which is the point of it: a stranger who
     * can write to you cannot also make your phone ring until you say so. The
     * answer comes back as the whole conversation, with [ConversationAccess.peerCanCall]
     * set - and both phones are told over the socket, so their call button changes
     * at the same moment this switch does.
     *
     * This is the only thing that decides who may call: there is no account-wide
     * setting on top of it.
     */
    suspend fun allowCalls(token: String, id: String, allow: Boolean): ApiResult<ChatConversation> =
        ApiClient.put(
            "/v1/conversations/${Uri.encode(id)}/calls",
            JSONObject().put("allow", allow),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatConversation::from)

    /**
     * Lets the other person message freely, or stops them sending anything until
     * it is turned back on - their side then looks like a request that waits. On,
     * with their message request open, is the same as accepting it.
     */
    suspend fun allowMessages(token: String, id: String, allow: Boolean): ApiResult<ChatConversation> =
        ApiClient.put(
            "/v1/conversations/${Uri.encode(id)}/messages",
            JSONObject().put("allow", allow),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatConversation::from)

    /**
     * Asks the other person to allow calls. The request is a `call_request` message
     * in the chat, so it is pushed to them like any message. [clientId] makes a
     * retry store it once. The message is null when an earlier request still waits.
     */
    suspend fun askToCall(token: String, id: String, clientId: String): ApiResult<CallRequestSent> =
        ApiClient.post(
            "/v1/conversations/${Uri.encode(id)}/calls/request",
            JSONObject().put("client_id", clientId),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { json ->
            CallRequestSent(
                conversation = ChatConversation.from(json.optJSONObject("conversation") ?: JSONObject()),
                message = json.optJSONObject("message")?.let(ChatMessage::from)
            )
        }

    /** "Not now" to their call request. They are told, and may ask again. */
    suspend fun declineCall(token: String, id: String): ApiResult<ChatConversation> =
        answer(token, id, "calls/decline")

    suspend fun unreadCount(token: String): ApiResult<UnreadCount> =
        ApiClient.get("/v1/conversations/unread-count", token, MessengerConfig.BASE_URL)
            .mapTo { UnreadCount(it.optInt("conversations"), it.optInt("messages")) }

    /**
     * Marks read up to [seq], or everything when [seq] is null. Also clears a
     * "marked unread" flag.
     */
    suspend fun markRead(token: String, id: String, seq: Int? = null): ApiResult<Unit> =
        ApiClient.post(
            "/v1/conversations/${Uri.encode(id)}/read",
            JSONObject().apply { seq?.let { put("seq", it) } },
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }

    /**
     * Reports that this device received messages up to [seq] — the sender's ✓✓.
     *
     * The spec notes that with a socket connected you send `message:delivered`
     * instead; this HTTP form is what a polling client uses.
     */
    suspend fun markDelivered(token: String, id: String, seq: Int): ApiResult<Unit> =
        ApiClient.post(
            "/v1/conversations/${Uri.encode(id)}/delivered",
            JSONObject().put("seq", seq),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }

    /* -- list management ------------------------------------------------------ */

    /** At most five pinned conversations; the server answers 409 past that. */
    suspend fun pin(token: String, id: String): ApiResult<Unit> = act(token, id, "pin")

    suspend fun unpin(token: String, id: String): ApiResult<Unit> = act(token, id, "unpin")

    suspend fun archive(token: String, id: String): ApiResult<Unit> = act(token, id, "archive")

    suspend fun unarchive(token: String, id: String): ApiResult<Unit> =
        act(token, id, "unarchive")

    suspend fun unmute(token: String, id: String): ApiResult<Unit> = act(token, id, "unmute")

    /** Marks unread; reading the conversation clears it again. */
    suspend fun markUnread(token: String, id: String): ApiResult<Unit> =
        act(token, id, "unread")

    /** [duration] is the spec's mute window: `8h`, `1w` or `always`. */
    suspend fun mute(token: String, id: String, duration: String): ApiResult<Unit> =
        ApiClient.post(
            "/v1/conversations/${Uri.encode(id)}/mute",
            JSONObject().put("duration", duration),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }

    /* -- message requests ----------------------------------------------------- */

    /**
     * Lets the person who wrote to this account write freely, and puts them on this
     * account's people. Also works on a request rejected earlier.
     *
     * 409 when there is no request to this account here: nobody has written yet,
     * this account is the one that asked, or it was answered already.
     */
    suspend fun accept(token: String, id: String): ApiResult<ChatConversation> =
        answer(token, id, "accept")

    /**
     * Turns a request down. The other person is never told and keeps waiting; only
     * this account's own devices hear of it. Safe to repeat, and can be undone with
     * [accept].
     */
    suspend fun reject(token: String, id: String): ApiResult<ChatConversation> =
        answer(token, id, "reject")

    private suspend fun answer(
        token: String,
        id: String,
        action: String
    ): ApiResult<ChatConversation> =
        ApiClient.post(
            "/v1/conversations/${Uri.encode(id)}/$action",
            JSONObject(),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatConversation::from)

    private suspend fun act(token: String, id: String, action: String): ApiResult<Unit> =
        ApiClient.post(
            "/v1/conversations/${Uri.encode(id)}/$action",
            JSONObject(),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }
}

/** Maps a successful envelope's payload, carrying the server's message through. */
internal inline fun <T> ApiResult<JSONObject>.mapTo(
    transform: (JSONObject) -> T
): ApiResult<T> = when (this) {
    is ApiResult.Failure -> this
    is ApiResult.Success -> ApiResult.Success(transform(data), message)
}

/** What asking to call leaves behind: the conversation, and the request message unless one was already waiting. */
data class CallRequestSent(val conversation: ChatConversation, val message: ChatMessage?)
