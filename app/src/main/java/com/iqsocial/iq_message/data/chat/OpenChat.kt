package com.iqsocial.iq_message.data.chat

/**
 * Which conversation the reader is looking at, if any.
 *
 * One question, asked from a background thread by things that have no business
 * reaching into the composition: a message arriving on the socket needs to know
 * whether it is for the chat already on screen, and the socket listener has no view
 * of what is composed.
 *
 * Deliberately only the open chat, not "is the app in front". Those are different
 * questions and conflating them is what made the floating heads disappear whenever
 * the app was open: a message about another conversation is worth a head whether or
 * not this app happens to be the one on screen, which is how every messenger with
 * heads behaves.
 */
object OpenChat {

    @Volatile
    var id: String? = null
        private set

    fun opened(conversationId: String) {
        id = conversationId
    }

    /**
     * Closes it, but only if it is still the one that was opened.
     *
     * Compose disposes the screen being left *after* the one being entered is
     * composed, so an unguarded clear here would wipe out the chat just opened and
     * put a head over the conversation being read.
     */
    fun closed(conversationId: String) {
        if (id == conversationId) id = null
    }

    fun isOpen(conversationId: String): Boolean =
        conversationId.isNotBlank() && id == conversationId
}
