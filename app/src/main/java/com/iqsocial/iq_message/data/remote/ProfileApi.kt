package com.iqsocial.iq_message.data.remote

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Which of the two pictures an account has.
 *
 * The server takes it in the path and limits the two differently - two megabytes
 * for a face, five for a banner - so the size belongs to the kind rather than to
 * the caller remembering which is which.
 */
enum class ProfileImage(
    val wire: String,
    val maxBytes: Long,
    val maxEdge: Int,
    /**
     * Width over height, which is the shape the picture is cropped to.
     *
     * Decided here rather than left to whatever came out of the camera, because
     * every place that draws one of these crops it anyway: a square avatar and a
     * wide banner. Cropping before the upload means the reader chooses what is cut
     * off, instead of finding out afterwards that a screen took the middle of
     * their photo and left their head out of it.
     */
    val aspect: Float
) {

    /** The round one. */
    PICTURE("picture", 2L * 1024 * 1024, 1080, 1f),

    /** The wide one behind it. */
    COVER("cover", 5L * 1024 * 1024, 1600, 16f / 9f)
}

/** Step one of a picture change: where to put the bytes, and what to call them. */
@Immutable
data class ProfileImageSlot(
    /** Handed back to `complete` once the bytes are there. */
    val key: String,
    val upload: UploadTarget
) {
    companion object {
        fun from(json: JSONObject): ProfileImageSlot = ProfileImageSlot(
            key = json.string("key"),
            upload = UploadTarget.from(json.optJSONObject("upload") ?: JSONObject())
        )
    }
}

/**
 * What the server says when a name may not change yet.
 *
 * A name changes once every sixty days, counted across the messenger **and** the
 * main IQ Social app, because it is one account and one clock. The refusal carries
 * the date rather than only a sentence, so the screen can say how long is left
 * without asking a second question.
 */
@Immutable
data class NameCooldown(val nextChangeAt: Long, val daysRemaining: Int) {
    companion object {
        const val CODE = "NAME_CHANGE_COOLDOWN"

        /** The cooldown inside a refusal, or null if that is not what it was. */
        fun of(failure: ApiResult.Failure): NameCooldown? {
            val body = failure.payload ?: return null
            if (body.string("code") != CODE) return null
            return NameCooldown(
                nextChangeAt = body.optLong("next_change_at"),
                daysRemaining = body.optInt("days_remaining")
            )
        }
    }
}

/**
 * Writing to the signed-in account's own profile.
 *
 * Four endpoints and one shape: every one of them answers with the whole
 * [ProfileFields] as it now stands, so nothing here has to guess what took. The
 * name is split out from the rest because it is rationed - sixty days between
 * changes - and the pictures are split out because they are bytes rather than
 * fields.
 *
 * `PATCH` bodies carry **only what changed**. That is the server's instruction and
 * it matters for more than tidiness: sending every field back would write values
 * nobody touched, and would quietly undo a change made on the main IQ Social app
 * between this screen's read and its write.
 */
object ProfileApi {

    /**
     * Bio, gender, birthday, country, location - any of them, none of the rest.
     *
     * A bio or a location can be cleared by sending an empty string; a gender, a
     * birthday or a country can only be replaced, which is the server's rule and
     * the reason those three are not nullable here in the sense the others are.
     */
    suspend fun updateProfile(
        token: String,
        bio: String? = null,
        gender: String? = null,
        birthday: Long? = null,
        country: String? = null,
        location: String? = null
    ): ApiResult<ProfileFields> {
        val body = JSONObject()
        bio?.let { body.put("bio", it) }
        gender?.takeIf { it.isNotBlank() }?.let { body.put("gender", it) }
        birthday?.let { body.put("birthday", it) }
        country?.takeIf { it.isNotBlank() }?.let { body.put("country", it) }
        location?.let { body.put("location", it) }
        return ApiClient.patch("/v1/me/profile", body, token, MessengerConfig.BASE_URL)
            .mapTo(ProfileFields::from)
    }

    /**
     * Changes the name, which is allowed once every sixty days.
     *
     * Sending the name the account already has changes nothing and does **not**
     * start the sixty days, so there is no need to guard against a save that did
     * not really edit the name - though the screen does anyway, because a request
     * that cannot do anything is still a request.
     *
     * A refusal inside the window is a 409 carrying [NameCooldown].
     */
    suspend fun rename(token: String, fullname: String): ApiResult<ProfileFields> =
        ApiClient.put(
            "/v1/me/name",
            JSONObject().put("fullname", fullname),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ProfileFields::from)

    /**
     * Who may message.
     *
     * Shared with the main IQ Social app. Stored and returned but not yet enforced
     * by the messenger. Who may call is not an account setting: it is answered per
     * person, in each chat ([ConversationsApi.allowCalls]).
     */
    suspend fun updatePrivacy(
        token: String,
        whoCanMessage: String? = null
    ): ApiResult<ProfileFields> {
        val body = JSONObject()
        whoCanMessage?.let { body.put("who_can_message", it) }
        return ApiClient.patch("/v1/me/privacy", body, token, MessengerConfig.BASE_URL)
            .mapTo(ProfileFields::from)
    }

    /**
     * Step one of two: asks for somewhere to put a new picture.
     *
     * The size is declared to the byte and the type to the string, and the link is
     * signed for exactly those - so the bytes that follow have to be the same bytes
     * this was called for. Nothing on the profile changes yet; twenty links an hour.
     */
    suspend fun imageSlot(
        token: String,
        kind: ProfileImage,
        mimeType: String,
        size: Long
    ): ApiResult<ProfileImageSlot> =
        ApiClient.post(
            "/v1/me/images/${kind.wire}/upload-url",
            JSONObject().put("mime_type", mimeType).put("size", size),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ProfileImageSlot::from)

    /**
     * Step two of two: makes the uploaded file the account's picture.
     *
     * The server checks the bytes arrived and are an image it accepts before
     * publishing them where the main IQ Social app shows them too, and deletes the
     * picture this one replaces. 409 means the upload has not landed yet, which is
     * worth retrying; 400 means the file was refused, which is not.
     */
    suspend fun completeImage(
        token: String,
        kind: ProfileImage,
        key: String
    ): ApiResult<ProfileFields> =
        ApiClient.post(
            "/v1/me/images/${kind.wire}/complete",
            JSONObject().put("key", key),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ProfileFields::from)

    /** Takes the picture away: back to no picture, or to the default banner. */
    suspend fun removeImage(token: String, kind: ProfileImage): ApiResult<ProfileFields> =
        ApiClient.delete(
            "/v1/me/images/${kind.wire}",
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ProfileFields::from)
}
