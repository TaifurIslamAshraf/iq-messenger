package com.iqsocial.iq_message.data.chat

import com.iqsocial.iq_message.data.remote.ChatPerson

/**
 * People this app has already been told about, kept for when it is told less later.
 *
 * The case it exists for: a chat opened from search. The search result carries the
 * whole person - name, picture, tick - and opening the conversation hands back an
 * id, which is all that is passed on. The chat screen then asks the server who it is
 * talking to, and for a conversation created a moment ago the answer can come back
 * with the peer's name empty. The screen had nothing to fall back on and drew
 * "Unknown", for somebody whose name was on screen one tap earlier.
 *
 * So what was known is kept rather than thrown away. Not a cache in front of the
 * API - whatever the server says still wins - only an answer for the moments it says
 * nothing.
 *
 * Keyed by conversation because that is what both ends have: the screen is given an
 * id and nothing else, and the search knows the id the moment the conversation is
 * opened.
 *
 * Process-scoped and small. Names and faces of people already on screen, lost when
 * the app is, which is the right lifetime for something only here to cover a gap.
 */
object KnownPeople {

    private val byConversation =
        java.util.Collections.synchronizedMap(mutableMapOf<String, ChatPerson>())

    /** Keeps a person worth remembering. A nameless one is not. */
    fun remember(conversationId: String, person: ChatPerson) {
        if (conversationId.isBlank() || person.fullname.isBlank()) return
        byConversation[conversationId] = person
    }

    fun of(conversationId: String): ChatPerson? = byConversation[conversationId]

    /**
     * Fills in what the server left out.
     *
     * Field by field rather than all or nothing: a reply can carry the uid and the
     * picture and still have no name, and the half that did arrive is the better
     * half to keep - it is current, where this is only remembered.
     */
    fun complete(conversationId: String, peer: ChatPerson?): ChatPerson? {
        val known = of(conversationId) ?: return peer
        peer ?: return known
        return peer.copy(
            uid = peer.uid.ifBlank { known.uid },
            fullname = peer.fullname.ifBlank { known.fullname },
            profilePicture = peer.profilePicture.ifBlank { known.profilePicture },
            badgeKey = peer.badgeKey.ifBlank { known.badgeKey }
        )
    }

    fun clear() {
        byConversation.clear()
    }
}
