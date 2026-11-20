package com.iqsocial.iq_message.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the server's `access` object means, from this account's own side. */
class ConversationAccessTest {

    private fun conversation(access: String?): ChatConversation {
        val tail = access?.let { ""","access":$it""" }.orEmpty()
        return ChatConversation.from(JSONObject("""{"id":"c1","type":"private"$tail}"""))
    }

    @Test
    fun `a conversation with no access object is open both ways`() {
        val access = conversation(null).access
        assertEquals(ConversationAccess.OPEN, access)
        assertTrue(access.canSend)
        assertNull(access.request)
    }

    @Test
    fun `an incoming request can be answered and the reader may still write`() {
        val access = conversation(
            """{"request":"incoming","can_send":true,"can_call":false,"peer_can_call":false}"""
        ).access
        assertEquals(RequestSide.INCOMING, access.request)
        assertTrue(access.canSend)
        assertFalse(access.canCall)
    }

    @Test
    fun `an outgoing request closes the composer`() {
        val access = conversation(
            """{"request":"outgoing","can_send":false,"can_call":false,"peer_can_call":false}"""
        ).access
        assertEquals(RequestSide.OUTGOING, access.request)
        assertFalse(access.canSend)
    }

    @Test
    fun `a rejected request is the rejecting side's and can be accepted later`() {
        val access = conversation(
            """{"request":"rejected","can_send":true,"can_call":false,"peer_can_call":false}"""
        ).access
        assertEquals(RequestSide.REJECTED, access.request)
    }

    @Test
    fun `a null request is a normal chat`() {
        val access = conversation(
            """{"request":null,"can_send":true,"can_call":false,"peer_can_call":false}"""
        ).access
        assertNull(access.request)
        assertTrue(access.canSend)
    }

    @Test
    fun `a request the app does not know is no request`() {
        assertNull(RequestSide.from("something-new"))
        assertNull(RequestSide.from(""))
    }

    @Test
    fun `the stored copy reads back the same access`() {
        val sent = conversation(
            """{"request":"incoming","can_send":true,"can_call":false,"peer_can_call":false}"""
        )
        val again = ChatConversation.from(JSONObject(sent.raw))
        assertEquals(sent.access, again.access)
    }
}
