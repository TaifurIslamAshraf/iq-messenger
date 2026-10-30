package com.iqsocial.iq_message.data.remote

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * How much of a message its notification is allowed to show.
 *
 * Not a display preference. The spec is explicit that **hidden parts are never sent
 * through FCM**, so this decides what the push itself contains - which makes it the
 * only setting here that is also a privacy control. A phone that shows notifications
 * on its lock screen cannot leak what the server never sent it.
 *
 * Which is also why there is nothing to do on this side to honour it: the push for
 * an account set to `nothing` arrives with no sender and no text, and
 * `IqMessagingService` already writes whatever is missing as a stand-in line.
 */
enum class NotificationPreview(val wire: String) {

    /** Who sent it and the start of the text. The server's default. */
    NAME_AND_TEXT("name_and_text"),

    /** Who sent it, and nothing of what they said. */
    NAME_ONLY("name_only"),

    /** Only that a message arrived. */
    NOTHING("nothing");

    companion object {
        /**
         * Falls back to the default rather than failing.
         *
         * An unknown value means the server has grown an option this build does not
         * know, and treating that as an error would break the settings screen over a
         * string. The default is what a new account has anyway.
         */
        fun from(value: String?): NotificationPreview =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: NAME_AND_TEXT
    }
}

/**
 * Every messenger setting the account has.
 *
 * Kept as an object rather than returning the fields bare so that the next setting
 * is an addition here instead of a new signature everywhere.
 */
@Immutable
data class MessengerSettings(
    val notificationPreview: NotificationPreview,
    /**
     * Whether other people see when this account is online, and when it was last
     * seen.
     *
     * Shown until it is turned off - which is what the missing-field default says.
     * `GET` answers with every setting in force, so the default is only ever reached
     * by a reply this build does not understand, and erring towards the visible
     * state there would be the wrong way round for a privacy control. It is the way
     * round the server documents, though: until it is changed here the account
     * follows the IQ Social setting, which shows it.
     */
    val showActiveStatus: Boolean
) {
    companion object {
        fun from(json: JSONObject): MessengerSettings = MessengerSettings(
            notificationPreview = NotificationPreview.from(json.optString("notification_preview")),
            showActiveStatus = json.optBoolean("show_active_status", true)
        )
    }
}

/**
 * `/v1/settings`, both verbs.
 *
 * `GET` always answers with every setting and its default, so there is no "unset"
 * state to represent - what comes back is what is in force.
 */
object SettingsApi {

    suspend fun get(token: String): ApiResult<MessengerSettings> =
        ApiClient.get("/v1/settings", token, MessengerConfig.BASE_URL)
            .mapTo(MessengerSettings::from)

    /**
     * Changes the preview, for every device on the account.
     *
     * It takes effect from the next push, not for messages already sent. Calls are
     * not covered by it: the server always says who is calling.
     */
    suspend fun setNotificationPreview(
        token: String,
        preview: NotificationPreview
    ): ApiResult<MessengerSettings> =
        patch(token, JSONObject().put("notification_preview", preview.wire))

    /**
     * Shows or hides this account's online and last-seen state.
     *
     * Takes effect at once rather than from the next message: the server sends a
     * presence event to whoever is watching, so the change is visible on their phone
     * before this one has finished drawing it.
     */
    suspend fun setActiveStatus(
        token: String,
        show: Boolean
    ): ApiResult<MessengerSettings> =
        patch(token, JSONObject().put("show_active_status", show))

    /**
     * One field at a time, which is what the endpoint asks for.
     *
     * The API's own words are "send only what changes", and nothing in the body is
     * required - so a patch carrying every setting would be writing back values
     * nobody touched, and would quietly undo a change made on another device between
     * this screen's read and its write.
     *
     * The reply carries the settings as they now stand, and that is what the caller
     * should keep: the server's word on the change rather than this side's
     * assumption that it took.
     */
    private suspend fun patch(token: String, body: JSONObject): ApiResult<MessengerSettings> =
        ApiClient.patch(
            path = "/v1/settings",
            body = body,
            token = token,
            baseUrl = MessengerConfig.BASE_URL
        ).mapTo(MessengerSettings::from)
}
