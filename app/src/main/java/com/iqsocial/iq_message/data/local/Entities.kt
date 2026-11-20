package com.iqsocial.iq_message.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A message, as it is kept on this phone.
 *
 * Flat on purpose. The wire model nests - a quote, a media object, reactions, a
 * link preview - and a table that mirrored that nesting would need four joins to
 * draw one bubble. What is nested on the wire is stored here as the JSON it arrived
 * as, and parsed back by the same code that parses it from the network; the columns
 * are only the fields something queries, sorts or counts by.
 *
 * **The key is [clientId], not the server's id.** A message written while offline
 * has no server id yet, and the row has to exist before the server has ever heard
 * of it. `client_id` is the one identifier that is true for the whole life of a
 * message, which is also why the send endpoint takes it: sending twice returns the
 * message already stored rather than a second copy.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "seq"]),
        Index(value = ["conversationId", "createdAt"]),
        Index(value = ["serverId"], unique = true)
    ]
)
data class MessageRow(
    @PrimaryKey val clientId: String,
    /** Blank until the server has acknowledged it. */
    val serverId: String,
    val conversationId: String,
    /** Position in the conversation, and zero while it is still only ours. */
    val seq: Int,
    val senderUid: String,
    val type: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val editedAt: Long,
    val deletedAt: Long,
    /** The whole message as the API sends it, for the parts no column holds. */
    val payload: String,
    /**
     * Where this row is in its journey to the server.
     *
     * Held as a column rather than worked out, because the answer has to survive
     * the process: a message left as [SendState.SENDING] when the app was killed is
     * one nobody is sending any more, and only a stored state can say so.
     */
    val sendState: Int
)

/** What has happened to a message of our own. */
object SendState {
    /** Written here, not yet handed to the network. */
    const val QUEUED = 0

    /** A send is in flight. */
    const val SENDING = 1

    /** The server has it. */
    const val SENT = 2

    /** The server refused it, and it can be tried again. */
    const val FAILED = 3
}

/**
 * A conversation row: what the chat list draws, and where the sync has got to.
 *
 * The two live together because they are the same question asked twice - the list
 * shows the newest message, and the sync asks what came after it.
 */
@Entity(tableName = "conversations")
data class ConversationRow(
    @PrimaryKey val id: String,
    val peerUid: String,
    val peerName: String,
    val peerPhoto: String,
    val lastMessageAt: Long,
    val unread: Int,
    val muted: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    /** The conversation as the API sends it, for the fields no column holds. */
    val payload: String,
    /**
     * The highest `seq` this phone has seen, which is what `?after_seq=` is given.
     *
     * Zero means nothing has been fetched yet, and the first sync asks for history
     * rather than a catch-up.
     */
    val lastSeq: Int,
    /**
     * When this conversation was last reconciled, in server milliseconds.
     *
     * Fed to `/messages/changes?since=` - the endpoint that reports edits, deletes,
     * reactions and link previews, none of which change `seq` and none of which a
     * catch-up by `after_seq` would ever mention.
     */
    val syncedAt: Long
)

/**
 * Something this phone has decided and the server has not been told yet.
 *
 * The outbox. Written in the same transaction as the change it describes, so the two
 * cannot come apart: either the message is in the thread **and** queued to send, or
 * neither happened. A queue written separately is a queue that, sooner or later,
 * describes a message that was never stored - or misses one that was.
 *
 * [kind] says what to do and [payload] carries what it needs. Deliberately not one
 * table per action: the drainer walks a single queue in order, and order is the
 * point - a reaction to a message that has not been sent yet must not go first.
 */
@Entity(tableName = "outbox", indices = [Index(value = ["createdAt"])])
data class OutboxRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val conversationId: String,
    /** The message this is about, by its client id. */
    val clientId: String,
    val payload: String,
    val createdAt: Long,
    /** How many times it has been tried, for backing off rather than hammering. */
    val attempts: Int = 0,
    /** Why the last attempt failed, for a screen that wants to say so. */
    val lastError: String = ""
)

/**
 * A file on its way to the server, kept so it survives the app being killed.
 *
 * The outbox cannot carry this one, because what has to be kept is not a decision
 * but bytes. Everything here is enough to finish the upload from cold: the prepared
 * payload, the declaration the server was going to be sent, and what the bubble needs
 * to keep drawing itself in the meantime.
 *
 * **[path] is a copy inside this app's cache, not where the file came from.** A photo
 * picked from the gallery arrives as a `content://` grant that dies with the process,
 * and the picker's grants cannot be made persistable, so a stored source URI would be
 * unreadable by the time anything tried to resume from it. Owning a copy is what makes
 * a photo picked on a train still there when the app is reopened.
 */
@Entity(tableName = "uploads")
data class UploadRow(
    @PrimaryKey val clientId: String,
    val conversationId: String,
    /** The prepared bytes, in this app's cache. */
    val path: String,
    /** The thumbnail, in the cache too, or blank when there is none. */
    val thumbPath: String,
    /**
     * The declaration the server is given, as JSON.
     *
     * Kept rather than built again on resume, because it was measured from exactly
     * the bytes at [path] - the server presigns for that one length, and a
     * declaration rebuilt from a file prepared a second time would not match.
     */
    val body: String,
    val size: Long,
    val kind: String,
    /** Shape of the picture, so the bubble is the right size before it is sent. */
    val aspect: Float,
    /**
     * Whether the photo goes up as it was taken.
     *
     * Stored rather than held in memory because it is decided when the picture is
     * picked and used when it is prepared, and those are now two different moments -
     * possibly on two sides of the app being killed. A toggle flipped in between
     * must not change what this one sends.
     */
    val hd: Boolean,
    val durationMs: Long,
    /** Voice only: the measured bars, as a JSON array. */
    val waveform: String,
    val replyToId: String,
    val createdAt: Long
)

/** The kinds of thing the outbox carries. */
object OutboxKind {
    const val SEND = "send"
    const val EDIT = "edit"
    const val DELETE_FOR_EVERYONE = "delete_for_everyone"
    const val DELETE_FOR_ME = "delete_for_me"
    const val REACT = "react"
    const val UNREACT = "unreact"

    /** How far the thread has been read, by `seq`. */
    const val READ = "read"

    /** That a message arrived on this phone, by `seq`. */
    const val DELIVERED = "delivered"

    /**
     * The whole conversation marked read, or unread, from the chat list.
     *
     * Separate from [READ] because it is a different statement. [READ] says how far
     * somebody got; these two say what the conversation should look like, and the
     * answer is whichever was decided last - so they replace each other instead of
     * queueing up.
     */
    const val MARK_READ = "mark_read"
    const val MARK_UNREAD = "mark_unread"
}
