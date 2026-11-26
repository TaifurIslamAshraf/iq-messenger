package com.iqsocial.iq_message.data.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.remote.ChatMessage

/**
 * A message somebody picked on another screen, waiting for its chat to show it.
 *
 * The search screen sits on top of the chat it searches. Picking a result goes back
 * to that chat, and the chat - which may well have been rebuilt from nothing in the
 * meantime - has to learn where to scroll. Navigation here passes routes, not
 * answers, so the answer waits here instead: written by the screen that found it,
 * taken once by the chat it belongs to.
 *
 * Holds the message itself rather than its id. The chat may not have it - search
 * reaches back further than the thread has loaded - and the message carries its
 * `seq` and time, which is how the chat works out how far back to go.
 */
object ThreadJump {

    data class Target(val conversationId: String, val message: ChatMessage)

    var pending by mutableStateOf<Target?>(null)
        private set

    /**
     * Leaves [message] for the chat of [conversationId].
     *
     * The conversation is given rather than read off the message: the shared-media
     * listing does not always say which conversation its items belong to, and a
     * message with a blank one would never be taken by any chat.
     */
    fun request(conversationId: String, message: ChatMessage) {
        val owned = if (message.conversationId.isBlank()) {
            message.copy(conversationId = conversationId)
        } else {
            message
        }
        pending = Target(conversationId, owned)
    }

    /**
     * The message waiting for [conversationId], handed over once.
     *
     * Another conversation's request is left where it is: a chat opened in between
     * must not consume a jump meant for a different one.
     */
    fun take(conversationId: String): ChatMessage? {
        val waiting = pending ?: return null
        if (waiting.conversationId != conversationId) return null
        pending = null
        return waiting.message
    }
}
