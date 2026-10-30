package com.iqsocial.iq_message.ui.screens

import com.iqsocial.iq_message.data.BubbleGroup
import com.iqsocial.iq_message.data.Message
import com.iqsocial.iq_message.data.MessageKind
import com.iqsocial.iq_message.data.QuotedMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How messages stack into blocks.
 *
 * The rule that keeps breaking is the one about something arriving in between. It
 * used to hold only because the gap that joins a run (5 minutes) is shorter than the
 * gap that prints a stamp (20) - two numbers in two places with nothing tying them
 * together. These pin the behaviour instead of the coincidence.
 */
class RunsTest {

    private var clock = 1_700_000_000_000L

    private fun text(mine: Boolean = true, after: Long = 1_000L): Message {
        clock += after
        return Message(
            id = "m" + clock,
            text = "hello",
            time = "",
            fromMe = mine,
            createdAt = clock
        )
    }

    private fun photo(after: Long = 1_000L): Message =
        text(after = after).copy(mediaKind = "image")

    private fun groups(items: List<Message>, stamps: List<String?> = items.map { null }) =
        runsIn(items, stamps)

    /* -- the rule that prompted all this ------------------------------------ */

    @Test
    fun `a stamp between two messages splits the run`() {
        val items = listOf(text(), text(), text())
        // The stamp belongs above the middle message.
        val stamps = listOf(null, "11:14 AM", null)

        assertEquals(
            listOf(BubbleGroup.SINGLE, BubbleGroup.FIRST, BubbleGroup.LAST),
            groups(items, stamps)
        )
    }

    @Test
    fun `the message before a stamp ends its run even mid-block`() {
        val items = listOf(text(), text(), text(), text())
        val stamps = listOf(null, null, "SUN AT 8:33 PM", null)

        assertEquals(
            listOf(BubbleGroup.FIRST, BubbleGroup.LAST, BubbleGroup.FIRST, BubbleGroup.LAST),
            groups(items, stamps)
        )
    }

    @Test
    fun `a stamp on a lone message leaves it single`() {
        val items = listOf(text(), text())
        val stamps = listOf(null, "11:14 AM")
        assertEquals(listOf(BubbleGroup.SINGLE, BubbleGroup.SINGLE), groups(items, stamps))
    }

    /* -- the rest of the rule ------------------------------------------------ */

    @Test
    fun `consecutive messages from one person join`() {
        val items = listOf(text(), text(), text())
        assertEquals(
            listOf(BubbleGroup.FIRST, BubbleGroup.MIDDLE, BubbleGroup.LAST),
            groups(items)
        )
    }

    @Test
    fun `the other person starts a run of their own`() {
        val items = listOf(text(mine = true), text(mine = false), text(mine = false))
        assertEquals(
            listOf(BubbleGroup.SINGLE, BubbleGroup.FIRST, BubbleGroup.LAST),
            groups(items)
        )
    }

    @Test
    fun `an hour apart divides, because an hour apart carries a stamp`() {
        val items = listOf(text(), text(after = 60 * 60 * 1000L))
        val stamps = listOf(null, "SUN AT 8:33 PM")
        assertEquals(listOf(BubbleGroup.SINGLE, BubbleGroup.SINGLE), groups(items, stamps))
    }

    @Test
    fun `a gap too small to earn a stamp does not divide`() {
        // The break would have nothing on screen explaining it, which reads as a
        // fault rather than as a pause.
        val items = listOf(text(), text(after = 6 * 60 * 1000L), text())
        assertEquals(
            listOf(BubbleGroup.FIRST, BubbleGroup.MIDDLE, BubbleGroup.LAST),
            groups(items)
        )
    }

    @Test
    fun `photos never join, not even to each other`() {
        val items = listOf(photo(), photo(), photo())
        assertEquals(
            listOf(BubbleGroup.SINGLE, BubbleGroup.SINGLE, BubbleGroup.SINGLE),
            groups(items)
        )
    }

    @Test
    fun `a photo does not join the words around it`() {
        val items = listOf(text(), photo(), text())
        assertEquals(
            listOf(BubbleGroup.SINGLE, BubbleGroup.SINGLE, BubbleGroup.SINGLE),
            groups(items)
        )
    }

    @Test
    fun `a reply starts its own run`() {
        val quoted = QuotedMessage(author = "You replied to Sam", text = "ok")
        val items = listOf(text(), text().copy(quoted = quoted), text())
        // The reply breaks the run above it. What comes after may still join the
        // reply, so the break is on one side only.
        assertEquals(
            listOf(BubbleGroup.SINGLE, BubbleGroup.FIRST, BubbleGroup.LAST),
            groups(items)
        )
    }

    @Test
    fun `a voice note does not join words`() {
        val voice = text().copy(kind = MessageKind.VOICE)
        val items = listOf(text(), voice, text())
        assertEquals(
            listOf(BubbleGroup.SINGLE, BubbleGroup.SINGLE, BubbleGroup.SINGLE),
            groups(items)
        )
    }

    @Test
    fun `one message on its own is single`() {
        assertEquals(listOf(BubbleGroup.SINGLE), groups(listOf(text())))
    }

    @Test
    fun `an empty thread has no runs`() {
        assertEquals(emptyList<BubbleGroup>(), groups(emptyList()))
    }
}
