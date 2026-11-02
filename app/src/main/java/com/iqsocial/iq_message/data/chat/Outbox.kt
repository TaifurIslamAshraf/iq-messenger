package com.iqsocial.iq_message.data.chat

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.local.ChatDao
import com.iqsocial.iq_message.data.local.ChatStore
import com.iqsocial.iq_message.data.local.OutboxKind
import com.iqsocial.iq_message.data.local.OutboxRow
import com.iqsocial.iq_message.data.local.SendState
import com.iqsocial.iq_message.data.media.UploadQueue
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatMessage
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.data.remote.MessagesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import com.iqsocial.iq_message.data.remote.string

/**
 * What this phone has decided and the server has not been told yet.
 *
 * The other half of reading from the store. Until now a change was made locally
 * *and* sent in the same breath, so a change that could not happen was a change that
 * never would: the failure was shown, and the only way to try again was to do it a
 * second time by hand. With a queue, deciding and managing to tell the server come
 * apart - which is the whole of what "works offline" means for writing.
 *
 * Everything that alters a message goes through here: sending, editing, deleting,
 * reacting, and reporting how far the thread has been read. Each one is two writes in
 * one transaction - the change to this phone's copy, and the entry saying the server
 * still needs to hear about it - so the two cannot come apart.
 *
 * The queue is drained in order and one at a time. Order is the contract, because the
 * entries are not independent: an edit of a message still waiting to be sent would be
 * an edit of something the server has never heard of. One at a time matters for the
 * same reason - two drains at once would race through that order.
 */
object Outbox {

    private const val TAG = "Outbox"

    /**
     * How many times the server may refuse an entry before it is abandoned.
     *
     * Only refusals count. A phone with no signal is not a server saying no, and
     * burning the tries on a tunnel would throw away a message for being written at
     * the wrong moment. A refusal, though, tends to be the same refusal every time -
     * a message deleted on the other side, a conversation that no longer exists - and
     * without a ceiling that one entry would block every entry behind it for ever.
     */
    private const val MAX_ATTEMPTS = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val draining = Mutex()
    private var runner: Job? = null

    /* -- deciding things ------------------------------------------------------- */

    /**
     * Writes the message and queues the send, together.
     *
     * One transaction, and it is the point of the whole arrangement: either the
     * bubble is on screen **and** something is going to send it, or neither
     * happened. Written separately, the two come apart in exactly the case this
     * exists for - the app being killed between them.
     */
    suspend fun send(
        context: Context,
        conversationId: String,
        clientId: String,
        senderUid: String,
        text: String,
        replyToId: String? = null,
        mediaId: String? = null
    ) {
        val message = ChatMessage.pending(conversationId, clientId, senderUid, text)
        val work = work(
            kind = OutboxKind.SEND,
            conversationId = conversationId,
            clientId = clientId
        ) {
            put("text", text)
            replyToId?.let { put("reply_to_id", it) }
            mediaId?.let { put("media_id", it) }
        }
        ChatStore.of(context).chats().writeAndQueue(message.toRow(), work)
        drain(context)
    }

    /**
     * Changes the words, here first.
     *
     * A message still sitting in the queue is a special case worth having: editing
     * it is not an edit at all but a change of mind about what to send, so the queued
     * send is reworded and the server never hears the first version. Queueing an
     * edit behind the send would mean telling the server something and correcting it
     * a moment later, and if that send were abandoned the edit would be an edit of
     * nothing.
     */
    suspend fun edit(context: Context, message: ChatMessage, text: String) {
        val db = ChatStore.of(context)
        val dao = db.chats()
        val key = message.rowKey()
        db.withTransaction {
            val row = dao.message(key) ?: return@withTransaction
            val unsent = dao.queuedFor(key).firstOrNull { it.kind == OutboxKind.SEND }
            if (unsent != null) {
                dao.reword(unsent.id, parse(unsent.payload).put("text", text).toString())
                // Not marked as edited: as far as anybody else will ever know, this
                // is what was written in the first place.
                dao.putMessage(row.withText(text, row.editedAt))
                return@withTransaction
            }
            dao.writeAndQueue(
                row.withText(text, System.currentTimeMillis()),
                work(OutboxKind.EDIT, message.conversationId, key) {
                    put("text", text)
                    put("message_id", row.serverId)
                }
            )
        }
        drain(context)
    }

