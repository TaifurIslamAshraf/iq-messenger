package com.iqsocial.iq_message.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * The messenger service, which is a different host from the account API.
 *
 * It is authenticated with the **same** bearer token: the spec says outright that
 * it takes the JWT issued by the main backend's `/v1/api/login`, so there is no
 * second sign-in. Its envelope is its own though — `{success, message,
 * responseObject, statusCode}` — and unlike the main backend it answers failures
 * with real HTTP status codes.
 */
object MessengerConfig {
    const val BASE_URL = "https://messanger.iqamasocial.com"

    /** Limits from the spec; the request is trimmed to them rather than rejected. */
    const val MAX_PHONES = 20_000
    const val MAX_PHONE_LENGTH = 40
}

/** A contact of yours who turns out to have an IQ Social account. */
data class ContactMatch(
    val uid: String,
    /** The contact's number in E.164, as the server resolved it. */
    val phone: String,
    val fullname: String,
    val profilePicture: String,
    val verified: Int,
    val badgeKey: String
) {
    companion object {
        fun from(json: JSONObject): ContactMatch = ContactMatch(
            uid = json.string("uid"),
            phone = json.string("phone"),
            fullname = json.string("fullname"),
            profilePicture = json.string("profile_picture"),
            verified = json.optInt("verified"),
            badgeKey = json.string("badge_key")
        )
    }
}

/**
 * The part of a profile that can be read **and** written.
 *
 * Exactly the server's `Profile` schema, which is what every write answers with:
 * change the bio, the name, the privacy or the picture and the reply is the whole
 * of this, as it now stands. Kept as its own type for that reason - a write hands
 * back a `Profile`, not a `Me`, and merging one into the other
 * ([MessengerProfile.with]) is safer than parsing a partial reply into a full
 * profile and silently blanking the account facts that were not in it.
 */
data class ProfileFields(
    val fullname: String,
    val bio: String,
    val profilePicture: String,
    /** Empty while the account still has the app's own default banner. */
    val coverPicture: String,
    /** Epoch ms, 0 when never set. */
    val birthday: Long,
    /** `Male`, `Female`, or empty. */
    val gender: String,
    val country: String,
    val location: String,
    val whoCanMessage: String,
    val whoCanCall: String,
    /** False while the name was changed less than sixty days ago. */
    val nameCanChange: Boolean,
    /** Epoch ms the name may change from; 0 when it may change now. */
    val nameNextChangeAt: Long
) {
    companion object {
        fun from(json: JSONObject): ProfileFields {
            val privacy = json.optJSONObject("privacy_settings")
            val name = json.optJSONObject("name_change")
            return ProfileFields(
                // Names come back with stray whitespace ("Mithun sarker "), which
                // would show up against the verified tick beside it.
                fullname = json.string("fullname").trim(),
                bio = json.string("bio"),
                profilePicture = json.string("profile_picture"),
                coverPicture = json.string("cover_picture"),
                birthday = json.optLong("birthday"),
                gender = json.string("gender"),
                country = json.string("country"),
                location = json.string("location"),
                whoCanMessage = privacy?.string("who_can_message").orEmpty(),
                whoCanCall = privacy?.string("who_can_call").orEmpty(),
                nameCanChange = name?.optBoolean("can_change", true) ?: true,
                nameNextChangeAt = name?.optLong("next_change_at") ?: 0L
            )
        }
    }
}

/**
 * The signed-in user's messenger profile.
 *
 * Richer than what login returns and than anything the account API offers — it has
 * no `/me` at all — so this is the only source for bio, verification, badge, chat
 * status and privacy settings.
 *
 * No longer read-only. The server grew the write half of it - `/v1/me/profile`,
 * `/v1/me/name`, `/v1/me/privacy` and `/v1/me/images/{kind}` - and [ProfileApi] is
 * where those live. Each of them answers with a [ProfileFields], which [with]
 * folds back in here.
 */
