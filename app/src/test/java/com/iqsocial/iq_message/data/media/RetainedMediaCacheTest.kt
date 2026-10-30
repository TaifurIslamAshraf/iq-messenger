package com.iqsocial.iq_message.data.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedMediaCacheTest {
    private fun cache(
        owner: CoroutineScope,
        budget: Long = 20,
        maxEntries: Int = 128,
        peek: (String) -> String? = { null },
        fetch: suspend (String) -> String?
    ) = RetainedMediaCache(owner, budget, maxEntries, { _: String -> 10L }, peek, fetch)

    @Test
    fun `scrolling away and back reuses state even after the shared cache evicts the file`() = runBlocking {
        var loads = 0
        val shared = mutableMapOf<String, String>()
        cache(this, peek = { shared[it] }) { key ->
            loads++
            "decoded-$key".also { shared[key] = it }
        }.use { media ->
            val firstRow = media.state("photo")
            media.load("photo")
            yield()
            shared.clear()
            repeat(20) {
                assertSame(firstRow, media.state("photo"))
                media.load("photo")
                assertEquals("decoded-photo", media.state("photo").value)
            }
            assertEquals(1, loads)
        }
    }

    @Test
    fun `recycling during a pending local preview keeps one load alive`() = runBlocking {
        val decoded = CompletableDeferred<String>()
        var loads = 0
        cache(this) { loads++; decoded.await() }.use { media ->
            val firstRow = media.state("local-video")
            val row = launch { media.load("local-video"); awaitCancellation() }
            yield()
            row.cancel()
            row.join()
            repeat(10) { media.load("local-video") }
            decoded.complete("frame")
            yield()
            assertSame(firstRow, media.state("local-video"))
            assertEquals("frame", firstRow.value)
            assertEquals(1, loads)
        }
    }

    @Test
    fun `an already decoded preview is available on the first frame without loading`() = runBlocking {
        var loads = 0
        cache(this, peek = { "cached" }) { loads++; "download" }.use { media ->
            assertEquals("cached", media.state("photo").value)
            media.load("photo")
            yield()
            assertEquals(0, loads)
        }
    }

    @Test
    fun `failed files do not retry every time a row is recycled`() = runBlocking {
        var loads = 0
        cache(this) { loads++; error("unavailable") }.use { media ->
            val firstRow = media.state("missing")
            media.load("missing")
            yield()
            repeat(20) { media.load("missing") }
            yield()
            assertSame(firstRow, media.state("missing"))
            assertNull(firstRow.value)
            assertEquals(1, loads)
        }
    }

    @Test
    fun `old results are bounded without blanking a row that still displays them`() = runBlocking {
        val loads = mutableMapOf<String, Int>()
        cache(this) { key ->
            loads[key] = loads.getOrDefault(key, 0) + 1
            key
        }.use { media ->
            val first = media.state("first")
            for (key in listOf("first", "second", "third")) {
                media.load(key)
                yield()
            }
            assertEquals("first", first.value)
            assertEquals("second", media.state("second").value)
            assertNotSame(first, media.state("first"))
            media.load("first")
            yield()
            assertEquals(2, loads["first"])
            assertEquals(1, loads["second"])
        }
    }

    @Test
    fun `different files have different states and failed entries are bounded`() = runBlocking {
        cache(this, maxEntries = 2) { null }.use { media ->
            val first = media.state("first")
            val second = media.state("second")
            assertNotSame(first, second)
            for (key in listOf("first", "second", "third")) {
                media.load(key)
                yield()
            }
            assertNotSame(first, media.state("first"))
        }
    }

    @Test
    fun `leaving the screen cancels its pending work`() = runBlocking {
        var cancelled = false
        val media = cache(this) {
            try { awaitCancellation() } finally { cancelled = true }
        }
        val state = media.state("photo")
        try {
            media.load("photo")
            yield()
        } finally {
            media.close()
        }
        yield()
        assertTrue(cancelled)
        assertNull(state.value)
    }
}
