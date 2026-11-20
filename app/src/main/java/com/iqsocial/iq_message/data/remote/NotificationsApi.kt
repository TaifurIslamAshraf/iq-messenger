package com.iqsocial.iq_message.data.remote

import android.net.Uri
import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * One row of the account's notification list.
 *
 * Shared with the main IQ Social app - follows, likes, comments, mentions all land
 * in the same collection - so the messenger reads the kinds it understands and
 * leaves the rest alone rather than assuming it knows every [type] that exists.
 */
@Immutable
data class IqNotification(
    val id: String,
    /** `profile_screenshot`, `follow`, `like`, `comment`, `mention`, … */
    val type: String,
    val contentType: String,
    val text: String,
    val title: String,
    val imageUrl: String,
    val link: String,
    /** Who caused it; null for system notices and deleted accounts. */
    val actor: ChatPerson?,
    /** Epoch ms. */
    val createdAt: Long
) {
    /** What the row says, with the actor's name in front of the server's line. */
    val headline: String
        get() = actor?.fullname?.takeIf { it.isNotBlank() } ?: title.ifBlank { "IQ Social" }

    companion object {
        fun from(json: JSONObject): IqNotification = IqNotification(
            id = json.string("id"),
            type = json.string("type"),
            contentType = json.string("content_type"),
            text = json.string("text"),
            title = json.string("title"),
            imageUrl = json.string("image_url"),
            link = json.string("link"),
            actor = json.optJSONObject("actor")?.let(ChatPerson::from),
            createdAt = json.optLong("created_at")
        )
    }
}

/** A page of them, newest first. */
@Immutable
data class NotificationsPage(
    val items: List<IqNotification>,
    /** Pass back as `cursor`; null on the last page. */
    val nextCursor: String?
)

/**
 * `GET /v1/notifications`.
 *
 * Where a screenshot alert ends up. The push is how it arrives while the phone is
 * awake and listening; this is the record of it, which is what makes the feature
 * survive a phone that was off, out of signal, or had notifications turned off at
 * the system level when it happened.
 */
object NotificationsApi {

    /** The one kind this app raises and reads back. */
    const val TYPE_SCREENSHOT = "profile_screenshot"

    suspend fun list(
        token: String,
        type: String? = null,
        cursor: String? = null,
        limit: Int = 30
    ): ApiResult<NotificationsPage> {
        val query = buildString {
            append("/v1/notifications?limit=").append(limit.coerceIn(1, 100))
            type?.takeIf { it.isNotBlank() }?.let { append("&type=").append(Uri.encode(it)) }
            cursor?.takeIf { it.isNotBlank() }?.let { append("&cursor=").append(Uri.encode(it)) }
        }
        return ApiClient.get(query, token, MessengerConfig.BASE_URL).mapTo { page ->
            NotificationsPage(
                items = page.optJSONArray("items")?.let { array ->
                    (0 until array.length()).mapNotNull {
                        array.optJSONObject(it)?.let(IqNotification::from)
                    }
                }.orEmpty(),
                nextCursor = page.string("next_cursor").takeIf { it.isNotBlank() }
            )
        }
    }
}