    /**
     * Takes a message away, for one person or for everybody.
     *
     * A message the server has never seen is simply dropped, along with the send
     * waiting to carry it - there is nobody to tell. Deleting for everybody leaves a
     * row marked deleted rather than removing it, because that is what the server's
     * own tombstone will say when it arrives, and the thread already knows not to
     * draw those.
     */
    suspend fun remove(context: Context, message: ChatMessage, forEveryone: Boolean) {
        val db = ChatStore.of(context)
        val dao = db.chats()
        val key = message.rowKey()
        db.withTransaction {
            val row = dao.message(key) ?: return@withTransaction
            if (row.serverId.isBlank()) {
                // Never sent. Both kinds of delete mean the same thing here, and
                // neither needs the network: the message existed only on this phone.
                dao.forgetUnsent(key)
                return@withTransaction
            }
            val kind =
                if (forEveryone) OutboxKind.DELETE_FOR_EVERYONE else OutboxKind.DELETE_FOR_ME
            val entry = work(kind, message.conversationId, key) {
                // Carried rather than looked up later, because deleting for yourself
                // takes the row away and with it the only other copy of this id.
                put("message_id", row.serverId)
            }
            if (forEveryone) dao.writeAndQueue(row.asDeleted(System.currentTimeMillis()), entry)
            else dao.dropAndQueue(key, entry)
        }
        drain(context)
    }

    /**
     * Puts a reaction on, swaps it, or takes it off.
     *
     * One per person, so [emoji] being the one already there means taking it off -
     * and that is decided here rather than by the caller, because the stored message
     * is where the current answer lives.
     *
     * @return whether a reaction was added, for a caller that wants to make a sound
     *         about it. Taking one off is not worth a sound.
     */
    suspend fun react(
        context: Context,
        message: ChatMessage,
        myUid: String,
        emoji: String
    ): Boolean {
        val db = ChatStore.of(context)
        val dao = db.chats()
        val key = message.rowKey()
        var added = false
        db.withTransaction {
            val row = dao.message(key) ?: return@withTransaction
            added = row.reactionOf(myUid) != emoji
            val wanted = if (added) emoji else null
            dao.writeAndQueue(
                row.withReaction(myUid, wanted),
                work(
                    kind = if (added) OutboxKind.REACT else OutboxKind.UNREACT,
                    conversationId = message.conversationId,
                    clientId = key
                ) {
                    put("message_id", row.serverId)
                    wanted?.let { put("emoji", it) }
                }
            )
        }
        drain(context)
        return added
    }

    /**
     * Reports how far the thread has been read.
     *
     * One entry per conversation, holding the furthest point reached. Receipts are
     * produced by scrolling, so queueing each one would fill the outbox with rows
     * that each say less than the last - and the server only ever needs the last
     * answer, never the path taken to it.
     */
    suspend fun read(context: Context, conversationId: String, seq: Int) {
        furthest(context, OutboxKind.READ, conversationId, seq) {
            // The badge goes now. Whether the other person knows yet is the queue's
            // business; whether this phone knows is not in question.
            setUnread(conversationId, 0)
        }
    }

    /**
     * Reports that a message arrived here.
     *
     * Queued like everything else, and the lateness is the point rather than a
     * problem with it: "delivered" is not a claim about when, it is the answer to
     * "did it get there at all", and that answer does not expire while the phone is
     * in a tunnel. Sent late it turns one tick into two, which is true; dropped, the
     * other person is left with a message that looks lost for ever.
     */
    suspend fun delivered(context: Context, conversationId: String, seq: Int) {
        furthest(context, OutboxKind.DELIVERED, conversationId, seq)
    }

    /**
     * The chat list marking a whole conversation read, or unread.
     *
     * The two replace each other, and they replace the scroll receipts too: these
     * say what the conversation should look like, so the last decision is the only
     * one worth sending. Queueing both would have the server apply them in order and
     * land wherever the order happened to put it.
     */
    suspend fun markRead(context: Context, conversationId: String) =
        settleReadState(context, conversationId, read = true)

