package com.iqsocial.iq_message.data.chat

import android.content.Context
import com.iqsocial.iq_message.data.local.ChatStore
import com.iqsocial.iq_message.data.auth.SessionStore
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
        // Released whatever happens, cancellation included. Without this, a screen
        // that goes away mid-fetch leaves the flag set, and `loading` is also what
        // this function returns early on - so one cancelled load would stop the list
        // from ever being fetched again, and leave a spinner turning over it.
        try {
            when (val result = ConversationsApi.list(token, folder)) {
                is ApiResult.Success -> {
                    conversations = result.data.items
                    // Every list carries the peers with it, so this is where the names
                    // come from for free - whichever way a chat is opened later.
                    conversations.forEach { c -> c.peer?.let { KnownPeople.remember(c.id, it) } }
                    // And kept, so the next launch has a list before it has a network.
                    // Only the inbox: the folders are views of the same conversations,
                    // and storing an archived page would file them all as archived.
                    if (folder == ConversationsApi.Folder.INBOX) {
                        store?.let { ChatList.store(it, result.data.items) }
                    }
                    nextCursor = result.data.nextCursor
                    error = null
                }
                is ApiResult.Failure -> error = result.message
            }
            loaded = true
        } finally {
            loading = false
        }
    }

    /**
     * Where the stored list lives, once somebody has said.
     *
     * A context, kept rather than passed: this object is reached from screens,
     * from the socket listener and from the push, and threading one through every
     * one of those to save a field would be the field in a worse place.
     */
    private var store: Context? = null

    /**
     * Shows what is stored, at once, and asks the server afterwards.
     *
     * The order is the point. A list drawn from the store appears with no network
     * at all; the fetch that follows corrects it. Before this, an app opened on a
     * train showed an empty screen and a spinner for as long as the tunnel lasted.
     */
    suspend fun open(context: Context, token: String?) {
        store = context.applicationContext
        // Whose messages these are, written down while somebody is signed in. The
        // store checks it on the next cold start, and without this it would only
        // ever have been told by the start that opened the database - which, after a
        // sign-out and a sign-in without a restart, was the wrong account or none.
        SessionStore(context).load()?.uid?.let { ChatStore.claim(context, it) }
        if (conversations.isEmpty()) {
            val held = ChatList.stored(context)
            // Shown, but **not** marked loaded. `loaded` says the server has been
            // asked, and the chat list screen uses it as an effect key - so setting
            // it here restarted the very effect this is running inside, cancelling
            // the fetch below before it could finish. The stored list appearing is
            // not an answer from anybody; it is what there is until one arrives.
            if (held.isNotEmpty()) {
                conversations = held
                // The names and faces, the same way the server list hands them over.
                // Anything that opens a chat before the network answers reads them
                // from here, and with no connection the network never does.
                held.forEach { c -> c.peer?.let { KnownPeople.remember(c.id, it) } }
            }
        }
        load(token, force = true)
    }

    /** Appends the next page; a no-op once the server stops sending a cursor. */
    suspend fun loadMore(token: String?) {
        val cursor = nextCursor ?: return
        if (token.isNullOrBlank() || loading) return

        loading = true
        // Released on cancellation too, for the reason given in [load]: this flag
        // guards both functions, so one abandoned page would jam the whole list.
        try {
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
        } finally {
            loading = false
        }
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

    /**
     * The chat list's own read/unread toggle, queued.
     *
     * Through the outbox rather than straight at the server, because this is a
     * decision and not a question: somebody tapped "mark unread" to find the
     * conversation again later, and losing that because the train went underground
     * would be the app forgetting something it was told. The badge changes here
     * either way - the local store is what the list draws.
     */
    suspend fun setUnread(token: String?, id: String, unread: Boolean): String? {
        if (token.isNullOrBlank()) return "Not signed in"
        val context = store ?: return "Not ready"
        if (unread) Outbox.markUnread(context, id) else Outbox.markRead(context, id)
        invalidate()
        return null
    }

    /**
     * Answers the message request in a conversation, and hands back the conversation
     * as it now stands.
     *
     * Not queued like [setUnread]. An answer is something the other person is waiting
     * on, and one that sat in a queue behind a dead network would say "accepted" on
     * this screen while the requester still could not write - so it is made, or it is
     * refused out loud and the buttons stay.
     */
    suspend fun answerRequest(
        token: String?,
        id: String,
        accept: Boolean
    ): ApiResult<ChatConversation> {
        if (token.isNullOrBlank()) return ApiResult.Failure("Not signed in")
        val result = if (accept) ConversationsApi.accept(token, id)
        else ConversationsApi.reject(token, id)
        if (result is ApiResult.Success) {
            // Kept, so the chat opens the same way next time with no connection.
            store?.let { ChatList.store(it, listOf(result.data)) }
            invalidate()
        }
        return result
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
