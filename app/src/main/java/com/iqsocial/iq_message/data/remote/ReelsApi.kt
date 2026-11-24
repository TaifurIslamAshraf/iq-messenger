package com.iqsocial.iq_message.data.remote

import android.net.Uri
import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * One reel from the feed.
 *
 * Posted from the IQ Social app; the messenger only shows them. The counters are
 * read-only for the same reason - liking, commenting and counting a view are not in
 * the API yet - so they are drawn as numbers and not as buttons.
 */
@Immutable
data class Reel(
    val id: String,
    val text: String,
    /** An HLS manifest on Cloudflare Stream. Empty means there is nothing to play. */
    val videoUrl: String,
    val thumbnailUrl: String,
    val width: Int,
    val height: Int,
    /** Width over height. 0.5625 for an upright 9:16 video. */
    val ratio: Float,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    val views: Int,
    val liked: Boolean,
    val createdAt: Long,
    /** Who posted it. Never a phone number. */
    val person: ChatPerson?,
    /** `contacts`, `follows` or `everyone`: which part of the feed it came from. */
    val section: String
) {
    companion object {
        fun from(json: JSONObject): Reel = Reel(
            id = json.string("id"),
            text = json.string("text"),
            videoUrl = json.string("video_url"),
            thumbnailUrl = json.string("thumbnail_url"),
            width = json.optInt("width"),
            height = json.optInt("height"),
            ratio = json.optDouble("ratio", 0.0).toFloat()
                .takeIf { it > 0f && !it.isNaN() } ?: (9f / 16f),
            likes = json.optInt("likes"),
            comments = json.optInt("comments"),
            shares = json.optInt("shares"),
            views = json.optInt("views"),
            liked = json.optBoolean("liked"),
            createdAt = json.optLong("created_at"),
            person = json.optJSONObject("person")?.let(ChatPerson::from),
            section = json.string("section")
        )
    }
}

data class ReelPage(
    val items: List<Reel>,
    /** Null at the end of the feed. Sent back exactly as received. */
    val nextCursor: String?
)

/**
 * `GET /v1/reels`: the people you know first, then the people you follow or who
 * follow you, then everyone else - newest first inside each.
 */
object ReelsApi {

    /**
     * A page, smaller than the server's default of 30.
     *
     * The strip shows five or six at a time and the viewer is swiped one by one,
     * so thirty is mostly reels nobody reaches - and every one of them is a
     * thumbnail somebody's phone downloads.
     */
    const val PAGE = 20

    suspend fun feed(
        token: String,
        cursor: String? = null,
        limit: Int = PAGE
    ): ApiResult<ReelPage> {
        val path = buildString {
            append("/v1/reels?limit=").append(limit.coerceIn(1, 100))
            if (!cursor.isNullOrBlank()) append("&cursor=").append(Uri.encode(cursor))
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo { json ->
            ReelPage(
                // "A reel with video_url: "" has nothing to play: skip it."
                items = json.optJSONArray("items")
                    .mapObjects(Reel::from)
                    .filter { it.id.isNotBlank() && it.videoUrl.isNotBlank() },
                nextCursor = json.string("next_cursor").ifBlank { null }
            )
        }
    }
}
