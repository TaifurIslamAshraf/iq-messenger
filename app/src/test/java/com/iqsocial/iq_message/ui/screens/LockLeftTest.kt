package com.iqsocial.iq_message.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** The countdown on the special card while a pick holds. */
class LockLeftTest {

    private val second = 1000L
    private val minute = 60 * second
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `days, hours and minutes while a day or more is left`() {
        assertEquals("2d 0h 0m", lockLeft(2 * day))
        assertEquals("1d 23h 59m", lockLeft(2 * day - 30 * second))
    }

    @Test
    fun `seconds appear once it is under a day`() {
        assertEquals("23h 59m 59s", lockLeft(day - second))
        assertEquals("59m 59s", lockLeft(hour - second))
        assertEquals("0m 1s", lockLeft(1))
    }
}
