package com.iqsocial.iq_message.data.chat

import android.content.Context
import com.iqsocial.iq_message.data.local.ChatStore
import com.iqsocial.iq_message.data.local.MessageRow
import com.iqsocial.iq_message.data.local.SendState
import com.iqsocial.iq_message.data.remote.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import com.iqsocial.iq_message.data.remote.string

/**
 * The thread, out of this phone's own store.
 *
 * What the screen reads. The network no longer hands messages to the chat; it hands
 * them to the database, and the database tells the chat - so a thread that has been
 * opened once opens again with no connection at all, and anything that writes
 * (a fetch, the socket, this phone sending) reaches the screen by the same road.
 *
 * **Rows are keyed by `client_id`.** It is the only identifier a message has for its
 * whole life: a message written offline has no server id yet, and when the server
 * finally answers, writing the reply over the same key *is* the reconciliation that
 * the screen used to do by hand with `indexOfFirst`. A message from somebody else
 * may carry a blank one, and then its server id does the job.
 */
object ChatMessages {

    /**
     * Oldest first, as the thread is drawn, and re-emitted on every write.
     *
     * **`flowOn` is not decoration here.** A screen collects this with
     * `collectAsState`, which collects in the composition's context - the main
     * thread - and a `map` with nothing dispatching it runs where it is collected.
     * So every row of the whole conversation was being turned into a message on
     * the main thread, and [toMessage] parses a JSON payload per row: opening a
     * chat with a few hundred messages in it stalled for about a second, which the
     * frame counter reported as
     *
     *     Choreographer: Skipped 65 frames!
     *     OpenGLRenderer: Davey! duration=1291ms
     *
     * Moved to IO, the database read and the parsing happen off the main thread and
     * the list arrives finished.
     *
     * Conflated as well, because a write is rarely one row: storing a page of fifty
     * messages emits as they land, and only the last of those lists is worth
     * drawing.
     */
    fun thread(
        context: Context,
        conversationId: String,
        limit: Int = PAGE
    ): Flow<List<ChatMessage>> =
        ChatStore.of(context).chats().threadTail(conversationId, limit)
            // Read newest first so the limit means the end of the conversation,
            // and turned round here so the screen gets what it draws: oldest
            // first, with the newest at the bottom.
            .map { rows -> rows.asReversed().map { it.toMessage() } }
            .flowOn(Dispatchers.IO)
            .conflate()

    /**
     * How many messages a screen opens with, and grows by.
     *
     * Twenty-five is about a screenful and a half on a tall phone: enough that the
     * thread looks like a thread before anything else arrives, few enough that the
     * work of opening is the same whether the conversation is a week old or three
     * years old.
     */
    const val PAGE = 25

    suspend fun store(context: Context, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        ChatStore.of(context).chats().putMessages(messages.map { it.toRow() })
    }

    suspend fun store(context: Context, message: ChatMessage) {
        ChatStore.of(context).chats().putMessage(message.toRow())
    }

    /** The highest `seq` held, which is where `?after_seq=` starts. */
    suspend fun highestSeq(context: Context, conversationId: String): Int =
        ChatStore.of(context).chats().highestSeq(conversationId)

    /** The lowest `seq` held, which is where `?before_seq=` starts. */
    suspend fun lowestSeq(context: Context, conversationId: String): Int =
        ChatStore.of(context).chats().lowestSeq(conversationId)

    /**
     * Stores [message] only if no row holds it yet.
     *
     * For a message that came from somewhere other than the thread - search, the
     * shared-media list. Those copies can be thinner than the thread's and can lack
     * the client id the thread's row is keyed by, so writing one over a message the
     * thread already has would either strip it or put the same message in twice.
     */
    suspend fun storeIfMissing(context: Context, message: ChatMessage) {
        if (message.id.isNotBlank() &&
            ChatStore.of(context).chats().messageByServerId(message.id) != null
        ) return
        store(context, message)
    }

    /** How wide the thread's window must be for [message] to be in it. */
    suspend fun windowFor(context: Context, message: ChatMessage): Int =
        ChatStore.of(context).chats()
            .countFrom(message.conversationId, message.createdAt, message.seq)

    /** Anything left mid-send when the app stopped is put back in the queue. */
    suspend fun recoverInterrupted(context: Context) {
        ChatStore.of(context).chats().requeueInterrupted()
    }

    /** Signing out: this account's conversations are not the next one's. */
    suspend fun wipe(context: Context) {
        ChatStore.of(context).chats().wipe()
    }
}

/* -- between the wire and the table ------------------------------------------ */

/**
 * The row key: the client id, or the server's when there is no client id.
 *
 * Both are needed because both happen. Messages this app sent always have a client
 * id; messages from another account may have been sent by a client that did not
 * bother with one, and a blank key would make every such message the same row.
 */
