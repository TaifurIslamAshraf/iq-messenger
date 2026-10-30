package com.iqsocial.iq_message.data.remote

import org.json.JSONObject

/**
 * This install's push token, as the server knows it.
 *
 * The spec is explicit that a token belonging to another account **moves** to yours
 * when you register it, so one phone only ever rings for whoever is signed in on it.
 * That is why unregistering on log-out matters: without it the next person to sign in
 * on this handset inherits nothing, but the previous account keeps a token pointing
 * at a phone that is no longer theirs.
 */
object DevicesApi {

    /**
     * Registers [token] against the signed-in account.
     *
     * Safe to repeat, which is what makes it callable on every launch as well as
     * from `onNewToken` - there is no need to track whether it was sent before.
     */
    suspend fun register(authToken: String, token: String): ApiResult<Unit> =
        ApiClient.post(
            "/v1/devices",
            JSONObject().put("token", token),
            authToken,
            MessengerConfig.BASE_URL
        ).mapTo { }

    /**
     * Drops it again, before the login token is discarded.
     *
     * Succeeds whether or not it was registered, so a log-out never has to care.
     */
    suspend fun unregister(authToken: String, token: String): ApiResult<Unit> =
        ApiClient.post(
            "/v1/devices/unregister",
            JSONObject().put("token", token),
            authToken,
            MessengerConfig.BASE_URL
        ).mapTo { }
}
