package com.iqsocial.iq_message.data.push

/**
 * Which messages have already been announced.
 *
 * Exists for one rule in the push spec: an open app is told about a message
 * **twice**, once on the socket and once by FCM, and it must act on it once. The
 * socket is nearly always first, so by the time the push lands the message is
 * usually already in the thread.
 *
 * Nothing here knows which chat is on screen, on purpose. Every message gets its
 * notification, including one in the chat being read: that is the product's choice,
 * and a chat on screen is not a reason to stay quiet.
 *
 * Deliberately a plain object rather than part of a repository. It is read on the
 * FCM service's own thread, with no composition and no coroutine scope around it,
 * and the only thing it has to be is correct when asked.
 */
object SeenMessages {

    /** Message ids already shown, newest last. */
    private val seen = java.util.Collections.synchronizedSet(linkedSetOf<String>())

    /**
     * True the first time a message id is offered, false every time after.
     *
     * Bounded, because a long-lived process would otherwise remember every message
     * it ever saw. Dropping the oldest is safe: a duplicate arrives within seconds
     * of the original, not hundreds of messages later.
     */
    fun firstSight(messageId: String): Boolean {
        if (messageId.isBlank()) return true
        synchronized(seen) {
            if (!seen.add(messageId)) return false
            while (seen.size > MAX_SEEN) {
                val oldest = seen.firstOrNull() ?: break
                seen.remove(oldest)
            }
            return true
        }
    }

    private const val MAX_SEEN = 200
}
