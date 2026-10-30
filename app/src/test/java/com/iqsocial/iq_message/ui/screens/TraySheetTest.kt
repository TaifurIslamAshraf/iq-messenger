package com.iqsocial.iq_message.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The photo tray's drag behaviour, checked without a device.
 *
 * Deltas and velocities are *screen* quantities, positive downwards, so a finger
 * moving up is negative. Getting that backwards would make the sheet shrink when
 * swiped up, which is the failure these cover first.
 */
class TraySheetTest {

    private val short = 400f
    private val tall = 740f
    private val middle = (short + tall) / 2f

    /* -- dragging ---------------------------------------------------------- */

    @Test
    fun `swiping up grows the sheet`() {
        val after = TraySheet.heightAfterDrag(short, delta = -100f, short = short, tall = tall)
        assertEquals(500f, after, 0.01f)
    }

    @Test
    fun `swiping down shrinks the sheet`() {
        val after = TraySheet.heightAfterDrag(600f, delta = 100f, short = short, tall = tall)
        assertEquals(500f, after, 0.01f)
    }

    @Test
    fun `cannot be dragged past the tall stop`() {
        val after = TraySheet.heightAfterDrag(700f, delta = -9999f, short = short, tall = tall)
        assertEquals(tall, after, 0.01f)
    }

    @Test
    fun `can be pushed all the way down, but no further`() {
        // Closed is a stop, so the sheet follows the finger off the bottom rather
        // than sticking at its smallest while the hand keeps going.
        val after = TraySheet.heightAfterDrag(420f, delta = 9999f, short = short, tall = tall)
        assertEquals(0f, after, 0.01f)
    }

    /* -- where a release lands --------------------------------------------- */

    @Test
    fun `a flick up opens it even from the very bottom`() {
        val stop = TraySheet.stopFor(short, velocity = -800f, short = short, tall = tall)
        assertEquals(tall, stop, 0.01f)
    }

    @Test
    fun `a flick down from the top only shortens it`() {
        // Not closed: from full height that gesture nearly always means "give me the
        // conversation back", and closing would lose the photos in one movement.
        val stop = TraySheet.stopFor(tall, velocity = 800f, short = short, tall = tall)
        assertEquals(short, stop, 0.01f)
    }

    @Test
    fun `a flick down from the short stop closes it`() {
        val stop = TraySheet.stopFor(short, velocity = 800f, short = short, tall = tall)
        assertEquals(0f, stop, 0.01f)
    }

    @Test
    fun `dragged well below the short stop and let go, it closes`() {
        val stop = TraySheet.stopFor(short * 0.5f, velocity = 0f, short = short, tall = tall)
        assertEquals(0f, stop, 0.01f)
    }

    @Test
    fun `nudged down and let go, it springs back`() {
        // A small push is not a dismissal; it has to be most of the way down.
        val stop = TraySheet.stopFor(short * 0.9f, velocity = 0f, short = short, tall = tall)
        assertEquals(short, stop, 0.01f)
    }

    @Test
    fun `released slowly past halfway it opens`() {
        val stop = TraySheet.stopFor(middle + 1f, velocity = 0f, short = short, tall = tall)
        assertEquals(tall, stop, 0.01f)
    }

    @Test
    fun `released slowly before halfway it falls back`() {
        val stop = TraySheet.stopFor(middle - 1f, velocity = 0f, short = short, tall = tall)
        assertEquals(short, stop, 0.01f)
    }

    @Test
    fun `a drag too slow to count as a throw is decided by position`() {
        // Just under the threshold, and sitting low: the slow upward drag must not
        // open it, or every hesitant touch would expand the sheet.
        val stop = TraySheet.stopFor(short + 20f, velocity = -(TraySheet.FLING - 1f), short, tall)
        assertEquals(short, stop, 0.01f)
    }

    /* -- handing the grid's scroll to the sheet ---------------------------- */

    @Test
    fun `scrolling up in the photos raises the sheet before the grid moves`() {
        val taken = TraySheet.preScrollTaken(short, delta = -60f, short = short, tall = tall)
        assertEquals(-60f, taken, 0.01f)
    }

