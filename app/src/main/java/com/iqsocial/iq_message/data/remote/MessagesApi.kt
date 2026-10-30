package com.iqsocial.iq_message.data.remote

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reading and sending messages.
 *
 * Paging is by `seq` — a message's position in the conversation — not by time or
 * offset. History walks down with `before_seq`, catching up walks up with
 * `after_seq`, and neither reports *changes* to messages already seen: an edit, a
 * deletion or a reaction leaves `seq` alone, so [changes] is the only way to learn
 * about them.
 */
object MessagesApi {

    /** Newest first. Omit [beforeSeq] for the most recent page. */
    suspend fun history(
        token: String,
        conversationId: String,
        beforeSeq: Int? = null,
        limit: Int? = null
    ): ApiResult<MessagePage> {
        val path = buildString {
            append("/v1/conversations/").append(Uri.encode(conversationId)).append("/messages")
            val params = buildList {
                beforeSeq?.let { add("before_seq=$it") }
                limit?.let { add("limit=$it") }
            }
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo(MessagePage::from)
    }

    /** Oldest first: everything above [afterSeq], for catching up after a gap. */
    suspend fun since(
        token: String,
        conversationId: String,
        afterSeq: Int,
        limit: Int? = null
    ): ApiResult<MessagePage> {
        val path = buildString {
            append("/v1/conversations/").append(Uri.encode(conversationId))
            append("/messages?after_seq=").append(afterSeq)
            limit?.let { append("&limit=").append(it) }
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo(MessagePage::from)
    }

    /**
     * Sends a text message.
     *
     * [clientId] must be a UUID this app generates and **keep** for retries: the
     * server answers 201 when it stores the message and 200 with the stored one
     * when it has seen that id before, so resending after a timeout cannot produce
     * a duplicate.
     */
    suspend fun send(
        token: String,
        conversationId: String,
        clientId: String,
        text: String,
        replyToId: String? = null,
        /** A completed upload; the message then carries the file. */
        mediaId: String? = null
    ): ApiResult<ChatMessage> {
        val body = JSONObject()
            .put("conversation_id", conversationId)
            .put("client_id", clientId)
        // `text` is required on its own but is only a caption alongside a file, so
        // an empty one is left out rather than sent blank.
        if (text.isNotBlank() || mediaId.isNullOrBlank()) body.put("text", text)
        if (!mediaId.isNullOrBlank()) body.put("media_id", mediaId)
        if (!replyToId.isNullOrBlank()) body.put("reply_to_id", replyToId)

        return ApiClient.post("/v1/messages", body, token, MessengerConfig.BASE_URL)
            .mapTo(ChatMessage::from)
    }

    /** Edits your own text message, within the server's 15-minute window. */
    suspend fun edit(token: String, messageId: String, text: String): ApiResult<ChatMessage> =
        ApiClient.patch(
            "/v1/messages/${Uri.encode(messageId)}",
            JSONObject().put("text", text),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatMessage::from)

    /**
     * Edits, deletions, reactions and link previews since [since] (ms).
     *
     * Cursor-paged, not seq-paged: a change does not move a message position, so
     * this cannot be walked with `before_seq`. Keep [MessageChanges.syncedAt] from
     * the last page and send it as `since` next time.
     */
    suspend fun changes(
        token: String,
        conversationId: String,
        since: Long,
        cursor: String? = null,
        limit: Int? = null
    ): ApiResult<MessageChanges> {
        val path = buildString {
            append("/v1/conversations/").append(Uri.encode(conversationId))
            append("/messages/changes?since=").append(since)
            cursor?.let { append("&cursor=").append(Uri.encode(it)) }
            limit?.let { append("&limit=").append(it) }
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo(MessageChanges::from)
    }

    /** Searches one conversation, newest first. */
    suspend fun searchIn(
        token: String,
        conversationId: String,
        query: String,
        beforeSeq: Int? = null,
        limit: Int? = null
    ): ApiResult<MessagePage> {
        val path = buildString {
            append("/v1/conversations/").append(Uri.encode(conversationId))
            append("/messages/search?q=").append(Uri.encode(query))
            beforeSeq?.let { append("&before_seq=").append(it) }
            limit?.let { append("&limit=").append(it) }
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo(MessagePage::from)
    }

    /** What has been shared in a conversation. The server requires [kind]. */
    enum class Shared(val value: String) {
        MEDIA("media"), DOCUMENTS("documents"), LINKS("links")
    }

    suspend fun shared(
        token: String,
        conversationId: String,
        kind: Shared,
        beforeSeq: Int? = null,
        limit: Int? = null
    ): ApiResult<MessagePage> {
        val path = buildString {
            append("/v1/conversations/").append(Uri.encode(conversationId))
            append("/messages/shared?kind=").append(kind.value)
            beforeSeq?.let { append("&before_seq=").append(it) }
            limit?.let { append("&limit=").append(it) }
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo(MessagePage::from)
    }

    /** Searches message text across every conversation, newest first. */
    suspend fun search(
        token: String,
        query: String,
        cursor: String? = null,
        limit: Int? = null
    ): ApiResult<MessageSearchResult> {
        val path = buildString {
            append("/v1/messages/search?q=").append(Uri.encode(query))
            cursor?.let { append("&cursor=").append(Uri.encode(it)) }
            limit?.let { append("&limit=").append(it) }
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL)
            .mapTo(MessageSearchResult::from)
    }

    /** When your message reached each member, and when they read it. */
    suspend fun info(token: String, messageId: String): ApiResult<MessageInfo> =
        ApiClient.get(
            "/v1/messages/" + Uri.encode(messageId) + "/info",
            token,
            MessengerConfig.BASE_URL
        ).mapTo(MessageInfo::from)

    /**
     * Forwards into up to five conversations.
     *
     * [clientId] is required here as it is for sending, and for the same reason:
     * it makes a retry idempotent.
     */
    suspend fun forward(
        token: String,
        messageId: String,
        conversationIds: List<String>,
        clientId: String
    ): ApiResult<Unit> =
        ApiClient.post(
            "/v1/messages/" + Uri.encode(messageId) + "/forward",
            JSONObject()
                .put("conversation_ids", JSONArray(conversationIds.take(5)))
                .put("client_id", clientId),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }

    /**
     * Hides messages from yourself only, up to 100 at a time.
     *
     * `conversation_id` is required alongside the ids; the server rejects the list
     * on its own.
     */
    suspend fun deleteForMe(
        token: String,
        conversationId: String,
        messageIds: List<String>
    ): ApiResult<DeletedForMe> =
        ApiClient.post(
            "/v1/messages/delete-for-me",
            deleteBody(conversationId, messageIds),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(DeletedForMe::from)

    /** Your own messages, for everyone, inside the 48-hour window. */
    suspend fun deleteForEveryone(
        token: String,
        conversationId: String,
        messageIds: List<String>
    ): ApiResult<Unit> =
        ApiClient.post(
            "/v1/messages/delete-for-everyone",
            deleteBody(conversationId, messageIds),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }

    private fun deleteBody(conversationId: String, messageIds: List<String>) =
        JSONObject()
            .put("conversation_id", conversationId)
            .put("message_ids", JSONArray(messageIds.take(100)))

    /** One emoji, replacing whatever you reacted with before. */
    suspend fun react(token: String, messageId: String, emoji: String): ApiResult<Unit> =
        ApiClient.put(
            "/v1/messages/${Uri.encode(messageId)}/reaction",
            JSONObject().put("emoji", emoji),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }

    suspend fun removeReaction(token: String, messageId: String): ApiResult<Unit> =
        ApiClient.delete(
            "/v1/messages/${Uri.encode(messageId)}/reaction",
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }
}
