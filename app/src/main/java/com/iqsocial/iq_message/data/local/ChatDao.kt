package com.iqsocial.iq_message.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app asks of the local store.
 *
 * The reads return [Flow], and that is what makes the database the source of truth
 * rather than a cache beside one: a screen collects the thread and is told again
 * whenever anything writes to it, whether that was the socket, a catch-up fetch or
 * this phone sending a message. Nothing has to remember to refresh anybody.
 */
@Dao
interface ChatDao {

    /* -- the thread ---------------------------------------------------------- */

    /**
     * Oldest first, with messages that have no `seq` yet at the end.
     *
     * A message of this phone's own has `seq` zero until the server numbers it, and
     * sorting by `seq` alone would file it before the first message ever sent.
     * Ordering by time and settling ties with `seq` puts it where it was written.
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId AND deletedAt = 0
        ORDER BY createdAt ASC, seq ASC
        """
    )
    fun thread(conversationId: String): Flow<List<MessageRow>>

    /**
     * The newest [limit] of them, newest first.
     *
     * The thread is read through this rather than through [thread], because the
     * screen only ever draws from the bottom: opening a conversation with four
     * thousand messages in it does not need four thousand rows read, parsed and
     * turned into bubbles to show the last twenty-five of them. The caller turns
     * the page back the right way round.
     *
     * Newest first is what makes the limit mean "the end of the conversation";
     * ascending with a limit would be the *beginning* of it.
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId AND deletedAt = 0
        ORDER BY createdAt DESC, seq DESC
        LIMIT :limit
        """
    )
    fun threadTail(conversationId: String, limit: Int): Flow<List<MessageRow>>

    /**
     * How many messages [threadTail] would read to reach back as far as the one at
     * [createdAt] / [seq], counting that one.
     *
     * Ordered and filtered exactly as the tail is, so the number is a window size:
     * open the tail this wide and the message is its oldest row.
     */
    @Query(
        """
        SELECT COUNT(*) FROM messages
        WHERE conversationId = :conversationId AND deletedAt = 0
          AND (createdAt > :createdAt OR (createdAt = :createdAt AND seq >= :seq))
        """
    )
    suspend fun countFrom(conversationId: String, createdAt: Long, seq: Int): Int

    @Query("SELECT * FROM messages WHERE clientId = :clientId LIMIT 1")
    suspend fun message(clientId: String): MessageRow?

    @Query("SELECT * FROM messages WHERE serverId = :serverId LIMIT 1")
    suspend fun messageByServerId(serverId: String): MessageRow?

    /** The highest `seq` stored, which is where a catch-up starts from. */
    @Query("SELECT COALESCE(MAX(seq), 0) FROM messages WHERE conversationId = :conversationId")
    suspend fun highestSeq(conversationId: String): Int