    @Test
    fun `once fully open the photos scroll instead of the sheet`() {
        val taken = TraySheet.preScrollTaken(tall, delta = -60f, short = short, tall = tall)
        assertEquals(0f, taken, 0.01f)
    }

    @Test
    fun `the sheet only takes what it can still use`() {
        // 40px of room left, 60px of scroll: the sheet takes 40 and the grid gets 20.
        val taken = TraySheet.preScrollTaken(tall - 40f, delta = -60f, short = short, tall = tall)
        assertEquals(-40f, taken, 0.01f)
    }

    @Test
    fun `the sheet never takes a downward scroll before the grid`() {
        val taken = TraySheet.preScrollTaken(tall, delta = 60f, short = short, tall = tall)
        assertEquals(0f, taken, 0.01f)
    }

    @Test
    fun `scrolling down with the grid at its top lowers the sheet`() {
        val taken = TraySheet.postScrollTaken(tall, delta = 60f, short = short, tall = tall)
        assertEquals(60f, taken, 0.01f)
    }

    @Test
    fun `an already short sheet does not absorb a downward scroll`() {
        val taken = TraySheet.postScrollTaken(short, delta = 60f, short = short, tall = tall)
        assertEquals(0f, taken, 0.01f)
    }

    @Test
    fun `scrolling the photos can shrink the sheet but never close it`() {
        // Closing needs a hand on the sheet, not a flick of the grid inside it.
        val taken = TraySheet.postScrollTaken(short + 10f, delta = 900f, short = short, tall = tall)
        assertEquals(10f, taken, 0.01f)
    }

    @Test
    fun `left-over upward scroll is not taken after the grid`() {
        // The grid is at its end; stealing this would make the sheet grow on a
        // gesture the person meant for the photos.
        val taken = TraySheet.postScrollTaken(600f, delta = -60f, short = short, tall = tall)
        assertEquals(0f, taken, 0.01f)
    }

    /* -- who owns a fling -------------------------------------------------- */

    @Test
    fun `mid-travel the fling belongs to the sheet`() {
        assertTrue(TraySheet.ownsFling(middle, short, tall))
    }

    @Test
    fun `at either stop the fling belongs to the grid`() {
        assertFalse(TraySheet.ownsFling(short, short, tall))
        assertFalse(TraySheet.ownsFling(tall, short, tall))
    }

    /* -- the whole gesture, end to end ------------------------------------- */

    @Test
    fun `a swipe up from rest lands open`() {
        var height = short
        repeat(12) {                       // twelve frames of a finger moving up
            height = TraySheet.heightAfterDrag(height, delta = -18f, short = short, tall = tall)
        }
        assertTrue("sheet should have grown, was $height", height > short)
        val stop = TraySheet.stopFor(height, velocity = -600f, short = short, tall = tall)
        assertEquals(tall, stop, 0.01f)
    }

    @Test
    fun `a short tug that is let go returns to rest`() {
        var height = short
        repeat(3) {
            height = TraySheet.heightAfterDrag(height, delta = -10f, short = short, tall = tall)
        }
        val stop = TraySheet.stopFor(height, velocity = -50f, short = short, tall = tall)
        assertEquals(short, stop, 0.01f)
    }

    @Test
    fun `dragging back down from open passes through the short stop`() {
        var height = tall
        repeat(20) {
            height = TraySheet.heightAfterDrag(height, delta = 20f, short = short, tall = tall)
        }
        // 740 - 400 = 340 of travel, so twenty 20px steps land above the short stop.
        assertEquals(tall - 400f, height, 0.01f)
        assertEquals(short, TraySheet.stopFor(height, 0f, short, tall), 0.01f)
    }

    @Test
    fun `a long drag down ends closed`() {
        var height = tall
        repeat(40) {
            height = TraySheet.heightAfterDrag(height, delta = 20f, short = short, tall = tall)
        }
        assertEquals(0f, height, 0.01f)
        assertEquals(0f, TraySheet.stopFor(height, 0f, short, tall), 0.01f)
    }
}
