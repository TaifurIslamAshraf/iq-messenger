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
    const val BASE_URL = "https://messanger-server.gd2zih.easypanel.host"

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
            uid = json.optString("uid"),
            phone = json.optString("phone"),
            fullname = json.optString("fullname"),
            profilePicture = json.optString("profile_picture"),
            verified = json.optInt("verified"),
            badgeKey = json.optString("badge_key")
        )
    }
}

/**
 * The signed-in user's messenger profile.
 *
 * Richer than what login returns and than anything the account API offers — it has
 * no `/me` at all — so this is the only source for bio, verification, badge, chat
 * status and privacy settings.
 *
 * Read-only: `PUT`, `PATCH` and `POST` on `/v1/me` all 404, so nothing here can be
 * changed from the app yet.
 */
data class MessengerProfile(
    val uid: String,
    val fullname: String,
    val phoneNumber: String,
    val bio: String,
    val profilePicture: String,
    val verified: Int,
    val badgeKey: String,
    /** One of `online`, `offline`, `away`, `busy`. */
    val chatStatus: String,
    val chatLastSeen: Long,
    val showActiveStatus: Boolean,
    val whoCanMessage: String,
    val whoCanCall: String,
    val followers: Int,
    val following: Int,
    /** Posts only — reels are a separate collection and are not counted. */
    val totalPost: Int
) {
    val isVerified: Boolean get() = verified != 0

    /** Presence is only shown when the user has not switched it off. */
    val isOnline: Boolean get() = showActiveStatus && chatStatus.equals("online", true)

    companion object {
        fun from(json: JSONObject): MessengerProfile {
            val privacy = json.optJSONObject("privacy_settings")
            return MessengerProfile(
                uid = json.optString("uid"),
                // Names come back with stray whitespace ("Mithun sarker "), which
                // would show up against the verified tick beside it.
                fullname = json.optString("fullname").trim(),
                phoneNumber = json.optString("phone_number"),
                bio = json.optString("bio"),
                profilePicture = json.optString("profile_picture"),
                verified = json.optInt("verified"),
                badgeKey = json.optString("badge_key"),
                chatStatus = json.optString("chat_status"),
                chatLastSeen = json.optLong("chat_last_seen"),
                showActiveStatus = json.optBoolean("show_active_status", true),
                whoCanMessage = privacy?.optString("who_can_message").orEmpty(),
                whoCanCall = privacy?.optString("who_can_call").orEmpty(),
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
