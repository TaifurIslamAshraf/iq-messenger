package com.iqsocial.iq_message.data.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.remote.DevicesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Keeps the server's idea of this phone's push token in step with Firebase's.
 *
 * Two things can change it and they are independent: signing in, and Firebase
 * reissuing the token. Both end up here, and registering is safe to repeat, so
 * neither has to know whether the other has already run.
 */
object PushTokens {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Asks Firebase for the current token and hands it to the server.
     *
     * Called on every launch while signed in rather than once at sign-in. A token
     * can be reissued while the app is not running - a restore to a new device, data
     * cleared, the app reinstalled - and `onNewToken` only fires if the app is there
     * to hear it.
     */
    fun register(context: Context) {
        val session = SessionStore(context)
        val auth = session.token ?: return
        scope.launch {
            val token = current() ?: return@launch
            DevicesApi.register(auth, token)
        }
    }

    /** The server was told by [IqMessagingService] that Firebase issued a new one. */
    fun register(context: Context, token: String) {
        val auth = SessionStore(context).token ?: return
        scope.launch { DevicesApi.register(auth, token) }
    }

    /**
     * Drops the token before the login is discarded.
     *
     * Takes the auth token as an argument rather than reading it: by the time a
     * log-out gets here the session may already be cleared, and unregistering with
     * no credentials would silently do nothing - leaving the account ringing a phone
     * that has signed out of it.
     */
    fun unregister(authToken: String, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                current()?.let { DevicesApi.unregister(authToken, it) }
            } finally {
                onDone()
            }
        }
    }

    /** Firebase's callback-shaped API, waited on properly. */
    private suspend fun current(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
                .addOnCanceledListener { cont.resume(null) }
        }.onFailure { cont.resume(null) }
    }
}
