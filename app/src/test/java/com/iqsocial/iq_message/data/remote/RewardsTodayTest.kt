package com.iqsocial.iq_message.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `GET /v1/rewards/today` as the screen reads it: stars, and which slots show Collect. */
class RewardsTodayTest {

    private fun slot(status: String, collectedAt: String) = """
        {"kind":"normal","person":{"uid":"u1","fullname":"Rahim"},"status":"$status",
         "talk_ms":600000,"target_ms":600000,"points":10,"completed_at":1,"collected_at":$collectedAt}
    """

    private fun today(items: String, special: String) = RewardsToday.from(
        JSONObject(
            """{"day":"2026-11-28","server_now":5,"points":{"balance":85},
               "slots":{"used":2,"total":7,"normal_free":5,"items":[$items]},"special":$special}"""
        )
    )

    @Test
    fun `reads the balance and each slot's worth as stars`() {
        val read = today(slot("completed", "null"), """{"person":null,"status":"not_started","points":60}""")

        assertEquals(85, read.stars)
        assertEquals(10, read.slots.single().stars)
        assertEquals(60, read.special.stars)
        assertEquals(5, read.normalFree)
    }

    @Test
    fun `shows Collect only on a completed slot that hasn't been collected`() {
        val ready = today(slot("completed", "null"), "{}").slots.single()
        val collected = today(slot("completed", "1700000000000"), "{}").slots.single()
        val running = today(slot("in_progress", "null"), "{}").slots.single()

        assertTrue(ready.readyToCollect)
        assertFalse(collected.readyToCollect)
        assertEquals(1700000000000L, collected.collectedAt)
        assertFalse(running.readyToCollect)
    }

    @Test
    fun `shows Collect on the special card only while someone is picked and it is uncollected`() {
        val person = """"person":{"uid":"u2","fullname":"Salma"}"""
        val ready = today("", """{$person,"status":"completed","points":60,"collected_at":null}""").special
        val collected = today("", """{$person,"status":"completed","points":60,"collected_at":9}""").special
        val nobody = today("", """{"person":null,"status":"completed","points":60}""").special

        assertTrue(ready.readyToCollect)
        assertFalse(collected.readyToCollect)
        assertFalse(nobody.readyToCollect)
    }
}
