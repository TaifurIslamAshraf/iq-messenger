package com.iqsocial.iq_message.data.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatConversation
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.data.remote.UnreadCount

/**
 * The chat list, paged as the API pages it.
 *
 * [loaded] means a request finished, not that anything came back: an account with
 * no conversations is a successful empty list, and the UI has to tell that apart
 * from "we never asked", which is why both flags exist.
 */
object ChatRepository {

    var conversations by mutableStateOf<List<ChatConversation>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    /** True once a list request has completed, successfully or not. */
    var loaded by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Which folder the list is showing. Switching it reloads. */
    var folder by mutableStateOf(ConversationsApi.Folder.INBOX)
        private set

    /** Totals for the tab badge; muted and archived conversations are excluded. */
    var unread by mutableStateOf(UnreadCount(0, 0))
        private set

    /** Null once the last page has been read. */
    private var nextCursor: String? = null

    val canLoadMore: Boolean get() = nextCursor != null

    suspend fun showFolder(token: String?, next: ConversationsApi.Folder) {
        if (folder == next) return
        folder = next
        conversations = emptyList()
        nextCursor = null
        loaded = false
        load(token, force = true)
    }

    suspend fun load(token: String?, force: Boolean = false) {
        if (token.isNullOrBlank()) {
            loaded = true
            return
        }
        if (loading || (loaded && !force)) return

        loading = true
        when (val result = ConversationsApi.list(token, folder)) {
            is ApiResult.Success -> {
                conversations = result.data.items
                // Every list carries the peers with it, so this is where the names
                // come from for free - whichever way a chat is opened later.
                conversations.forEach { c -> c.peer?.let { KnownPeople.remember(c.id, it) } }
                nextCursor = result.data.nextCursor
                error = null
            }
            is ApiResult.Failure -> error = result.message
        }
        loading = false
        loaded = true
    }

    /** Appends the next page; a no-op once the server stops sending a cursor. */
    suspend fun loadMore(token: String?) {
        val cursor = nextCursor ?: return
        if (token.isNullOrBlank() || loading) return

        loading = true
        when (val result = ConversationsApi.list(token, cursor = cursor)) {
            is ApiResult.Success -> {
                // Guard against a server that repeats a page: ids must stay unique
                // or LazyColumn's keys collide.
                val known = conversations.mapTo(HashSet()) { it.id }
                conversations = conversations + result.data.items.filterNot { it.id in known }
                result.data.items.forEach { c -> c.peer?.let { KnownPeople.remember(c.id, it) } }
                nextCursor = result.data.nextCursor
                error = null
            }
            is ApiResult.Failure -> error = result.message
        }
        loading = false
    }

    /**
     * Where each conversation's change feed was last read up to.
     *
     * `/messages/changes` resumes from a `synced_at` rather than a position, so
     * this is kept per conversation and handed back on the next visit. It lives
     * for the process only: a cold start re-reads history anyway.
     */
    private val syncPoints = mutableMapOf<String, Long>()

    fun syncedAt(conversationId: String): Long = syncPoints[conversationId] ?: 0L

    fun rememberSync(conversationId: String, at: Long) {
        syncPoints[conversationId] = at
    }

    /**
     * Applies a list action and re-reads.
     *
     * Pinning, muting and archiving all change what the list contains and the order
     * it comes in - pinning even evicts a conversation from the archived folder -
     * so guessing the new state locally would drift from the server within a tap or
     * two. Re-reading one page is cheap and always right.
     */
    private suspend fun act(token: String?, block: suspend (String) -> ApiResult<Unit>): String? {
        val auth = token ?: return "Not signed in"
        return when (val r = block(auth)) {
            is ApiResult.Failure -> r.message
            is ApiResult.Success -> {
                load(auth, force = true)
                refreshUnread(auth)
                null
            }
        }
    }

    suspend fun setPinned(token: String?, id: String, pinned: Boolean): String? =
        act(token) { if (pinned) ConversationsApi.pin(it, id) else ConversationsApi.unpin(it, id) }

    /** [duration] is `8h`, `1w` or `always`; null unmutes. */
    suspend fun setMuted(token: String?, id: String, duration: String?): String? =
        act(token) {
            if (duration == null) ConversationsApi.unmute(it, id)
            else ConversationsApi.mute(it, id, duration)
        }

    suspend fun setArchived(token: String?, id: String, archived: Boolean): String? =
        act(token) {
            if (archived) ConversationsApi.archive(it, id)
            else ConversationsApi.unarchive(it, id)
        }

    suspend fun setUnread(token: String?, id: String, unread: Boolean): String? =
        act(token) {
            if (unread) ConversationsApi.markUnread(it, id)
            else ConversationsApi.markRead(it, id)
        }

    suspend fun delete(token: String?, id: String): String? =
        act(token) { ConversationsApi.deleteForMe(it, id) }

    suspend fun refreshUnread(token: String?) {
        val auth = token ?: return
        val r = ConversationsApi.unreadCount(auth)
        if (r is ApiResult.Success) unread = r.data
    }

    /**
     * Says the list is out of date without throwing it away.
     *
     * [clear] was being used for this, and it is the wrong tool twice over: it
     * empties `conversations`, so whatever is on screen goes blank, and the reload
     * that was supposed to follow never came - the chat list's effect is keyed on
     * the token, not on this, so nothing re-ran. The list vanished and stayed
     * vanished.
     *
     * Marking it unloaded is enough. The screen re-runs its load, `load` fetches
     * because it is no longer loaded, and the rows already on screen stay up until
     * the new ones arrive - including when the fetch fails, which [clear] turned
     * into an empty screen with no way back.
     */
    fun invalidate() {
        loaded = false
    }

    /** Throws the list away. For signing out, where none of it is ours any more. */
    fun clear() {
        conversations = emptyList()
        nextCursor = null
        loading = false
        loaded = false
        error = null
        folder = ConversationsApi.Folder.INBOX
        unread = UnreadCount(0, 0)
        syncPoints.clear()
    }
}
