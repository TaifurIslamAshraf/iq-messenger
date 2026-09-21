package com.iqsocial.iq_message.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.iqsocial.iq_message.data.remote.AuthUser

/**
 * Keeps the signed-in user on disk so the app opens straight into the chat list
 * instead of the login screen every launch.
 *
 * SharedPreferences rather than DataStore: a handful of scalars, and it keeps the
 * module dependency-free.
 */
class SessionStore(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("iq_session", Context.MODE_PRIVATE)

    val token: String? get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    val isSignedIn: Boolean get() = token != null

    /**
     * Stable identifier for this install, required by `/signup`.
     *
     * The server rejects an empty one and binds it to the account it creates
     * ("This device is already registered to another account"), so it has to be
     * stable across launches — a fresh value each time would make every signup look
     * like a new device.
     *
     * ANDROID_ID is per app-signing-key per user per device and needs no permission.
     * It can be null on some builds, so the fallback is a UUID kept in prefs.
     */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null)?.takeIf { it.isNotBlank() } ?: run {
            val androidId = runCatching {
                android.provider.Settings.Secure.getString(
                    appContext.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
            }.getOrNull()

            val id = androidId?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                ?: java.util.UUID.randomUUID().toString()

            prefs.edit().putString(KEY_DEVICE, id).apply()
            id
        }

    /**
     * Whether the contacts popup has been shown for this session. Cleared with the
     * rest of the session on log-out, so the next person to sign in is asked.
     */
    var contactsPrompted: Boolean
        get() = prefs.getBoolean(KEY_CONTACTS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CONTACTS, value).apply()
        }

    fun save(user: AuthUser) {
        prefs.edit()
            .putString(KEY_TOKEN, user.token)
            .putString(KEY_UID, user.uid)
            .putString(KEY_NAME, user.fullname)
            .putString(KEY_PHONE, user.phone)
            .putString(KEY_PICTURE, user.profilePicture)
            .putString(KEY_REF, user.refCode)
            .apply()
    }

    fun load(): AuthUser? {
        val t = token ?: return null
        return AuthUser(
            uid = prefs.getString(KEY_UID, "").orEmpty(),
            fullname = prefs.getString(KEY_NAME, "").orEmpty(),
            phone = prefs.getString(KEY_PHONE, "").orEmpty(),
            profilePicture = prefs.getString(KEY_PICTURE, "").orEmpty(),
            refCode = prefs.getString(KEY_REF, "").orEmpty(),
            token = t
        )
    }

    /** Signs out. The device id survives: it identifies the install, not the session. */
    fun clear() {
        val device = prefs.getString(KEY_DEVICE, null)
        prefs.edit().clear().apply()
        if (device != null) prefs.edit().putString(KEY_DEVICE, device).apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_CONTACTS = "contacts_prompted"
        const val KEY_UID = "uid"
        const val KEY_NAME = "fullname"
        const val KEY_PHONE = "phone"
        const val KEY_PICTURE = "profile_picture"
        const val KEY_REF = "ref_code"
        const val KEY_DEVICE = "device_id"
    }
}