    suspend fun markUnread(context: Context, conversationId: String) =
        settleReadState(context, conversationId, read = false)

    private suspend fun settleReadState(
        context: Context,
        conversationId: String,
        read: Boolean
    ) {
        val db = ChatStore.of(context)
        val dao = db.chats()
        db.withTransaction {
            // Everything that had an opinion about this conversation's read state.
            listOf(
                OutboxKind.READ,
                OutboxKind.MARK_READ,
                OutboxKind.MARK_UNREAD
            ).forEach { kind ->
                dao.queuedOfKind(kind, conversationId).forEach { dao.dequeue(it.id) }
            }
            dao.setUnread(conversationId, if (read) 0 else 1)
            val kind = if (read) OutboxKind.MARK_READ else OutboxKind.MARK_UNREAD
            dao.enqueue(work(kind, conversationId, clientId = "") {})
        }
        drain(context)
    }

    /**
     * Keeps one entry of [kind] per conversation, the one that says the most.
     *
     * Both receipts work this way: they are a high-water mark, not a history, and a
     * queue holding every step to it would be a queue of things already superseded
     * when they were written.
     */
    private suspend fun furthest(
        context: Context,
        kind: String,
        conversationId: String,
        seq: Int,
        also: suspend ChatDao.() -> Unit = {}
    ) {
        if (seq <= 0) return
        val db = ChatStore.of(context)
        val dao = db.chats()
        db.withTransaction {
            val queued = dao.queuedOfKind(kind, conversationId)
            if (seq <= (queued.maxOfOrNull { parse(it.payload).optInt("seq") } ?: 0)) {
                return@withTransaction
            }
            queued.forEach { dao.dequeue(it.id) }
            dao.also()
            dao.enqueue(work(kind, conversationId, clientId = "") { put("seq", seq) })
        }
        drain(context)
    }

    /* -- telling the server ---------------------------------------------------- */

    /**
     * Empties the queue, as far as it can get.
     *
     * Called whenever something might have changed: a new entry, the socket coming
     * back, the app starting. Cheap when there is nothing to do, and safe to call
     * from anywhere - a second call while one is running simply returns.
     */
    fun drain(context: Context) {
        val app = context.applicationContext
        if (runner?.isActive == true) return
        runner = scope.launch {
            // Handed to the system as well, so that what is queued now goes even if
            // the app is closed before there is a network to send it over - which,
            // for something written offline, is the usual way round.
            //
            // Only when there is something to hand over, though. Most drains find an
            // empty queue - every message that arrives asks for one, by way of its
            // read receipt - and scheduling a job to go and discover that would be
            // the app waking itself up to do nothing.
            if (ChatStore.of(app).chats().pending().isNotEmpty()) OutboxWork.scheduleFor(app)
            emptyQueue(app)
        }
    }

    /**
     * The same, waited for.
     *
     * For a caller that has to know when it is done rather than that it has started:
     * a worker handed the job by the system is killed the moment it returns, so
     * launching and walking away would stop the queue mid-send.
     */
    suspend fun drainNow(context: Context) = emptyQueue(context.applicationContext)

    private suspend fun emptyQueue(app: Context) {
        // Held across the whole walk, so the order the entries were written in
        // is the order they are sent in.
        draining.withLock {
            // Round after round while the queue keeps filling. A walk works from
            // the entries it found when it started, so a message written while
            // one was in flight would otherwise sit there until something else
            // triggered a drain - and the likeliest moment to send a second
            // message is while the first one is still going.
            //
            // It ends: a walk that reaches the end of its round has taken every
            // entry it saw out of the queue, so anything still there is new.
            while (walk(app) && ChatStore.of(app).chats().pending().isNotEmpty()) {
                // keep going
            }
        }
    }

