package com.iqsocial.iq_message.data.remote

import android.net.Uri
import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * One install's public key, as the server hands it back.
 *
 * A person is not a key here - a person is a handful of devices, each with its own
 * key, and a call key has to be wrapped once per device or that device cannot join.
 * [updatedAt] is what makes a change noticeable: a key that is newer than the one
 * last seen means either a reinstall or somebody in the middle, and the spec is
 * explicit that the app should say so rather than swap it silently.
 */
@Immutable
data class DeviceKey(
    val deviceId: String,
    /** The X25519 public key: 32 bytes, base64. */
    val publicKey: String,
    val updatedAt: Long
) {
    companion object {
        fun from(json: JSONObject): DeviceKey = DeviceKey(
            deviceId = json.optString("device_id"),
            publicKey = json.optString("public_key"),
            updatedAt = json.optLong("updated_at")
        )
    }
}

/** Everyone a key was asked for, and the devices of theirs that can decrypt. */
@Immutable
data class PersonKeys(
    val uid: String,
    /** Newest first, at most five. Empty when none of their devices can decrypt. */
    val keys: List<DeviceKey>
) {
    /** Nothing to wrap to, so this person cannot be in an encrypted call. */
    val canDecrypt: Boolean get() = keys.isNotEmpty()
}

/**
 * `/v1/keys` - where the public halves live.
 *
 * The private half never appears in this file and never leaves the phone; that is
 * the whole arrangement. All the server does is carry public keys between people,
 * which is also why it cannot be trusted to do it honestly - hence the fingerprints
 * the spec asks to be shown, and the warning when one changes.
 */
object KeysApi {

    /** The server takes at most eight at a time. */
    const val MAX_UIDS = 8

    /**
     * Publishes this install's public key.
     *
     * Called after signing in, and again whenever a new pair is made - which
     * replaces the old one, so an install only ever has one key on the server.
     * Without it this device cannot answer an encrypted call: there is nothing for
     * the caller to wrap to.
     */
    suspend fun publish(
        token: String,
        deviceId: String,
        publicKey: String
    ): ApiResult<Unit> =
        ApiClient.post(
            path = "/v1/keys",
            body = JSONObject()
                .put("device_id", deviceId)
                .put("public_key", publicKey),
            token = token,
            baseUrl = MessengerConfig.BASE_URL
        ).mapTo { }

    /**
     * The newest devices of each person named, with their keys.
     *
     * People who cannot be found, or who have blocked the caller, are simply left
     * out of the reply rather than reported - so a missing uid is an answer, not an
     * error, and the caller should read the list rather than count it.
     */
    suspend fun of(token: String, uids: List<String>): ApiResult<List<PersonKeys>> {
        val wanted = uids.filter { it.isNotBlank() }.distinct().take(MAX_UIDS)
        if (wanted.isEmpty()) return ApiResult.Success(emptyList())
        val path = "/v1/keys?uids=" + Uri.encode(wanted.joinToString(","))
        return ApiClient.get(path, token, MessengerConfig.BASE_URL).mapTo { json ->
            json.optJSONArray("users").mapObjects { person ->
                PersonKeys(
                    uid = person.optString("uid"),
                    keys = person.optJSONArray("keys").mapObjects(DeviceKey::from)
                )
            }
        }
    }
}
