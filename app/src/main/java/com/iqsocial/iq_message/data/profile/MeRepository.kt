package com.iqsocial.iq_message.data.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.MessengerApi
import com.iqsocial.iq_message.data.remote.MessengerProfile

/**
 * The signed-in user's messenger profile, fetched once per launch.
 *
 * `/v1/me` is the only place this data exists — the account API has no `/me`, and
 * login returns just name, phone and picture. Bio, verification, badge, chat
 * status and privacy settings all come from here.
 */
object MeRepository {

    var profile by mutableStateOf<MessengerProfile?>(null)
        private set

    /** True once a fetch has finished, successfully or not. */
    var loaded by mutableStateOf(false)
        private set

    /** Why the last fetch failed, for screens that want to say so. */
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load(token: String?) {
        if (token.isNullOrBlank()) {
            loaded = true
            return
        }
        when (val result = MessengerApi.me(token)) {
            is ApiResult.Success -> {
                profile = result.data
                error = null
            }
            is ApiResult.Failure -> error = result.message
        }
        loaded = true
    }

    fun clear() {
        profile = null
        loaded = false
        error = null
    }
}
