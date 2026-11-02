package com.iqsocial.iq_message.data.chat

import android.content.Context
import com.iqsocial.iq_message.data.local.ChatStore
import com.iqsocial.iq_message.data.local.ConversationRow
import com.iqsocial.iq_message.data.remote.ChatConversation
import org.json.JSONObject

/**
 * The chat list, kept on this phone.
 *
 * The thread was the hard half; this is the visible one. A messenger that opens on
 * an empty screen and a spinner has nothing to say for itself on a train, and the
 * list is the first thing anybody sees.
 *
 * Stored the same way as messages, for the same reasons: the whole row as the API
 * sent it, parsed back by the parser that reads it from the network, with columns
 * only for what something sorts or filters by.
 */
object ChatList {

    /** What was kept, newest first, pinned above the rest - as the screen draws it. */
    suspend fun stored(context: Context): List<ChatConversation> =
        ChatStore.of(context).chats().conversationsOnce().mapNotNull { it.toConversation() }

    suspend fun store(context: Context, conversations: List<ChatConversation>) {
        if (conversations.isEmpty()) return
        ChatStore.of(context).chats().mergeConversations(conversations.map { it.toRow() })
    }

    /** Signing out: the next person does not inherit a list of these chats. */
    suspend fun wipe(context: Context) {
        ChatStore.of(context).chats().wipeConversations()
    }
}

private fun ChatConversation.toRow(): ConversationRow = ConversationRow(
    id = id,
    peerUid = peer?.uid.orEmpty(),
    peerName = peer?.fullname.orEmpty(),
    peerPhoto = peer?.profilePicture.orEmpty(),
    lastMessageAt = lastMessageAt,
    unread = unreadCount,
    muted = muted,
    pinned = pinned,
    archived = archived,
    payload = raw,
    // Not this object's to set. The sync marks belong to whoever last caught this
    // conversation up, and `mergeConversations` is what keeps them through a write
    // from the list - which carries no such thing.
    lastSeq = 0,
    syncedAt = 0L
)

/**
 * Back from the stored JSON, or nothing.
 *
 * A row written by an older build may not parse into today's model, and a list that
 * refused to open because one row was stale would be worse than a list one row
 * short. The next fetch replaces it either way.
 */
private fun ConversationRow.toConversation(): ChatConversation? =
    payload.takeIf { it.isNotBlank() }
        ?.let { runCatching { ChatConversation.from(JSONObject(it)) }.getOrNull() }
