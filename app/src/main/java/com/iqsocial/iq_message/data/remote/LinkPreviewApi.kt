package com.iqsocial.iq_message.data.remote

import android.net.Uri
import androidx.compose.runtime.Immutable
import org.json.JSONObject

@Immutable
data class LinkPreview(
    val url: String,
    val title: String,
    val description: String,
    val siteName: String,
    /**
     * `GET /v1/link-preview/image`, already addressed and already signed.
     *
     * Used exactly as the server gave it - the query carries a `sig` this app cannot
     * produce, and the address is the proxy rather than the site, which is the point:
     * fetching the original would hand the linked site the reader's IP. It needs the
     * bearer token like any other route here.
     *
     * Only the host is filled in, and only when the server left it out. The spec
     * does not say whether `image_url` comes back absolute or as a path, and a path
     * handed to a URL parser is simply an error - the image would never load and
     * nothing would say why.
     */
    val imageUrl: String?
) {
    companion object {
        fun from(json: JSONObject): LinkPreview = LinkPreview(
            url = json.optString("url"),
            title = json.optString("title"),
            description = json.optString("description"),
            siteName = json.optString("site_name"),
            imageUrl = json.optString("image_url")
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let(::absolute)
        )

        /** Leaves a full address alone; gives a path the messenger host. */
        private fun absolute(imageUrl: String): String = when {
            imageUrl.startsWith("http://", ignoreCase = true) ||
                imageUrl.startsWith("https://", ignoreCase = true) -> imageUrl

            imageUrl.startsWith("/") -> MessengerConfig.BASE_URL + imageUrl
            else -> MessengerConfig.BASE_URL + "/" + imageUrl
        }
    }
}

/**
 * Previews a link while it is being typed.
 *
 * Only for the compose box: a link in a message that has already been sent gets its
 * preview by itself, and every member including the sender is told over the socket
 * as `message:updated`. The server fetches the page, so the site never sees the
 * person typing.
 */
object LinkPreviewApi {

    /** Null when the link has no preview — the server answers 404 for that. */
    suspend fun preview(token: String, url: String): LinkPreview? {
        val result = ApiClient.get(
            "/v1/link-preview?url=${Uri.encode(url)}",
            token,
            MessengerConfig.BASE_URL
        )
        return (result as? ApiResult.Success)?.data
            ?.takeIf { it.optString("url").isNotBlank() }
            ?.let(LinkPreview::from)
    }

    /**
     * The first link in [text], with a scheme on it, or null.
     *
     * Three shapes, because people write links three ways: with the scheme, with
     * `www.` and no scheme, and as a bare host with a path. The last needs the path:
     * without it `i.e.` and `e.g.` are links, and a preview would be requested for
     * every abbreviation anybody types.
     *
     * The server's parameter is a URL, so a match without a scheme gets `https://`
     * put in front of it rather than being sent as-is and refused.
     */
    fun firstLink(text: String): String? = linksIn(text).firstOrNull()?.url

    /** Where a link sits in the text, and the address it opens. */
    data class Span(val from: Int, val to: Int, val url: String)

    /**
     * Every link in [text], in order.
     *
     * The preview wants the first; a message bubble wants all of them, so that each
     * one can be made to open rather than only the one the card was built from.
     *
     * Trailing punctuation is dropped from the address but counted out of the span
     * as well, so the full stop at the end of a sentence stays plain text instead of
     * being swallowed into the link.
     */
    fun linksIn(text: String): List<Span> = LINK.findAll(text).mapNotNull { match ->
        val trimmed = match.value.trimEnd('.', ',', ')', ']', '!', '?', '"')
        if (trimmed.isEmpty()) return@mapNotNull null
        val url = if (trimmed.startsWith("http", ignoreCase = true)) trimmed
        else "https://" + trimmed
        Span(match.range.first, match.range.first + trimmed.length, url)
    }.toList()

    private val LINK = Regex(
        """https?://\S+|www\.\S+\.\S+|\b[\w-]+(?:\.[\w-]+)+/\S*""",
        RegexOption.IGNORE_CASE
    )
}