    /**
     * The lowest `seq` stored, which is where a walk backwards starts from.
     *
     * Messages with no `seq` are left out: those are this phone's own, waiting to be
     * sent, and they are not a position in the conversation - counting one would ask
     * the server for everything before zero.
     */
    @Query(
        """
        SELECT COALESCE(MIN(seq), 0) FROM messages
        WHERE conversationId = :conversationId AND seq > 0
        """
    )
    suspend fun lowestSeq(conversationId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMessages(rows: List<MessageRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMessage(row: MessageRow)

    @Query("DELETE FROM messages WHERE clientId = :clientId")
    suspend fun dropMessage(clientId: String)

    @Query("UPDATE messages SET sendState = :state WHERE clientId = :clientId")
    suspend fun setSendState(clientId: String, state: Int)

    /**
     * Anything left mid-flight when the app stopped.
     *
     * Called at start-up: a row marked [SendState.SENDING] is one whose send died
     * with the process, and nothing will ever finish it. Put back in the queue it
     * is tried again; left alone it is a message that looks like it is going and
     * never arrives.
     */
    @Query("UPDATE messages SET sendState = ${SendState.QUEUED} WHERE sendState = ${SendState.SENDING}")
    suspend fun requeueInterrupted()

    /* -- the chat list -------------------------------------------------------- */

    @Query(
        """
        SELECT * FROM conversations
        WHERE archived = 0
        ORDER BY pinned DESC, lastMessageAt DESC
        """
    )
    fun conversations(): Flow<List<ConversationRow>>

    /**
     * The same list, read once.
     *
     * Beside the [Flow] rather than instead of it: the chat list screen keeps its
     * own state and reloads on its own terms, so it wants an answer, not a
     * subscription - two things telling it the same news would be one too many.
     */
    @Query(
        """
        SELECT * FROM conversations
        WHERE archived = 0
        ORDER BY pinned DESC, lastMessageAt DESC
        """
    )
    suspend fun conversationsOnce(): List<ConversationRow>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun conversation(id: String): ConversationRow?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun watchConversation(id: String): Flow<ConversationRow?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putConversations(rows: List<ConversationRow>)

    /**
     * Keeps the sync marks when a conversation is written again.
     *
     * The chat list arrives from the server without them - they are this phone's
     * bookkeeping, not the account's - so a plain upsert from a list fetch would
     * reset every conversation to "never synced" and make the next open re-download
     * the whole thread.
     */
    @Transaction
    suspend fun mergeConversations(rows: List<ConversationRow>) {
        val merged = rows.map { incoming ->
            val known = conversation(incoming.id)
            if (known == null) incoming
            else incoming.copy(lastSeq = known.lastSeq, syncedAt = known.syncedAt)
        }
        putConversations(merged)
    }

    @Query("UPDATE conversations SET lastSeq = :seq, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, seq: Int, syncedAt: Long)

    @Query("UPDATE conversations SET unread = :unread WHERE id = :id")
    suspend fun setUnread(id: String, unread: Int)

    /* -- the outbox ----------------------------------------------------------- */

    /**
     * In the order they were decided.
     *
     * Order is the whole contract: a reaction to a message still queued behind it
     * would be a reaction to nothing, and an edit before its send would be an edit
     * of a message the server has never seen.
     */
    @Query("SELECT * FROM outbox ORDER BY createdAt ASC, id ASC")
    suspend fun pending(): List<OutboxRow>

    @Query("SELECT COUNT(*) FROM outbox")
    fun pendingCount(): Flow<Int>

    @Insert
    suspend fun enqueue(row: OutboxRow)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun dequeue(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :why WHERE id = :id")
    suspend fun failed(id: Long, why: String)

    @Query("DELETE FROM outbox WHERE clientId = :clientId")
    suspend fun dropQueued(clientId: String)

    /**
     * Everything queued about one message.
     *
     * Asked before queueing a change to it, because a message whose send is still
     * waiting is a different problem from one the server already has: there is no
     * message on the server to edit, delete or react to yet.
     */
    @Query("SELECT * FROM outbox WHERE clientId = :clientId ORDER BY id ASC")
    suspend fun queuedFor(clientId: String): List<OutboxRow>

    /** One kind of entry for one conversation, for the kinds that keep only one. */
    @Query("SELECT * FROM outbox WHERE kind = :kind AND conversationId = :conversationId")
    suspend fun queuedOfKind(kind: String, conversationId: String): List<OutboxRow>

    /**
     * Changes what a queued entry will say.
     *
     * For editing a message that has not been sent: the send is still here, so the
     * new words replace the old ones in it and the first version is never sent at
     * all. Cheaper than it is clever - it keeps the server from hearing a message
     * and a correction one second apart.
     */
    @Query("UPDATE outbox SET payload = :payload WHERE id = :id")
    suspend fun reword(id: Long, payload: String)

    /* -- uploads in flight ---------------------------------------------------- */

    /**
     * Oldest first, which is the order they were picked in.
     *
     * Read at start-up: anything here is an upload that was interrupted, and the
     * bytes are still in the cache waiting for it to be tried again.
     */
    @Query("SELECT * FROM uploads ORDER BY createdAt ASC")
    suspend fun uploads(): List<UploadRow>

    @Query("SELECT * FROM uploads WHERE clientId = :clientId LIMIT 1")
    suspend fun upload(clientId: String): UploadRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putUpload(row: UploadRow)

    @Query("DELETE FROM uploads WHERE clientId = :clientId")
    suspend fun dropUpload(clientId: String)

    @Query("DELETE FROM uploads")
    suspend fun wipeUploads()

    /* -- writing a message and queueing it, together -------------------------- */

    /**
     * The two halves of sending, in one transaction.
     *
     * This is the heart of the whole arrangement. Either the message is in the
     * thread **and** in the queue, or neither happened - there is no state where a
     * bubble is on screen that nothing will ever send, and none where the queue
     * describes a message that was never written.
     */
    @Transaction
    suspend fun writeAndQueue(message: MessageRow, work: OutboxRow) {
        putMessage(message)
        enqueue(work)
    }

    /**
     * The same bargain for a change that takes the message away.
     *
     * Deleting for yourself removes the row, so there is nothing left to mark - but
     * the server still has to be told, and the telling must not survive on its own
     * if the removal did not happen.
     */
    @Transaction
    suspend fun dropAndQueue(clientId: String, work: OutboxRow) {
        dropMessage(clientId)
        enqueue(work)
    }

    /**
     * Takes back a message the server never heard about.
     *
     * Deleting something still in the queue needs no entry of its own: the send goes
     * with it, and nobody outside this phone ever knew. Both halves together, so a
     * send cannot be left behind to deliver a message that has been deleted.
     */
    @Transaction
    suspend fun forgetUnsent(clientId: String) {
        dropQueued(clientId)
        dropMessage(clientId)
    }

    /* -- signing out ---------------------------------------------------------- */

    @Query("DELETE FROM messages")
    suspend fun wipeMessages()

    @Query("DELETE FROM conversations")
    suspend fun wipeConversations()

    @Query("DELETE FROM outbox")
    suspend fun wipeOutbox()

    @Transaction
    suspend fun wipe() {
        wipeMessages()
        wipeConversations()
        wipeOutbox()
        wipeUploads()
    }
}
