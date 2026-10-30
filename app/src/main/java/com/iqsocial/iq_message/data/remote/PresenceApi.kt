package com.iqsocial.iq_message.data.remote

import android.net.Uri
import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Whether an account is online, and when it was last seen.
 *
 * **An absence here is not a fact about the person.** Someone who has turned their
 * active status off, and someone who has blocked you, come back from the server
 * looking exactly like someone who has never opened the messenger: offline, with no
 * last seen. So [lastSeenAt] of zero means *the server did not say*, and anything
 * this app writes over that - "last seen recently", say - is an invention. It is
 * worth being strict about, because the whole point of the setting is that the
 * answer gives nothing away.
 */
@Immutable
data class Presence(
    val uid: String,
    val online: Boolean,
    /** Epoch millis, or zero when the server gave none. */
    val lastSeenAt: Long
) {
    companion object {
        fun from(json: JSONObject): Presence = Presence(
            uid = json.optString("uid"),
            online = json.optBoolean("online"),
            lastSeenAt = json.optLong("last_seen_at")
        )
    }
}

/**
 * `/v1/presence`.
 *
 * A snapshot for a set of accounts. The server's own note says to keep it live over
 * the socket with `presence:subscribe` - not done here, because the shape of that
 * event is not in the API description and guessing at it would be writing a client
 * for a protocol nobody has written down. This is the part that is specified.
 */
object PresenceApi {

    /** The server's limit on one request, and the reason [get] chunks. */
    const val MAX_UIDS = 100

    /**
     * Asks about a set of accounts, in as many requests as the limit needs.
     *
     * Returns what came back, so a chunk that fails costs those ids and not the
     * rest: presence is decoration over the top of a working screen, and a list that
     * refuses to say anything because one request of four timed out is worse than a
     * list that is partly grey.
     */
    suspend fun get(token: String, uids: List<String>): ApiResult<List<Presence>> {
        val wanted = uids.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return ApiResult.Success(emptyList())

        val found = mutableListOf<Presence>()
        var failure: ApiResult.Failure? = null
        for (chunk in wanted.chunked(MAX_UIDS)) {
            val path = "/v1/presence?uids=" + Uri.encode(chunk.joinToString(","))
            when (val r = ApiClient.get(path, token, MessengerConfig.BASE_URL)
                .mapTo { json -> json.optJSONArray("items").mapObjects(Presence::from) }) {
                is ApiResult.Success -> found += r.data
                is ApiResult.Failure -> failure = r
            }
        }
        // Only when nothing at all came back is this a failure worth reporting.
        return if (found.isEmpty() && failure != null) failure else ApiResult.Success(found)
    }
}
