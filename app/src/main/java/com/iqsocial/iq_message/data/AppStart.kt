package com.iqsocial.iq_message.data

import android.content.Context
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.data.profile.SettingsRepository
import com.iqsocial.iq_message.data.push.SocketAlerts
import com.iqsocial.iq_message.data.remote.ChatSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What a signed-in process needs before it can show a conversation.
 *
 * All of this used to live inside the app's own composition, which meant it ran
 * when - and only when - [com.iqsocial.iq_message.MainActivity] was on screen. A
 * bubble does not go through there: it is its own activity hosting one chat, and in
 * a process woken by a push it was the first thing running. So a chat opened from a
 * bubble had no socket, no profile and no settings: nothing arrived live, and the
 * thread was whatever one HTTP fetch returned.
 *
 * Idempotent and cheap to call. The socket ignores a second connect with the same
 * token, and the two repositories keep what they already hold.
 *
 * It is not the Application class, deliberately. A push wakes this process without
 * anything to show, and starting a socket and two requests on the way to drawing a
 * notification would make every message slower for nobody's benefit. This runs
 * where a conversation is about to be read.
 */
object AppStart {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun ensure(context: Context) {
        val app = context.applicationContext
        val token = SessionStore(app).token
        if (token.isNullOrBlank()) return

        CallCenter.init(app)
        ChatSocket.connect(token)
        SocketAlerts.start(app)
        scope.launch {
            MeRepository.load(token)
            SettingsRepository.load(token)
        }
    }
}
