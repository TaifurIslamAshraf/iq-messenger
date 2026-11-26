package com.iqsocial.iq_message.ui.screens

import com.iqsocial.iq_message.data.remote.CallRequest
import com.iqsocial.iq_message.data.remote.ConversationAccess
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the newest call request bubble says, from each side. */
class CallRequestStateTest {

    private val closed = ConversationAccess(request = null, canSend = true, canCall = false, peerCanCall = false)
    private fun asked(outgoing: Boolean, pending: Boolean) =
        closed.copy(callRequest = CallRequest(outgoing = outgoing, pending = pending, at = 1))

    @Test
    fun `the person who asked waits, then hears the answer`() {
        assertEquals(CallRequestState.WAITING, callRequestState(fromMe = true, asked(outgoing = true, pending = true)))
        assertEquals(CallRequestState.NOT_NOW, callRequestState(fromMe = true, asked(outgoing = true, pending = false)))
        assertEquals(CallRequestState.ALLOWED, callRequestState(fromMe = true, closed.copy(canCall = true)))
    }

    @Test
    fun `the person asked gets Allow and Not now until they answer`() {
        assertEquals(CallRequestState.ASKING_YOU, callRequestState(fromMe = false, asked(outgoing = false, pending = true)))
        assertEquals(CallRequestState.NOT_NOW, callRequestState(fromMe = false, asked(outgoing = false, pending = false)))
        assertEquals(CallRequestState.ALLOWED, callRequestState(fromMe = false, closed.copy(peerCanCall = true)))
    }
}
