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