data class MessengerProfile(
    val uid: String,
    val fullname: String,
    val phoneNumber: String,
    val bio: String,
    val profilePicture: String,
    /** The banner behind the picture; empty means the app's own default. */
    val coverPicture: String,
    /** Epoch ms, 0 when never set. */
    val birthday: Long,
    /** `Male`, `Female`, or empty when not set. */
    val gender: String,
    val country: String,
    val location: String,
    /** False while this account changed its name less than sixty days ago. */
    val nameCanChange: Boolean,
    /** Epoch ms the name may change from; 0 when it may change now. */
    val nameNextChangeAt: Long,
    val verified: Int,
    val badgeKey: String,
    /** One of `online`, `offline`, `away`, `busy`. */
    val chatStatus: String,
    val chatLastSeen: Long,
    val showActiveStatus: Boolean,
    /** Whether this person lets other people screenshot them; see [ChatPerson]. */
    val allowScreenshot: Boolean,
    val whoCanMessage: String,
    val whoCanCall: String,
    val followers: Int,
    val following: Int,
    /** Posts only — reels are a separate collection and are not counted. */
    val totalPost: Int
) {
    val isVerified: Boolean get() = verified != 0

    /**
     * This profile with everything a write just changed folded into it.
     *
     * A write answers with the `Profile` half only - no uid, no phone number, no
     * follower counts - so replacing the whole object with what came back would
     * empty half the screen. This keeps the account's facts and takes the rest.
     */
    fun with(fields: ProfileFields): MessengerProfile = copy(
        fullname = fields.fullname,
        bio = fields.bio,
        profilePicture = fields.profilePicture,
        coverPicture = fields.coverPicture,
        birthday = fields.birthday,
        gender = fields.gender,
        country = fields.country,
        location = fields.location,
        whoCanMessage = fields.whoCanMessage,
        whoCanCall = fields.whoCanCall,
        nameCanChange = fields.nameCanChange,
        nameNextChangeAt = fields.nameNextChangeAt
    )

    /** Presence is only shown when the user has not switched it off. */
    val isOnline: Boolean get() = showActiveStatus && chatStatus.equals("online", true)

    /**
     * Back into the shape [from] reads, for keeping on disk.
     *
     * Needed because the profile on screen is not always one the server sent
     * whole: a write answers with half of it and [with] folds that in. What is
     * stored is what is shown.
     */
    fun toJson(): JSONObject = JSONObject()
        .put("uid", uid)
        .put("fullname", fullname)
        .put("phone_number", phoneNumber)
        .put("bio", bio)
        .put("profile_picture", profilePicture)
        .put("cover_picture", coverPicture)
        .put("birthday", birthday)
        .put("gender", gender)
        .put("country", country)
        .put("location", location)
        .put(
            "privacy_settings",
            JSONObject()
                .put("who_can_message", whoCanMessage)
                .put("who_can_call", whoCanCall)
        )
        .put(
            "name_change",
            JSONObject()
                .put("can_change", nameCanChange)
                .put("next_change_at", nameNextChangeAt)
        )
        .put("verified", verified)
        .put("badge_key", badgeKey)
        .put("chat_status", chatStatus)
        .put("chat_last_seen", chatLastSeen)
        .put("show_active_status", showActiveStatus)
        .put("allow_screenshot", allowScreenshot)
        .put("followers", followers)
        .put("following", following)
        .put("total_post", totalPost)

    companion object {
        fun from(json: JSONObject): MessengerProfile {
            // `Me` is the `Profile` schema plus the account's own facts, so the
            // half they share is parsed in one place rather than twice.
            val fields = ProfileFields.from(json)
            return MessengerProfile(
                uid = json.string("uid"),
                fullname = fields.fullname,
                phoneNumber = json.string("phone_number"),
                bio = fields.bio,
                profilePicture = fields.profilePicture,
                coverPicture = fields.coverPicture,
                birthday = fields.birthday,
                gender = fields.gender,
                country = fields.country,
                location = fields.location,
                nameCanChange = fields.nameCanChange,
                nameNextChangeAt = fields.nameNextChangeAt,
                verified = json.optInt("verified"),
                badgeKey = json.string("badge_key"),
                chatStatus = json.string("chat_status"),
                chatLastSeen = json.optLong("chat_last_seen"),
                showActiveStatus = json.optBoolean("show_active_status", true),
                allowScreenshot = json.optBoolean("allow_screenshot", true),
                whoCanMessage = fields.whoCanMessage,
                whoCanCall = fields.whoCanCall,
                followers = json.optInt("followers"),
                following = json.optInt("following"),
                totalPost = json.optInt("total_post")
            )
        }
    }
}

data class ContactSyncResult(
    val synced: Int,
    val invalid: Int,
    val removed: Int,
    val matches: List<ContactMatch>
)

object MessengerApi {

    /** The signed-in user's messenger profile. */
    suspend fun me(token: String): ApiResult<MessengerProfile> =
        when (
            val result = ApiClient.get(
                path = "/v1/me",
                token = token,
                baseUrl = MessengerConfig.BASE_URL
            )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success ->
                ApiResult.Success(MessengerProfile.from(result.data), result.message)
        }

    /**
     * Replaces the stored address book with [phones] and returns the contacts who
     * are on IQ Social.
     *
     * This is a **full** sync by design: the spec is explicit that numbers not sent
     * are removed, so this must always be the whole address book, never a delta.
     *
     * Numbers go up exactly as they are saved on the device. The server normalises
     * them using [region], which is why a contact stored as `01712345678` resolves
     * the same as `+8801712345678` — doing that work here would only risk
     * disagreeing with it.
     */
    suspend fun syncContacts(
        phones: List<String>,
        region: String?,
        token: String
    ): ApiResult<ContactSyncResult> {
        val payload = phones
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it.length <= MessengerConfig.MAX_PHONE_LENGTH }
            .take(MessengerConfig.MAX_PHONES)
            .toList()

        val body = JSONObject().put("phones", JSONArray(payload))
        if (!region.isNullOrBlank()) body.put("region", region.uppercase())

        return when (
            val result = ApiClient.post(
                path = "/v1/contacts/sync",
                body = body,
                token = token,
                baseUrl = MessengerConfig.BASE_URL
            )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val matches = result.data.optJSONArray("matches")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let(ContactMatch::from)
                    }
                }.orEmpty()

                ApiResult.Success(
                    ContactSyncResult(
                        synced = result.data.optInt("synced"),
                        invalid = result.data.optInt("invalid"),
                        removed = result.data.optInt("removed"),
                        matches = matches
                    ),
                    result.message
                )
            }
        }
    }
}
