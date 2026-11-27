package com.iqsocial.iq_message.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A row of `GET /v1/people`, as the server sends it and as this phone keeps it. */
class SavedPersonTest {

    private val fromPhoneBook = JSONObject(
        """{"uid":"u1","fullname":" Rahim Uddin ","profile_picture":"https://cdn/p.jpg","verified":1,
           "badge_key":"","sources":["phone","qr"],"phone":"+8801712345678","added_at":1731427200000}"""
    )

    @Test
    fun `reads a person with how they got on the list and their saved number`() {
        val saved = SavedPerson.from(fromPhoneBook)

        assertEquals("u1", saved.uid)
        assertEquals("Rahim Uddin", saved.person.fullname)
        assertEquals(listOf("phone", "qr"), saved.sources)
        assertEquals("+8801712345678", saved.phone)
        assertEquals(1731427200000L, saved.addedAt)
        assertTrue(saved.fromPhoneBook)
    }

    @Test
    fun `someone added by QR code has no number`() {
        val saved = SavedPerson.from(JSONObject("""{"uid":"u2","sources":["qr"],"phone":null,"added_at":5}"""))

        assertNull(saved.phone)
        assertFalse(saved.fromPhoneBook)
        assertEquals("", saved.person.fullname)
    }

    @Test
    fun `the copy kept on the phone reads back as the same person`() {
        val saved = SavedPerson.from(fromPhoneBook)
        assertEquals(saved, SavedPerson.from(saved.toJson()))

        val byQr = SavedPerson.from(JSONObject("""{"uid":"u2","sources":["qr"],"phone":null,"added_at":5}"""))
        assertEquals(byQr, SavedPerson.from(byQr.toJson()))
    }
}
