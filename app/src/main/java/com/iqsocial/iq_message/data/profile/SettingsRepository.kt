package com.iqsocial.iq_message.data.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.MessengerSettings
import com.iqsocial.iq_message.data.remote.NotificationPreview
import com.iqsocial.iq_message.data.remote.SettingsApi

/**
 * The account's messenger settings, fetched once per launch.
 *
 * Alongside [MeRepository] and shaped like it, because they are read together: the
 * profile screen shows both and neither should be requested twice for a tab change.
 *
 * Settings belong to the **account**, not to this device - the spec says a change
 * applies to every device from the next push on - so there is nothing to keep on
 * disk. What the server says is the answer, and asking it once a launch is enough.
 */
object SettingsRepository {

    var settings by mutableStateOf<MessengerSettings?>(null)
        private set

    /** True once a fetch has finished, successfully or not. */
    var loaded by mutableStateOf(false)
        private set

    /** Why the last read or write failed, for screens that want to say so. */
    var error by mutableStateOf<String?>(null)
        private set

    /** True while a change is in flight, so the control can be held still. */
    var saving by mutableStateOf(false)
        private set

    /** What is in force, or the server's own default until the first fetch lands. */
    val notificationPreview: NotificationPreview
        get() = settings?.notificationPreview ?: NotificationPreview.NAME_AND_TEXT

    /** Whether others can see this account online, same reading as above. */
    val showActiveStatus: Boolean
        get() = settings?.showActiveStatus ?: true

    suspend fun load(token: String?) {
        if (token.isNullOrBlank()) {
            loaded = true
            return
        }
        when (val result = SettingsApi.get(token)) {
            is ApiResult.Success -> {
                settings = result.data
                error = null
            }
            is ApiResult.Failure -> error = result.message
        }
        loaded = true
    }

    /**
     * Changes the preview and keeps what the server says it became.
     *
     * Not optimistic, deliberately. An optimistic switch is right when the change is
     * local and the network is a formality; this one is neither - it is the server
     * that will or will not strip the text out of the next push, and showing the new
     * setting before it has agreed would be claiming a privacy guarantee this app
     * has not been given yet. [saving] is what the UI shows in the meantime.
     *
     * Returns true if it took, so a caller can say so without reading [error].
     */
    suspend fun setNotificationPreview(
        token: String?,
        preview: NotificationPreview
    ): Boolean {
        if (token.isNullOrBlank()) return false
        if (preview == settings?.notificationPreview) return true
        saving = true
        val ok = when (val result = SettingsApi.setNotificationPreview(token, preview)) {
            is ApiResult.Success -> {
                // The reply, not the request: if the server settled on something
                // other than what was asked for, that is what is in force.
                settings = result.data
                error = null
                true
            }
            is ApiResult.Failure -> {
                error = result.message
                false
            }
        }
        saving = false
        return ok
    }

    /**
     * Shows or hides this account's online state, and keeps what the server says.
     *
     * Not optimistic, for the same reason as the preview above and more sharply: a
     * switch that slid to "hidden" before the server agreed would be showing someone
     * a privacy guarantee that had not been made. [saving] covers the wait.
     */
    suspend fun setActiveStatus(token: String?, show: Boolean): Boolean {
        if (token.isNullOrBlank()) return false
        if (show == settings?.showActiveStatus) return true
        saving = true
        val ok = when (val result = SettingsApi.setActiveStatus(token, show)) {
            is ApiResult.Success -> {
                settings = result.data
                error = null
                true
            }
            is ApiResult.Failure -> {
                error = result.message
                false
            }
        }
        saving = false
        return ok
    }

    /** Clears the last complaint once a screen has shown it. */
    fun clearError() {
        error = null
    }

    fun clear() {
        settings = null
        loaded = false
        error = null
        saving = false
    }
}
