package com.iqsocial.iq_message.data.remote

import android.net.Uri
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
    val showActiveStatus: Boolean,
    /**
     * Whether other people may take a screenshot of this account.
     *
     * The server's words: off, "the app asks the device to block screenshots of your
     * profile while someone else views it". So the setting travels with the account
     * and is enforced on the *other* person's phone - which is the only place it can
     * be enforced, since a screenshot is taken there.
     *
     * Defaults to allowed, which is both the server's default and the only safe way
     * round for a field this build might not recognise: claiming a block that is not
     * in force would be worse than admitting there is none.
     */
    val allowScreenshot: Boolean,
    /** Whether this account is told when somebody screenshots it. */
    val notifyOnScreenshot: Boolean
) {
    companion object {
        fun from(json: JSONObject): MessengerSettings = MessengerSettings(
            notificationPreview = NotificationPreview.from(json.string("notification_preview")),
            showActiveStatus = json.optBoolean("show_active_status", true),
            allowScreenshot = json.optBoolean("allow_screenshot", true),
            notifyOnScreenshot = json.optBoolean("notify_on_screenshot", true)
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
     * Whether this account may screenshot [uid], asked of the server.
     *
     * The one setting of somebody else's that is any of this phone's business, and
     * the server shares only that one: `allow_screenshot`, for the person named,
     * and nothing else of theirs. It exists because the setting is enforced on the
     * looker's phone - a screenshot is taken there - so the looker has to be told.
     *
     * Everything that is not a plain yes answers 404 by design: an unknown id, a
     * banned account, somebody who has blocked you. That is a failure here and
     * [PeerPrivacy] decides what to do about it, which is not "lock the window" -
     * a 404 is not a request for privacy, it is a person this phone cannot see.
     */
    suspend fun allowsScreenshot(token: String, uid: String): ApiResult<Boolean> =
        ApiClient.get(
            "/v1/settings/users/${Uri.encode(uid)}",
            token,
            MessengerConfig.BASE_URL
        ).mapTo { it.optBoolean("allow_screenshot", true) }

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
     * Lets other people screenshot this account, or asks that they cannot.
     *
     * Enforced on their phone rather than this one - theirs is where the button is
     * pressed - so this call only records the wish. What makes it real is the other
     * app reading it off this account and locking its own window while it shows
     * this person's chat, call or profile.
     */
    suspend fun setAllowScreenshot(
        token: String,
        allow: Boolean
    ): ApiResult<MessengerSettings> =
        patch(token, JSONObject().put("allow_screenshot", allow))

    /** Asks to be told when somebody screenshots this account, or not to be. */
    suspend fun setNotifyOnScreenshot(
        token: String,
        notify: Boolean
    ): ApiResult<MessengerSettings> =
        patch(token, JSONObject().put("notify_on_screenshot", notify))

    /**
     * Tells the server this phone just screenshotted [uid].
     *
     * Whether anybody hears about it is the server's decision, not this app's: it
     * checks that person's `notify_on_screenshot` before it saves a notification
     * row or sends anything. So this is called whenever a screenshot of somebody
     * is taken, without asking first what they would want - asking would mean this
     * app holding a second copy of their setting, and the answer comes back anyway.
     *
     * Answers `notified`: true when they were told, false when they had it off or
     * the screenshot was of this account's own profile.
     */
    suspend fun reportScreenshot(
        token: String,
        uid: String,
        /**
         * True when the system refused the capture and this is only the attempt.
         *
         * Sent so that the notification can say which it was - "took" and "tried
         * to take" are different things to the person being told, and only the
         * phone that did it knows the difference. The endpoint does not document a
         * body, so this is a field the server may simply ignore today; if it
         * refuses the request because of it, the report is sent again without it
         * rather than lost.
         */
        attempted: Boolean = false
    ): ApiResult<Boolean> {
        val path = "/v1/settings/users/${Uri.encode(uid)}/screenshot"
        val first = ApiClient.post(
            path,
            JSONObject().put("attempted", attempted),
            token,
            MessengerConfig.BASE_URL
        )
        val result = if (first is ApiResult.Failure && !first.offline && !first.notImplemented) {
            ApiClient.post(path, JSONObject(), token, MessengerConfig.BASE_URL)
        } else {
            first
        }
        return result.mapTo { it.optBoolean("notified") }
    }

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