    /**
     * One pass through the queue.
     *
     * @return whether it got to the end. False means it stopped on an entry that is
     *         still there - no network, or a refusal with tries left - and that the
     *         entries behind it must wait, because they may depend on it.
     */
    private suspend fun walk(app: Context): Boolean {
        val dao = ChatStore.of(app).chats()
        val token = SessionStore(app).token
        if (token.isNullOrBlank()) return false

        for (work in dao.pending()) {
            val outcome = when (work.kind) {
                OutboxKind.SEND -> trySend(app, token, work)
                OutboxKind.EDIT -> tryEdit(app, token, work)
                OutboxKind.DELETE_FOR_ME, OutboxKind.DELETE_FOR_EVERYONE ->
                    tryDelete(app, token, work)
                OutboxKind.REACT, OutboxKind.UNREACT -> tryReact(app, token, work)
                OutboxKind.READ, OutboxKind.DELIVERED -> tryReceipt(token, work)
                OutboxKind.MARK_READ, OutboxKind.MARK_UNREAD -> tryReadState(token, work)
                else -> {
                    Log.w(TAG, "Nothing handles outbox kind " + work.kind + "; dropping it")
                    Outcome.Done
                }
            }

            when (outcome) {
                Outcome.Done -> dao.dequeue(work.id)

                // The request never left the phone, so nothing has been refused and
                // nothing is counted. The walk stops: the entries behind this one may
                // depend on it, and the next drain starts here.
                Outcome.Wait -> return false

                is Outcome.Refused -> {
                    val tried = work.attempts + 1
                    dao.failed(work.id, outcome.why)
                    if (tried < MAX_ATTEMPTS) return false
                    // Abandoned, loudly. Keeping it would stop everything behind it
                    // for as long as the app is installed.
                    Log.w(
                        TAG,
                        "Giving up on " + work.kind + " after " + tried +
                            " refusals: " + outcome.why
                    )
                    dao.dequeue(work.id)
                    if (work.kind == OutboxKind.SEND) {
                        // The bubble stays, marked, so the words are still there to
                        // be copied or sent again.
                        dao.setSendState(work.clientId, SendState.FAILED)
                    }
                }
            }
        }
        return true
    }

    private suspend fun trySend(app: Context, token: String, work: OutboxRow): Outcome {
        val dao = ChatStore.of(app).chats()
        val body = parse(work.payload)

        dao.setSendState(work.clientId, SendState.SENDING)
        val result = MessagesApi.send(
            token = token,
            conversationId = work.conversationId,
            clientId = work.clientId,
            text = body.string("text"),
            replyToId = body.string("reply_to_id").takeIf { it.isNotBlank() },
            mediaId = body.string("media_id").takeIf { it.isNotBlank() }
        )

        return when (result) {
            is ApiResult.Success -> {
                // The server's copy lands on the optimistic one: same client id,
                // same row. Sending twice is safe for the same reason - the server
                // returns the message it already stored rather than a second one.
                ChatMessages.store(app, result.data)
                ChatRepository.invalidate()
                // If this message came from an upload, its placeholder has been
                // standing in for it until now. Retired here rather than when the
                // upload finished, so the thread is never showing neither of them.
                UploadQueue.handedOver(work.clientId)
                Outcome.Done
            }

            is ApiResult.Failure -> {
                // Kept either way. The words are the person's: the app does not get
                // to throw them away because the network said no. Left queued rather
                // than failed when nothing was reached, because nothing has gone
                // wrong yet - there was simply no network to try on.
                dao.setSendState(
                    work.clientId,
                    if (result.offline) SendState.QUEUED else SendState.FAILED
                )
                Log.w(TAG, "Send did not go through: " + result.message)
                outcomeOf(result)
            }
        }
    }

    private suspend fun tryEdit(app: Context, token: String, work: OutboxRow): Outcome {
        val id = serverIdFor(app, work) ?: return impossible(work)
        val text = parse(work.payload).string("text")
        return when (val result = MessagesApi.edit(token, id, text)) {
            is ApiResult.Success -> {
                ChatMessages.store(app, result.data)
                ChatRepository.invalidate()
                Outcome.Done
            }
            is ApiResult.Failure -> outcomeOf(result)
        }
    }

