package com.iqsocial.iq_message.data.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.iqsocial.iq_message.MainActivity
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.MessagesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Answers a notification without opening the app.
 *
 * A broadcast receiver rather than an activity, because the point of replying from
 * the shade is not leaving what you were doing. Starting the app to send one word
 * would undo that.
 *
 * Both actions send an ordinary message through the ordinary route - there is no
 * special "quick reply" endpoint, and a reply typed here is the same thing as one
 * typed in the thread.
 */
class NotificationActions : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        // Calls first: they have their own two actions and no text to carry.
        when (intent.action) {
            ACTION_ANSWER -> {
                // The app has to come forward either way - there is a call to show,
                // and on Android 10 and up a receiver cannot start an activity
                // without one, so the accept rides on the launch.
                val open = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_ANSWER, true)
                }
                runCatching { context.startActivity(open) }
                return
            }

            ACTION_DECLINE -> {
                CallCenter.decline(context.applicationContext)
                return
            }
        }

        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION).orEmpty()
        if (conversationId.isBlank()) return

        val text = when (intent.action) {
            ACTION_LIKE -> LIKE
            ACTION_REPLY -> RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(KEY_REPLY)
                ?.toString()
                ?.trim()
                .orEmpty()

            else -> return
        }
        if (text.isBlank()) return

        val app = context.applicationContext
        val token = SessionStore(app).token ?: return

        // The line goes up straight away, so the shade answers the tap rather than
        // sitting still until the network does.
        MessageNotifications.addOwnLine(app, conversationId, text)

        // goAsync keeps the process alive past onReceive, which otherwise ends the
        // moment this returns and takes the request with it.
        val pending = goAsync()
        scope.launch {
            try {
                val sent = MessagesApi.send(
                    token = token,
                    conversationId = conversationId,
                    clientId = UUID.randomUUID().toString(),
                    text = text
                )
                if (sent is ApiResult.Success) {
                    ChatRepository.invalidate()
                } else {
                    MessageNotifications.markFailed(app, conversationId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.iqsocial.iq_message.REPLY"
        const val ACTION_LIKE = "com.iqsocial.iq_message.LIKE"
        const val ACTION_ANSWER = "com.iqsocial.iq_message.ANSWER"
        const val ACTION_DECLINE = "com.iqsocial.iq_message.DECLINE"
        const val EXTRA_CONVERSATION = "conversation_id"
        const val EXTRA_CALL = "call_id"

        /** Set on the launch intent when the app is opened by answering. */
        const val EXTRA_ANSWER = "answer_call"
        const val KEY_REPLY = "reply"

        /** What the Like button sends, as Messenger's does: a message, not a reaction. */
        const val LIKE = "👍"
    }
}
