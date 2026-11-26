package com.iqsocial.iq_message.data.remote

import android.net.Uri
import com.iqsocial.iq_message.data.profile.ProfileCache
import androidx.compose.runtime.Immutable

/**
 * Finding people to message.
 *
 * The spec reuses its `Person` shape here, so [ChatPerson] parses both; note that
 * these results **never carry a phone number**, by design — you can look someone up
 * by their number, but you cannot read numbers back off a search.
 */
/**
 * Somebody else's profile, as much of it as they share.
 *
 * Deliberately less than [MessengerProfile]: no phone number, no birthday, no
 * privacy choices. The server will not send those about another person and this
 * type will not hold them, so there is nowhere for a screen to accidentally draw
 * one.
 */
@Immutable
data class PublicProfile(
    val uid: String,
    val fullname: String,
    val profilePicture: String,
    /** Empty when they still have the default banner. */
    val coverPicture: String,
    val bio: String,
    val verified: Int,
    val badgeKey: String,
    val followers: Int,
    val following: Int,
    /** Posts only; reels are a separate collection and are not counted. */
    val totalPost: Int
) {
    val isVerified: Boolean get() = verified != 0

    companion object {
        fun from(json: org.json.JSONObject): PublicProfile = PublicProfile(
            uid = json.string("uid"),
            fullname = json.string("fullname").trim(),
            profilePicture = json.string("profile_picture"),
            coverPicture = json.string("cover_picture"),
            bio = json.string("bio"),
            verified = json.optInt("verified"),
            badgeKey = json.string("badge_key"),
            followers = json.optInt("followers"),
            following = json.optInt("following"),
            totalPost = json.optInt("total_post")
        )
    }
}

object PeopleApi {

    /** `q` is rejected below this length, so the UI should not ask. */
    const val MIN_QUERY = 2
    const val MAX_QUERY = 64

    /** How the server decided to read `q`. */
    @Immutable
    data class SearchResult(
        /** `phone` or `name`, as the server reports it. */
        val by: String,
        val items: List<ChatPerson>
    ) {
        val byPhone: Boolean get() = by.equals("phone", ignoreCase = true)
    }

    @Immutable
    data class Suggestions(
        val items: List<ChatPerson>,
        val nextCursor: String?
    )

    /**
     * Search by name or by a **complete** phone number.
     *
     * The server decides which: a query shaped like a number is an exact lookup —
     * partial numbers match nothing — and anything else is a name search that
     * matches whole words, so `Rahim` finds `Rahim Uddin` but `Rah` does not.
     *
     * [region] resolves a number typed without a country code, and defaults to the
     * caller's own country when omitted.
     */
    /**
     * One person, by id.
     *
     * 404 covers everything that is not a plain yes - an unknown id, a banned
     * account, somebody who has blocked you - which is the same answer search
     * gives, and for the same reason: the absence of an answer should not say
     * which of those it was.
     */
    /**
     * Their profile, and a copy kept on this phone for opening it offline - see
     * [cachedProfile].
     */
    suspend fun profile(token: String, uid: String): ApiResult<PublicProfile> {
        val result = ApiClient.get(
            "/v1/people/${Uri.encode(uid)}",
            token,
            MessengerConfig.BASE_URL
        )
        if (result is ApiResult.Success) {
            ProfileCache.write(ProfileCache.personKey(uid), result.data)
        }
        return result.mapTo(PublicProfile::from)
    }

    /** Their profile as it was last fetched on this phone, or null if it never was. */
    suspend fun cachedProfile(uid: String): PublicProfile? =
        ProfileCache.read(ProfileCache.personKey(uid))
            ?.let { runCatching { PublicProfile.from(it) }.getOrNull() }

    suspend fun search(
        token: String,
        query: String,
        region: String? = null,
        limit: Int? = null
    ): ApiResult<SearchResult> {
        val q = query.trim()
        if (q.length < MIN_QUERY) {
            return ApiResult.Failure("Type at least $MIN_QUERY characters")
        }
        val path = buildString {
            append("/v1/people/search?q=").append(Uri.encode(q.take(MAX_QUERY)))
            if (!region.isNullOrBlank()) append("&region=").append(region.uppercase())
            limit?.let { append("&limit=").append(it) }
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo { json ->
            SearchResult(
                by = json.string("by"),
                items = json.optJSONArray("items").mapObjects(ChatPerson::from)
            )
        }
    }

    /**
     * People the caller follows on IQ Social, most recently followed first.
     *
     * Cursor-paginated, and the spec warns a page can hold fewer than `limit`
     * items — so emptiness is not the end, only a null `next_cursor` is.
     */
    suspend fun suggestions(
        token: String,
        cursor: String? = null,
        limit: Int? = null
    ): ApiResult<Suggestions> {
        val path = buildString {
            append("/v1/people/suggestions")
            val params = buildList {
                cursor?.let { add("cursor=" + Uri.encode(it)) }
                limit?.let { add("limit=$it") }
            }
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo { json ->
            Suggestions(
                items = json.optJSONArray("items").mapObjects(ChatPerson::from),
                nextCursor = json.string("next_cursor")
                    .takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
