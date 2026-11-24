package com.iqsocial.iq_message.data.reels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.Reel
import com.iqsocial.iq_message.data.remote.ReelsApi

/**
 * The reels feed as this process knows it: one list, shared by the strip on the
 * chat list and the full-screen viewer.
 *
 * Shared because the viewer is opened *at* a reel in the strip and swiped onward
 * from there. Two lists would mean two orders, and the reel under the finger in
 * the viewer would not be the one tapped in the strip.
 */
object ReelsFeed {

    var reels by mutableStateOf<List<Reel>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    /** The first page has been asked for and answered, either way. */
    var loaded by mutableStateOf(false)
        private set

    private var cursor: String? = null
    private var ended = false
    private var fetchedAt = 0L

    /**
     * The first page, again.
     *
     * Skipped when the list is recent: coming back to the chat list from a chat
     * would otherwise reload the feed every time, and reorder it under somebody
     * who was about to tap the reel they had been looking at.
     */
    suspend fun refresh(token: String?, force: Boolean = false) {
        if (token.isNullOrBlank() || loading) return
        if (!force && loaded && System.currentTimeMillis() - fetchedAt < FRESH_MS) return
        loading = true
        try {
            when (val r = ReelsApi.feed(token)) {
                is ApiResult.Success -> {
                    reels = r.data.items.distinctBy { it.id }
                    cursor = r.data.nextCursor
                    ended = cursor == null
                    fetchedAt = System.currentTimeMillis()
                }
                // Quiet on screen. The strip is an extra on the chat list, not its
                // point: a feed that will not load is a strip that is not there, not
                // an error over somebody's conversations. But said in the log, or a
                // missing strip is indistinguishable from an empty feed - which is
                // exactly the question somebody looking at a phone will be asking.
                is ApiResult.Failure -> Log.w(TAG, describe("feed", r))
            }
        } finally {
            loading = false
            loaded = true
        }
    }

    /**
     * The next page, appended.
     *
     * De-duplicated by id, as the API asks: the feed moves while it is read - a
     * contact posts, somebody new follows - and a reel can turn up on two pages.
     */
    suspend fun loadMore(token: String?) {
        if (token.isNullOrBlank() || loading || ended) return
        val next = cursor ?: return
        loading = true
        try {
            when (val r = ReelsApi.feed(token, next)) {
                is ApiResult.Success -> {
                    val seen = reels.mapTo(HashSet()) { it.id }
                    val fresh = r.data.items.filter { seen.add(it.id) }
                    if (fresh.isNotEmpty()) reels = reels + fresh
                    cursor = r.data.nextCursor
                    ended = cursor == null
                }
                is ApiResult.Failure -> Log.w(TAG, describe("next page", r))
            }
        } finally {
            loading = false
        }
    }

    /** Signing out: somebody else's feed is not this account's to keep. */
    fun clear() {
        reels = emptyList()
        cursor = null
        ended = false
        loaded = false
        fetchedAt = 0L
    }

    private fun describe(what: String, failure: ApiResult.Failure): String = when {
        failure.notImplemented -> "Reels $what: the server has no such route (404) - " + failure.message
        failure.offline -> "Reels $what: no connection - " + failure.message
        else -> "Reels $what failed: " + failure.message
    }

    private const val TAG = "ReelsFeed"

    /** How long a loaded feed is good for before the strip asks again. */
    private const val FRESH_MS = 5 * 60 * 1000L
}