internal fun ChatMessage.rowKey(): String = clientId.ifBlank { id }

/**
 * The whole message as it arrived, kept alongside the columns.
 *
 * Quotes, media, reactions and link previews are all nested, and a table shaped like
 * that would need four joins to draw one bubble. Storing the original JSON and
 * parsing it back with the same code that parses it from the network means the
 * stored message and the received message cannot drift apart - there is no second
 * serialiser to keep in step.
 *
 * Blank for a message this phone wrote and the server has not seen, which has
 * nothing nested in it yet; those are rebuilt from the columns.
 */
internal fun ChatMessage.toRow(): MessageRow = MessageRow(
    clientId = rowKey(),
    serverId = id,
    conversationId = conversationId,
    seq = seq,
    senderUid = senderUid,
    type = type,
    text = text,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedAt = editedAt,
    deletedAt = deletedAt,
    payload = raw,
    sendState = when {
        failed -> SendState.FAILED
        pending -> SendState.QUEUED
        else -> SendState.SENT
    }
)

/* -- changes this phone makes before the server knows ------------------------- */

/**
 * The stored JSON with a change made to it, or left alone if it will not parse.
 *
 * The payload is "the message as the API sent it" right up until this phone changes
 * something, and from then until the server answers it is the best account of the
 * message there is. Keeping local changes out of it is not an option: [toMessage]
 * reads the payload in preference to the columns, so an edit that touched only the
 * column would never reach the screen.
 *
 * Blank for a message of this phone's own that has never been sent. Those have no
 * JSON to amend and are drawn from the columns instead.
 */
private fun String.rewritten(change: (JSONObject) -> Unit): String {
    if (isBlank()) return this
    val json = runCatching { JSONObject(this) }.getOrNull() ?: return this
    change(json)
    return json.toString()
}

/** New words, in both of the places the words are kept. */
internal fun MessageRow.withText(text: String, editedAt: Long): MessageRow = copy(
    text = text,
    editedAt = editedAt,
    payload = payload.rewritten { it.put("text", text).put("edited_at", editedAt) }
)

/**
 * This person's reaction put on, swapped, or taken off with a null [emoji].
 *
 * Their old one goes whatever happens next, because the server allows one reaction
 * per member and the screen counts on that.
 */
internal fun MessageRow.withReaction(uid: String, emoji: String?): MessageRow = copy(
    payload = payload.rewritten { json ->
        val kept = JSONArray()
        val existing = json.optJSONArray("reactions")
        for (i in 0 until (existing?.length() ?: 0)) {
            val one = existing?.optJSONObject(i) ?: continue
            if (one.string("uid") != uid) kept.put(one)
        }
        if (emoji != null) {
            kept.put(
                JSONObject()
                    .put("uid", uid)
                    .put("emoji", emoji)
                    .put("created_at", System.currentTimeMillis())
            )
        }
        json.put("reactions", kept)
    }
)

/** Which emoji this person has on the message, if any. */
internal fun MessageRow.reactionOf(uid: String): String? =
    payload.takeIf { it.isNotBlank() }
        ?.let { runCatching { JSONObject(it).optJSONArray("reactions") }.getOrNull() }
        ?.let { arr ->
            (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { it.string("uid") == uid }
                ?.string("emoji")
                ?.takeIf { it.isNotBlank() }
        }

/**
 * The message as one that is no longer there.
 *
 * A tombstone rather than a removal, which is what the server will send when the
 * delete reaches it: the thread query already leaves deleted rows out, so the
 * difference is invisible on screen and tells the truth in the table.
 */
internal fun MessageRow.asDeleted(at: Long): MessageRow = copy(
    deletedAt = at,
    payload = payload.rewritten { it.put("deleted_at", at) }
)

private fun MessageRow.toMessage(): ChatMessage {
    val parsed = payload.takeIf { it.isNotBlank() }
        ?.let { runCatching { ChatMessage.from(JSONObject(it)) }.getOrNull() }

    // A message of this phone's own, written before the server ever saw it. There is
    // no payload to parse, so the columns are the message.
    val message = parsed ?: ChatMessage(
        id = serverId,
        conversationId = conversationId,
        seq = seq,
        senderUid = senderUid,
        clientId = clientId,
        type = type,
        text = text,
        replyTo = null,
        forwarded = false,
        reactions = emptyList(),
        media = null,
        call = null,
        linkPreview = null,
        editedAt = editedAt,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // The send state is this phone's, not the server's, so it is put back on top of
    // whatever the payload said.
    return message.copy(
        pending = sendState == SendState.QUEUED || sendState == SendState.SENDING,
        failed = sendState == SendState.FAILED
    )
}
