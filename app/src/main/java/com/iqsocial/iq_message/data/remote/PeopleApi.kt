package com.iqsocial.iq_message.data.remote

import android.net.Uri
import com.iqsocial.iq_message.data.profile.ProfileCache
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

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

/**
 * One person on the account's own contact list (`GET /v1/people`).
 *
 * [sources] says how they got there: `phone` (the phone book) or `qr` (their QR
 * code, scanned or typed). [phone] is their number as **this account's** phone book
 * holds it, so a screen can show the name saved for them here; null unless `phone`
 * is a source.
 */
@Immutable
data class SavedPerson(
    val person: ChatPerson,
    val sources: List<String>,
    val phone: String?,
    val addedAt: Long
) {
    val uid: String get() = person.uid
    val fromPhoneBook: Boolean get() = SOURCE_PHONE in sources

    /** Back into the server's shape, for the copy kept on this phone. */
    fun toJson(): JSONObject = JSONObject()
        .put("uid", person.uid)
        .put("fullname", person.fullname)
        .put("profile_picture", person.profilePicture)
        .put("verified", person.verified)
        .put("badge_key", person.badgeKey)
        .put("allow_screenshot", person.allowScreenshot)
        .put("sources", JSONArray(sources))
        .put("phone", phone ?: JSONObject.NULL)
        .put("added_at", addedAt)

    companion object {
        const val SOURCE_PHONE = "phone"
        const val SOURCE_QR = "qr"

        fun from(json: JSONObject): SavedPerson = SavedPerson(
            person = ChatPerson.from(json),
            sources = json.optJSONArray("sources")
                ?.let { array -> (0 until array.length()).map { array.string(it) } }
                .orEmpty(),
            phone = json.string("phone").takeIf { it.isNotBlank() },
            addedAt = json.optLong("added_at")
        )
    }
}

/**
 * The account's people, and the people behind an id.
 *
 * There is **one** list of people in this app - the phone book's people on IQ and
 * whoever was added by QR code - and every screen that offers someone reads it: the
 * People tab, "New message", the rewards special-person picker and "Add to call".
 * Searching only ever looks inside it. The server has no search over everyone on IQ
 * any more, by the owner's decision.
 */
object PeopleApi {

    /** `q` is rejected below this length, so the UI should not ask. */
    const val MIN_QUERY = 2
    const val MAX_QUERY = 64

    @Immutable
    data class Page(
        val items: List<SavedPerson>,
        /** Null on the last page. A page can hold fewer than asked for, so emptiness is not the end. */
        val nextCursor: String?
    )

    /**
     * One page of the list, newest first - all of it, or what matches [query].
     *
     * [query] matches the start of any word in the name (`rah` finds Rahim Uddin),
     * or, when it looks like a number, digits anywhere in a saved number.
     */
    suspend fun list(
        token: String,
        query: String? = null,
        cursor: String? = null,
        limit: Int? = null
    ): ApiResult<Page> {
        val params = buildList {
            query?.trim()?.takeIf { it.length >= MIN_QUERY }?.let { add("q=" + Uri.encode(it.take(MAX_QUERY))) }
            cursor?.let { add("cursor=" + Uri.encode(it)) }
            limit?.let { add("limit=$it") }
        }
        val path = "/v1/people" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo { json ->
            Page(
                items = json.optJSONArray("items").mapObjects(SavedPerson::from),
                nextCursor = json.string("next_cursor").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    /**
     * One person, by id.
     *
     * 404 covers everything that is not a plain yes - an unknown id, a banned
     * account, somebody who has blocked you - so the absence of an answer does not
     * say which of those it was.
     *
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
}