    private suspend fun tryDelete(app: Context, token: String, work: OutboxRow): Outcome {
        val id = serverIdFor(app, work) ?: return impossible(work)
        val result = if (work.kind == OutboxKind.DELETE_FOR_EVERYONE) {
            MessagesApi.deleteForEveryone(token, work.conversationId, listOf(id))
        } else {
            MessagesApi.deleteForMe(token, work.conversationId, listOf(id))
        }
        if (result is ApiResult.Success) ChatRepository.invalidate()
        return outcomeOf(result)
    }

    private suspend fun tryReact(app: Context, token: String, work: OutboxRow): Outcome {
        val id = serverIdFor(app, work) ?: return impossible(work)
        val result = if (work.kind == OutboxKind.UNREACT) {
            MessagesApi.removeReaction(token, id)
        } else {
            MessagesApi.react(token, id, parse(work.payload).string("emoji"))
        }
        return outcomeOf(result)
    }

    private suspend fun tryReceipt(token: String, work: OutboxRow): Outcome {
        val seq = parse(work.payload).optInt("seq")
        if (seq <= 0) return impossible(work)
        val result = if (work.kind == OutboxKind.DELIVERED) {
            ConversationsApi.markDelivered(token, work.conversationId, seq)
        } else {
            ConversationsApi.markRead(token, work.conversationId, seq)
        }
        return outcomeOf(result)
    }

    /**
     * The whole conversation, read or unread.
     *
     * No `seq`: marking a conversation read from the list is a statement about the
     * conversation, and the server reads a missing `seq` as all of it.
     */
    private suspend fun tryReadState(token: String, work: OutboxRow): Outcome {
        val result = if (work.kind == OutboxKind.MARK_UNREAD) {
            ConversationsApi.markUnread(token, work.conversationId)
        } else {
            ConversationsApi.markRead(token, work.conversationId)
        }
        if (result is ApiResult.Success) ChatRepository.invalidate()
        return outcomeOf(result)
    }

    /* -- the small print ------------------------------------------------------- */

    /**
     * The server's id for the message an entry is about.
     *
     * Looked up rather than trusted from the entry, because when the entry was
     * written there may not have been one: a reaction or an edit can be decided on a
     * message this phone has not managed to send yet. The queue is drained in order,
     * so by the time this entry comes up its send has been through and the row
     * carries the server's id - which is the whole reason order is kept.
     *
     * The entry's own copy is the fallback, for deleting for yourself: that takes the
     * row away, and the id with it.
     */
    private suspend fun serverIdFor(app: Context, work: OutboxRow): String? =
        ChatStore.of(app).chats().message(work.clientId)?.serverId?.takeIf { it.isNotBlank() }
            ?: parse(work.payload).string("message_id").takeIf { it.isNotBlank() }

    /**
     * An entry that can never be carried out, so it is dropped rather than retried.
     *
     * Reached when the message it is about has no server id and no longer has a row -
     * a reaction to a message whose send was abandoned, say. Said out loud because a
     * queue that quietly discards work is a queue nobody notices is broken.
     */
    private fun impossible(work: OutboxRow): Outcome {
        Log.w(
            TAG,
            "Dropping " + work.kind + ": no server id for " + work.clientId +
                ", so there is nothing to tell the server about"
        )
        return Outcome.Done
    }

    private fun outcomeOf(result: ApiResult<*>): Outcome = when (result) {
        is ApiResult.Success -> Outcome.Done
        is ApiResult.Failure ->
            if (result.offline) Outcome.Wait else Outcome.Refused(result.message)
    }

    private fun work(
        kind: String,
        conversationId: String,
        clientId: String,
        body: JSONObject.() -> Unit
    ): OutboxRow = OutboxRow(
        kind = kind,
        conversationId = conversationId,
        clientId = clientId,
        payload = JSONObject().apply(body).toString(),
        createdAt = System.currentTimeMillis()
    )

    private fun parse(payload: String): JSONObject =
        runCatching { JSONObject(payload) }.getOrElse { JSONObject() }

    /** What came of trying to tell the server about one entry. */
    private sealed interface Outcome {
        /** Told, or never needed telling. The entry leaves the queue. */
        data object Done : Outcome

        /** Not told, and not refused either: the network was not there. */
        data object Wait : Outcome

        /** The server answered, and the answer was no. */
        data class Refused(val why: String) : Outcome
    }
}
